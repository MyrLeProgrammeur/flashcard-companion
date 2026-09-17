package com.matheo.flashcardcompanion.ui

import androidx.compose.runtime.compositionLocalOf

val SUPPORTED_LANGS = listOf("fr", "en")
val LANG_LABELS = mapOf("fr" to "Français", "en" to "English")

/**
 * Lookup for one language, with French as the fallback for any key an
 * incomplete translation is missing (and the key itself as a last resort, so a
 * typo shows up as a visible key rather than as blank UI).
 */
class Translator(val lang: String) {

    private val table = if (lang == "en") Strings.en else Strings.fr
    private val extra = if (lang == "en") StringsExtra.en else StringsExtra.fr
    private val override = if (lang == "en") StringsExtra.overrideEn else StringsExtra.overrideFr

    private fun lookup(key: String): String? =
        override[key] ?: table[key] ?: extra[key]
            ?: StringsExtra.overrideFr[key] ?: Strings.fr[key] ?: StringsExtra.fr[key]

    fun t(key: String, vars: Map<String, Any?> = emptyMap()): String {
        val raw = lookup(key) ?: return key
        if (vars.isEmpty()) return raw
        var out = raw
        for ((k, v) in vars) out = out.replace("{$k}", v?.toString() ?: "")
        return out
    }

    /**
     * Plural pair "singular|plural". French treats 0 as singular, which is why
     * the test is n > 1 rather than n != 1.
     */
    fun tn(key: String, n: Int, vars: Map<String, Any?> = emptyMap()): String {
        val raw = lookup(key) ?: return key
        val parts = raw.split("|")
        val chosen = if (parts.size == 2 && n > 1) parts[1] else parts[0]
        var out = chosen.replace("{n}", n.toString())
        for ((k, v) in vars) out = out.replace("{$k}", v?.toString() ?: "")
        return out
    }
}

val LocalT = compositionLocalOf { Translator("fr") }
