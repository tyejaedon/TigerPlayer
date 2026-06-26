package com.example.tigerplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// ------------------------------
// CORE AMOLED SURFACES
// ------------------------------
val TigerBlack = Color(0xFF040406)           // True OLED-friendly black
val TigerDeepGrey = Color(0xFF0D0D12)        // Cards / Containers
val TigerSurfaceCharcoal = Color(0xFF13131A) // Elevated sheets
val TigerSurfaceElevated = Color(0xFF1A1A24) // Modals / Inputs
val TigerSurfaceFloating = Color(0xFF252536) // Active states

// Brighten the text slightly so it's not harsh white on deep grey
val TigerTextHigh = Color(0xFFF4F5FF)
val TigerTextMed = Color(0xFFC6C9DD)
val TigerTextLow = Color(0xFF8B90B0)
// ------------------------------
// LIGHT MODE BASE
// ------------------------------
val TigerIvory = Color(0xFFFFFBF5)
val TigerPaper = Color(0xFFFFFFFF)
val TigerMutedSilk = Color(0xFFF2F0EA)

// ------------------------------
// ACCENTS (Samsung-style “clean neon”)
// ------------------------------
val TigerNeonOrange = Color(0xFFFF6A00)
val TigerElectricAmber = Color(0xFFFFB300)
val TigerSpectralViolet = Color(0xFF6C5CE7)
val TigerCyberCyan = Color(0xFF00E5FF)
val TigerToxicLime = Color(0xFF39FF14)
val TigerHotPink = Color(0xFFFF007F)

val TigerRgbRed = Color(0xFFFF1744)
val TigerRgbGreen = Color(0xFF00E676)
val TigerRgbBlue = Color(0xFF18FFFF)

val SpotifyGreen = Color(0xFF1DB954)

// ------------------------------
// TEXT TOKENS
// ------------------------------

val TigerTextInverse = Color(0xFF121212)

// ------------------------------
// ADAPTIVE COLORS (Samsung-like system tinting)
// ------------------------------
val AardBlueLight = Color(0xFF0077FF)
val AardBlueDark = Color(0xFF4FC3F7)

val IgniRedLight = Color(0xFFD93025)
val IgniRedDark = Color(0xFFFF5252)

// ------------------------------
// BACKGROUND GRADIENT SYSTEM (NEW)
// ------------------------------
val TigerAmbientGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF0A0A14),
        Color(0xFF020203)
    )
)

fun tigerAmbientGradient(accent: Color, topAlpha: Float = 0.18f): Brush {
    val snappedAccent = DominantColorExtractor.snapToNearestNeon(accent)
    return Brush.verticalGradient(
        colors = listOf(
            snappedAccent.copy(alpha = topAlpha.coerceIn(0f, 1f)),
            Color(0xFF020202)
        )
    )
}

@Composable
fun rememberTigerAmbientGradient(
    accent: Color,
    baseTopAlpha: Float = 0.18f
): Brush {
    val contrastMode = LocalTigerNeonContrastMode.current
    val intensityMode = LocalTigerNeonIntensityMode.current

    val intensityMultiplier = when (intensityMode) {
        NeonIntensityMode.SOFT -> 0.86f
        NeonIntensityMode.BALANCED -> 1f
        NeonIntensityMode.HIGH -> 1.24f
    }
    val contrastBoost = if (contrastMode == NeonContrastMode.HIGH) 0.04f else 0f
    val adjustedAlpha = (baseTopAlpha * intensityMultiplier + contrastBoost).coerceIn(0.10f, 0.38f)

    return remember(accent, adjustedAlpha) {
        tigerAmbientGradient(accent = accent, topAlpha = adjustedAlpha)
    }
}

val TigerRgbNeonBorderBrush = Brush.linearGradient(
    listOf(
        TigerRgbRed,
        TigerRgbBlue,
        TigerRgbGreen,
        TigerHotPink,
        TigerRgbBlue
    )
)

val TigerRgbNeonBorderSoftBrush = Brush.linearGradient(
    listOf(
        TigerRgbRed.copy(alpha = 0.65f),
        TigerRgbBlue.copy(alpha = 0.65f),
        TigerRgbGreen.copy(alpha = 0.65f),
        TigerHotPink.copy(alpha = 0.65f)
    )
)

val TigerGlassLight = Brush.verticalGradient(
    listOf(
        Color.White.copy(alpha = 0.08f),
        Color.Transparent
    )
)