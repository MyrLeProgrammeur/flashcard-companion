import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

from fastapi.testclient import TestClient  # noqa: E402

from test_apkg_reader import build_fixture_apkg  # noqa: E402

DEFAULT_SETTINGS = {
    "again_days": 0.0,
    "hard_days": 1.0,
    "good_days": 3.0,
    "easy_days": 7.0,
    "easy_bonus": 1.3,
    "notify_hour": 9.0,
}


def _make_client(tmp_path, monkeypatch):
    """Boots the real FastAPI app against a scratch config/data dir, the way
    Batch 1 exercised /api/health — real backend is Termux-only, so paths are
    monkeypatched to a tmp_path instead."""
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


def test_settings_get_returns_defaults(tmp_path, monkeypatch):
    _, client = _make_client(tmp_path, monkeypatch)
    resp = client.get("/api/settings")
    assert resp.status_code == 200
    assert resp.json() == DEFAULT_SETTINGS


def test_settings_put_then_get_reflects_change(tmp_path, monkeypatch):
    _, client = _make_client(tmp_path, monkeypatch)
    put_resp = client.put("/api/settings", json={"good_days": 5.0, "easy_bonus": 2.0})
    assert put_resp.status_code == 200
    assert put_resp.json()["good_days"] == 5.0
    assert put_resp.json()["easy_bonus"] == 2.0

    get_resp = client.get("/api/settings")
    assert get_resp.json()["good_days"] == 5.0
    assert get_resp.json()["easy_bonus"] == 2.0
    # untouched knobs keep their default
    assert get_resp.json()["hard_days"] == 1.0


def test_settings_put_rejects_invalid_values(tmp_path, monkeypatch):
    _, client = _make_client(tmp_path, monkeypatch)
    resp = client.put("/api/settings", json={"good_days": -1.0})
    assert resp.status_code == 400

    resp = client.put("/api/settings", json={"easy_bonus": 0.5})
    assert resp.status_code == 400


def test_notify_hour_put_then_get_round_trips(tmp_path, monkeypatch):
    _, client = _make_client(tmp_path, monkeypatch)
    put_resp = client.put("/api/settings", json={"notify_hour": 20})
    assert put_resp.status_code == 200
    assert put_resp.json()["notify_hour"] == 20.0

    get_resp = client.get("/api/settings")
    assert get_resp.json()["notify_hour"] == 20.0
    # untouched SM-2 knobs keep their default alongside it
    assert get_resp.json()["hard_days"] == 1.0


def test_notify_hour_rejects_out_of_range(tmp_path, monkeypatch):
    _, client = _make_client(tmp_path, monkeypatch)
    resp = client.put("/api/settings", json={"notify_hour": 24})
    assert resp.status_code == 400

    resp = client.put("/api/settings", json={"notify_hour": -1})
    assert resp.status_code == 400


def test_due_count_matches_due_list_length(tmp_path, monkeypatch):
    """/api/due/count must never diverge from /api/due (Batch 7 — shared
    _due_cards_in_scope query, no duplicated due-computation)."""
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    due_resp = client.get("/api/due", params={"path": ""})
    count_resp = client.get("/api/due/count")
    assert count_resp.status_code == 200
    assert count_resp.json() == {"due": len(due_resp.json())}


def test_due_count_is_zero_with_no_cards(tmp_path, monkeypatch):
    _, client = _make_client(tmp_path, monkeypatch)
    resp = client.get("/api/due/count")
    assert resp.status_code == 200
    assert resp.json() == {"due": 0}


def test_fresh_card_previews_are_4_distinct_values(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    resp = client.get("/api/due", params={"path": ""})
    assert resp.status_code == 200
    cards = resp.json()
    assert len(cards) == 2

    previews = cards[0]["previews"]
    assert len(set(previews.values())) == 4


def test_put_settings_changes_previews_and_real_review(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    due_before = client.get("/api/due", params={"path": ""}).json()
    guid = due_before[0]["guid"]
    good_preview_before = due_before[0]["previews"]["good"]

    client.put("/api/settings", json={"good_days": 20.0})

    due_after = client.get("/api/due", params={"path": ""}).json()
    good_preview_after = next(c for c in due_after if c["guid"] == guid)["previews"]["good"]
    assert good_preview_after != good_preview_before
    assert good_preview_after == "20 j"

    review_resp = client.post(f"/api/cards/{guid}/review", json={"quality": 4})
    assert review_resp.status_code == 200
    assert review_resp.json()["interval_days"] == 20.0
