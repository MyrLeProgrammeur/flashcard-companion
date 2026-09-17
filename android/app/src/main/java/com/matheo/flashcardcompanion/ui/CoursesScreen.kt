package com.matheo.flashcardcompanion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheo.flashcardcompanion.AppViewModel
import java.io.File

/**
 * The course PDF folder, browsed as it actually is on disk.
 *
 * Folders with no PDF anywhere beneath them are dropped, and Syncthing's dot
 * directories (`.stfolder` and friends) are skipped, so this shows course
 * material rather than a file manager.
 */
@Composable
fun CoursesScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenPdf: (String) -> Unit,
) {
    val t = LocalT.current
    val c = LocalFcColors.current

    val root = remember { vm.repo.prefs.pdfDirFile }
    var stack by remember { mutableStateOf(listOf<File>()) }
    var loading by remember { mutableStateOf(true) }
    var pdfCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(Unit) {
        // One walk, reused for every folder's count.
        val all = vm.repo.coursePdfs()
        pdfCounts = all.groupingBy { it.parent ?: "" }.eachCount()
        loading = false
    }

    val dir = stack.lastOrNull() ?: root
    BackHandler(enabled = stack.isNotEmpty()) { stack = stack.dropLast(1) }

    val entries = remember(dir, pdfCounts) { listEntries(dir) }

    Column(Modifier.fillMaxSize().padding(horizontal = PagePadding)) {
        AppHeader(title = t.t("nav.courses"), onBack = onBack)

        if (stack.isNotEmpty()) {
            Crumbs(
                buildList {
                    add(t.t("courses.title") to { stack = emptyList() })
                    stack.forEachIndexed { i, f -> add(f.name to { stack = stack.take(i + 1) }) }
                }
            )
        }

        ScreenTitle(stack.lastOrNull()?.name ?: t.t("courses.title"))
        ScreenSub(t.t("courses.sub"))

        Box(Modifier.weight(1f)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.accent)

                entries.isEmpty() ->
                    Text(t.t("courses.noPdf"), color = c.muted, fontSize = 14.sp)

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(entries, key = { it.absolutePath }) { entry ->
                        val isDir = entry.isDirectory
                        val count = if (isDir) countPdfs(entry) else 0
                        DeckRow(
                            name = if (isDir) entry.name else entry.nameWithoutExtension,
                            meta = if (isDir) t.t("courses.fileCount", mapOf("n" to count)) else "PDF",
                            dueCount = 0,
                            glyph = if (isDir) "◫" else "¶",
                            done = true,
                            onClick = {
                                if (isDir) stack = stack + entry else onOpenPdf(entry.absolutePath)
                            },
                        )
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
}

/** Folders first, then PDFs; both alphabetical, dotfiles skipped. */
private fun listEntries(dir: File): List<File> {
    val children = dir.listFiles()?.filterNot { it.name.startsWith(".") } ?: return emptyList()
    val dirs = children.filter { it.isDirectory && countPdfs(it) > 0 }.sortedBy { it.name.lowercase() }
    val pdfs = children.filter { it.isFile && it.extension.equals("pdf", true) }
        .sortedBy { it.name.lowercase() }
    return dirs + pdfs
}

private fun countPdfs(dir: File): Int {
    var n = 0
    val stack = ArrayDeque(listOf(dir))
    while (stack.isNotEmpty()) {
        val d = stack.removeLast()
        for (f in d.listFiles() ?: emptyArray()) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) stack.add(f)
            else if (f.extension.equals("pdf", true)) n++
        }
    }
    return n
}
