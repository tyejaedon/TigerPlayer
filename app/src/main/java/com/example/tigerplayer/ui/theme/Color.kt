package com.example.tigerplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ------------------------------
// CORE AMOLED SURFACES
// ------------------------------
val TigerBlack = Color(0xFF101012)        // Softened background
val TigerDeepGrey = Color(0xFF18181A)     // Cards / Containers
val TigerSurfaceCharcoal = Color(0xFF202022) // Elevated sheets
val TigerSurfaceElevated = Color(0xFF2C2C2E) // Modals / Inputs
val TigerSurfaceFloating = Color(0xFF3A3A3C) // Active states

// Brighten the text slightly so it's not harsh white on deep grey
val TigerTextHigh = Color(0xFFEBEBF0)
val TigerTextMed = Color(0xFFB8B8C0)
val TigerTextLow = Color(0xFF767680)
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
val TigerCyberCyan = Color(0xFF00E5FF)
val TigerToxicLime = Color(0xFF39FF14)
val TigerElectricAmber = Color(0xFFFFB300)
val TigerSpectralViolet = Color(0xFF6C5CE7)

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