"""
Extract text from candidate source PDFs for a card's explanation context.
Reuses flashcard-pipeline's parsers/pdf_parser.py extraction pattern.
"""
from pathlib import Path


def extract_pdf_text(filepath: Path) -> str:
    try:
        import pdfplumber
    except ImportError:
        raise ImportError("pdfplumber requis: pip install pdfplumber")

    texts = []
    with pdfplumber.open(filepath) as pdf:
        for page in pdf.pages:
            text = page.extract_text()
            if text:
                texts.append(text.strip())
    return "\n\n".join(texts)


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
