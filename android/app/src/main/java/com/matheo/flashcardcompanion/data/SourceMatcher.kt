package com.matheo.flashcardcompanion.data

import java.io.File

/**
 * Best-effort matching of a deck to the course PDFs behind it.
 *
 * The similarity is a faithful port of Python's `difflib.SequenceMatcher.ratio`
 * — the Ratcliff/Obershelp (gestalt) coefficient, *not* Levenshtein. That
 * matters: the 0.6 threshold below was tuned against this exact measure, and a
 * lookalike metric quietly changes which PDFs ground an explanation.
 */
object Similarity {

    /**
     * Longest contiguous matching block within a[alo,ahi) and b[blo,bhi).
     * Returns (i, j, size), preferring the earliest block in a, then in b.
     */
    private fun longestMatch(
        a: String, b: String, alo: Int, ahi: Int, blo: Int, bhi: Int,
        b2j: Map<Char, List<Int>>,
    ): Triple<Int, Int, Int> {
        var besti = alo; var bestj = blo; var bestsize = 0
        var j2len = HashMap<Int, Int>()
        for (i in alo until ahi) {
            val newj2len = HashMap<Int, Int>()
            for (j in b2j[a[i]].orEmpty()) {
                if (j < blo) continue
                if (j >= bhi) break
                val k = (j2len[j - 1] ?: 0) + 1
                newj2len[j] = k
                if (k > bestsize) {
                    besti = i - k + 1; bestj = j - k + 1; bestsize = k
                }
            }
            j2len = newj2len
        }
        return Triple(besti, bestj, bestsize)
    }

    private fun matchingBlocksTotal(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        val b2j = HashMap<Char, MutableList<Int>>()
        for ((j, ch) in b.withIndex()) b2j.getOrPut(ch) { mutableListOf() }.add(j)

        var total = 0
        val queue = ArrayDeque<IntArray>()
        queue.add(intArrayOf(0, a.length, 0, b.length))
        while (queue.isNotEmpty()) {
            val (alo, ahi, blo, bhi) = queue.removeLast()
            val (i, j, size) = longestMatch(a, b, alo, ahi, blo, bhi, b2j)
            if (size == 0) continue
            total += size
            if (alo < i && blo < j) queue.add(intArrayOf(alo, i, blo, j))
            if (i + size < ahi && j + size < bhi) queue.add(intArrayOf(i + size, ahi, j + size, bhi))
        }
        return total
    }

    private operator fun IntArray.component1() = this[0]
    private operator fun IntArray.component2() = this[1]
    private operator fun IntArray.component3() = this[2]
    private operator fun IntArray.component4() = this[3]

    /** `2 * matched / (len(a) + len(b))`, case-insensitive, as difflib computes it. */
    fun ratio(a: String, b: String): Double {
        val x = a.lowercase()
        val y = b.lowercase()
        val total = x.length + y.length
        if (total == 0) return 1.0
        return 2.0 * matchingBlocksTotal(x, y) / total
    }
}

object SourceMatcher {
    const val MATCH_THRESHOLD = 0.6

    /**
     * Every PDF under [pdfDir]. Walking this costs seconds on Android shared
     * storage (it is FUSE-backed), so callers matching several subjects must
     * reuse one listing rather than walk per subject.
     */
    fun listPdfs(pdfDir: File): List<File> {
        if (!pdfDir.isDirectory) return emptyList()
        val out = ArrayList<File>()
        pdfDir.walkTopDown()
            .onEnter { !it.name.startsWith(".") }
            .forEach { if (it.isFile && it.extension.equals("pdf", ignoreCase = true)) out.add(it) }
        return out.sortedBy { it.absolutePath }
    }

    /**
     * Matches each non-blank deck-path segment against each PDF's filename stem
     * and its immediate parent folder name, keeping the best score per PDF.
     * Returns every match above the threshold, best first.
     */
    fun findSourcePdfs(terms: List<String>, pdfs: List<File>): List<File> {
        val cleaned = terms.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return emptyList()
        return pdfs.mapNotNull { pdf ->
            val candidates = listOf(pdf.nameWithoutExtension, pdf.parentFile?.name ?: "")
            val score = cleaned.maxOf { t -> candidates.maxOf { c -> Similarity.ratio(t, c) } }
            if (score >= MATCH_THRESHOLD) score to pdf else null
        }.sortedByDescending { it.first }.map { it.second }
    }
}
