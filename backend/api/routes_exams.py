"""
Batch 8 — exam-result tracking + per-subject correlation with revision
behavior. `subject_grades` is normal CRUD (create ahead of time, set the
grade later), unlike `review_log` which is append-only — see srs_store.py.

"Subject" = deck_path exactly as typed by the user, matched against
apkg_reader's `card.subject` (the root Anki `::` folder, already parsed
there — never re-split `::` here).

Correlation is 3 independent numbers, side by side, no weighted/composite
score (settled Batch 8 decision): grade obtained, % success in revision,
total time invested. Comparison is left to the user's own judgment.
"""
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from srs import QUALITY_GOOD

router = APIRouter()


class ExamCreateBody(BaseModel):
    deck_path: str
    expected_results_date: str


class GradeBody(BaseModel):
    grade: float


def _subject_stats(store, apkg_dir: str) -> dict[str, dict]:
    """% success + total time invested per subject, derived from review_log.
    review_log only stores `guid`, not deck path, so we join guid -> subject
    via apkg_reader.read_all_cards (same subject derivation already used by
    /api/decks, not re-implemented here)."""
    import apkg_reader

    guid_to_subject = {c.guid: c.subject for c in apkg_reader.read_all_cards(apkg_dir)}

    stats: dict[str, dict] = {}
    rows = store.conn.execute("SELECT guid, quality, time_spent_ms FROM review_log").fetchall()
    for guid, quality, time_spent_ms in rows:
        subject = guid_to_subject.get(guid)
        if subject is None:
            continue
        s = stats.setdefault(subject, {"total": 0, "successes": 0, "total_time_spent_ms": 0})
        s["total"] += 1
        if quality is not None and quality >= QUALITY_GOOD:
            s["successes"] += 1
        s["total_time_spent_ms"] += time_spent_ms or 0

    return {
        subject: {
            "success_rate": (s["successes"] / s["total"]) if s["total"] else None,
            "total_time_spent_ms": s["total_time_spent_ms"],
        }
        for subject, s in stats.items()
    }


@router.get("/api/exams")
def list_exams(request: Request):
    """Every subject_grades row, enriched with the subject's 2 revision
    metrics — grade / success_rate / total_time_spent_ms sit side by side,
    no formula combines them."""
    store = request.app.state.store
    apkg_dir = request.app.state.cfg["paths"]["apkg_dir"]
    subject_stats = _subject_stats(store, apkg_dir)

    out = []
    for row in store.list_subject_grades():
        stats = subject_stats.get(row["deck_path"], {})
        out.append(
            {
                **row,
                "success_rate": stats.get("success_rate"),
                "total_time_spent_ms": stats.get("total_time_spent_ms", 0),
            }
        )
    return out


@router.post("/api/exams")
def create_exam(body: ExamCreateBody, request: Request):
    store = request.app.state.store
    if not body.deck_path.strip():
        raise HTTPException(status_code=400, detail="deck_path must not be empty")
    return store.create_subject_grade(body.deck_path, body.expected_results_date)


@router.put("/api/exams/{exam_id}")
def set_grade(exam_id: int, body: GradeBody, request: Request):
    store = request.app.state.store
    updated = store.update_grade(exam_id, body.grade)
    if updated is None:
        raise HTTPException(status_code=404, detail="Exam entry not found")
    return updated
