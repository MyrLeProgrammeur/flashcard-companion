package com.matheo.flashcardcompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheo.flashcardcompanion.AppViewModel
import com.matheo.flashcardcompanion.ai.ExplainResult
import com.matheo.flashcardcompanion.data.CardRecord
import kotlinx.coroutines.launch

/**
 * The "explain in depth" sheet.
 *
 * Grounding is stated plainly: a green chip naming the source PDFs when the
 * answer is grounded, an amber banner when the model had only the card to work
 * from. A thumbs-down asks what was wrong and regenerates with that critique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplainSheet(vm: AppViewModel, card: CardRecord, onDismiss: () -> Unit) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var result by remember { mutableStateOf<ExplainResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var voted by remember { mutableStateOf(false) }
    var critiqueOpen by remember { mutableStateOf(false) }

    suspend fun run(critique: String?) {
        loading = true
        error = null
        result = null
        val t0 = System.currentTimeMillis()
        runCatching { vm.explainService.explain(card, t.lang, critique) }
            .onSuccess { result = it; voted = false }
            .onFailure {
                error = if (vm.repo.prefs.apiKey.isBlank()) t.t("ai.noKey") else t.t("ai.error")
            }
        elapsedMs = System.currentTimeMillis() - t0
        loading = false
    }

    LaunchedEffect(card.guid) { run(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxHeight(0.88f).padding(horizontal = 18.dp)) {
            Text(
                t.t("review.deepExplTitle"),
                fontFamily = FcType.serif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = c.ink,
            )
            Spacer(Modifier.height(12.dp))

            when {
                loading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator(color = c.accent)
                }

                error != null -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.TopStart) {
                    Text(error!!, color = c.degrBandInk, fontSize = 14.sp)
                }

                else -> {
                    val r = result!!
                    if (r.grounded) {
                        Chip(
                            text = t.t(
                                "review.groundedIn",
                                mapOf("files" to r.sourceFiles.joinToString(", ") { it.name }),
                            ),
                            fg = c.pdfChipInk, bg = c.pdfChipBg, border = c.pdfChipBorder,
                            icon = Icons.Filled.Description,
                        )
                    } else {
                        Chip(
                            text = t.t("review.noSourceChip"),
                            fg = c.degrChipInk, bg = c.degrChipBg, border = c.degrChipBorder,
                            icon = Icons.Filled.Info,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(c.degrBandBg)
                                .border(BorderStroke(1.dp, c.degrBandBorder), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(t.t("review.noSourceBand"), color = c.degrBandInk, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    MathText(
                        html = renderMarkdown(r.explanation),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        fontSizeSp = 15,
                        serif = false,
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Weighted and clipped: without this the meta line takes
                        // its full intrinsic width and squeezes the thumbs into a
                        // one-letter-per-line column.
                        Text(
                            "${r.model} · " +
                                (if (r.cached) t.t("pdf.cached") else t.t("pdf.freshGen")) +
                                " · $elapsedMs ms",
                            modifier = Modifier.weight(1f),
                            fontFamily = FcType.mono,
                            fontSize = 10.sp,
                            color = c.muted2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (!voted) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.t("review.fbQuestion"), color = c.muted, fontSize = 12.sp)
                                Spacer(Modifier.width(8.dp))
                                IconCircleButton(Icons.Filled.ThumbUp, t.t("review.fbUp")) {
                                    vm.explainService.logFeedback(card, t.lang, 1)
                                    voted = true
                                }
                                IconCircleButton(Icons.Filled.ThumbDown, t.t("review.fbDown")) {
                                    vm.explainService.logFeedback(card, t.lang, -1)
                                    critiqueOpen = true
                                }
                            }
                        } else {
                            Text(
                                t.t("review.fbThanks"),
                                color = c.muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    if (critiqueOpen) {
        var critique by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { critiqueOpen = false },
            containerColor = c.surface,
            title = { Text(t.t("review.critiqueTitle"), fontFamily = FcType.serif, color = c.ink) },
            text = {
                OutlinedTextField(
                    value = critique,
                    onValueChange = { critique = it },
                    placeholder = { Text(t.t("review.critiquePlaceholder")) },
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = critique.isNotBlank(),
                    onClick = {
                        critiqueOpen = false
                        voted = false
                        scope.launch { run(critique) }
                    },
                ) { Text(t.t("review.critiqueSend")) }
            },
            dismissButton = {
                TextButton({ critiqueOpen = false; voted = true }) { Text(t.t("common.cancel")) }
            },
        )
    }
}

@Composable
private fun Chip(
    text: String,
    fg: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
    border: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = fg, fontSize = 12.sp)
    }
}
