import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

from test_explain_feedback import _make_client  # noqa: E402


class _FakeResponse:
    def __init__(self, content):
        self.choices = [type("C", (), {"message": type("M", (), {"content": content})()})()]


class _FakeClient:
    """Counts calls and captures the system prompt."""

    def __init__(self):
        self.calls = 0
        self.last_system = None

        parent = self

        class _Completions:
            def create(self, model, messages):
                parent.calls += 1
                parent.last_system = messages[0]["content"]
                return _FakeResponse(f"answer #{parent.calls}")

        class _Chat:
            completions = _Completions()

        self.chat = _Chat()


def _setup(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    pdf_dir = Path(main_module.app.state.cfg["paths"]["pdf_dir"])

    from pypdf import PdfWriter

    writer = PdfWriter()
    writer.add_blank_page(width=200, height=200)
    with open(pdf_dir / "cours.pdf", "wb") as f:
        writer.write(f)

    fake = _FakeClient()
    main_module.app.state.infercom_client = fake
    return main_module, client, fake


def _fail(msg):
    raise AssertionError(msg)


def _ask(client, **extra):
    body = {"rel_path": "cours.pdf", "messages": [{"role": "user", "content": "q?"}]}
    body.update(extra)
    return client.post("/api/courses/help", json=body)


def test_page_grounds_the_context_on_that_page(tmp_path, monkeypatch):
    main_module, client, fake = _setup(tmp_path, monkeypatch)

    seen = {}
    import api.routes_pdf_help as mod

    monkeypatch.setattr(
        mod,
        "build_window_context",
        lambda path, budget, page: seen.update(path=path, budget=budget, page=page)
        or "TEXTE DE LA PAGE 12",
    )

    resp = _ask(client, page=12)

    assert resp.status_code == 200
    assert seen["page"] == 12
    assert seen["budget"] == 1000  # explain.max_pdf_context_chars from the test cfg
    assert seen["path"].endswith("cours.pdf")
    assert "page 12" in fake.last_system
    assert "TEXTE DE LA PAGE 12" in fake.last_system


def test_no_page_falls_back_to_whole_document(tmp_path, monkeypatch):
    """Back-compat: an older front end that sends no page still works."""
    main_module, client, fake = _setup(tmp_path, monkeypatch)

    import api.routes_pdf_help as mod

    monkeypatch.setattr(mod, "build_context", lambda files, budget: "DOC ENTIER")
    monkeypatch.setattr(
        mod, "build_window_context", lambda *a: _fail("must not be called")
    )

    resp = _ask(client)

    assert resp.status_code == 200
    assert "DOC ENTIER" in fake.last_system
    assert "L'étudiant lit la page" not in fake.last_system


def test_identical_questions_are_never_served_from_a_cache(tmp_path, monkeypatch):
    _, client, fake = _setup(tmp_path, monkeypatch)

    first = _ask(client, page=1)
    second = _ask(client, page=1)

    assert first.json()["answer"] == "answer #1"
    assert second.json()["answer"] == "answer #2"
    assert fake.calls == 2
    assert "cached" not in second.json()


def test_path_traversal_and_empty_messages_are_rejected(tmp_path, monkeypatch):
    _, client, fake = _setup(tmp_path, monkeypatch)

    assert client.post(
        "/api/courses/help", json={"rel_path": "../secret.pdf", "question": "q"}
    ).status_code == 404
    assert client.post(
        "/api/courses/help", json={"rel_path": "cours.pdf", "messages": []}
    ).status_code == 400
    assert fake.calls == 0
