package com.matheo.flashcardcompanion.ui

/**
 * The small Markdown subset the models actually emit, turned into the HTML that
 * [MathText] renders. A port of `renderMarkdown` in the web UI's `common.js`.
 *
 * Maths delimiters are deliberately left untouched — `$...$`, `\(...\)` and
 * friends are handed through for KaTeX to pick up after this runs.
 */
fun renderMarkdown(src: String): String {
    val out = StringBuilder()
    var inList = false
    var inCode = false
    val para = ArrayList<String>()

    fun flushPara() {
        if (para.isNotEmpty()) {
            out.append("<p>").append(inlineMd(para.joinToString(" "))).append("</p>")
            para.clear()
        }
    }

    fun closeList() {
        if (inList) {
            out.append("</ul>")
            inList = false
        }
    }

    for (line in src.replace("\r\n", "\n").split("\n")) {
        if (line.trimStart().startsWith("```")) {
            flushPara(); closeList()
            out.append(if (inCode) "</code></pre>" else "<pre><code>")
            inCode = !inCode
            continue
        }
        if (inCode) {
            out.append(escapeHtml(line)).append("\n")
            continue
        }
        if (line.isBlank()) {
            flushPara(); closeList()
            continue
        }

        val heading = Regex("^(#{1,6})\\s+(.*)$").find(line)
        if (heading != null) {
            flushPara(); closeList()
            out.append("<h3>").append(inlineMd(heading.groupValues[2])).append("</h3>")
            continue
        }

        val item = Regex("^\\s*[-*]\\s+(.*)$").find(line)
        if (item != null) {
            flushPara()
            if (!inList) { out.append("<ul>"); inList = true }
            out.append("<li>").append(inlineMd(item.groupValues[1])).append("</li>")
            continue
        }

        closeList()
        para.add(line.trim())
    }

    flushPara()
    closeList()
    if (inCode) out.append("</code></pre>")
    return out.toString()
}

private fun escapeHtml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun inlineMd(s: String): String {
    var t = escapeHtml(s)
    t = Regex("`([^`]+)`").replace(t) { "<code>${it.groupValues[1]}</code>" }
    t = Regex("\\*\\*([^*]+)\\*\\*").replace(t) { "<strong>${it.groupValues[1]}</strong>" }
    t = Regex("(^|[^*])\\*([^*]+)\\*").replace(t) { "${it.groupValues[1]}<em>${it.groupValues[2]}</em>" }
    return t
}
