package com.matheo.flashcardcompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matheo.flashcardcompanion.AppViewModel
import com.matheo.flashcardcompanion.data.DeckNode

/**
 * The deck tree, drilled into one level at a time.
 *
 * A folder ("@group:") is display-only: it never renames a deck or moves a
 * card, so dissolving one just returns its subjects to the root.
 */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onReview: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val state by vm.home.collectAsStateWithLifecycle()
    val aiOnline by vm.aiOnline.collectAsStateWithLifecycle()

    // Drill-down path; empty = root.
    var stack by remember { mutableStateOf(listOf<DeckNode>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<DeckNode?>(null) }
    var folderDialog by remember { mutableStateOf<FolderDialog?>(null) }

    // Re-resolve the drill path against freshly loaded data, so archiving or
    // filing a subject cannot leave the screen pointing at a stale node.
    val currentChildren: List<DeckNode> = remember(state.tree, stack) {
        var level = state.tree
        var resolved: List<DeckNode> = level
        for (node in stack) {
            val match = level.firstOrNull { it.path == node.path } ?: return@remember resolved
            resolved = match.children
            level = match.children
        }
        resolved
    }
    val current = stack.lastOrNull()

    BackHandler(enabled = stack.isNotEmpty()) { stack = stack.dropLast(1) }

    Column(Modifier.fillMaxSize().padding(horizontal = PagePadding)) {
        AppHeader(actions = {
            HealthPill(aiOnline)
            Spacer(Modifier.width(6.dp))
            IconCircleButton(Icons.Filled.Menu, t.t("nav.menu")) { menuOpen = true }
        })

        if (stack.isNotEmpty()) {
            Crumbs(
                buildList {
                    add(t.t("home.crumbRoot") to { stack = emptyList() })
                    stack.forEachIndexed { i, node ->
                        add(node.name to { stack = stack.take(i + 1) })
                    }
                }
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ScreenTitle(current?.name ?: t.t("home.today"), Modifier.weight(1f, fill = false))
            if (stack.isEmpty()) {
                IconCircleButton(Icons.Filled.CreateNewFolder, t.t("home.newFolder")) {
                    folderDialog = FolderDialog.Create
                }
            }
        }

        val due = current?.dueCount ?: state.totalDue
        val count = currentChildren.size
        val noun = if (stack.isEmpty()) t.tn("home.deck", count) else t.tn("home.deck", count)
        ScreenSub(
            when {
                state.loading -> t.t("home.loading")
                state.error != null -> state.error!!
                else -> t.t(
                    "home.subtitle",
                    mapOf("due" to due, "dueS" to if (due > 1) "s" else "", "count" to count, "noun" to noun),
                )
            }
        )

        Box(Modifier.weight(1f)) {
            when {
                state.loading && state.tree.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.accent)

                currentChildren.isEmpty() && state.archived.isEmpty() ->
                    Text(
                        t.t("home.empty"),
                        color = c.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.TopStart),
                    )

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(currentChildren, key = { it.path + it.isDirect }) { node ->
                        DeckRow(
                            name = if (node.isDirect) t.t("home.directCards") else node.name,
                            meta = deckMeta(node, t),
                            dueCount = node.dueCount,
                            glyph = glyphFor(node),
                            done = node.dueCount == 0,
                            onClick = {
                                if (node.children.isEmpty()) onReview(node.path) else stack = stack + node
                            },
                            onLongClick = { if (!node.isDirect) actionsFor = node },
                        )
                    }

                    if (stack.isEmpty() && state.archived.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(top = 18.dp)) {
                                SectionTitle(t.t("home.archivesTitle"))
                                Text(
                                    t.t("home.archivesSub"),
                                    color = c.muted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                            }
                        }
                        items(state.archived, key = { "archived:$it" }) { subject ->
                            DeckRow(
                                name = subject,
                                meta = t.t("home.archivedMeta"),
                                dueCount = 0,
                                glyph = subject.take(1).uppercase(),
                                done = true,
                                onClick = { vm.setArchived(subject, false) },
                            )
                        }
                    }

                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            val label =
                if (current == null) t.t("home.reviewAll")
                else t.t("home.reviewScoped", mapOf("name" to current.name))
            Cta(label, count = due, enabled = due > 0) { onReview(current?.path ?: "") }
        }
    }

    if (menuOpen) {
        AppMenuSheet(
            vm = vm,
            onDismiss = { menuOpen = false },
            onNavigate = { route -> menuOpen = false; onOpen(route) },
        )
    }

    actionsFor?.let { node ->
        DeckActionsSheet(
            node = node,
            vm = vm,
            onDismiss = { actionsFor = null },
            onFileInto = { folderDialog = FolderDialog.FileInto(node); actionsFor = null },
            onRename = { folderDialog = FolderDialog.Rename(node); actionsFor = null },
        )
    }

    folderDialog?.let { dialog ->
        FolderDialogHost(dialog, vm) { folderDialog = null }
    }
}

private fun glyphFor(node: DeckNode): String = when {
    node.isGroup -> "◫"
    node.isDirect -> "•"
    else -> node.name.trim().take(1).uppercase().ifEmpty { "·" }
}

private fun deckMeta(node: DeckNode, t: Translator): String {
    val cards = t.t("home.cardCount", mapOf("n" to node.cardCount))
    val kids = node.children.size
    val folderPart = if (node.isGroup && kids > 0) " · $kids ${t.tn("home.deck", kids)}" else ""
    val upToDate = if (node.dueCount == 0) t.t("home.upToDate") else ""
    return cards + folderPart + upToDate
}
