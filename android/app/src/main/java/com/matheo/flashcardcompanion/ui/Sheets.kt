package com.matheo.flashcardcompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheo.flashcardcompanion.AppViewModel
import com.matheo.flashcardcompanion.data.DeckNode
import com.matheo.flashcardcompanion.data.GROUP_PREFIX
import com.matheo.flashcardcompanion.data.groupNameFromPath

@Composable
private fun SheetItem(
    icon: ImageVector,
    label: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val c = LocalFcColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressScale(interaction)
            .clickableNoRipple(interaction, onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = c.muted, modifier = Modifier.size(20.dp))
        Text(label, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = c.muted, fontFamily = FcType.mono, fontSize = 12.sp)
    }
}

/** The hamburger menu: navigation plus the theme and language toggles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMenuSheet(vm: AppViewModel, onDismiss: () -> Unit, onNavigate: (String) -> Unit) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            SheetItem(Icons.Filled.Book, t.t("nav.courses")) { onNavigate("courses") }
            SheetItem(Icons.Filled.EventNote, t.t("nav.exams")) { onNavigate("exams") }
            SheetItem(Icons.Filled.ShowChart, t.t("nav.stats")) { onNavigate("stats") }
            SheetItem(Icons.Filled.Settings, t.t("nav.settings")) { onNavigate("settings") }

            Spacer(Modifier.height(6.dp))

            val isDark = c.isDark
            SheetItem(
                if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                t.t("nav.theme"),
                trailing = if (isDark) "dark" else "light",
            ) { vm.setTheme(if (isDark) "light" else "dark") }

            val lang = vm.repo.prefs.lang
            SheetItem(
                Icons.Filled.Language,
                t.t("nav.language"),
                trailing = LANG_LABELS[lang],
            ) { vm.setLang(if (lang == "fr") "en" else "fr") }

            Spacer(Modifier.height(14.dp))
        }
    }
}

/** Long-press actions on a subject or a folder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckActionsSheet(
    node: DeckNode,
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onFileInto: () -> Unit,
    onRename: () -> Unit,
) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val sheetState = rememberModalBottomSheetState()
    val groupName = groupNameFromPath(node.path)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                node.name,
                Modifier.padding(start = 14.dp, bottom = 6.dp),
                fontFamily = FcType.serif,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = c.ink,
            )

            if (groupName != null) {
                SheetItem(Icons.Filled.Edit, t.t("home.renameFolder")) { onRename() }
                SheetItem(Icons.Filled.FolderOff, t.t("home.dissolveFolder")) {
                    vm.dissolveGroup(groupName); onDismiss()
                }
                SheetItem(Icons.Filled.Archive, t.t("home.archiveFolder")) {
                    node.children.forEach { vm.setArchived(it.name, true) }
                    onDismiss()
                }
            } else {
                SheetItem(Icons.Filled.DriveFileMove, t.t("home.newFolder")) { onFileInto() }
                SheetItem(Icons.Filled.Archive, t.t("home.archive")) {
                    vm.setArchived(node.name, true); onDismiss()
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

sealed interface FolderDialog {
    data object Create : FolderDialog
    data class Rename(val node: DeckNode) : FolderDialog
    data class FileInto(val node: DeckNode) : FolderDialog
}

@Composable
fun FolderDialogHost(dialog: FolderDialog, vm: AppViewModel, onClose: () -> Unit) {
    val t = LocalT.current
    val c = LocalFcColors.current

    when (dialog) {
        is FolderDialog.FileInto -> {
            // Pick an existing folder, or type a new name.
            val existing = remember { vm.groupNames() }
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = onClose,
                containerColor = c.surface,
                title = { Text(t.t("home.newFolderTitle"), fontFamily = FcType.serif, color = c.ink) },
                text = {
                    Column {
                        Text(t.t("home.pickSubjects"), color = c.muted, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        if (existing.isNotEmpty()) {
                            LazyColumn(Modifier.heightIn(max = 180.dp)) {
                                items(existing) { g ->
                                    SheetItem(Icons.Filled.DriveFileMove, g) {
                                        vm.setDeckGroup(dialog.node.name, g); onClose()
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(t.t("home.folderNameLabel")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = isLegalFolderName(name),
                        onClick = { vm.setDeckGroup(dialog.node.name, name.trim()); onClose() },
                    ) { Text(t.t("home.confirm")) }
                },
                dismissButton = { TextButton(onClose) { Text(t.t("home.cancel")) } },
            )
        }

        is FolderDialog.Rename -> {
            val old = groupNameFromPath(dialog.node.path).orEmpty()
            var name by remember { mutableStateOf(old) }
            AlertDialog(
                onDismissRequest = onClose,
                containerColor = c.surface,
                title = { Text(t.t("home.renameFolderTitle"), fontFamily = FcType.serif, color = c.ink) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(t.t("home.folderNameLabel")) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = isLegalFolderName(name),
                        onClick = { vm.renameGroup(old, name.trim()); onClose() },
                    ) { Text(t.t("home.confirm")) }
                },
                dismissButton = { TextButton(onClose) { Text(t.t("home.cancel")) } },
            )
        }

        FolderDialog.Create -> {
            // Creating an empty folder has nothing to hold, so this picks the
            // subject to file first, then its folder name.
            var name by remember { mutableStateOf("") }
            var subject by remember { mutableStateOf<String?>(null) }
            val roots = remember { vm.home.value.tree.filterNot { it.isGroup }.map { it.name } }
            AlertDialog(
                onDismissRequest = onClose,
                containerColor = c.surface,
                title = { Text(t.t("home.newFolderTitle"), fontFamily = FcType.serif, color = c.ink) },
                text = {
                    Column {
                        Text(t.t("home.pickSubjects"), color = c.muted, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.heightIn(max = 200.dp)) {
                            items(roots) { s ->
                                val selected = subject == s
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) c.accentSoft else c.surface)
                                        .clickableNoRipple(remember(s) { MutableInteractionSource() }) {
                                            subject = s
                                        }
                                        .padding(12.dp),
                                ) {
                                    Text(s, color = if (selected) c.accent else c.ink, fontSize = 14.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(t.t("home.folderNameLabel")) },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = subject != null && isLegalFolderName(name),
                        onClick = { vm.setDeckGroup(subject!!, name.trim()); onClose() },
                    ) { Text(t.t("home.confirm")) }
                },
                dismissButton = { TextButton(onClose) { Text(t.t("home.cancel")) } },
            )
        }
    }
}

/** "::" would collide with a deck path, and the prefix with a folder path. */
private fun isLegalFolderName(name: String): Boolean {
    val n = name.trim()
    return n.isNotEmpty() && !n.contains("::") && !n.startsWith(GROUP_PREFIX)
}

/** Shown until the app can read the Syncthing folders. */
@Composable
fun StorageGateScreen(onGrant: () -> Unit) {
    val t = LocalT.current
    val c = LocalFcColors.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(80.dp))
        ScreenTitle(t.t("storage.title"))
        Text(
            t.t("storage.desc"),
            color = c.muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
        )
        Cta(t.t("storage.grant"), onClick = onGrant)
        Spacer(Modifier.height(14.dp))
        Text(
            t.t("storage.note"),
            color = c.muted2,
            fontSize = 12.sp,
            fontFamily = FcType.mono,
        )
    }
}
