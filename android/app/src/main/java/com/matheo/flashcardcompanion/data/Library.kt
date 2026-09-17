package com.matheo.flashcardcompanion.data

import com.matheo.flashcardcompanion.srs.CardState
import java.time.Instant

const val GROUP_PREFIX = "@group:"

/**
 * Subject names are compared loosely because the pipeline turns underscores
 * into spaces for deck names while the source PDF folder keeps them
 * ("Foundations of ML" vs "Foundations_of_ML"). One filing must group both.
 */
fun normalise(name: String): String = name.replace("_", " ").trim().lowercase()

fun groupPath(groupName: String) = "$GROUP_PREFIX$groupName"
fun groupNameFromPath(path: String): String? =
    if (path.startsWith(GROUP_PREFIX)) path.removePrefix(GROUP_PREFIX) else null

fun subjectsInGroup(groups: Map<String, String>, groupName: String): Set<String> =
    groups.filterValues { it == groupName }.keys

data class DeckNode(
    val name: String,
    val path: String,
    val cardCount: Int,
    val dueCount: Int,
    val children: List<DeckNode> = emptyList(),
    val isGroup: Boolean = false,
    /** A node's own cards, shown beside its sub-decks. */
    val isDirect: Boolean = false,
)

/**
 * Archiving hides a subject from the home tree and from every due queue, while
 * keeping its history in the stats. It matches on the *top-level* segment, so
 * archiving "M1" hides "M1::Eco::TD1" — and archiving "M1::Eco" hides nothing.
 */
fun archivedFilter(archived: List<String>): (String) -> Boolean {
    val set = archived.map(::normalise).toSet()
    return { deckName -> normalise(deckName.substringBefore("::")) in set }
}

/**
 * `path` scoping, matching the three cases the backend supported:
 *  - ""            -> everything (review-all)
 *  - "@group:Foo"  -> the folder's member subjects, compared loosely
 *  - "a::b"        -> that deck and its descendants, compared exactly
 */
fun scopeFilter(path: String, groups: Map<String, String>): (String) -> Boolean {
    val groupName = groupNameFromPath(path)
    if (groupName != null) {
        val members = subjectsInGroup(groups, groupName).map(::normalise).toSet()
        return { deckName -> normalise(deckName.substringBefore("::")) in members }
    }
    if (path.isEmpty()) return { true }
    return { deckName -> deckName == path || deckName.startsWith("$path::") }
}

object Library {

    /**
     * Cards that are due now, most-overdue first.
     *
     * A card with no stored state carries the epoch as its due date, so unseen
     * cards are always due and always sort to the front. The sort is stable, so
     * cards tied on the epoch keep library order.
     */
    fun dueCards(
        cards: List<CardRecord>,
        states: Map<String, CardState>,
        archived: List<String>,
        groups: Map<String, String>,
        now: Instant,
        path: String = "",
    ): List<Pair<CardRecord, CardState>> {
        val isArchived = archivedFilter(archived)
        val inScope = scopeFilter(path, groups)
        return cards.asSequence()
            .filter { !isArchived(it.deckName) && inScope(it.deckName) }
            .map { it to (states[it.guid] ?: CardState()) }
            .filter { (_, s) -> s.dueAt != null && !s.dueAt.isAfter(now) }
            .sortedBy { (_, s) -> s.dueAt }
            .toList()
    }

    /**
     * The home tree: every deck path, nested at arbitrary depth, with counts
     * aggregated over all descendants, then folded into display-only folders.
     */
    fun tree(
        cards: List<CardRecord>,
        states: Map<String, CardState>,
        archived: List<String>,
        groups: Map<String, String>,
        now: Instant,
    ): List<DeckNode> {
        val isArchived = archivedFilter(archived)
        val visible = cards.filter { !isArchived(it.deckName) }

        // path -> [cards, due]; every ancestor of a card is credited.
        val totals = LinkedHashMap<String, IntArray>()
        val own = HashMap<String, IntArray>()
        for (card in visible) {
            val state = states[card.guid] ?: CardState()
            val due = if (state.dueAt != null && !state.dueAt.isAfter(now)) 1 else 0
            val segments = card.deckName.split("::").filter { it.isNotBlank() }
                .ifEmpty { listOf("(sans nom)") }
            var prefix = ""
            for (seg in segments) {
                prefix = if (prefix.isEmpty()) seg else "$prefix::$seg"
                val t = totals.getOrPut(prefix) { intArrayOf(0, 0) }
                t[0]++; t[1] += due
            }
            val o = own.getOrPut(prefix) { intArrayOf(0, 0) }
            o[0]++; o[1] += due
        }

        fun build(prefix: String): List<DeckNode> {
            val depth = if (prefix.isEmpty()) 1 else prefix.split("::").size + 1
            val childPaths = totals.keys.filter {
                it.split("::").size == depth && (prefix.isEmpty() || it.startsWith("$prefix::"))
            }.sorted()
            return childPaths.map { p ->
                val kids = build(p)
                val t = totals[p]!!
                val o = own[p]
                // A node with both its own cards and sub-decks shows them as a
                // nameless leaf; its path is the parent's, so reviewing it also
                // pulls the sub-decks in — as the backend always did.
                val withDirect =
                    if (kids.isNotEmpty() && o != null && o[0] > 0)
                        kids + DeckNode("", p, o[0], o[1], isDirect = true)
                    else kids
                DeckNode(p.substringAfterLast("::"), p, t[0], t[1], withDirect)
            }
        }

        return applyGroups(build(""), groups)
    }

    /**
     * Folds root-level subjects into their folder. Only roots are considered —
     * nested nodes are never re-parented — and a folder whose every subject has
     * disappeared simply does not appear.
     */
    private fun applyGroups(roots: List<DeckNode>, groups: Map<String, String>): List<DeckNode> {
        val byNormalised = groups.entries.associate { normalise(it.key) to it.value }
        val loose = ArrayList<DeckNode>()
        val grouped = LinkedHashMap<String, MutableList<DeckNode>>()
        for (node in roots) {
            val g = byNormalised[normalise(node.name)]
            if (g == null) loose.add(node) else grouped.getOrPut(g) { mutableListOf() }.add(node)
        }
        val folders = grouped.map { (name, children) ->
            val sorted = children.sortedBy { it.name.lowercase() }
            DeckNode(
                name = name,
                path = groupPath(name),
                cardCount = sorted.sumOf { it.cardCount },
                dueCount = sorted.sumOf { it.dueCount },
                children = sorted,
                isGroup = true,
            )
        }
        // Folders and loose subjects sort together — a folder is not privileged.
        return (loose + folders).sortedBy { it.name.lowercase() }
    }

    /** Flat list of every deck prefix, for the exam subject picker. */
    fun subjects(cards: List<CardRecord>, archived: List<String>): List<Pair<String, Int>> {
        val isArchived = archivedFilter(archived)
        val counts = LinkedHashMap<String, Int>()
        for (card in cards) {
            if (isArchived(card.deckName)) continue
            var prefix = ""
            for (seg in card.deckName.split("::").filter { it.isNotBlank() }) {
                prefix = if (prefix.isEmpty()) seg else "$prefix::$seg"
                counts[prefix] = (counts[prefix] ?: 0) + 1
            }
        }
        return counts.entries.sortedBy { it.key }.map { it.key to it.value }
    }
}
