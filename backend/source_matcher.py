"""
MVP heuristic linking a deck's subject to candidate source PDFs, without
touching flashcard-pipeline's own state (no per-card traceability exists
upstream yet — see plan §2.3 stretch goal for the eventual precise version).
"""
from difflib import SequenceMatcher
from pathlib import Path

MATCH_THRESHOLD = 0.6


def _similarity(a: str, b: str) -> float:
    return SequenceMatcher(None, a.lower(), b.lower()).ratio()


def find_source_pdfs(terms: list[str], pdf_dir: str | Path) -> list[str]:
    """Best-effort match: any deck-path segment vs. filename or containing-folder name."""
    pdf_dir = Path(pdf_dir)
    if not pdf_dir.exists():
        return []

    terms = [t for t in terms if t and t.strip()]
    if not terms:
        return []

    matches = []
    for pdf_path in pdf_dir.rglob("*.pdf"):
        candidates = [pdf_path.stem, pdf_path.parent.name]
        # Match against every deck-path segment (e.g. "M1", "Stats"), keep the best.
        score = max(_similarity(term, c) for term in terms for c in candidates)
        if score >= MATCH_THRESHOLD:
            matches.append((score, str(pdf_path)))

    matches.sort(key=lambda m: m[0], reverse=True)
    return [path for _, path in matches]
