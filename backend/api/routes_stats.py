"""
Read-only analytics over the append-only `review_log` (Batch 4). All
aggregation happens in SQL (GROUP BY etc.) — the log is never purged (D2)
and can grow unbounded, so we never pull every row into Python to loop
over it for the overview/cards endpoints. The export endpoint is the one
legitimate full-table dump, streamed straight from the cursor.

Success = quality in {QUALITY_GOOD, QUALITY_EASY} (i.e. quality >= GOOD),
matching the Good/Easy rating buttons — never hardcode this threshold
elsewhere, import QUALITY_GOOD from srs.py.
"""
import csv
import io
import json

from fastapi import APIRouter, Request
from fastapi.responses import Response

from srs import QUALITY_GOOD

router = APIRouter()

REVIEW_LOG_COLUMNS = [
    "id",
    "guid",
    "reviewed_at",
    "quality",
    "time_spent_ms",
    "prev_interval_days",
    "new_interval_days",
    "prev_reps",
    "new_reps",
]


@router.get("/api/stats/overview")
def stats_overview(request: Request):
    conn = request.app.state.store.conn

    total_row = conn.execute(
        """
        SELECT
            COUNT(*),
            COALESCE(SUM(time_spent_ms), 0),
            SUM(CASE WHEN quality >= ? THEN 1 ELSE 0 END)
        FROM review_log
        """,
        (QUALITY_GOOD,),
    ).fetchone()
    total_reviews, total_time_spent_ms, successes = total_row
    successes = successes or 0
    success_rate = (successes / total_reviews) if total_reviews else 0.0

    per_day_rows = conn.execute(
        """
        SELECT substr(reviewed_at, 1, 10) AS day, COUNT(*)
        FROM review_log
        WHERE reviewed_at IS NOT NULL
        GROUP BY day
        ORDER BY day
        """
    ).fetchall()
    per_day = [{"date": day, "count": count} for day, count in per_day_rows]

    return {
        "total_reviews": total_reviews,
        "total_time_spent_ms": total_time_spent_ms,
        "success_rate": success_rate,
        "per_day": per_day,
    }


@router.get("/api/stats/cards")
def stats_cards(request: Request):
    conn = request.app.state.store.conn

    rows = conn.execute(
        """
        SELECT
            guid,
            COUNT(*) AS review_count,
            SUM(CASE WHEN quality >= ? THEN 1 ELSE 0 END) AS successes,
            AVG(time_spent_ms) AS avg_time_spent_ms,
            COALESCE(SUM(time_spent_ms), 0) AS total_time_spent_ms,
            MAX(reviewed_at) AS last_reviewed_at
        FROM review_log
        GROUP BY guid
        ORDER BY guid
        """,
        (QUALITY_GOOD,),
    ).fetchall()

    result = []
    for guid, review_count, successes, avg_time_spent_ms, total_time_spent_ms, last_reviewed_at in rows:
        last_quality = conn.execute(
            "SELECT quality FROM review_log WHERE guid = ? AND reviewed_at = ? "
            "ORDER BY id DESC LIMIT 1",
            (guid, last_reviewed_at),
        ).fetchone()
        result.append(
            {
                "guid": guid,
                "review_count": review_count,
                "success_rate": (successes / review_count) if review_count else 0.0,
                "avg_time_spent_ms": avg_time_spent_ms,
                "total_time_spent_ms": total_time_spent_ms,
                "last_quality": last_quality[0] if last_quality else None,
                "last_reviewed_at": last_reviewed_at,
            }
        )
    return result


@router.get("/api/stats/export")
def stats_export(request: Request, format: str = "json"):
    conn = request.app.state.store.conn
    rows = conn.execute(
        f"SELECT {', '.join(REVIEW_LOG_COLUMNS)} FROM review_log ORDER BY id"
    ).fetchall()

    if format == "csv":
        buf = io.StringIO()
        writer = csv.writer(buf)
        writer.writerow(REVIEW_LOG_COLUMNS)
        writer.writerows(rows)
        return Response(
            content=buf.getvalue(),
            media_type="text/csv",
            headers={"Content-Disposition": 'attachment; filename="review_log.csv"'},
        )

    payload = [dict(zip(REVIEW_LOG_COLUMNS, row)) for row in rows]
    return Response(
        content=json.dumps(payload),
        media_type="application/json",
        headers={"Content-Disposition": 'attachment; filename="review_log.json"'},
    )
