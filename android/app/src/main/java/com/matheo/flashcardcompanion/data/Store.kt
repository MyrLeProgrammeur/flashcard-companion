package com.matheo.flashcardcompanion.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.matheo.flashcardcompanion.srs.CardState
import com.matheo.flashcardcompanion.srs.SrsSettings
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * All app-owned state, in the same shape the Python backend used.
 *
 * The DDL is deliberately byte-identical to `srs_store.py`'s: the user's review
 * history lives in a `companion_state.db` written by that backend, and keeping
 * the schema identical means it can simply be copied in rather than migrated or
 * thrown away (see [importFrom]). Timestamps are written in Python's
 * `datetime.isoformat()` shape for the same reason.
 */
private const val DB_NAME = "companion_state.db"
private const val DB_VERSION = 1

private val TS_OUT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+00:00'").withZone(ZoneOffset.UTC)

/** Python omits `.ffffff` when the microsecond is 0, so the fraction is optional. */
private val TS_IN: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
    .appendPattern("XXX")
    .toFormatter()

fun nowIso(): String = TS_OUT.format(Instant.now())
fun Instant.toIso(): String = TS_OUT.format(this)

fun parseIso(s: String?): Instant? = s?.takeIf { it.isNotBlank() }?.let { text ->
    runCatching { Instant.from(TS_IN.parse(text)) }
        .recoverCatching { Instant.parse(text) }
        .getOrNull()
}

data class ExamRow(
    val id: Long,
    val deckPath: String,
    val expectedResultsDate: String,
    val grade: Double?,
)

data class CachedExplanation(
    val explanation: String,
    val sourceFiles: List<String>,
    val generatedAt: String,
    val model: String,
)

