package com.matheo.flashcardcompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheo.flashcardcompanion.AppViewModel
import com.matheo.flashcardcompanion.data.ExamWithStats
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Exams: the grade you got, next to how well and how long you actually revised
 * that subject. Three independent numbers, deliberately never merged into a
 * single score — the point is to let you see the relationship yourself.
 */
@Composable
fun ExamsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<ExamWithStats>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var addOpen by remember { mutableStateOf(false) }
    var gradeFor by remember { mutableStateOf<ExamWithStats?>(null) }

    LaunchedEffect(reload) { rows = vm.repo.examsWithStats() }

    Column(Modifier.fillMaxSize().padding(horizontal = PagePadding)) {
        AppHeader(title = t.t("nav.exams"), onBack = onBack)
        ScreenTitle(t.t("exams.corrTitle"))
        ScreenSub(t.t("exams.corrSub"))

        Box(Modifier.weight(1f)) {
            if (rows.isEmpty()) {
                Text(t.t("exams.empty"), color = c.muted, fontSize = 14.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(rows, key = { it.row.id }) { item ->
                        val interaction = remember(item.row.id) { MutableInteractionSource() }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .pressScale(interaction, .985f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.surface)
                                .clickableNoRipple(interaction) { gradeFor = item }
                                .padding(14.dp),
                        ) {
                            Text(
                                item.row.deckPath,
                                color = c.ink,
                                fontFamily = FcType.serif,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Metric(t.t("exams.thGrade"), item.row.grade?.let { "%.1f".format(it) } ?: "—")
                                Metric(
                                    t.t("stats.successRate"),
                                    item.successRate?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                                )
                                Metric(t.t("stats.totalTime"), formatMinutes(item.totalTimeSpentMs))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                item.row.expectedResultsDate,
                                color = c.muted2,
                                fontFamily = FcType.mono,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            Cta(t.t("exams.add")) { addOpen = true }
        }
    }

    if (addOpen) {
        AddExamDialog(vm, onClose = { addOpen = false; reload++ })
    }

    gradeFor?.let { item ->
        var grade by remember(item.row.id) { mutableStateOf(item.row.grade?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { gradeFor = null },
            containerColor = c.surface,
            title = { Text(item.row.deckPath, fontFamily = FcType.serif, color = c.ink) },
            text = {
                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it },
                    label = { Text(t.t("exams.thGrade")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton({
                    vm.repo.store.setExamGrade(item.row.id, grade.toDoubleOrNull())
                    gradeFor = null
                    reload++
                }) { Text(t.t("common.ok")) }
            },
            dismissButton = {
                TextButton({
                    vm.repo.store.deleteExam(item.row.id)
                    gradeFor = null
                    reload++
                }) { Text(t.t("exams.delete")) }
            },
        )
    }
}

@Composable
private fun Metric(label: String, value: String) {
    val c = LocalFcColors.current
    Column {
        Text(value, color = c.ink, fontFamily = FcType.mono, fontSize = 14.sp)
        Text(label, color = c.muted2, fontSize = 10.sp)
    }
}

@Composable
private fun AddExamDialog(vm: AppViewModel, onClose: () -> Unit) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val scope = rememberCoroutineScope()
    var subjects by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var picked by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { subjects = vm.repo.subjects() }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = c.surface,
        title = { Text(t.t("exams.newTitle"), fontFamily = FcType.serif, color = c.ink) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(subjects, key = { it.first }) { (path, count) ->
                        val selected = picked == path
                        val interaction = remember(path) { MutableInteractionSource() }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) c.accentSoft else c.surface)
                                .clickableNoRipple(interaction) { picked = path }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                path,
                                color = if (selected) c.accent else c.ink,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text("$count", color = c.muted2, fontFamily = FcType.mono, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(t.t("exams.expectedResults")) },
                    placeholder = { Text("2026-06-15") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked != null && date.isNotBlank(),
                onClick = {
                    vm.repo.store.createExam(picked!!, date.trim())
                    onClose()
                },
            ) { Text(t.t("home.confirm")) }
        },
        dismissButton = { TextButton(onClose) { Text(t.t("home.cancel")) } },
    )
}

private fun formatMinutes(ms: Long): String {
    val minutes = ms / 60000
    return if (minutes >= 60) "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}"
    else "${minutes}min"
}
