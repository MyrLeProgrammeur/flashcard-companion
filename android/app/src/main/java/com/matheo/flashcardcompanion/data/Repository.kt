package com.matheo.flashcardcompanion.data

import android.content.Context
import com.matheo.flashcardcompanion.srs.CardState
import com.matheo.flashcardcompanion.srs.SrsSettings
import com.matheo.flashcardcompanion.srs.review
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

data class ReviewStats(
    val totalReviews: Int,
    val totalTimeSpentMs: Long,
    val successRate: Double,
    val perDay: List<Pair<String, Int>>,
)

data class CardStat(
    val guid: String,
    val reviewCount: Int,
    val successRate: Double,
    val avgTimeSpentMs: Double?,
    val totalTimeSpentMs: Long,
    val lastQuality: Int?,
    val lastReviewedAt: String?,
)

data class ExamWithStats(
    val row: ExamRow,
    val successRate: Double?,
    val totalTimeSpentMs: Long,
)

/**
 * Everything the screens need, in place of the HTTP API.
 *
 * The backend re-parsed every `.apkg` on every single request. Here the library
 * is parsed once into memory and only re-read when the folder's files actually
 * change, which is what makes the app usable offline and instant — parsing ~100
 * decks takes about half a second and used to sit in front of every screen.
 */
class Repository(private val context: Context) {

    val prefs = Prefs(context)
    val store = Store(context)

    @Volatile private var cards: List<CardRecord> = emptyList()
    @Volatile private var byGuid: Map<String, CardRecord> = emptyMap()
    @Volatile private var signature: String? = null
    @Volatile private var pdfs: List<File> = emptyList()
    @Volatile private var pdfsLoadedAt: Instant? = null

    val allCards: List<CardRecord> get() = cards

    /** Cheap fingerprint of the deck folder: names, sizes and mtimes. */
    private fun folderSignature(dir: File): String =
        ApkgReader.listApkgFiles(dir).joinToString("|") { "${it.name}:${it.length()}:${it.lastModified()}" }

    /** Re-reads the decks only when the folder changed, unless [force]. */
    suspend fun loadCards(force: Boolean = false): List<CardRecord> = withContext(Dispatchers.IO) {
        val dir = prefs.apkgDirFile
        val sig = folderSignature(dir)
        if (!force && sig == signature && cards.isNotEmpty()) return@withContext cards
        val loaded = ApkgReader.readAllCards(dir, context.cacheDir)
        cards = loaded
        byGuid = loaded.associateBy { it.guid }
        signature = sig
        loaded
    }

    fun card(guid: String): CardRecord? = byGuid[guid]

    suspend fun states(): Map<String, CardState> = withContext(Dispatchers.IO) { store.allStates() }

    suspend fun srsSettings(): SrsSettings = withContext(Dispatchers.IO) { store.getSrsSettings() }

    suspend fun tree(): List<DeckNode> = withContext(Dispatchers.IO) {
        Library.tree(loadCards(), store.allStates(), store.getArchivedSubjects(), store.getDeckGroups(), Instant.now())
    }

    suspend fun dueCards(path: String = ""): List<Pair<CardRecord, CardState>> = withContext(Dispatchers.IO) {
        Library.dueCards(
            loadCards(), store.allStates(), store.getArchivedSubjects(),
            store.getDeckGroups(), Instant.now(), path,
        )
    }

    suspend fun dueCount(path: String = ""): Int = dueCards(path).size

