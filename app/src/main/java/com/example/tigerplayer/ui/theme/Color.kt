package com.example.tigerplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ------------------------------
// CORE AMOLED SURFACES
// ------------------------------
val TigerBlack = Color(0xFF050505)
val TigerDeepGrey = Color(0xFF0D0D0D)
val TigerSurfaceCharcoal = Color(0xFF151515)
val TigerSurfaceElevated = Color(0xFF1C1C1C)
val TigerSurfaceFloating = Color(0xFF242424)

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

val SpotifyGreen = Color(0xFF1DB954)

// ------------------------------
// TEXT TOKENS
// ------------------------------
val TigerTextHigh = Color(0xFFF5F5F5)
val TigerTextMed = Color(0xFFB0B0B0)
val TigerTextLow = Color(0xFF6A6A6A)

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
        Color(0xFF0B0B0B),
        Color(0xFF050505)
    )
)

val TigerGlassLight = Brush.verticalGradient(
    listOf(
        Color.White.copy(alpha = 0.08f),
        Color.Transparent
    )
)