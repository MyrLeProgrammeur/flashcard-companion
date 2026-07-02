"""
Batch 8 — exam-result tracking + per-subject correlation with revision
behavior. `subject_grades` is normal CRUD (create ahead of time, set the
grade later), unlike `review_log` which is append-only — see srs_store.py.

"Subject" = deck_path picked from the /api/subjects tree (any node, not just
the root folder). Correlation matches by deck-path PREFIX: a row with
deck_path="M1::Éco" aggregates every card whose deck_name is exactly that
path or nested under it ("M1::Éco::...").

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


def _subject_stats(store, apkg_dir: str, deck_paths: list[str]) -> dict[str, dict]:
    """% success + total time invested per requested deck_path, derived from
    review_log. review_log only stores `guid`, not deck path, so we join
    guid -> deck_name via apkg_reader.read_all_cards, then aggregate by
    PREFIX match against each requested deck_path (exact or nested under
    `deck_path::...`) rather than exact-subject equality."""
    import apkg_reader

    guid_to_deck_name = {c.guid: c.deck_name for c in apkg_reader.read_all_cards(apkg_dir)}

    rows = store.conn.execute("SELECT guid, quality, time_spent_ms FROM review_log").fetchall()

    stats: dict[str, dict] = {
        deck_path: {"total": 0, "successes": 0, "total_time_spent_ms": 0} for deck_path in deck_paths
    }
    for guid, quality, time_spent_ms in rows:
        deck_name = guid_to_deck_name.get(guid)
        if deck_name is None:
            continue
        for deck_path in deck_paths:
            if deck_name != deck_path and not deck_name.startswith(deck_path + "::"):
                continue
            s = stats[deck_path]
            s["total"] += 1
            if quality is not None and quality >= QUALITY_GOOD:
                s["successes"] += 1
            s["total_time_spent_ms"] += time_spent_ms or 0

    return {
        deck_path: {
            "success_rate": (s["successes"] / s["total"]) if s["total"] else None,
            "total_time_spent_ms": s["total_time_spent_ms"],
        }
        for deck_path, s in stats.items()
    }


@router.get("/api/exams")
def list_exams(request: Request):
    """Every subject_grades row, enriched with the subject's 2 revision
    metrics — grade / success_rate / total_time_spent_ms sit side by side,
    no formula combines them."""
    store = request.app.state.store
    apkg_dir = request.app.state.cfg["paths"]["apkg_dir"]
    exam_rows = store.list_subject_grades()
    subject_stats = _subject_stats(store, apkg_dir, [row["deck_path"] for row in exam_rows])

    out = []
    for row in exam_rows:
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
