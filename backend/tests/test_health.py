import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

from test_explain_feedback import _make_client  # noqa: E402


class _LiveAI:
    class _Models:
        def list(self):
            return []

    models = _Models()

    def with_options(self, **_):
        return self


class _DeadAI:
    def with_options(self, **_):
        raise RuntimeError("no internet")


def test_health_ok_when_ai_and_storage_answer(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    main_module.app.state.infercom_client = _LiveAI()
    Path(main_module.app.state.cfg["paths"]["apkg_dir"], "f.apkg").write_bytes(b"x")

    body = client.get("/api/health").json()

    assert body == {"status": "ok", "ai": "ok", "decks": "ok"}


def test_health_degrades_when_the_deck_folder_is_unreadable(tmp_path, monkeypatch):
    """The bug this exists for: uvicorn started outside Termux's context could
    not read Android shared storage, every deck route 500'd, and health said ok."""
    main_module, client = _make_client(tmp_path, monkeypatch)
    main_module.app.state.infercom_client = _LiveAI()

    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    monkeypatch.setattr(
        Path, "iterdir", lambda self: (_ for _ in ()).throw(PermissionError(13, "denied"))
    )

    body = client.get("/api/health").json()

    assert body["decks"] == "unreadable"
    assert body["status"] == "degraded"
    assert body["ai"] == "ok"  # the AI link is fine; only storage is not
    assert apkg_dir.name  # keep the fixture referenced


def test_health_degrades_when_the_deck_folder_is_missing(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    main_module.app.state.infercom_client = _LiveAI()
    main_module.app.state.cfg["paths"]["apkg_dir"] = str(tmp_path / "gone")

    body = client.get("/api/health").json()

    assert body["decks"] == "unreadable"
    assert body["status"] == "degraded"


def test_health_still_reports_storage_when_the_ai_is_down(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    main_module.app.state.infercom_client = _DeadAI()

    body = client.get("/api/health").json()

    assert body["ai"] == "unreachable"
    assert body["decks"] == "ok"
    assert body["status"] == "degraded"
