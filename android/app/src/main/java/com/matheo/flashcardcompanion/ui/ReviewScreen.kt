package com.matheo.flashcardcompanion.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheo.flashcardcompanion.AppViewModel
import com.matheo.flashcardcompanion.data.CardRecord
import com.matheo.flashcardcompanion.srs.CardState
import com.matheo.flashcardcompanion.srs.QUALITY_AGAIN
import com.matheo.flashcardcompanion.srs.QUALITY_EASY
import com.matheo.flashcardcompanion.srs.QUALITY_GOOD
import com.matheo.flashcardcompanion.srs.QUALITY_HARD
import com.matheo.flashcardcompanion.srs.SrsSettings
import com.matheo.flashcardcompanion.srs.review
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

private data class QueueItem(val card: CardRecord, val state: CardState)

/**
 * A review session over the cards due in [path].
 *
 * The card text is rendered from data already in hand, and nothing else is
 * allowed to gate it. The web version awaited the source-PDF scan alongside the
 * due query, so a slow scan of the course folder left both faces of the card
 * completely blank; here that lookup runs after the card is on screen and only
 * ever adds the "view source" link.
 */
@Composable
fun ReviewScreen(
    vm: AppViewModel,
    path: String,
    onBack: () -> Unit,
    onOpenPdf: (String) -> Unit,
) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val scope = rememberCoroutineScope()

    var queue by remember { mutableStateOf<List<QueueItem>>(emptyList()) }
    var idx by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf(SrsSettings()) }
    var shownAt by remember { mutableStateOf(0L) }
    var sourcePdf by remember { mutableStateOf<File?>(null) }
    var explainOpen by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        settings = vm.repo.srsSettings()
        queue = vm.repo.dueCards(path).map { QueueItem(it.first, it.second) }
        idx = 0
        flipped = false
        shownAt = System.currentTimeMillis()
        loading = false
    }

    val current = queue.getOrNull(idx)

    // Ancillary: resolving the source PDF walks the course folder, which takes
    // seconds on shared storage. Deliberately not awaited by anything above.
    LaunchedEffect(current?.guidKey) {
        sourcePdf = null
        val card = current?.card ?: return@LaunchedEffect
        sourcePdf = runCatching { vm.repo.sourcePdfsFor(card.deckName).firstOrNull() }.getOrNull()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = PagePadding)) {
        AppHeader(
            title = if (path.isEmpty()) t.t("review.allDecks") else path.substringAfterLast("::"),
            onBack = onBack,
        ) {
            Text(
                "${(idx + 1).coerceAtMost(queue.size.coerceAtLeast(1))} / ${queue.size}",
                fontFamily = FcType.mono,
                fontSize = 11.sp,
                color = c.muted,
            )
        }

        // progress
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(c.hairline)
        ) {
            val frac = if (queue.isEmpty()) 0f else idx.toFloat() / queue.size
            val animated by animateFloatAsState(frac, label = "progress")
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(animated)
                    .clip(RoundedCornerShape(4.dp)).background(c.accent)
            )
        }

        Spacer(Modifier.height(8.dp))

        when {
            loading -> Box(Modifier.weight(1f))

            current == null -> SessionEnd(
                reviewed = queue.size,
                onBack = onBack,
                modifier = Modifier.weight(1f),
            )

            else -> {
                FlipCard(
                    card = current.card,
                    flipped = flipped,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    onClick = { flipped = !flipped },
                )

                Spacer(Modifier.height(12.dp))

                if (sourcePdf != null) {
                    SourceLink(t.t("review.viewSource")) { onOpenPdf(sourcePdf!!.absolutePath) }
                    Spacer(Modifier.height(8.dp))
                }

                if (!flipped) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (c.isDark) Color.White.copy(alpha = .05f) else Color.Black.copy(alpha = .04f))
                            .padding(15.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(t.t("review.tapToAnswer"), color = c.muted, fontSize = 14.sp)
                    }
                } else {
                    ExplainButton(t.t("review.explainBtn")) { explainOpen = true }
                    Spacer(Modifier.height(10.dp))
                    RatingRow(
                        state = current.state,
                        settings = settings,
                        lang = t.lang,
                        onRate = { quality ->
                            val spent = System.currentTimeMillis() - shownAt
                            val guid = current.card.guid
                            scope.launch { vm.repo.rate(guid, quality, spent) }
                            idx += 1
                            flipped = false
                            shownAt = System.currentTimeMillis()
                            if (idx >= queue.size) vm.refresh()
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (explainOpen && current != null) {
        ExplainSheet(vm = vm, card = current.card, onDismiss = { explainOpen = false })
    }
}

private val QueueItem.guidKey: String get() = card.guid

/** 3D flip driven by one content slot, swapped at the halfway point. */
@Composable
private fun FlipCard(
    card: CardRecord,
    flipped: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val density = LocalDensity.current
    val angle by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(550),
        label = "flip",
    )
    val showBack = angle > 90f
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = angle
                cameraDistance = 14f * density.density
            }
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .border(BorderStroke(1.dp, c.hairline), RoundedCornerShape(22.dp))
            .clickableNoRipple(interaction, onClick),
    ) {
        // Counter-rotate the back so its content is not mirrored.
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationY = if (showBack) 180f else 0f }
                .padding(22.dp),
        ) {
            Text(
                t.t(if (showBack) "review.verso" else "review.recto"),
                fontFamily = FcType.mono,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                color = c.muted2,
            )
            Spacer(Modifier.height(14.dp))
            MathText(
                html = if (showBack) card.back else card.front,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                fontSizeSp = if (showBack) 17 else 19,
                centered = !showBack,
                onTap = onClick,
            )
            if (showBack && card.note.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(c.surfaceSunk).padding(14.dp)
                ) {
                    MathText(
                        html = card.note,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        color = c.inkSoft,
                        fontSizeSp = 13,
                        serif = false,
                        onTap = onClick,
                    )
                }
            }
            if (!showBack) {
                Spacer(Modifier.height(10.dp))
                Text(
                    t.t("review.tapReveal"),
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = FcType.serif,
                    fontSize = 14.sp,
                    color = c.muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RatingRow(
    state: CardState,
    settings: SrsSettings,
    lang: String,
    onRate: (Int) -> Unit,
) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val now = remember(state) { Instant.now() }
    val buttons = listOf(
        Triple(QUALITY_AGAIN, t.t("rate.again"), c.again),
        Triple(QUALITY_HARD, t.t("rate.hard"), c.hard),
        Triple(QUALITY_GOOD, t.t("rate.good"), c.good),
        Triple(QUALITY_EASY, t.t("rate.easy"), c.easy),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        buttons.forEach { (quality, label, color) ->
            val preview = remember(state, settings, quality, lang) {
                formatInterval(review(state, quality, now, settings).intervalDays, lang)
            }
            val interaction = remember(quality) { MutableInteractionSource() }
            Column(
                Modifier
                    .weight(1f)
                    .pressScale(interaction)
                    .clip(RoundedCornerShape(13.dp))
                    .background(color)
                    .clickableNoRipple(interaction) { onRate(quality) }
                    .padding(vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = c.rateInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(preview, color = c.rateInk.copy(alpha = .75f), fontFamily = FcType.mono, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ExplainButton(label: String, onClick: () -> Unit) {
    val c = LocalFcColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(RoundedCornerShape(14.dp))
            .background(c.accentSoft)
            .clickableNoRipple(interaction, onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = c.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(label, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SourceLink(label: String, onClick: () -> Unit) {
    val c = LocalFcColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(RoundedCornerShape(12.dp))
            .background(c.pdfChipBg)
            .clickableNoRipple(interaction, onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Description, null, tint = c.pdfChipInk, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = c.pdfChipInk, fontSize = 13.sp)
    }
}

@Composable
private fun SessionEnd(reviewed: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalT.current
    val c = LocalFcColors.current
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            t.t("review.sessionDone"),
            fontFamily = FcType.serif,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            color = c.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (reviewed > 0)
                t.t("review.cardsReviewed", mapOf("n" to reviewed, "s" to if (reviewed > 1) "s" else ""))
            else t.t("review.nothingToReview"),
            color = c.muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        Cta(t.t("review.backToDecks"), Modifier.fillMaxWidth(0.7f), onClick = onBack)
    }
}

/** Localised interval label for the rating buttons. */
fun formatInterval(days: Double, lang: String): String {
    val r = { x: Double -> Math.rint(x).toLong() }
    return when {
        days < 1 -> if (lang == "en") "<1 d" else "<1 j"
        days < 30 -> "${r(days)} " + if (lang == "en") "d" else "j"
        days < 365 -> "${r(days / 30)} " + if (lang == "en") "mo" else "mois"
        else -> {
            val y = r(days / 365)
            if (lang == "en") "$y yr" + (if (y > 1) "s" else "")
            else "$y an" + (if (y > 1) "s" else "")
        }
    }
}
