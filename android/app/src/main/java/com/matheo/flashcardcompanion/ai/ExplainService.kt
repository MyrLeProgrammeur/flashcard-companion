package com.matheo.flashcardcompanion.ai

import android.content.Context
import com.matheo.flashcardcompanion.data.CardRecord
import com.matheo.flashcardcompanion.data.Prefs
import com.matheo.flashcardcompanion.data.Repository
import java.io.File

data class ExplainResult(
    val explanation: String,
    val sourceFiles: List<File>,
    val model: String,
    val cached: Boolean,
    val generatedAt: String? = null,
) {
    val grounded: Boolean get() = sourceFiles.isNotEmpty()
}

/**
 * The "explain this card" and "ask about this PDF" features.
 *
 * Both build their prompt exactly as the Python backend did (see [Prompts]),
 * and both degrade explicitly rather than silently: with no source PDF the
 * model is told so and answers from the card alone, which the UI surfaces as a
 * degraded-mode banner.
 */
class ExplainService(private val context: Context, private val repo: Repository) {

    private val prefs: Prefs get() = repo.prefs

    fun client(): InfercomClient = InfercomClient(prefs.baseUrl, prefs.apiKey, prefs.model)

    suspend fun explain(
        card: CardRecord,
        lang: String,
        critique: String? = null,
        force: Boolean = false,
    ): ExplainResult {
        val l = Prompts.clampLang(lang)
        val key = repo.store.explainKey(card.guid, l)
        val hasCritique = !critique.isNullOrBlank()

        // A critique is an explicit request for a different answer, so it
        // bypasses the cache the same way `force` does.
        if (!force && !hasCritique) {
            repo.store.getExplanation(key)?.let { hit ->
                return ExplainResult(
                    explanation = hit.explanation,
                    sourceFiles = hit.sourceFiles.map(::File),
                    model = hit.model,
                    cached = true,
                    generatedAt = hit.generatedAt,
                )
            }
        }

        if (hasCritique) {
            repo.store.saveExplainCritique(card.guid, l, prefs.model, critique!!.trim())
        }

        val sources = repo.sourcePdfsFor(card.deckName)
        val pdfContext =
            if (sources.isEmpty()) ""
            else PdfText.buildContext(context, sources, Prefs.MAX_PDF_CONTEXT_CHARS)

        val system = "${Prompts.EXPLAIN_SYSTEM} ${Prompts.langDirective(l)}"

        val user = buildString {
            append("Question: ").append(card.front).append('\n')
            append("Réponse: ").append(card.back).append('\n')
            if (card.note.isNotEmpty()) append("Note: ").append(card.note).append('\n')
            if (pdfContext.isNotEmpty()) {
                append('\n').append(Prompts.SOURCE_EXCERPTS_HEADER).append('\n').append(pdfContext)
            } else {
                append('\n').append(Prompts.NO_SOURCE_NOTE)
            }
            if (hasCritique) {
                append("\n\n").append(Prompts.CRITIQUE_PREFIX).append(critique!!.trim()).append('\n')
                append(Prompts.CRITIQUE_SUFFIX)
            }
        }

        val answer = client().chat(
            listOf(ChatMessage("system", system), ChatMessage("user", user))
        )

        // Only a successful call replaces the cache, so a failure leaves the
        // previous explanation intact.
        repo.store.saveExplanation(key, answer, sources.map { it.absolutePath }, prefs.model)

        return ExplainResult(answer, sources, prefs.model, cached = false)
    }

    /** Records a thumbs vote. Pure telemetry: never blocks or fails the UI. */
    fun logFeedback(card: CardRecord, lang: String, vote: Int) {
        val l = Prompts.clampLang(lang)
        val cached = repo.store.getExplanation(repo.store.explainKey(card.guid, l))
        repo.store.logExplainFeedback(
            guid = card.guid,
            lang = l,
            model = cached?.model ?: prefs.model,
            vote = vote,
            grounded = cached?.let { if (it.sourceFiles.isNotEmpty()) 1 else 0 },
            deckName = card.deckName,
        )
    }

    /**
     * A chat turn about an open PDF. The whole conversation is re-sent each
     * turn with the context re-grounded on the page being read — nothing is
     * cached, because a turn is only meaningful in its conversation.
     */
    suspend fun pdfHelp(
        pdf: File,
        messages: List<ChatMessage>,
        lang: String,
        page: Int?,
    ): String {
        val l = Prompts.clampLang(lang)
        val pdfContext =
            if (page != null && page > 0)
                PdfText.buildWindowContext(context, pdf, Prefs.MAX_PDF_CONTEXT_CHARS, page)
            else
                PdfText.buildContext(context, listOf(pdf), Prefs.MAX_PDF_CONTEXT_CHARS)

        val system = buildString {
            append(Prompts.PDF_HELP_SYSTEM).append(' ').append(Prompts.langDirective(l))
            if (pdfContext.isNotEmpty()) {
                if (page != null && page > 0) {
                    append("\n\n").append(Prompts.readingPageNote(page))
                }
                append("\n\n").append(Prompts.SOURCE_EXCERPTS_HEADER).append('\n').append(pdfContext)
            } else {
                append("\n\n").append(Prompts.NO_PDF_TEXT_NOTE)
            }
        }

        return client().chat(listOf(ChatMessage("system", system)) + messages)
    }
}
