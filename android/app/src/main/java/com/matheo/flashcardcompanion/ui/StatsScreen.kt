package com.matheo.flashcardcompanion.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.matheo.flashcardcompanion.AppViewModel
import com.matheo.flashcardcompanion.data.CardStat
import com.matheo.flashcardcompanion.data.ReviewStats
import java.io.File
import kotlin.math.roundToInt

/**
 * Review history. Deliberately whole-history and never archive-filtered:
 * archiving a subject hides it from study, it does not rewrite what happened.
 */
@Composable
fun StatsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val context = LocalContext.current

    var overview by remember { mutableStateOf<ReviewStats?>(null) }
    var cards by remember { mutableStateOf<List<CardStat>>(emptyList()) }

    LaunchedEffect(Unit) {
        overview = vm.repo.overview()
        cards = vm.repo.cardStats()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = PagePadding)) {
        AppHeader(title = t.t("nav.stats"), onBack = onBack)
        ScreenTitle(t.t("stats.overviewTitle"))
        ScreenSub(t.t("stats.overviewSub"))

        val o = overview
        if (o == null || o.totalReviews == 0) {
            Text(t.t("stats.noReviews"), color = c.muted, fontSize = 14.sp)
            return@Column
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(o.totalReviews.toString(), t.t("stats.reviews"), Modifier.weight(1f))
            StatTile(formatDuration(o.totalTimeSpentMs), t.t("stats.totalTime"), Modifier.weight(1f))
            StatTile(
                "${(o.successRate * 100).roundToInt()}%",
                t.t("stats.successRate"),
                Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionTitle(t.t("stats.daysTitle"))
        Spacer(Modifier.height(8.dp))
        DayBars(o.perDay)

        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionTitle(t.t("stats.perCard"))
            Box(Modifier.width(120.dp)) {
                Cta(t.t("stats.export")) { shareCsv(context, vm.repo.exportCsv()) }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (cards.isEmpty()) {
            Text(t.t("stats.noCards"), color = c.muted, fontSize = 14.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cards, key = { it.guid }) { stat ->
                    val card = vm.repo.card(stat.guid)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(c.surface).padding(12.dp)
                    ) {
                        Text(
                            card?.front?.let { plainPreview(it, 90) } ?: stat.guid,
                            color = c.ink,
                            fontFamily = FcType.serif,
                            fontSize = 14.sp,
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${stat.reviewCount} · ${(stat.successRate * 100).roundToInt()}% · " +
                                formatDuration(stat.totalTimeSpentMs),
                            color = c.muted,
                            fontFamily = FcType.mono,
                            fontSize = 11.sp,
                        )
                    }
                }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val c = LocalFcColors.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(c.surface).padding(14.dp),
    ) {
        Text(value, color = c.ink, fontFamily = FcType.serif, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(label, color = c.muted, fontFamily = FcType.mono, fontSize = 10.sp)
    }
}

/** A plain bar per day — the shape of a study habit, not a precise chart. */
@Composable
private fun DayBars(perDay: List<Pair<String, Int>>) {
    val c = LocalFcColors.current
    if (perDay.isEmpty()) return
    val recent = perDay.takeLast(60)
    val max = recent.maxOf { it.second }.coerceAtLeast(1)
    // Fixed-width bars rather than weighted: with a single day of history a
    // weighted bar stretches across the whole card and reads as a filled block.
    Row(
        Modifier
            .fillMaxWidth()
            .height(90.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        recent.forEach { (_, count) ->
            Box(
                Modifier
                    .width(9.dp)
                    .fillMaxHeight(count.toFloat() / max)
                    .heightIn(min = 3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.accent),
            )
        }
    }
}

/**
 * A readable one-line preview of card text for a list row, where a WebView per
 * row would be far too heavy to render the maths properly. Delimiters and the
 * commonest commands are stripped so the row reads as prose rather than as
 * "\\(\\tau\\)".
 */
fun plainPreview(src: String, max: Int): String {
    var t = src
    t = Regex("\\\\[\\[\\]()]").replace(t, "")
    t = t.replace("$$", " ").replace("$", "")
    // The leading space matters: unwrapping "\\mathbb{R}" to a bare "R" would glue
    // it to a preceding command ("\\in" + "R" -> "\\inR"), which the next pass then
    // swallows whole, silently losing the symbol.
    t = Regex("\\\\(?:text|mathrm|mathbb|mathcal|mathbf)\\{([^}]*)\\}").replace(t) { " " + it.groupValues[1] }
    t = Regex("\\\\[a-zA-Z]+").replace(t, "")
    t = t.replace(Regex("[{}]"), "")
    t = Regex("<[^>]*>").replace(t, "")
    t = Regex("\\s+").replace(t, " ").trim()
    return if (t.length > max) t.take(max).trimEnd() + "…" else t
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return when {
        h > 0 -> "${h}h${m.toString().padStart(2, '0')}"
        m > 0 -> "${m}min"
        else -> "${totalSeconds}s"
    }
}

/** Hands the review log to whatever the user wants to open it with. */
private fun shareCsv(context: android.content.Context, csv: String) {
    runCatching {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "review_log.csv").apply { writeText(csv) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "review_log.csv",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
