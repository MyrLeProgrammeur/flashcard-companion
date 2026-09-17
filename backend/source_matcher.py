"""
MVP heuristic linking a deck's subject to candidate source PDFs, without
touching flashcard-pipeline's own state (no per-card traceability exists
upstream yet — see plan §2.3 stretch goal for the eventual precise version).
"""
import time
from difflib import SequenceMatcher
from pathlib import Path

MATCH_THRESHOLD = 0.6

# `pdf_dir` lives in Android shared storage (FUSE): one rglob over it measures
# ~20 s on the phone for ~60 files. The listing is therefore walked once and
# memoised — without this, any caller looping over subjects pays that walk per
# subject. Syncthing only changes the tree occasionally, so a short TTL keeps
# new course files appearing without another multi-second stall per request.
_PDF_CACHE_TTL_S = 120.0
_pdf_cache: dict[str, tuple[float, list[Path]]] = {}


def list_pdfs(pdf_dir: str | Path, *, max_age_s: float = _PDF_CACHE_TTL_S) -> list[Path]:
    """Every `.pdf` under `pdf_dir`, memoised for `max_age_s` seconds."""
    pdf_dir = Path(pdf_dir)
    if not pdf_dir.exists():
        return []

    key = str(pdf_dir)
    hit = _pdf_cache.get(key)
    now = time.monotonic()
    if hit and now - hit[0] < max_age_s:
        return hit[1]

    pdfs = sorted(pdf_dir.rglob("*.pdf"))
    _pdf_cache[key] = (now, pdfs)
    return pdfs


def invalidate_pdf_cache() -> None:
    _pdf_cache.clear()


def _similarity(a: str, b: str) -> float:
    return SequenceMatcher(None, a.lower(), b.lower()).ratio()


def find_source_pdfs(
    terms: list[str],
    pdf_dir: str | Path,
    pdfs: list[Path] | None = None,
) -> list[str]:
    """Best-effort match: any deck-path segment vs. filename or containing-folder name.

    `pdfs` lets a caller matching several subjects in a row reuse one listing
    instead of re-walking `pdf_dir` for each of them.
    """
    terms = [t for t in terms if t and t.strip()]
    if not terms:
        return []

    if pdfs is None:
        pdfs = list_pdfs(pdf_dir)

    matches = []
    for pdf_path in pdfs:
        candidates = [pdf_path.stem, pdf_path.parent.name]
        # Match against every deck-path segment (e.g. "M1", "Stats"), keep the best.
        score = max(_similarity(term, c) for term in terms for c in candidates)
        if score >= MATCH_THRESHOLD:
            matches.append((score, str(pdf_path)))

    matches.sort(key=lambda m: m[0], reverse=True)
    return [path for _, path in matches]
