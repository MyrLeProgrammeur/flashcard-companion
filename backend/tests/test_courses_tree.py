import importlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

from fastapi.testclient import TestClient  # noqa: E402


def _make_client(tmp_path, monkeypatch):
    """Boots the real FastAPI app against a scratch config/data dir (same
    pattern as test_settings_api._make_client) so /api/courses/tree can be
    exercised against a real pdf_dir layout."""
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
    return main_module, TestClient(main_module.app), pdf_dir


def test_tree_is_empty_with_no_pdfs(tmp_path, monkeypatch):
    _, client, _ = _make_client(tmp_path, monkeypatch)
    resp = client.get("/api/courses/tree")
    assert resp.status_code == 200
    assert resp.json() == []


def test_tree_mirrors_nested_directory_layout(tmp_path, monkeypatch):
    _, client, pdf_dir = _make_client(tmp_path, monkeypatch)

    # Cours/Maths/chap1.pdf
    # Cours/Maths/Algebre/chap2.pdf
    (pdf_dir / "Maths" / "Algebre").mkdir(parents=True)
    (pdf_dir / "Maths" / "chap1.pdf").write_bytes(b"%PDF-1.4")
    (pdf_dir / "Maths" / "Algebre" / "chap2.pdf").write_bytes(b"%PDF-1.4")

    resp = client.get("/api/courses/tree")
    assert resp.status_code == 200
    tree = resp.json()

    assert len(tree) == 1
    maths = tree[0]
    assert maths["name"] == "Maths"
    assert maths["is_file"] is False
    assert maths["rel_path"] == "Maths"

    names = {c["name"] for c in maths["children"]}
    assert names == {"Algebre", "chap1.pdf"}

    chap1 = next(c for c in maths["children"] if c["name"] == "chap1.pdf")
    assert chap1["is_file"] is True
    assert chap1["rel_path"] == "Maths/chap1.pdf"
    assert chap1["children"] == []

    algebre = next(c for c in maths["children"] if c["name"] == "Algebre")
    assert algebre["is_file"] is False
    assert len(algebre["children"]) == 1
    chap2 = algebre["children"][0]
    assert chap2["name"] == "chap2.pdf"
    assert chap2["rel_path"] == "Maths/Algebre/chap2.pdf"


def test_tree_skips_dotfiles_and_non_pdf(tmp_path, monkeypatch):
    _, client, pdf_dir = _make_client(tmp_path, monkeypatch)

    (pdf_dir / ".stfolder").mkdir()
    (pdf_dir / ".stfolder" / "hidden.pdf").write_bytes(b"%PDF-1.4")
    (pdf_dir / "Maths").mkdir()
    (pdf_dir / "Maths" / "notes.txt").write_text("not a pdf")
    (pdf_dir / "Maths" / ".hidden.pdf").write_bytes(b"%PDF-1.4")
    (pdf_dir / "Maths" / "visible.pdf").write_bytes(b"%PDF-1.4")

    resp = client.get("/api/courses/tree")
    tree = resp.json()

    assert len(tree) == 1
    assert tree[0]["name"] == "Maths"
    assert [c["name"] for c in tree[0]["children"]] == ["visible.pdf"]


def test_tree_drops_empty_folders(tmp_path, monkeypatch):
    _, client, pdf_dir = _make_client(tmp_path, monkeypatch)

    (pdf_dir / "Empty").mkdir()
    (pdf_dir / "Empty" / "EmptyNested").mkdir()
    (pdf_dir / "HasPdf").mkdir()
    (pdf_dir / "HasPdf" / "a.pdf").write_bytes(b"%PDF-1.4")

    resp = client.get("/api/courses/tree")
    tree = resp.json()

    assert [n["name"] for n in tree] == ["HasPdf"]
