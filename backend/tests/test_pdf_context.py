import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from pdf_context import extract_pdf_text  # noqa: E402


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
