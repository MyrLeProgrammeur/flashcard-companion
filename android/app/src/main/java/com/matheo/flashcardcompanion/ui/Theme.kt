package com.matheo.flashcardcompanion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The web UI's design tokens, carried over verbatim from `style.css`.
 *
 * Material3's ColorScheme has no slot for most of these (the four rating
 * colours, the grounding chips, the health pill), so the palette is its own
 * type and Material3 is fed a derived scheme for the few built-in widgets.
 *
 * Fonts: the web build self-hosts Newsreader / IBM Plex as .woff2, which
 * Android cannot load. Rather than fetch anything at runtime, this maps onto
 * the platform's own families — which is exactly what the CSS fallback stack
 * (Georgia/serif, system-ui, ui-monospace) degrades to anyway.
 */
data class FcColors(
    val bg: Color,
    val bgCanvas: Color,
    val surface: Color,
    val surfaceSunk: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val muted2: Color,
    val hairline: Color,
    val accent: Color,
    val accentSoft: Color,
    val ctaBg: Color,
    val ctaInk: Color,
    val again: Color,
    val hard: Color,
    val good: Color,
    val easy: Color,
    val rateInk: Color,
    val healthOnline: Color,
    val healthOnlineBg: Color,
    val healthOffline: Color,
    val healthOfflineBg: Color,
    val pdfChipBg: Color,
    val pdfChipBorder: Color,
    val pdfChipInk: Color,
    val degrChipBg: Color,
    val degrChipBorder: Color,
    val degrChipInk: Color,
    val degrBandBg: Color,
    val degrBandBorder: Color,
    val degrBandInk: Color,
    val isDark: Boolean,
)

val LightColors = FcColors(
    bg = Color(0xFFFAF8F3),
    bgCanvas = Color(0xFFE7E3DA),
    surface = Color(0xFFFFFFFF),
    surfaceSunk = Color(0xFFF6F4EF),
    ink = Color(0xFF211F1B),
    inkSoft = Color(0xFF33302A),
    muted = Color(0xFF6B6660),
    muted2 = Color(0xFF9A948A),
    hairline = Color(0x14000000),
    accent = Color(0xFF2F6F4E),
    accentSoft = Color(0x1A2F6F4E),
    ctaBg = Color(0xFF211F1B),
    ctaInk = Color(0xFFFAF8F3),
    again = Color(0xFFC14A3F),
    hard = Color(0xFFC07A2E),
    good = Color(0xFF2F8158),
    easy = Color(0xFF35618F),
    rateInk = Color(0xFFFFFFFF),
    healthOnline = Color(0xFF2F8158),
    healthOnlineBg = Color(0x172F6F4E),
    healthOffline = Color(0xFFC14A3F),
    healthOfflineBg = Color(0x1AC14A3F),
    pdfChipBg = Color(0x1C2F8158),
    pdfChipBorder = Color(0x402F8158),
    pdfChipInk = Color(0xFF2F6F4E),
    degrChipBg = Color(0x1FC07A2E),
    degrChipBorder = Color(0x4DC07A2E),
    degrChipInk = Color(0xFFA5651F),
    degrBandBg = Color(0x17C07A2E),
    degrBandBorder = Color(0x38C07A2E),
    degrBandInk = Color(0xFF8A5A1E),
    isDark = false,
)

val DarkColors = FcColors(
    bg = Color(0xFF16140F),
    bgCanvas = Color(0xFF0E0D0A),
    surface = Color(0xFF211E18),
    surfaceSunk = Color(0xFF191712),
    ink = Color(0xFFECE7DD),
    inkSoft = Color(0xFFDCD7CD),
    muted = Color(0xFF8F887B),
    muted2 = Color(0xFF6A6459),
    hairline = Color(0x12FFFFFF),
    accent = Color(0xFF57A67C),
    accentSoft = Color(0x1F57A67C),
    ctaBg = Color(0xFFFAF8F3),
    ctaInk = Color(0xFF16140F),
    again = Color(0xFFD9564A),
    hard = Color(0xFFD38A3A),
    good = Color(0xFF43A276),
    easy = Color(0xFF5A86BF),
    rateInk = Color(0xFF16140F),
    healthOnline = Color(0xFF43A276),
    healthOnlineBg = Color(0x1F43A276),
    healthOffline = Color(0xFFD9564A),
    healthOfflineBg = Color(0x1FD9564A),
    pdfChipBg = Color(0x1C2F8158),
    pdfChipBorder = Color(0x402F8158),
    pdfChipInk = Color(0xFF57A67C),
    degrChipBg = Color(0x1FC07A2E),
    degrChipBorder = Color(0x4DC07A2E),
    degrChipInk = Color(0xFFD38A3A),
    degrBandBg = Color(0x17C07A2E),
    degrBandBorder = Color(0x38C07A2E),
    degrBandInk = Color(0xFFD38A3A),
    isDark = true,
)

val LocalFcColors = staticCompositionLocalOf { LightColors }

/** Card text and headings are serif; chrome is sans; numbers/labels are mono. */
object FcType {
    val serif = FontFamily.Serif
    val sans = FontFamily.SansSerif
    val mono = FontFamily.Monospace
}

/** Page gutter and content width, mirroring `.app`'s clamp + 520px max. */
val PagePadding = 18.dp
val MaxContentWidth = 520.dp

val FcColors.onSurfaceVariant: Color get() = muted

@Composable
fun FlashcardTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            background = colors.bg,
            surface = colors.surface,
            onPrimary = colors.rateInk,
            onBackground = colors.ink,
            onSurface = colors.ink,
            onSurfaceVariant = colors.muted,
            outline = colors.muted2,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = colors.bg,
            surface = colors.surface,
            onPrimary = colors.rateInk,
            onBackground = colors.ink,
            onSurface = colors.ink,
            onSurfaceVariant = colors.muted,
            outline = colors.muted2,
        )
    }
    CompositionLocalProvider(LocalFcColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(
                bodyLarge = TextStyle(fontFamily = FcType.sans, fontSize = 16.sp),
                bodyMedium = TextStyle(fontFamily = FcType.sans, fontSize = 14.sp),
                titleLarge = TextStyle(
                    fontFamily = FcType.serif,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                ),
                labelSmall = TextStyle(
                    fontFamily = FcType.mono,
                    fontSize = 11.sp,
                    letterSpacing = 0.16.sp,
                ),
            ),
            content = content,
        )
    }
}
