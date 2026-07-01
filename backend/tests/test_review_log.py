import importlib
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

from fastapi.testclient import TestClient  # noqa: E402

from test_apkg_reader import build_fixture_apkg  # noqa: E402


def _make_client(tmp_path, monkeypatch):
    """Same harness as test_settings_api.py: real FastAPI app against a
    scratch config/data dir."""
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


def _review_log_rows(main_module, guid=None):
    conn = main_module.app.state.store.conn
    query = "SELECT guid, quality, time_spent_ms, prev_interval_days, new_interval_days, prev_reps, new_reps FROM review_log"
    params = ()
    if guid is not None:
        query += " WHERE guid = ?"
        params = (guid,)
    return conn.execute(query, params).fetchall()


def _first_due_guid(client):
    due = client.get("/api/due", params={"path": ""}).json()
    return due[0]["guid"]


def test_review_inserts_exactly_one_review_log_row(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    guid = _first_due_guid(client)
    assert _review_log_rows(main_module, guid) == []

    resp = client.post(f"/api/cards/{guid}/review", json={"quality": 4, "time_spent_ms": 2500})
    assert resp.status_code == 200

    rows = _review_log_rows(main_module, guid)
    assert len(rows) == 1
    row_guid, quality, time_spent_ms, prev_iv, new_iv, prev_reps, new_reps = rows[0]
    assert row_guid == guid
    assert quality == 4
    assert time_spent_ms == 2500
    assert prev_reps == 0
    assert new_reps == 1
    assert prev_iv == 0.0
    assert new_iv == 3.0  # DEFAULT_SETTINGS good_days, first pass, quality=4 (Good)


def test_review_log_grows_monotonically_and_never_overwrites(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    guid = _first_due_guid(client)

    for i, quality in enumerate([4, 4, 5], start=1):
        client.post(f"/api/cards/{guid}/review", json={"quality": quality, "time_spent_ms": 1000 * i})
        rows = _review_log_rows(main_module, guid)
        assert len(rows) == i

    rows = _review_log_rows(main_module, guid)
    assert len(rows) == 3
    # prev/new interval and reps chain correctly across the 3 reviews, i.e.
    # nothing got overwritten — each row keeps its own prev/new snapshot.
    assert [r[5] for r in rows] == [0, 1, 2]  # prev_reps
    assert [r[6] for r in rows] == [1, 2, 3]  # new_reps

    # card_state itself is still upserted as a single current-state row
    state = client.get(f"/api/cards/{guid}").json()
    assert state["reps"] == 3


def test_time_spent_ms_stored_and_retrievable(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    guid = _first_due_guid(client)
    client.post(f"/api/cards/{guid}/review", json={"quality": 4, "time_spent_ms": 4242})

    rows = _review_log_rows(main_module, guid)
    assert rows[0][2] == 4242


def test_review_without_time_spent_ms_still_works(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "fixture.apkg")

    guid = _first_due_guid(client)
    resp = client.post(f"/api/cards/{guid}/review", json={"quality": 4})
    assert resp.status_code == 200

    rows = _review_log_rows(main_module, guid)
    assert len(rows) == 1
    assert rows[0][2] is None