    suspend fun subjects(): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        Library.subjects(loadCards(), store.getArchivedSubjects())
    }

    /** Applies a rating and appends to the review log, returning the new state. */
    suspend fun rate(guid: String, quality: Int, timeSpentMs: Long?): CardState = withContext(Dispatchers.IO) {
        val settings = store.getSrsSettings()
        val current = store.getState(guid)
        val next = review(current, quality, Instant.now(), settings)
        store.saveState(guid, next)
        store.logReview(
            guid, quality, timeSpentMs,
            current.intervalDays, next.intervalDays, current.reps, next.reps,
        )
        next
    }

    // ---------------- source PDFs ----------------

    /** Memoised: walking the course folder costs seconds on shared storage. */
    suspend fun coursePdfs(force: Boolean = false): List<File> = withContext(Dispatchers.IO) {
        val age = pdfsLoadedAt
        if (!force && age != null && age.isAfter(Instant.now().minusSeconds(120))) return@withContext pdfs
        pdfs = SourceMatcher.listPdfs(prefs.pdfDirFile)
        pdfsLoadedAt = Instant.now()
        pdfs
    }

    /** Course PDFs matching a card's deck, best match first. */
    suspend fun sourcePdfsFor(deckName: String): List<File> = withContext(Dispatchers.IO) {
        val freshEnough = Instant.now().minusSeconds(3600)
        store.getSourceMatch(deckName, freshEnough)?.let { cached ->
            return@withContext cached.map(::File).filter { it.isFile }
        }
        val segments = deckName.split("::").filter { it.isNotBlank() }
        val matches = SourceMatcher.findSourcePdfs(segments, coursePdfs())
        store.saveSourceMatch(deckName, matches.map { it.absolutePath })
        matches
    }

    /** subject -> its matching PDFs, resolved with a single folder walk. */
    suspend fun coursesBySubject(): Map<String, List<File>> = withContext(Dispatchers.IO) {
        val listing = coursePdfs()
        loadCards().map { it.subject }.distinct().sorted()
            .associateWith { SourceMatcher.findSourcePdfs(listOf(it), listing) }
    }

    // ---------------- stats ----------------

    suspend fun overview(): ReviewStats = withContext(Dispatchers.IO) {
        val db = store.readableDatabase
        var total = 0; var time = 0L; var successes = 0
        db.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(time_spent_ms), 0), " +
                "COALESCE(SUM(CASE WHEN quality >= 4 THEN 1 ELSE 0 END), 0) FROM review_log",
            null,
        ).use { if (it.moveToFirst()) { total = it.getInt(0); time = it.getLong(1); successes = it.getInt(2) } }

        val perDay = ArrayList<Pair<String, Int>>()
        db.rawQuery(
            "SELECT substr(reviewed_at, 1, 10) AS day, COUNT(*) FROM review_log " +
                "WHERE reviewed_at IS NOT NULL GROUP BY day ORDER BY day",
            null,
        ).use { while (it.moveToNext()) perDay.add(it.getString(0) to it.getInt(1)) }

        ReviewStats(total, time, if (total > 0) successes.toDouble() / total else 0.0, perDay)
    }

    suspend fun cardStats(): List<CardStat> = withContext(Dispatchers.IO) {
        val out = ArrayList<CardStat>()
        store.readableDatabase.rawQuery(
            """
            SELECT guid, COUNT(*),
                   COALESCE(SUM(CASE WHEN quality >= 4 THEN 1 ELSE 0 END), 0),
                   AVG(time_spent_ms), COALESCE(SUM(time_spent_ms), 0),
                   MAX(reviewed_at)
            FROM review_log GROUP BY guid ORDER BY guid
            """.trimIndent(),
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val guid = c.getString(0)
                val count = c.getInt(1)
                val last = c.getString(5)
                out.add(
                    CardStat(
                        guid = guid,
                        reviewCount = count,
                        successRate = if (count > 0) c.getInt(2).toDouble() / count else 0.0,
                        avgTimeSpentMs = if (c.isNull(3)) null else c.getDouble(3),
                        totalTimeSpentMs = c.getLong(4),
                        lastQuality = lastQuality(guid, last),
                        lastReviewedAt = last,
                    )
                )
            }
        }
        out
    }

    private fun lastQuality(guid: String, reviewedAt: String?): Int? {
        if (reviewedAt == null) return null
        return store.readableDatabase.rawQuery(
            "SELECT quality FROM review_log WHERE guid = ? AND reviewed_at = ? ORDER BY id DESC LIMIT 1",
            arrayOf(guid, reviewedAt),
        ).use { if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) else null }
    }

    fun exportCsv(): String {
        val sb = StringBuilder()
        sb.append("id,guid,reviewed_at,quality,time_spent_ms,prev_interval_days,new_interval_days,prev_reps,new_reps\r\n")
        store.readableDatabase.rawQuery(
            "SELECT id, guid, reviewed_at, quality, time_spent_ms, prev_interval_days, " +
                "new_interval_days, prev_reps, new_reps FROM review_log ORDER BY id",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val cells = (0 until c.columnCount).map { i -> if (c.isNull(i)) "" else c.getString(i) }
                sb.append(cells.joinToString(",") { cell ->
                    if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
                        "\"" + cell.replace("\"", "\"\"") + "\"" else cell
                })
                sb.append("\r\n")
            }
        }
        return sb.toString()
    }

    // ---------------- exams ----------------

    /**
     * Grade, success rate and time invested side by side — deliberately three
     * independent numbers, never folded into one composite score.
     */
    suspend fun examsWithStats(): List<ExamWithStats> = withContext(Dispatchers.IO) {
        val rows = store.listExams()
        if (rows.isEmpty()) return@withContext emptyList()
        val deckByGuid = loadCards().associate { it.guid to it.deckName }
        val acc = rows.associate { it.deckPath to longArrayOf(0, 0, 0) }

        store.readableDatabase.rawQuery("SELECT guid, quality, time_spent_ms FROM review_log", null).use { c ->
            while (c.moveToNext()) {
                val deckName = deckByGuid[c.getString(0)] ?: continue
                val quality = if (c.isNull(1)) null else c.getInt(1)
                val time = if (c.isNull(2)) 0L else c.getLong(2)
                for ((path, a) in acc) {
                    if (deckName != path && !deckName.startsWith("$path::")) continue
                    a[0]++
                    if (quality != null && quality >= 4) a[1]++
                    a[2] += time
                }
            }
        }
        rows.map { row ->
            val a = acc[row.deckPath]!!
            ExamWithStats(
                row = row,
                successRate = if (a[0] > 0) a[1].toDouble() / a[0] else null,
                totalTimeSpentMs = a[2],
            )
        }
    }

    // ---------------- importing the Termux backend's history ----------------

    /**
     * The Termux backend kept its state in `~/flashcard-companion/data/companion_state.db`,
     * which this app cannot reach once Termux is gone. Same schema, so its rows
     * are copied in wholesale rather than lost. Existing rows win, so running
     * this twice cannot clobber reviews done in the native app.
     */
    fun importLegacyDb(path: File): Int {
        if (!path.isFile) return 0
        val db = store.writableDatabase
        var imported = 0
        db.beginTransaction()
        try {
            db.execSQL("ATTACH DATABASE ? AS legacy", arrayOf(path.absolutePath))
            val tables = listOf(
                "card_state", "settings", "deck_group", "archived_subject",
                "explain_cache", "source_match_cache", "subject_grades",
            )
            for (t in tables) {
                runCatching { db.execSQL("INSERT OR IGNORE INTO $t SELECT * FROM legacy.$t") }
            }
            runCatching {
                db.execSQL(
                    "INSERT INTO review_log (guid, reviewed_at, quality, time_spent_ms, " +
                        "prev_interval_days, new_interval_days, prev_reps, new_reps) " +
                        "SELECT guid, reviewed_at, quality, time_spent_ms, prev_interval_days, " +
                        "new_interval_days, prev_reps, new_reps FROM legacy.review_log " +
                        "WHERE reviewed_at NOT IN (SELECT reviewed_at FROM review_log WHERE reviewed_at IS NOT NULL)"
                )
            }
            db.rawQuery("SELECT COUNT(*) FROM card_state", null).use { if (it.moveToFirst()) imported = it.getInt(0) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            runCatching { db.execSQL("DETACH DATABASE legacy") }
        }
        return imported
    }
}
