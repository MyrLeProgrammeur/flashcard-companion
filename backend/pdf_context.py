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
    pages = extract_pdf_pages(filepath)
    return "\n\n".join(p for p in pages if p.strip())


def extract_pdf_pages(filepath: Path) -> list[str]:
    """Same extraction as `extract_pdf_text`, but keeping the page boundaries.

    An OCR sidecar has no page structure, so it comes back as a single
    pseudo-page — windowing on it degrades to plain truncation.
    """
    pages = _extract_pdf_pages_native(filepath)
    if any(p.strip() for p in pages):
        return pages
    sidecar = _read_ocr_sidecar(filepath)
    return [sidecar] if sidecar else []


def _extract_pdf_pages_native(filepath: Path) -> list[str]:
    # 1. pdfplumber — best; available on PC/dev.
    try:
        import pdfplumber

        with pdfplumber.open(filepath) as pdf:
            return [(page.extract_text() or "").strip() for page in pdf.pages]
    except ImportError:
        pass

    # 2. pypdf — pure Python, the Termux path.
    try:
        from pypdf import PdfReader

        reader = PdfReader(str(filepath))
        return [(page.extract_text() or "").strip() for page in reader.pages]
    except ImportError:
        pass

    # 3. poppler `pdftotext` binary, if present. It separates pages with \f.
    import shutil
    import subprocess

    if shutil.which("pdftotext"):
        out = subprocess.run(
            ["pdftotext", str(filepath), "-"],
            capture_output=True,
            text=True,
            timeout=60,
        )
        return [p.strip() for p in out.stdout.split("\f")]

    raise ImportError(
        "no PDF text extractor available (install one of: pypdf, pdfplumber; "
        "or the poppler `pdftotext` binary)"
    )


def build_window_context(filepath: str, max_chars: int, center_page: int) -> str:
    """Text of one PDF, windowed around the page the reader is actually on.

    `build_context` truncates from page 1, so on a long PDF the model never
    sees what is on screen. Here the centre page goes in first, then whole
    neighbouring pages are added outwards (forward first, reading order)
    while the character budget allows. A direction that no longer fits is
    dropped, the other keeps expanding. Pages are emitted in document order.

    On a PDF shorter than the budget this selects every page, i.e. exactly
    what `build_context` already did.
    """
    try:
        pages = extract_pdf_pages(Path(filepath))
    except Exception:
        return ""
    if not pages:
        return ""

    name = Path(filepath).name
    centre = max(0, min(center_page - 1, len(pages) - 1))

    selected = {centre: pages[centre][:max_chars]}
    remaining = max_chars - len(selected[centre])

    lo = hi = centre
    forward = True
    while remaining > 0 and (lo > 0 or hi < len(pages) - 1):
        # Alternate outwards; a direction that is exhausted yields to the other.
        if forward and hi >= len(pages) - 1:
            forward = False
            continue
        if not forward and lo <= 0:
            forward = True
            continue

        nxt = hi + 1 if forward else lo - 1
        text = pages[nxt]
        if not text.strip():  # blank page: step over it, spend no budget
            if forward:
                hi = nxt
            else:
                lo = nxt
            forward = not forward
            continue
        if len(text) > remaining:
            # This whole page won't fit: close this direction, don't split it.
            if forward:
                hi = len(pages) - 1
            else:
                lo = 0
            forward = not forward
            continue

        selected[nxt] = text
        remaining -= len(text)
        if forward:
            hi = nxt
        else:
            lo = nxt
        forward = not forward

    return "\n\n".join(
        f"--- {name} (p. {i + 1}) ---\n{selected[i]}"
        for i in sorted(selected)
        if selected[i].strip()
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
