package com.matheo.flashcardcompanion.ai

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.File

/**
 * Per-page plain text of a PDF, used to ground the AI answers.
 *
 * Android can render a PDF (`PdfRenderer`) but cannot extract its text, and the
 * Python backend leaned on pypdf/pdfplumber for this. Rather than add a PDF
 * library, this drives the pdf.js already vendored in the repo, offline from
 * `assets/pdfjs`, in a headless WebView.
 *
 * The document is served to that WebView through [WebViewClient.shouldInterceptRequest]
 * on a virtual origin, so the bytes stream from disk instead of being
 * base64-marshalled through `evaluateJavascript` — a course deck is easily
 * several megabytes.
 */
object PdfText {

    private const val ORIGIN = "https://pdf.invalid/"
    private const val DOC_PATH = "document.pdf"
    private const val TIMEOUT_MS = 60_000L

    private val cache = object : LinkedHashMap<String, List<String>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<String>>) = size > 6
    }

    private fun page(): String = """
<!DOCTYPE html><html><head><meta charset="utf-8"></head><body>
<script type="module">
  import * as pdfjsLib from './pdfjs/pdf.min.mjs';
  pdfjsLib.GlobalWorkerOptions.workerSrc = './pdfjs/pdf.worker.min.mjs';
  (async () => {
    try {
      const doc = await pdfjsLib.getDocument({ url: './$DOC_PATH' }).promise;
      const pages = [];
      for (let i = 1; i <= doc.numPages; i++) {
        const p = await doc.getPage(i);
        const tc = await p.getTextContent();
        // Join on the item level and let the backend-side cleanup collapse
        // whitespace; pdf.js emits one item per text run, not per line.
        pages.push(tc.items.map(it => it.str).join(' ').replace(/\s+/g, ' ').trim());
      }
      Android.onPages(JSON.stringify(pages));
    } catch (e) {
      Android.onError(String(e && e.message ? e.message : e));
    }
  })();
</script></body></html>
"""

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extractPages(context: Context, pdf: File): List<String> {
        val key = "${pdf.absolutePath}:${pdf.length()}:${pdf.lastModified()}"
        synchronized(cache) { cache[key] }?.let { return it }
        if (!pdf.isFile) return emptyList()

        val result = CompletableDeferred<List<String>>()

        withContext(Dispatchers.Main) {
            val web = WebView(context)
            web.settings.javaScriptEnabled = true
            web.settings.allowFileAccess = false
            web.settings.allowContentAccess = false
            web.settings.blockNetworkLoads = true

            web.addJavascriptInterface(object {
                @JavascriptInterface
                fun onPages(json: String) {
                    val arr = runCatching { JSONArray(json) }.getOrNull()
                    val out = if (arr == null) emptyList() else
                        (0 until arr.length()).map { arr.optString(it, "").trim() }
                    result.complete(out)
                }

                @JavascriptInterface
                fun onError(message: String) {
                    // An unreadable PDF degrades to "no grounding", never a crash.
                    result.complete(emptyList())
                }
            }, "Android")

            web.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    if (!url.startsWith(ORIGIN)) return null
                    val rel = url.removePrefix(ORIGIN).substringBefore('?')
                    return try {
                        when {
                            rel == DOC_PATH ->
                                WebResourceResponse("application/pdf", null, pdf.inputStream())
                            rel.startsWith("pdfjs/") -> WebResourceResponse(
                                "text/javascript", "utf-8", context.assets.open(rel),
                            )
                            else -> null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            web.loadDataWithBaseURL(ORIGIN, page(), "text/html", "utf-8", null)

            // Tear the WebView down once the answer (or the timeout) lands.
            result.invokeOnCompletion {
                web.post {
                    web.stopLoading()
                    web.destroy()
                }
            }
        }

        val pages = withTimeoutOrNull(TIMEOUT_MS) { result.await() } ?: emptyList()
        if (!result.isCompleted) result.complete(emptyList())

        val resolved = pages.ifEmpty { ocrSidecar(pdf) }
        synchronized(cache) { cache[key] = resolved }
        return resolved
    }

    /**
     * A scanned or handwritten course has no extractable text; the pipeline
     * leaves an OCR transcript next to the PDF. Returned as a single pseudo-page.
     */
    private fun ocrSidecar(pdf: File): List<String> {
        val sidecar = File(pdf.parentFile, pdf.nameWithoutExtension + ".ocr.md")
        if (!sidecar.isFile) return emptyList()
        val text = runCatching { sidecar.readText() }.getOrNull()?.trim().orEmpty()
        return if (text.isEmpty()) emptyList() else listOf(text)
    }

    // ---------------- context budgeting ----------------

    /**
     * Whole-document context across the matched PDFs, in match order, each
     * truncated to whatever budget is left.
     */
    suspend fun buildContext(context: Context, files: List<File>, maxChars: Int): String {
        val chunks = ArrayList<String>()
        var remaining = maxChars
        for (file in files) {
            if (remaining <= 0) break
            val text = extractPages(context, file).filter { it.isNotBlank() }.joinToString("\n\n")
            if (text.isEmpty()) continue
            val chunk = text.take(remaining)
            chunks.add("--- ${file.name} ---\n$chunk")
            remaining -= chunk.length
        }
        return chunks.joinToString("\n\n")
    }

    /**
     * Context centred on the page being read, expanding outward a whole page at
     * a time and alternating forward/backward.
     *
     * Reading page 30 used to be grounded with page 1, because the budget was
     * simply the first N characters of the document. The centre page is always
     * included (truncated if it alone overflows); a neighbour that does not fit
     * closes that direction rather than being cut in half; blank pages are
     * stepped over for free.
     */
    suspend fun buildWindowContext(
        context: Context,
        file: File,
        maxChars: Int,
        centerPage: Int,
    ): String {
        val pages = extractPages(context, file)
        if (pages.isEmpty()) return ""

        val centre = (centerPage - 1).coerceIn(0, pages.size - 1)
        val selected = sortedMapOf<Int, String>()
        selected[centre] = pages[centre].take(maxChars)
        var remaining = maxChars - selected[centre]!!.length

        var lo = centre
        var hi = centre
        var forward = true
        while (remaining > 0 && (lo > 0 || hi < pages.size - 1)) {
            if (forward && hi >= pages.size - 1) { forward = false; continue }
            if (!forward && lo <= 0) { forward = true; continue }
            val next = if (forward) hi + 1 else lo - 1
            val text = pages[next]
            if (text.isBlank()) {
                if (forward) hi = next else lo = next
                forward = !forward
                continue
            }
            if (text.length > remaining) {
                // Never split a page: close this direction and keep growing the other.
                if (forward) hi = pages.size - 1 else lo = 0
                forward = !forward
                continue
            }
            selected[next] = text
            remaining -= text.length
            if (forward) hi = next else lo = next
            forward = !forward
        }

        return selected.entries
            .filter { it.value.isNotBlank() }
            .joinToString("\n\n") { "--- ${file.name} (p. ${it.key + 1}) ---\n${it.value}" }
    }
}
