package com.matheo.flashcardcompanion.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The web UI shrinks every tappable surface to .97 while pressed. */
@Composable
fun Modifier.pressScale(
    interaction: MutableInteractionSource,
    pressed: Float = 0.97f,
): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, label = "pressScale")
    return this.scale(scale)
}

@Composable
fun AppHeader(
    modifier: Modifier = Modifier,
    title: String = "Flashcards",
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val c = LocalFcColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconCircleButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                title,
                fontFamily = FcType.mono,
                fontSize = 13.sp,
                letterSpacing = 0.04.sp,
                color = c.ink,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String? = null,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val c = LocalFcColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .pressScale(interaction)
            .clickableNoRipple(interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint ?: c.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 6.dp, bottom = 2.dp),
        fontFamily = FcType.serif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 31.sp,
        color = LocalFcColors.current.ink,
    )
}

@Composable
fun ScreenSub(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(bottom = 20.dp),
        fontSize = 14.sp,
        color = LocalFcColors.current.muted,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        fontFamily = FcType.serif,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        color = LocalFcColors.current.ink,
    )
}

/** The health pill now reports the AI link only — there is no backend to be down. */
@Composable
fun HealthPill(online: Boolean, label: String? = null) {
    val c = LocalFcColors.current
    val fg = if (online) c.healthOnline else c.healthOffline
    val bg = if (online) c.healthOnlineBg else c.healthOfflineBg
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(start = 9.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
        if (label != null) {
            Text(label, fontFamily = FcType.mono, fontSize = 11.sp, letterSpacing = 0.04.sp, color = fg)
        }
    }
}

/** A row in the deck list: glyph, name + meta, due count. */
@Composable
fun DeckRow(
    name: String,
    meta: String,
    dueCount: Int,
    glyph: String,
    modifier: Modifier = Modifier,
    done: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = LocalFcColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.985f)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(BorderStroke(1.dp, c.hairline), RoundedCornerShape(16.dp))
            .combinedClickableNoRipple(interaction, onClick, onLongClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                glyph,
                fontFamily = FcType.serif,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = c.accent,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontFamily = FcType.serif,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = if (done) c.muted else c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    fontFamily = FcType.mono,
                    fontSize = 11.sp,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
        else if (dueCount > 0) CountBadge(dueCount)
    }
}

@Composable
fun CountBadge(count: Int) {
    val c = LocalFcColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(c.accentSoft)
            .widthIn(min = 30.dp)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.toString(),
            fontFamily = FcType.mono,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = c.accent,
        )
    }
}

/** The dark, full-width primary action. */
@Composable
fun Cta(
    label: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalFcColors.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .pressScale(interaction)
            .clip(RoundedCornerShape(15.dp))
            .background(if (enabled) c.ctaBg else c.muted2)
            .clickableNoRipple(interaction) { if (enabled) onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // The label is clipped rather than allowed to push the count off the
        // edge - a long deck name used to hide the due count entirely.
        Text(
            label,
            modifier = Modifier.weight(1f, fill = false),
            color = c.ctaInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (count != null) {
            Spacer(Modifier.width(9.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.ctaInk.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(count.toString(), color = c.ctaInk, fontFamily = FcType.mono, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun Crumbs(parts: List<Pair<String, () -> Unit>>) {
    val c = LocalFcColors.current
    if (parts.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEachIndexed { i, (label, action) ->
            if (i > 0) Text("/", fontFamily = FcType.mono, fontSize = 11.sp, color = c.muted2)
            val interaction = remember(label, i) { MutableInteractionSource() }
            Text(
                label,
                modifier = Modifier.clickableNoRipple(interaction, action),
                fontFamily = FcType.mono,
                fontSize = 11.sp,
                color = if (i == parts.lastIndex) c.accent else c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
