import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

from fastapi.testclient import TestClient  # noqa: E402

from test_apkg_reader import build_fixture_apkg  # noqa: E402


def _make_client(tmp_path, monkeypatch):
    """Same harness as test_stats.py / test_settings_api.py."""
    monkeypatch.setenv("INFERCOM_API_KEY", "test-key")

    data_dir = tmp_path / "data"
    apkg_dir = tmp_path / "apkg"
    pdf_dir = tmp_path / "pdf"
    apkg_dir.mkdir()
    pdf_dir.mkdir()

    test_cfg = {
        "paths": {
            "apkg_dir": str(apkg_dir),
            "pdf_dir": str(pdf_dir),
            "data_dir": str(data_dir),
        },
        "infercom": {"base_url": "https://example.invalid/v1", "explain_model": "test"},
        "server": {"host": "127.0.0.1", "port": 8420},
        "explain": {"max_pdf_context_chars": 1000},
    }

    import config as config_module

    monkeypatch.setattr(config_module, "load_config", lambda *a, **k: test_cfg)

    import main as main_module

    importlib.reload(main_module)
    return main_module, TestClient(main_module.app)


def _due_guids(client):
    due = client.get("/api/due", params={"path": ""}).json()
    return [card["guid"] for card in due]


def test_create_exam_and_list_with_no_reviews(tmp_path, monkeypatch):
    _, client = _make_client(tmp_path, monkeypatch)

    resp = client.post(
        "/api/exams",
        json={"deck_path": "Statistical Inference", "expected_results_date": "2026-01-01"},
    )
    assert resp.status_code == 200
    created = resp.json()
    assert created["deck_path"] == "Statistical Inference"
    assert created["expected_results_date"] == "2026-01-01"
    assert created["grade"] is None
    assert "id" in created

    resp = client.get("/api/exams")
    assert resp.status_code == 200
    rows = resp.json()
    assert len(rows) == 1
    row = rows[0]
    assert row["id"] == created["id"]
    assert row["success_rate"] is None  # no review_log rows for this subject
    assert row["total_time_spent_ms"] == 0


def test_create_rejects_empty_deck_path(tmp_path, monkeypatch):
    _, client = _make_client(tmp_path, monkeypatch)
    resp = client.post(
        "/api/exams", json={"deck_path": "  ", "expected_results_date": "2026-01-01"}
    )
    assert resp.status_code == 400


def test_set_grade_updates_row(tmp_path, monkeypatch):
    _, client = _make_client(tmp_path, monkeypatch)

    created = client.post(
        "/api/exams",
        json={"deck_path": "Statistical Inference", "expected_results_date": "2026-01-01"},
    ).json()

    resp = client.put(f"/api/exams/{created['id']}", json={"grade": 15.5})
    assert resp.status_code == 200
    assert resp.json()["grade"] == 15.5

    rows = client.get("/api/exams").json()
    assert rows[0]["grade"] == 15.5


def test_set_grade_unknown_id_returns_404(tmp_path, monkeypatch):
    _, client = _make_client(tmp_path, monkeypatch)
    resp = client.put("/api/exams/999", json={"grade": 10.0})
    assert resp.status_code == 404


def test_correlation_matches_hand_computed_success_rate_and_time(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    guids = _due_guids(client)
    assert len(guids) == 2
    guid_a, guid_b = guids
    # Both cards belong to the same subject/root deck folder in the fixture
    # ("Statistical Inference" — see test_apkg_reader.build_fixture_apkg).

    # guid_a: quality 4 (success, 1000ms), quality 1 (failure, 2000ms)
    client.post(f"/api/cards/{guid_a}/review", json={"quality": 4, "time_spent_ms": 1000})
    client.post(f"/api/cards/{guid_a}/review", json={"quality": 1, "time_spent_ms": 2000})
    # guid_b: quality 5 (success, 500ms)
    client.post(f"/api/cards/{guid_b}/review", json={"quality": 5, "time_spent_ms": 500})

    # Hand-computed: 3 reviews total, 2 successes (quality>=4) -> 2/3;
    # total time = 1000 + 2000 + 500 = 3500ms.
    exam = client.post(
        "/api/exams",
        json={"deck_path": "Statistical Inference", "expected_results_date": "2026-01-01"},
    ).json()

    rows = client.get("/api/exams").json()
    assert len(rows) == 1
    row = rows[0]
    assert row["id"] == exam["id"]
    assert row["success_rate"] == 2 / 3
    assert row["total_time_spent_ms"] == 3500
    assert row["grade"] is None  # settled decision: not composited with the above


def test_correlation_ignores_unrelated_subject(tmp_path, monkeypatch):
    """An exam entry for a subject with no matching reviews must report
    success_rate=None / total_time_spent_ms=0, not another subject's data."""
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    guid_a = _due_guids(client)[0]
    client.post(f"/api/cards/{guid_a}/review", json={"quality": 5, "time_spent_ms": 100})

    exam = client.post(
        "/api/exams",
        json={"deck_path": "Some Other Subject", "expected_results_date": "2026-01-01"},
    ).json()

    row = next(r for r in client.get("/api/exams").json() if r["id"] == exam["id"])
    assert row["success_rate"] is None
    assert row["total_time_spent_ms"] == 0
