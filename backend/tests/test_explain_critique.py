import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from apkg_reader import CardRecord  # noqa: E402
from explain import explain_card  # noqa: E402
from srs_store import SrsStore  # noqa: E402


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
