"""
Companion's own SQLite state: review history (SM-2) + explain cache,
keyed by the pipeline's stable card GUID. Lives entirely outside any
Syncthing-watched folder — never touches the .apkg files themselves.
"""
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

from srs import DEFAULT_SETTINGS, CardState

SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS card_state (
    guid TEXT PRIMARY KEY,
    reps INTEGER NOT NULL DEFAULT 0,
    interval_days REAL NOT NULL DEFAULT 0,
    ease_factor REAL NOT NULL DEFAULT 2.5,
    due_at TEXT,
    last_reviewed_at TEXT,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS explain_cache (
    guid TEXT PRIMARY KEY,
    explanation TEXT NOT NULL,
    source_files TEXT NOT NULL,
    generated_at TEXT NOT NULL,
    model TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS source_match_cache (
    theme_key TEXT PRIMARY KEY,
    source_files TEXT NOT NULL,
    resolved_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS review_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guid TEXT,
    reviewed_at TEXT,
    quality INTEGER,
    time_spent_ms INTEGER,
    prev_interval_days REAL,
    new_interval_days REAL,
    prev_reps INTEGER,
    new_reps INTEGER
);
CREATE TABLE IF NOT EXISTS subject_grades (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    deck_path TEXT NOT NULL,
    expected_results_date TEXT NOT NULL,
    grade REAL
);
"""


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _parse_dt(value: str | None) -> datetime | None:
    return datetime.fromisoformat(value) if value else None


class SrsStore:
    def __init__(self, db_path: str | Path):
        db_path = Path(db_path)
        db_path.parent.mkdir(parents=True, exist_ok=True)
        # FastAPI runs sync endpoints in a threadpool; this is a single-user
        # local server (loopback only), so a shared connection without a lock
        # is an acceptable MVP tradeoff.
        self.conn = sqlite3.connect(db_path, check_same_thread=False)
        self.conn.executescript(SCHEMA_SQL)
        self.conn.commit()

    def get_state(self, guid: str) -> CardState:
        row = self.conn.execute(
            "SELECT reps, interval_days, ease_factor, due_at, last_reviewed_at "
            "FROM card_state WHERE guid = ?",
            (guid,),
        ).fetchone()
        if row is None:
            # New card: immediately due, matching Anki's "new card" queue closely
            # enough for MVP. Use epoch rather than "now" so it's due regardless
            # of how the caller's own "now" was captured (avoids a race where
            # this due_at ends up a few microseconds after the caller's now).
            return CardState(due_at=datetime.fromtimestamp(0, tz=timezone.utc))
        reps, interval_days, ease_factor, due_at, last_reviewed_at = row
        return CardState(
            reps=reps,
            interval_days=interval_days,
            ease_factor=ease_factor,
            due_at=_parse_dt(due_at),
            last_reviewed_at=_parse_dt(last_reviewed_at),
        )

    def save_state(self, guid: str, state: CardState) -> None:
        self.conn.execute(
            """
            INSERT INTO card_state (guid, reps, interval_days, ease_factor, due_at,
                                     last_reviewed_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(guid) DO UPDATE SET
                reps=excluded.reps, interval_days=excluded.interval_days,
                ease_factor=excluded.ease_factor, due_at=excluded.due_at,
                last_reviewed_at=excluded.last_reviewed_at
            """,
            (
                guid,
                state.reps,
                state.interval_days,
                state.ease_factor,
                state.due_at.isoformat() if state.due_at else None,
                state.last_reviewed_at.isoformat() if state.last_reviewed_at else None,
                _now_iso(),
            ),
        )
        self.conn.commit()

    def log_review(
        self,
        guid: str,
        quality: int,
        time_spent_ms: int | None,
        prev_interval_days: float,
        new_interval_days: float,
        prev_reps: int,
        new_reps: int,
    ) -> None:
        """Append-only review history (Batch 4 stats foundation). Never
        UPDATE/DELETE this table — card_state stays the sole mutable state."""
        self.conn.execute(
            """
            INSERT INTO review_log (guid, reviewed_at, quality, time_spent_ms,
                                     prev_interval_days, new_interval_days,
                                     prev_reps, new_reps)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                guid,
                _now_iso(),
                quality,
                time_spent_ms,
                prev_interval_days,
                new_interval_days,
                prev_reps,
                new_reps,
            ),
        )
        self.conn.commit()

    def due_guids(self, now: datetime) -> set[str]:
        rows = self.conn.execute(
            "SELECT guid FROM card_state WHERE due_at IS NOT NULL AND due_at <= ?",
            (now.isoformat(),),
        ).fetchall()
        return {r[0] for r in rows}

    def get_settings(self) -> dict:
        """SM-2 knobs (B2/B4): global, live-reloadable, defaulting to
        DEFAULT_SETTINGS when the `settings` table has no row yet — soft
        migration for pre-existing DBs, no crash, no explicit ALTER needed."""
        settings = dict(DEFAULT_SETTINGS)
        rows = self.conn.execute("SELECT key, value FROM settings").fetchall()
        for key, value in rows:
            if key in settings:
                settings[key] = float(value)
        return settings

    def save_settings(self, updates: dict) -> dict:
        """Partial update: only known keys are persisted/changed; unknown
        keys are ignored. Returns the full resulting settings dict."""
        current = self.get_settings()
        current.update({k: v for k, v in updates.items() if k in current})
        for key, value in current.items():
            self.conn.execute(
                """
                INSERT INTO settings (key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value=excluded.value
                """,
                (key, str(value)),
            )
        self.conn.commit()
        return current

    def create_subject_grade(self, deck_path: str, expected_results_date: str) -> dict:
        """subject_grades (Batch 8) is normal user-editable CRUD, NOT
        append-only like review_log — the user creates a row ahead of time,
        then comes back later to fill in the grade via update_grade."""
        cur = self.conn.execute(
            "INSERT INTO subject_grades (deck_path, expected_results_date, grade) "
            "VALUES (?, ?, NULL)",
            (deck_path, expected_results_date),
        )
        self.conn.commit()
        return self.get_subject_grade(cur.lastrowid)

    def get_subject_grade(self, exam_id: int) -> dict | None:
        row = self.conn.execute(
            "SELECT id, deck_path, expected_results_date, grade "
            "FROM subject_grades WHERE id = ?",
            (exam_id,),
        ).fetchone()
        if row is None:
            return None
        return {"id": row[0], "deck_path": row[1], "expected_results_date": row[2], "grade": row[3]}

    def list_subject_grades(self) -> list[dict]:
        rows = self.conn.execute(
            "SELECT id, deck_path, expected_results_date, grade "
            "FROM subject_grades ORDER BY expected_results_date"
        ).fetchall()
        return [
            {"id": r[0], "deck_path": r[1], "expected_results_date": r[2], "grade": r[3]}
            for r in rows
        ]

    def update_grade(self, exam_id: int, grade: float) -> dict | None:
        """Plain UPDATE (not append-only) — sets/overwrites the grade once
        it's known. Returns None if the row doesn't exist (caller -> 404)."""
        if self.get_subject_grade(exam_id) is None:
            return None
        self.conn.execute("UPDATE subject_grades SET grade = ? WHERE id = ?", (grade, exam_id))
        self.conn.commit()
        return self.get_subject_grade(exam_id)

    def get_explanation(self, guid: str) -> dict | None:
        row = self.conn.execute(
            "SELECT explanation, source_files, generated_at, model "
            "FROM explain_cache WHERE guid = ?",
            (guid,),
        ).fetchone()
        if row is None:
            return None
        explanation, source_files, generated_at, model = row
        return {
            "explanation": explanation,
            "source_files": source_files.split("\x1f") if source_files else [],
            "generated_at": generated_at,
            "model": model,
        }

    def save_explanation(
        self, guid: str, explanation: str, source_files: list[str], model: str
    ) -> None:
        self.conn.execute(
            """
            INSERT INTO explain_cache (guid, explanation, source_files, generated_at, model)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(guid) DO UPDATE SET
                explanation=excluded.explanation, source_files=excluded.source_files,
                generated_at=excluded.generated_at, model=excluded.model
            """,
            (guid, explanation, "\x1f".join(source_files), _now_iso(), model),
        )
        self.conn.commit()

    def get_source_match(self, theme_key: str) -> list[str] | None:
        row = self.conn.execute(
            "SELECT source_files FROM source_match_cache WHERE theme_key = ?",
            (theme_key,),
        ).fetchone()
        if row is None:
            return None
        return row[0].split("\x1f") if row[0] else []

    def save_source_match(self, theme_key: str, source_files: list[str]) -> None:
        self.conn.execute(
            """
            INSERT INTO source_match_cache (theme_key, source_files, resolved_at)
            VALUES (?, ?, ?)
            ON CONFLICT(theme_key) DO UPDATE SET
                source_files=excluded.source_files, resolved_at=excluded.resolved_at
            """,
            (theme_key, "\x1f".join(source_files), _now_iso()),
        )
        self.conn.commit()
