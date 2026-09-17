package com.matheo.flashcardcompanion.data

import android.content.Context
import java.io.File

/**
 * App-level preferences: where the synced folders are, the UI language and
 * theme, and the Infercom key.
 *
 * The key sits in the app's private SharedPreferences. That is the same
 * exposure the Termux backend had (a plaintext `.env` in the home directory)
 * and it stays off any Syncthing folder, so it never leaves the phone.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("flashcard_companion", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_APKG_DIR = "/storage/emulated/0/syncthing/Flashcards"
        const val DEFAULT_PDF_DIR = "/storage/emulated/0/syncthing/Cours"
        const val DEFAULT_BASE_URL = "https://api.infercom.ai/v1"
        const val DEFAULT_MODEL = "DeepSeek-V3.2"
        const val MAX_PDF_CONTEXT_CHARS = 15000
    }

    var apkgDir: String
        get() = sp.getString("apkg_dir", DEFAULT_APKG_DIR)!!
        set(v) = sp.edit().putString("apkg_dir", v).apply()

    var pdfDir: String
        get() = sp.getString("pdf_dir", DEFAULT_PDF_DIR)!!
        set(v) = sp.edit().putString("pdf_dir", v).apply()

    var lang: String
        get() = sp.getString("lang", "fr")!!
        set(v) = sp.edit().putString("lang", v).apply()

    /** "light", "dark", or "system". */
    var theme: String
        get() = sp.getString("theme", "system")!!
        set(v) = sp.edit().putString("theme", v).apply()

    var apiKey: String
        get() = sp.getString("api_key", "")!!
        set(v) = sp.edit().putString("api_key", v).apply()

    var baseUrl: String
        get() = sp.getString("base_url", DEFAULT_BASE_URL)!!
        set(v) = sp.edit().putString("base_url", v).apply()

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL)!!
        set(v) = sp.edit().putString("model", v).apply()

    /** One-shot: has the existing Termux SRS history already been offered for import? */
    var importPrompted: Boolean
        get() = sp.getBoolean("import_prompted", false)
        set(v) = sp.edit().putBoolean("import_prompted", v).apply()

    val apkgDirFile: File get() = File(apkgDir)
    val pdfDirFile: File get() = File(pdfDir)
}
