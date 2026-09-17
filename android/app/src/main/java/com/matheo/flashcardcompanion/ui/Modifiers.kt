package com.matheo.flashcardcompanion.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier

/**
 * Taps without Material's ripple: the design signals a press by scaling the
 * surface (see [pressScale]), and a ripple on top of that reads as double
 * feedback.
 */
fun Modifier.clickableNoRipple(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interaction,
    indication = null,
    onClick = onClick,
)

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableNoRipple(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = this.combinedClickable(
    interactionSource = interaction,
    indication = null,
    onClick = onClick,
    onLongClick = onLongClick,
)
