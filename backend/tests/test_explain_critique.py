import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

import pytest  # noqa: E402

from apkg_reader import CardRecord  # noqa: E402
from explain import explain_card  # noqa: E402
from srs_store import SrsStore  # noqa: E402
from test_apkg_reader import build_fixture_apkg  # noqa: E402
from test_explain_feedback import _first_due_guid, _make_client  # noqa: E402


class _FakeMessage:
    def __init__(self, content):
        self.content = content


class _FakeChoice:
    def __init__(self, content):
        self.message = _FakeMessage(content)


class _FakeResponse:
    def __init__(self, content):
        self.choices = [_FakeChoice(content)]


class _FakeClient:
    """Captures the messages passed to chat.completions.create."""

    def __init__(self, reply="regenerated"):
        self.reply = reply
        self.last_messages = None

        parent = self

        class _Completions:
            def create(self, model, messages):
                parent.last_messages = messages
                return _FakeResponse(parent.reply)

        class _Chat:
            completions = _Completions()

        self.chat = _Chat()


def _card():
    return CardRecord(
        guid="g1", deck_name="Stats::CI", subject="Stats", theme="CI",
        front="Q?", back="A.", note="",
    )


def test_critique_forces_regen_and_injects_text(tmp_path):
    store = SrsStore(tmp_path / "state.db")
    # Pre-seed a cached explanation that a plain (no-critique) call would serve.
    store.save_explanation("g1\x1ffr", "OLD cached explanation", [], "old-model")

    client = _FakeClient(reply="NEW explanation")
    result = explain_card(
        client=client, model="new-model", card=_card(),
        pdf_dir=str(tmp_path / "pdf"), store=store, max_pdf_context_chars=1000,
        lang="fr", critique="Trop vague sur la dérivation",
    )

    # Regenerated despite the cache being present.
    assert result["cached"] is False
    assert result["explanation"] == "NEW explanation"

    user_msg = client.last_messages[-1]["content"]
    assert "Trop vague sur la dérivation" in user_msg
    assert "insuffisante" in user_msg  # the critique directive was appended

    # Cache overwritten with the regenerated text.
    assert store.get_explanation("g1\x1ffr")["explanation"] == "NEW explanation"


def test_no_critique_still_serves_cache(tmp_path):
    store = SrsStore(tmp_path / "state.db")
    store.save_explanation("g1\x1ffr", "OLD cached explanation", [], "old-model")

    client = _FakeClient(reply="should not be used")
    result = explain_card(
        client=client, model="new-model", card=_card(),
        pdf_dir=str(tmp_path / "pdf"), store=store, max_pdf_context_chars=1000,
        lang="fr",
    )

    assert result["cached"] is True
    assert result["explanation"] == "OLD cached explanation"
    assert client.last_messages is None  # AI never called


# --- the 👎 critique is telemetry too: it must survive the regeneration ---


def _critique_rows(main_module):
    return main_module.app.state.store.conn.execute(
        "SELECT guid, lang, model, critique, created_at FROM explain_critique"
    ).fetchall()


def _explain(client, guid, critique):
    return client.post(
        f"/api/cards/{guid}/explain", params={"lang": "fr"}, json={"critique": critique}
    )


def test_route_persists_the_critique(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    build_fixture_apkg(Path(main_module.app.state.cfg["paths"]["apkg_dir"]) / "f.apkg")
    guid = _first_due_guid(client)
    main_module.app.state.infercom_client = _FakeClient(reply="NEW")

    assert _explain(client, guid, "Trop vague sur la dérivation").status_code == 200

    rows = _critique_rows(main_module)
    assert len(rows) == 1
    assert rows[0][:4] == (guid, "fr", "cfg-model", "Trop vague sur la dérivation")
    assert rows[0][4]  # created_at


def test_critique_is_append_only_and_blank_ones_are_ignored(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    build_fixture_apkg(Path(main_module.app.state.cfg["paths"]["apkg_dir"]) / "f.apkg")
    guid = _first_due_guid(client)
    main_module.app.state.infercom_client = _FakeClient(reply="NEW")

    _explain(client, guid, "premier reproche")
    _explain(client, guid, "second reproche")
    _explain(client, guid, None)     # plain explain: nothing to record
    _explain(client, guid, "   ")    # whitespace only: nothing to record

    critiques = [r[3] for r in _critique_rows(main_module)]
    assert critiques == ["premier reproche", "second reproche"]


def test_critique_survives_a_failing_regeneration(tmp_path, monkeypatch):
    """The critique is the signal; the retry is not. Record it even if the AI dies."""
    main_module, client = _make_client(tmp_path, monkeypatch)
    build_fixture_apkg(Path(main_module.app.state.cfg["paths"]["apkg_dir"]) / "f.apkg")
    guid = _first_due_guid(client)

    class _DeadClient:
        class _Chat:
            class _Completions:
                def create(self, model, messages):
                    raise RuntimeError("infercom unreachable")

            completions = _Completions()

        chat = _Chat()

    main_module.app.state.infercom_client = _DeadClient()

    with pytest.raises(RuntimeError):
        _explain(client, guid, "réponse hors sujet")

    assert [r[3] for r in _critique_rows(main_module)] == ["réponse hors sujet"]
