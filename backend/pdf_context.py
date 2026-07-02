"""
Extract text from candidate source PDFs for a card's explanation context.
Reuses flashcard-pipeline's parsers/pdf_parser.py extraction pattern.
"""
from pathlib import Path


def extract_pdf_text(filepath: Path) -> str:
    """Extract the text layer of a native PDF.

    Tries extractors in preference order. The production target is Termux
    (Android/aarch64) where pdfplumber's pypdfium2 dependency has no wheel and
    won't build, so we fall back to pure-Python pypdf (installs cleanly on
    Termux, same underlying quality), then to the poppler `pdftotext` binary.
    """
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
