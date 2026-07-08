import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

from fastapi.testclient import TestClient  # noqa: E402

from test_apkg_reader import build_fixture_apkg  # noqa: E402


def _make_client(tmp_path, monkeypatch):
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
        "infercom": {"base_url": "https://example.invalid/v1", "explain_model": "cfg-model"},
        "server": {"host": "127.0.0.1", "port": 8420},
        "explain": {"max_pdf_context_chars": 1000},
    }

    import config as config_module

    monkeypatch.setattr(config_module, "load_config", lambda *a, **k: test_cfg)

    import main as main_module

    importlib.reload(main_module)
    return main_module, TestClient(main_module.app)


def _first_due_guid(client):
    due = client.get("/api/due", params={"path": ""}).json()
    return due[0]["guid"]


def _feedback_rows(main_module, guid=None):
    conn = main_module.app.state.store.conn
    query = "SELECT guid, lang, model, vote, grounded, deck_name, surface, created_at FROM explain_feedback"
    params = ()
    if guid is not None:
        query += " WHERE guid = ?"
        params = (guid,)
    return conn.execute(query, params).fetchall()


def test_store_writes_all_fields_and_is_append_only(tmp_path, monkeypatch):
    main_module, _ = _make_client(tmp_path, monkeypatch)
    store = main_module.app.state.store

    snap = store.save_explain_feedback(
        guid="g1", lang="fr", model="m1", vote=1, grounded=1, deck_name="Deck::Sub"
    )
    assert snap == {
        "vote": 1,
        "grounded": 1,
        "model": "m1",
        "deck_name": "Deck::Sub",
        "created_at": snap["created_at"],
    }
    assert snap["created_at"]

    store.save_explain_feedback(
        guid="g1", lang="fr", model="m1", vote=-1, grounded=0, deck_name="Deck::Sub"
    )

    rows = _feedback_rows(main_module, "g1")
    assert len(rows) == 2  # append-only: two votes on same (guid, lang)
    guid, lang, model, vote, grounded, deck_name, surface, created_at = rows[0]
    assert (guid, lang, model, vote, grounded, deck_name, surface) == (
        "g1", "fr", "m1", 1, 1, "Deck::Sub", "explain",
    )
    assert created_at


def test_endpoint_200_grounded_and_model_from_cache(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")
    guid = _first_due_guid(client)

    store = main_module.app.state.store
    store.save_explanation(
        f"{guid}\x1ffr", "cached text", ["chap1.pdf"], "cache-model"
    )

    resp = client.post(f"/api/cards/{guid}/explain/feedback", json={"vote": 1, "lang": "fr"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["vote"] == 1
    assert body["grounded"] == 1
    assert body["model"] == "cache-model"
    assert body["deck_name"]
    assert body["created_at"]

    rows = _feedback_rows(main_module, guid)
    assert len(rows) == 1


def test_endpoint_card_only_cache_gives_grounded_zero(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")
    guid = _first_due_guid(client)

    store = main_module.app.state.store
    store.save_explanation(f"{guid}\x1ffr", "cached text", [], "cache-model")

    resp = client.post(f"/api/cards/{guid}/explain/feedback", json={"vote": -1, "lang": "fr"})
    assert resp.status_code == 200
    assert resp.json()["grounded"] == 0


def test_endpoint_empty_cache_grounded_null_model_from_config(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")
    guid = _first_due_guid(client)

    resp = client.post(f"/api/cards/{guid}/explain/feedback", json={"vote": 1, "lang": "fr"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["grounded"] is None
    assert body["model"] == "cfg-model"


def test_endpoint_invalid_vote_400(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")
    guid = _first_due_guid(client)

    resp = client.post(f"/api/cards/{guid}/explain/feedback", json={"vote": 0, "lang": "fr"})
    assert resp.status_code == 400
    assert _feedback_rows(main_module) == []


def test_endpoint_unknown_guid_404(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    resp = client.post("/api/cards/nope/explain/feedback", json={"vote": 1, "lang": "fr"})
    assert resp.status_code == 404
    assert _feedback_rows(main_module) == []
