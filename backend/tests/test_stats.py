import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

from fastapi.testclient import TestClient  # noqa: E402

from test_apkg_reader import build_fixture_apkg  # noqa: E402


def _make_client(tmp_path, monkeypatch):
    """Same harness as test_settings_api.py / test_review_log.py."""
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


def test_overview_with_zero_reviews_does_not_crash(tmp_path, monkeypatch):
    _, client = _make_client(tmp_path, monkeypatch)
    resp = client.get("/api/stats/overview")
    assert resp.status_code == 200
    body = resp.json()
    assert body["total_reviews"] == 0
    assert body["total_time_spent_ms"] == 0
    assert body["success_rate"] == 0.0
    assert body["per_day"] == []


def test_cards_with_zero_reviews_returns_empty_list(tmp_path, monkeypatch):
    _, client = _make_client(tmp_path, monkeypatch)
    resp = client.get("/api/stats/cards")
    assert resp.status_code == 200
    assert resp.json() == []


def test_overview_after_mixed_reviews(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    guid = _due_guids(client)[0]

    # 2 successes (quality 4, 5) + 1 failure (quality 1)
    client.post(f"/api/cards/{guid}/review", json={"quality": 4, "time_spent_ms": 1000})
    client.post(f"/api/cards/{guid}/review", json={"quality": 1, "time_spent_ms": 2000})
    client.post(f"/api/cards/{guid}/review", json={"quality": 5, "time_spent_ms": None})

    resp = client.get("/api/stats/overview")
    body = resp.json()
    assert body["total_reviews"] == 3
    assert body["total_time_spent_ms"] == 3000
    assert body["success_rate"] == 2 / 3

    assert len(body["per_day"]) == 1
    assert body["per_day"][0]["count"] == 3
    assert len(body["per_day"][0]["date"]) == 10  # YYYY-MM-DD


def test_cards_endpoint_aggregates_per_guid(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    guids = _due_guids(client)
    assert len(guids) == 2
    guid_a, guid_b = guids

    # guid_a: two reviews, one with NULL time_spent_ms
    client.post(f"/api/cards/{guid_a}/review", json={"quality": 4, "time_spent_ms": 1000})
    client.post(f"/api/cards/{guid_a}/review", json={"quality": 1})

    # guid_b: single review, NULL time_spent
    client.post(f"/api/cards/{guid_b}/review", json={"quality": 5})

    resp = client.get("/api/stats/cards")
    assert resp.status_code == 200
    rows = {row["guid"]: row for row in resp.json()}
    assert set(rows) == {guid_a, guid_b}

    row_a = rows[guid_a]
    assert row_a["review_count"] == 2
    assert row_a["success_rate"] == 0.5
    assert row_a["avg_time_spent_ms"] == 1000.0  # NULL ignored, not treated as 0
    assert row_a["total_time_spent_ms"] == 1000
    assert row_a["last_quality"] == 1

    row_b = rows[guid_b]
    assert row_b["review_count"] == 1
    assert row_b["success_rate"] == 1.0
    assert row_b["avg_time_spent_ms"] is None
    assert row_b["total_time_spent_ms"] == 0
    assert row_b["last_quality"] == 5


def test_export_json_returns_parseable_rows(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    guid = _due_guids(client)[0]
    client.post(f"/api/cards/{guid}/review", json={"quality": 4, "time_spent_ms": 100})
    client.post(f"/api/cards/{guid}/review", json={"quality": 5, "time_spent_ms": 200})

    resp = client.get("/api/stats/export", params={"format": "json"})
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("application/json")
    assert "attachment" in resp.headers["content-disposition"]

    rows = resp.json()
    assert isinstance(rows, list)
    assert len(rows) == 2
    assert rows[0]["guid"] == guid
    assert set(rows[0].keys()) == {
        "id", "guid", "reviewed_at", "quality", "time_spent_ms",
        "prev_interval_days", "new_interval_days", "prev_reps", "new_reps",
    }


def test_export_csv_returns_correct_row_count(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    guid = _due_guids(client)[0]
    client.post(f"/api/cards/{guid}/review", json={"quality": 4, "time_spent_ms": 100})
    client.post(f"/api/cards/{guid}/review", json={"quality": 5, "time_spent_ms": 200})
    client.post(f"/api/cards/{guid}/review", json={"quality": 1})

    resp = client.get("/api/stats/export", params={"format": "csv"})
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("text/csv")
    assert "attachment" in resp.headers["content-disposition"]

    lines = resp.text.strip("\r\n").split("\r\n")
    header = lines[0].split(",")
    assert header == [
        "id", "guid", "reviewed_at", "quality", "time_spent_ms",
        "prev_interval_days", "new_interval_days", "prev_reps", "new_reps",
    ]
    assert len(lines) == 1 + 3  # header + 3 review rows
