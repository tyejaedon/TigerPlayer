package com.example.tigerplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- AMOLED Optimized Dark Palette (The Shadows) ---
// VANGUARD POLISH: 0xFF050505 is visually identical to pitch black,
// but keeps OLED pixels just "warm" enough to prevent purple scrolling smear.
val TigerBlack = Color(0xFF050505)
val TigerDeepGrey = Color(0xFF0D0D0D)
val TigerSurfaceCharcoal = Color(0xFF1A1A1A)

// --- The Radiant Accents (The Fire) ---
val TigerNeonOrange = Color(0xFFE65100)   // Deep Sunset Orange (High Contrast)
val TigerElectricAmber = Color(0xFFFF8F00) // Rich Gold
val TigerSpectralViolet = Color(0xFF512DA8) // Royal Purple accent

// --- Light Mode Foundations (The Canvas) ---
val TigerIvory = Color(0xFFFFFBF5)    // Warm, crisp background
val TigerPaper = Color(0xFFFFFFFF)    // Pure white for cards/surfaces
val TigerMutedSilk = Color(0xFFF2F0EA) // Soft contrast for dividers/variants

// --- Typography (The Ledger) ---
val TigerTextHigh = Color(0xFFF5F5F5)    // Dark Mode White
val TigerTextMed = Color(0xFFB0B0B0)     // Dark Mode Grey
val TigerTextInverse = Color(0xFF1A1A1A) // Light Mode Obsidian (Clean Contrast)

// --- Spotify Integration ---
val SpotifyGreen = Color(0xFF1DB954)

// --- The Forged Gradient ---
val TigerGradient = Brush.verticalGradient(
    colors = listOf(TigerElectricAmber, TigerNeonOrange)
)

// --- ADAPTIVE SIGNS (Raw Values) ---
val AardBlueLight = Color(0xFF0288D1) // Deeper blue to pierce through Light Mode
val AardBlueDark = Color(0xFF4FC3F7)  // Glowing blue to pop in Dark Mode

val IgniRedLight = Color(0xFFD32F2F)  // Deep crimson for Light Mode
val IgniRedDark = Color(0xFFFF5252)   // Bright fire for Dark Mode