class Store(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) = createSchema(db)
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        // Everything is CREATE ... IF NOT EXISTS, so this also repairs a DB
        // imported from the Python backend that predates a newer table.
        createSchema(db)
    }

    private fun createSchema(db: SQLiteDatabase) {
        SCHEMA.forEach(db::execSQL)
    }

    // ---------------- card state ----------------

    /**
     * Epoch, not "now", for a card with no row: it must read as due whatever
     * instant the caller captured, and must sort ahead of every scheduled card.
     */
    fun getState(guid: String): CardState =
        readableDatabase.rawQuery(
            "SELECT reps, interval_days, ease_factor, due_at, last_reviewed_at FROM card_state WHERE guid = ?",
            arrayOf(guid),
        ).use { c ->
            if (!c.moveToFirst()) CardState()
            else CardState(
                reps = c.getInt(0),
                intervalDays = c.getDouble(1),
                easeFactor = c.getDouble(2),
                dueAt = parseIso(c.getString(3)),
                lastReviewedAt = parseIso(c.getString(4)),
            )
        }

    /** Every stored state in one pass — the due queue would otherwise do one query per card. */
    fun allStates(): Map<String, CardState> {
        val out = HashMap<String, CardState>()
        readableDatabase.rawQuery(
            "SELECT guid, reps, interval_days, ease_factor, due_at, last_reviewed_at FROM card_state",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out[c.getString(0)] = CardState(
                    reps = c.getInt(1),
                    intervalDays = c.getDouble(2),
                    easeFactor = c.getDouble(3),
                    dueAt = parseIso(c.getString(4)),
                    lastReviewedAt = parseIso(c.getString(5)),
                )
            }
        }
        return out
    }

    /** `created_at` is set on insert and never refreshed: it is the first-ever-review stamp. */
    fun saveState(guid: String, s: CardState) {
        writableDatabase.execSQL(
            """
            INSERT INTO card_state (guid, reps, interval_days, ease_factor, due_at, last_reviewed_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(guid) DO UPDATE SET
                reps=excluded.reps, interval_days=excluded.interval_days,
                ease_factor=excluded.ease_factor, due_at=excluded.due_at,
                last_reviewed_at=excluded.last_reviewed_at
            """.trimIndent(),
            arrayOf(guid, s.reps, s.intervalDays, s.easeFactor, s.dueAt?.toIso(), s.lastReviewedAt?.toIso(), nowIso()),
        )
    }

    /** Append-only. `card_state` stays the sole mutable state. */
    fun logReview(
        guid: String,
        quality: Int,
        timeSpentMs: Long?,
        prevInterval: Double,
        newInterval: Double,
        prevReps: Int,
        newReps: Int,
    ) {
        writableDatabase.execSQL(
            """
            INSERT INTO review_log (guid, reviewed_at, quality, time_spent_ms,
                                    prev_interval_days, new_interval_days, prev_reps, new_reps)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(guid, nowIso(), quality, timeSpentMs, prevInterval, newInterval, prevReps, newReps),
        )
    }

    // ---------------- settings ----------------

    fun getSettingsMap(): Map<String, Double> {
        val out = HashMap(DEFAULT_SETTINGS)
        readableDatabase.rawQuery("SELECT key, value FROM settings", null).use { c ->
            while (c.moveToNext()) {
                val k = c.getString(0)
                if (out.containsKey(k)) c.getString(1).toDoubleOrNull()?.let { out[k] = it }
            }
        }
        return out
    }

    fun getSrsSettings(): SrsSettings = getSettingsMap().let {
        SrsSettings(
            againDays = it["again_days"] ?: 0.0,
            hardDays = it["hard_days"] ?: 1.0,
            goodDays = it["good_days"] ?: 3.0,
            easyDays = it["easy_days"] ?: 7.0,
            easyBonus = it["easy_bonus"] ?: 1.3,
        )
    }

    /** Writes every key, so the first save materialises defaults for untouched ones. */
    fun saveSettings(updates: Map<String, Double>): Map<String, Double> {
        val merged = HashMap(getSettingsMap())
        updates.forEach { (k, v) -> if (merged.containsKey(k)) merged[k] = v }
        writableDatabase.beginTransaction()
        try {
            merged.forEach { (k, v) ->
                writableDatabase.execSQL(
                    "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                    arrayOf(k, v.toString()),
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return merged
    }

    // ---------------- deck groups (display-only folders) ----------------

    fun getDeckGroups(): Map<String, String> {
        val out = HashMap<String, String>()
        readableDatabase.rawQuery("SELECT subject, group_name FROM deck_group", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
        }
        return out
    }

    /** `group == null` unfiles the subject back to the root. */
    fun setDeckGroup(subject: String, group: String?) {
        if (group == null) {
            writableDatabase.execSQL("DELETE FROM deck_group WHERE subject = ?", arrayOf(subject))
        } else {
            writableDatabase.execSQL(
                "INSERT INTO deck_group (subject, group_name) VALUES (?, ?) ON CONFLICT(subject) DO UPDATE SET group_name=excluded.group_name",
                arrayOf(subject, group),
            )
        }
    }

    fun renameDeckGroup(from: String, to: String): Int =
        writableDatabase.compileStatement("UPDATE deck_group SET group_name = ? WHERE group_name = ?")
            .use { it.bindString(1, to); it.bindString(2, from); it.executeUpdateDelete() }

    fun dissolveDeckGroup(name: String): Int =
        writableDatabase.compileStatement("DELETE FROM deck_group WHERE group_name = ?")
            .use { it.bindString(1, name); it.executeUpdateDelete() }

    // ---------------- archive ----------------

    fun getArchivedSubjects(): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT subject FROM archived_subject ORDER BY subject", null).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    /** Idempotent both ways; re-archiving does not refresh `archived_at`. */
    fun setArchived(subject: String, archived: Boolean) {
        if (archived) {
            writableDatabase.execSQL(
                "INSERT INTO archived_subject (subject, archived_at) VALUES (?, ?) ON CONFLICT(subject) DO NOTHING",
                arrayOf(subject, nowIso()),
            )
        } else {
            writableDatabase.execSQL("DELETE FROM archived_subject WHERE subject = ?", arrayOf(subject))
        }
    }

    // ---------------- exams ----------------

    fun listExams(): List<ExamRow> {
        val out = ArrayList<ExamRow>()
        readableDatabase.rawQuery(
            "SELECT id, deck_path, expected_results_date, grade FROM subject_grades ORDER BY expected_results_date",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    ExamRow(
                        id = c.getLong(0),
                        deckPath = c.getString(1),
                        expectedResultsDate = c.getString(2),
                        grade = if (c.isNull(3)) null else c.getDouble(3),
                    )
                )
            }
        }
        return out
    }

    fun createExam(deckPath: String, expectedResultsDate: String): Long =
        writableDatabase.insert(
            "subject_grades", null,
            ContentValues().apply {
                put("deck_path", deckPath)
                put("expected_results_date", expectedResultsDate)
            },
        )

    fun setExamGrade(id: Long, grade: Double?): Int =
        writableDatabase.update(
            "subject_grades",
            ContentValues().apply { if (grade == null) putNull("grade") else put("grade", grade) },
            "id = ?", arrayOf(id.toString()),
        )

    fun deleteExam(id: Long): Int =
        writableDatabase.delete("subject_grades", "id = ?", arrayOf(id.toString()))

    // ---------------- explain cache / telemetry ----------------

    /** Key is `"<guid><lang>"` — an explanation is per language. */
    fun explainKey(guid: String, lang: String) = "$guid$UNIT_SEP$lang"

    fun getExplanation(key: String): CachedExplanation? =
        readableDatabase.rawQuery(
            "SELECT explanation, source_files, generated_at, model FROM explain_cache WHERE guid = ?",
            arrayOf(key),
        ).use { c ->
            if (!c.moveToFirst()) null
            else CachedExplanation(
                explanation = c.getString(0),
                sourceFiles = c.getString(1).takeIf { it.isNotEmpty() }?.split(UNIT_SEP) ?: emptyList(),
                generatedAt = c.getString(2),
                model = c.getString(3),
            )
        }

    fun saveExplanation(key: String, explanation: String, sourceFiles: List<String>, model: String) {
        writableDatabase.execSQL(
            """
            INSERT INTO explain_cache (guid, explanation, source_files, generated_at, model)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(guid) DO UPDATE SET
                explanation=excluded.explanation, source_files=excluded.source_files,
                generated_at=excluded.generated_at, model=excluded.model
            """.trimIndent(),
            arrayOf(key, explanation, sourceFiles.joinToString(UNIT_SEP.toString()), nowIso(), model),
        )
    }

    fun clearExplanation(key: String) {
        writableDatabase.execSQL("DELETE FROM explain_cache WHERE guid = ?", arrayOf(key))
    }

    fun logExplainFeedback(
        guid: String, lang: String, model: String, vote: Int, grounded: Int?, deckName: String,
    ) {
        writableDatabase.execSQL(
            """
            INSERT INTO explain_feedback (guid, lang, model, vote, grounded, deck_name, surface, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'explain', ?)
            """.trimIndent(),
            arrayOf(guid, lang, model, vote, grounded, deckName, nowIso()),
        )
    }

    fun saveExplainCritique(guid: String, lang: String, model: String, critique: String) {
        writableDatabase.execSQL(
            "INSERT INTO explain_critique (guid, lang, model, critique, created_at) VALUES (?, ?, ?, ?, ?)",
            arrayOf(guid, lang, model, critique, nowIso()),
        )
    }

    // ---------------- source match cache ----------------

    /**
     * Returns null when never resolved, and an empty list when resolved to
     * nothing. The distinction matters: unlike the Python version, a negative
     * result is re-scanned once the PDF folder changes, because caching "no
     * source, forever" meant dropping the right PDF in never took effect.
     */
    fun getSourceMatch(deckName: String, notOlderThan: Instant): List<String>? =
        readableDatabase.rawQuery(
            "SELECT source_files, resolved_at FROM source_match_cache WHERE theme_key = ?",
            arrayOf(deckName),
        ).use { c ->
            if (!c.moveToFirst()) return null
            val files = c.getString(0).takeIf { it.isNotEmpty() }?.split(UNIT_SEP) ?: emptyList()
            val at = parseIso(c.getString(1))
            if (files.isEmpty() && (at == null || at.isBefore(notOlderThan))) null else files
        }

    fun saveSourceMatch(deckName: String, files: List<String>) {
        writableDatabase.execSQL(
            """
            INSERT INTO source_match_cache (theme_key, source_files, resolved_at)
            VALUES (?, ?, ?)
            ON CONFLICT(theme_key) DO UPDATE SET
                source_files=excluded.source_files, resolved_at=excluded.resolved_at
            """.trimIndent(),
            arrayOf(deckName, files.joinToString(UNIT_SEP.toString()), nowIso()),
        )
    }

    companion object {
        val DEFAULT_SETTINGS: Map<String, Double> = mapOf(
            "again_days" to 0.0,
            "hard_days" to 1.0,
            "good_days" to 3.0,
            "easy_days" to 7.0,
            "easy_bonus" to 1.3,
            "notify_hour" to 9.0,
        )

        val SCHEMA = listOf(
            """
            CREATE TABLE IF NOT EXISTS card_state (
                guid TEXT PRIMARY KEY,
                reps INTEGER NOT NULL DEFAULT 0,
                interval_days REAL NOT NULL DEFAULT 0,
                ease_factor REAL NOT NULL DEFAULT 2.5,
                due_at TEXT,
                last_reviewed_at TEXT,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
            "CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS deck_group (subject TEXT PRIMARY KEY, group_name TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS archived_subject (subject TEXT PRIMARY KEY, archived_at TEXT NOT NULL)",
            """
            CREATE TABLE IF NOT EXISTS explain_cache (
                guid TEXT PRIMARY KEY,
                explanation TEXT NOT NULL,
                source_files TEXT NOT NULL,
                generated_at TEXT NOT NULL,
                model TEXT NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS source_match_cache (
                theme_key TEXT PRIMARY KEY,
                source_files TEXT NOT NULL,
                resolved_at TEXT NOT NULL
            )
            """.trimIndent(),
            """
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
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS subject_grades (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                deck_path TEXT NOT NULL,
                expected_results_date TEXT NOT NULL,
                grade REAL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS explain_feedback (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guid TEXT, lang TEXT, model TEXT,
                vote INTEGER, grounded INTEGER,
                deck_name TEXT, surface TEXT, created_at TEXT
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS explain_critique (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guid TEXT NOT NULL,
                lang TEXT NOT NULL,
                model TEXT NOT NULL,
                critique TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
            // The Python schema carried no indices at all; these two are what
            // the stats screen and the due queue actually scan.
            "CREATE INDEX IF NOT EXISTS idx_review_log_guid ON review_log (guid)",
            "CREATE INDEX IF NOT EXISTS idx_review_log_reviewed_at ON review_log (reviewed_at)",
        )
    }
}
