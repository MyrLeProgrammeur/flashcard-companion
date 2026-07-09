import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import pdf_context  # noqa: E402
from pdf_context import build_window_context, extract_pdf_text  # noqa: E402


def _write_blank_pdf(pdf_path: Path) -> None:
    """A syntactically valid PDF with a blank page: no text layer, like a scan."""
    from pypdf import PdfWriter

    writer = PdfWriter()
    writer.add_blank_page(width=200, height=200)
    with open(pdf_path, "wb") as f:
        writer.write(f)


def test_ocr_sidecar_fallback_when_no_text_layer(tmp_path):
    """A PDF with no extractable text falls back to its `.ocr.md` sidecar."""
    pdf_path = tmp_path / "scan.pdf"
    _write_blank_pdf(pdf_path)
    sidecar_path = tmp_path / "scan.ocr.md"
    sidecar_path.write_text("# OCR'd content\nHandwritten notes go here.")

    result = extract_pdf_text(pdf_path)

    assert result == "# OCR'd content\nHandwritten notes go here."


def test_no_sidecar_behaves_as_today(tmp_path):
    """Missing sidecar: empty extraction stays empty (no crash, no change)."""
    pdf_path = tmp_path / "scan.pdf"
    _write_blank_pdf(pdf_path)

    result = extract_pdf_text(pdf_path)

    assert result == ""


def _fake_pages(monkeypatch, pages: list[str]) -> None:
    monkeypatch.setattr(pdf_context, "_extract_pdf_pages_native", lambda _: pages)


def _pages_cited(context: str) -> list[int]:
    import re

    return [int(m) for m in re.findall(r"\(p\. (\d+)\)", context)]


def test_window_centres_on_the_page_being_read(monkeypatch):
    """Budget for ~3 pages: keep the centre and its closest neighbours."""
    _fake_pages(monkeypatch, [f"page{i}" + "x" * 94 for i in range(1, 6)])

    context = build_window_context("cours.pdf", 350, center_page=3)

    assert _pages_cited(context) == [2, 3, 4]


def test_window_expands_forward_first(monkeypatch):
    """Reading order: with room for one neighbour, take the page after."""
    _fake_pages(monkeypatch, [f"page{i}" + "x" * 94 for i in range(1, 6)])

    context = build_window_context("cours.pdf", 250, center_page=3)

    assert _pages_cited(context) == [3, 4]


def test_window_on_a_long_pdf_drops_the_first_pages(monkeypatch):
    """The bug this fixes: page 1 was grounding a question asked on page 30."""
    _fake_pages(monkeypatch, ["x" * 500 for _ in range(40)])

    context = build_window_context("cours.pdf", 1000, center_page=30)

    assert 30 in _pages_cited(context)
    assert 1 not in _pages_cited(context)


def test_short_pdf_selects_every_page(monkeypatch):
    """Under budget, windowing must equal the old whole-document behaviour."""
    _fake_pages(monkeypatch, ["short" for _ in range(4)])

    context = build_window_context("cours.pdf", 15000, center_page=2)

    assert _pages_cited(context) == [1, 2, 3, 4]


def test_centre_page_larger_than_budget_is_truncated(monkeypatch):
    _fake_pages(monkeypatch, ["a" * 100, "b" * 100, "c" * 100])

    context = build_window_context("cours.pdf", 50, center_page=2)

    assert _pages_cited(context) == [2]
    assert context.count("b") == 50


def test_blank_pages_are_stepped_over_not_paid_for(monkeypatch):
    _fake_pages(monkeypatch, ["a" * 40, "", "c" * 40, "", "e" * 40])

    context = build_window_context("cours.pdf", 120, center_page=3)

    assert _pages_cited(context) == [1, 3, 5]


def test_page_out_of_range_clamps(monkeypatch):
    _fake_pages(monkeypatch, ["a" * 40, "b" * 40])

    assert _pages_cited(build_window_context("c.pdf", 40, center_page=99)) == [2]
    assert _pages_cited(build_window_context("c.pdf", 40, center_page=0)) == [1]


def test_ocr_sidecar_has_no_pages_so_window_degrades_to_truncation(tmp_path):
    pdf_path = tmp_path / "scan.pdf"
    _write_blank_pdf(pdf_path)
    (tmp_path / "scan.ocr.md").write_text("z" * 200)

    context = build_window_context(str(pdf_path), 50, center_page=7)

    assert _pages_cited(context) == [1]
    assert context.count("z") == 50
