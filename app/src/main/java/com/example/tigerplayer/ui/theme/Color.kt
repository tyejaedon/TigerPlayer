package com.example.tigerplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ------------------------------
// CORE AMOLED SURFACES
// ------------------------------
val TigerBlack = Color(0xFF0E0C0A)
val TigerDeepGrey = Color(0xFF171311)
val TigerSurfaceCharcoal = Color(0xFF211A16)
val TigerSurfaceElevated = Color(0xFF2D241E)
val TigerSurfaceFloating = Color(0xFF3B2F26)

val TigerTextHigh = Color(0xFFF7F2ED)
val TigerTextMed = Color(0xFFCDBFB1)
val TigerTextLow = Color(0xFF8B7A6C)
// ------------------------------
// LIGHT MODE BASE
// ------------------------------
val TigerIvory = Color(0xFFF5F7FB)
val TigerPaper = Color(0xFFFFFFFF)
val TigerMutedSilk = Color(0xFFE8EDF5)

// ------------------------------
// ACCENTS (Samsung-style “clean neon”)
// ------------------------------
val TigerNeonOrange = Color(0xFFFF7A1A)
val TigerCyberCyan = Color(0xFF00E5FF)
val TigerToxicLime = Color(0xFF39FF14)
val TigerElectricAmber = Color(0xFFFFB347)
val TigerSpectralViolet = Color(0xFF6C5CE7)

val SpotifyGreen = Color(0xFF1DB954)

// ------------------------------
// TEXT TOKENS
// ------------------------------

val TigerTextInverse = Color(0xFF141A22)

// ------------------------------
// ADAPTIVE COLORS (Samsung-like system tinting)
// ------------------------------
val AardBlueLight = Color(0xFFE86A12)
val AardBlueDark = Color(0xFFF11212)

val IgniRedLight = Color(0xFFB64A16)
val IgniRedDark = Color(0xFFFF7F3A)

// ------------------------------
// BACKGROUND GRADIENT SYSTEM (NEW)
// ------------------------------
val TigerAmbientGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF120D09),
        Color(0xFF090705)
    )
)

val TigerGlassLight = Brush.verticalGradient(
    listOf(
        Color.White.copy(alpha = 0.08f),
        Color.Transparent
    )
)

@Composable
fun rememberTigerAmbientGradient(
    accent: Color,
    baseTopAlpha: Float = 0.18f
): Brush {
    return remember(accent, baseTopAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                accent.copy(alpha = baseTopAlpha),
                TigerBlack
            ),
            startY = 0f,
            endY = 1500f
        )
    }
}
