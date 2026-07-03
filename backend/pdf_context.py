"""
Extract text from candidate source PDFs for a card's explanation context.
Reuses flashcard-pipeline's parsers/pdf_parser.py extraction pattern.
"""
from pathlib import Path


def _read_ocr_sidecar(filepath: Path) -> str:
    """Read the `<stem>.ocr.md` sidecar next to a scanned PDF, if present.

    Scanned/handwritten PDFs have no text layer, so extraction returns empty.
    A hosted VLM can pre-OCR such PDFs into a Markdown sidecar (see
    flashcard-pipeline/tools/ocr_scans.py); this is the read-side fallback.
    Pure stdlib, no deps — safe on Termux.
    """
    sidecar = filepath.with_name(filepath.stem + ".ocr.md")
    try:
        return sidecar.read_text(encoding="utf-8").strip()
    except OSError:
        return ""


def extract_pdf_text(filepath: Path) -> str:
    """Extract the text layer of a native PDF.

    Tries extractors in preference order. The production target is Termux
    (Android/aarch64) where pdfplumber's pypdfium2 dependency has no wheel and
    won't build, so we fall back to pure-Python pypdf (installs cleanly on
    Termux, same underlying quality), then to the poppler `pdftotext` binary.
    If none of the above yield text (e.g. a handwritten/scanned PDF with no
    text layer), fall back to a `<stem>.ocr.md` sidecar file if present.
    """
    text = _extract_pdf_text_native(filepath)
    if text.strip():
        return text
    return _read_ocr_sidecar(filepath)


def _extract_pdf_text_native(filepath: Path) -> str:
    # 1. pdfplumber — best; available on PC/dev.
    try:
        import pdfplumber

        texts = []
        with pdfplumber.open(filepath) as pdf:
            for page in pdf.pages:
                text = page.extract_text()
                if text:
                    texts.append(text.strip())
        return "\n\n".join(texts)
    except ImportError:
        pass

    # 2. pypdf — pure Python, the Termux path.
    try:
        from pypdf import PdfReader

        reader = PdfReader(str(filepath))
        texts = [(page.extract_text() or "").strip() for page in reader.pages]
        return "\n\n".join(t for t in texts if t)
    except ImportError:
        pass

    # 3. poppler `pdftotext` binary, if present.
    import shutil
    import subprocess

    if shutil.which("pdftotext"):
        out = subprocess.run(
            ["pdftotext", str(filepath), "-"],
            capture_output=True,
            text=True,
            timeout=60,
        )
        return out.stdout.strip()

    raise ImportError(
        "no PDF text extractor available (install one of: pypdf, pdfplumber; "
        "or the poppler `pdftotext` binary)"
    )


def build_context(source_files: list[str], max_chars: int) -> str:
    """Concatenate text from candidate PDFs up to a character budget."""
    chunks = []
    remaining = max_chars
    for filepath in source_files:
        if remaining <= 0:
            break
        try:
            text = extract_pdf_text(Path(filepath))
        except Exception:
            continue
        chunk = text[:remaining]
        chunks.append(f"--- {Path(filepath).name} ---\n{chunk}")
        remaining -= len(chunk)
    return "\n\n".join(chunks)
