package com.matheo.flashcardcompanion.data

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** Anki's field separator inside `notes.flds`, and its deck separator in schema 18. */
const val UNIT_SEP = ''

data class CardRecord(
    val guid: String,
    val deckName: String,
    val subject: String,
    val theme: String,
    val front: String,
    val back: String,
    val note: String,
)

/**
 * Read-only parsing of the `.apkg` files produced by flashcard-pipeline.
 *
 * An `.apkg` is a zip holding a `collection.anki2` SQLite database. The zip
 * member is never opened in place and never written back: it is copied into the
 * app cache first, so this app can never corrupt a file Syncthing also manages
 * (which would surface to the user as a sync conflict).
 *
 * Two schema generations are handled. The pipeline currently writes schema 11,
 * where note types and decks are JSON blobs on the `col` row. Anki >= 2.1.28
 * writes schema 18, which moves them into real tables and switches the deck
 * separator from `::` to U+001F. Reading only schema 11 does not fail loudly on
 * an 18 file — it yields empty deck names and empty fields, i.e. a silently
 * blank library — so both are supported.
 */
object ApkgReader {
    private const val TAG = "ApkgReader"

    /**
     * Syncthing names a conflicted copy "<deck>.sync-conflict-<date>-<id>.apkg".
     * Picked up blindly those surface as ghost subjects with duplicated cards.
     */
    fun listApkgFiles(apkgDir: File): List<File> {
        if (!apkgDir.isDirectory) return emptyList()
        return (apkgDir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".apkg") && !it.name.contains(".sync-conflict-") }
            .sortedBy { it.name }
    }

    fun readAllCards(apkgDir: File, cacheDir: File): List<CardRecord> {
        val out = ArrayList<CardRecord>()
        for (f in listApkgFiles(apkgDir)) {
            try {
                out.addAll(readCards(f, cacheDir))
            } catch (e: Exception) {
                // One malformed deck must not take the whole library down.
                Log.w(TAG, "skipping ${f.name}: ${e.message}")
            }
        }
        return out
    }

    /** Newest usable collection first; the legacy `.anki2` may be a stale downgrade copy. */
    private val COLLECTION_ENTRIES = listOf("collection.anki21", "collection.anki2")

    fun readCards(apkgPath: File, cacheDir: File): List<CardRecord> {
        val tmp = File.createTempFile("collection", ".anki2", cacheDir)
        try {
            ZipFile(apkgPath).use { zip ->
                val entry = COLLECTION_ENTRIES.firstNotNullOfOrNull { zip.getEntry(it) }
                    ?: throw IllegalStateException(
                        "no readable collection in ${apkgPath.name} " +
                            "(a zstd-compressed collection.anki21b export is not supported)"
                    )
                zip.getInputStream(entry).use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
            }
            SQLiteDatabase.openDatabase(tmp.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use {
                return parse(it)
            }
        } finally {
            tmp.delete()
        }
    }

    private fun parse(db: SQLiteDatabase): List<CardRecord> {
        val modern = hasTable(db, "notetypes")
        val fieldOrder = if (modern) modernFieldOrder(db) else legacyFieldOrder(db)
        val deckNames = if (modern) modernDeckNames(db) else legacyDeckNames(db)

        val out = ArrayList<CardRecord>()
        db.rawQuery(
            "SELECT n.guid, n.mid, n.flds, c.did FROM notes n JOIN cards c ON c.nid = n.id",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val guid = c.getString(0) ?: continue
                val mid = c.getString(1)
                val flds = c.getString(2) ?: ""
                val did = c.getString(3)

                val order = fieldOrder[mid].orEmpty()
                val values = flds.split(UNIT_SEP)
                val byName = HashMap<String, String>(order.size)
                for (i in order.indices) byName[order[i]] = values.getOrElse(i) { "" }

                val deckName = deckNames[did] ?: ""
                val parts = deckName.split("::")
                out.add(
                    CardRecord(
                        guid = guid,
                        deckName = deckName,
                        subject = parts.getOrElse(0) { "" },
                        theme = parts.getOrElse(1) { "" },
                        front = byName["Front"] ?: "",
                        back = byName["Back"] ?: "",
                        note = byName["Note"] ?: "",
                    )
                )
            }
        }
        return out
    }

    private fun hasTable(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name))
            .use { it.moveToFirst() }

    // ---- schema 11: note types and decks are JSON on the single `col` row ----

    private fun colJson(db: SQLiteDatabase, column: String): JSONObject =
        try {
            db.rawQuery("SELECT $column FROM col", null).use { c ->
                if (c.moveToFirst()) JSONObject(c.getString(0) ?: "{}") else JSONObject()
            }
        } catch (e: Exception) {
            Log.w(TAG, "col.$column unreadable: ${e.message}")
            JSONObject()
        }

    private fun legacyFieldOrder(db: SQLiteDatabase): Map<String, List<String>> {
        val models = colJson(db, "models")
        val out = HashMap<String, List<String>>()
        for (mid in models.keys()) {
            val flds = models.optJSONObject(mid)?.optJSONArray("flds") ?: continue
            out[mid] = (0 until flds.length())
                .map { i ->
                    val f = flds.getJSONObject(i)
                    f.optInt("ord", i) to f.optString("name", "")
                }
                .sortedBy { it.first }
                .map { it.second }
        }
        return out
    }

    private fun legacyDeckNames(db: SQLiteDatabase): Map<String, String> {
        val decks = colJson(db, "decks")
        val out = HashMap<String, String>()
        for (did in decks.keys()) out[did] = decks.optJSONObject(did)?.optString("name", "") ?: ""
        return out
    }

    // ---- schema 18: real `fields` / `decks` tables ----

    private fun modernFieldOrder(db: SQLiteDatabase): Map<String, List<String>> {
        val acc = HashMap<String, MutableList<Pair<Int, String>>>()
        db.rawQuery("SELECT ntid, ord, name FROM fields", null).use { c ->
            while (c.moveToNext()) {
                acc.getOrPut(c.getString(0)) { mutableListOf() }
                    .add(c.getInt(1) to (c.getString(2) ?: ""))
            }
        }
        return acc.mapValues { (_, v) -> v.sortedBy { it.first }.map { it.second } }
    }

    private fun modernDeckNames(db: SQLiteDatabase): Map<String, String> {
        val out = HashMap<String, String>()
        db.rawQuery("SELECT id, name FROM decks", null).use { c ->
            while (c.moveToNext()) {
                // Schema 18 nests with U+001F; normalise to the `::` the rest of
                // the app (and every stored group/archive row) speaks.
                out[c.getString(0)] = (c.getString(1) ?: "").replace(UNIT_SEP.toString(), "::")
            }
        }
        return out
    }
}
