package com.example.tigerplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- AMOLED Optimized Dark Palette ---
val TigerBlack = Color(0xFF000000)        // Pure AMOLED Black
val TigerDeepGrey = Color(0xFF0D0D0D)     // Slightly elevated surface
val TigerSurfaceCharcoal = Color(0xFF1A1A1A) // For Cards

// --- The "Radiant Ember" Vibe ---
val TigerNeonOrange = Color(0xFFFF5E00)   // Primary
val TigerElectricAmber = Color(0xFFFF9D00) // Secondary
val TigerSpectralViolet = Color(0xFF6200EA) // UNIQUE VIBE: Deep shadow/glow contrast

// --- Light Mode (Warm Ivory) ---
val TigerIvory = Color(0xFFFAF9F6)
val TigerPaper = Color(0xFFFFFFFF)
val TigerMutedSilk = Color(0xFFE0DED7)

// --- Typography ---
val TigerTextHigh = Color(0xFFF5F5F5)
val TigerTextMed = Color(0xFFB0B0B0)
val TigerTextInverse = Color(0xFF121212)

// --- The "Tiger Gradient" (The Secret Sauce) ---
// Use this for buttons, progress bars, or headers to create a "forged" look
val TigerGradient = Brush.verticalGradient(
    colors = listOf(TigerElectricAmber, TigerNeonOrange)
)

val SpotifyGreen = Color(0xFF1DB954)