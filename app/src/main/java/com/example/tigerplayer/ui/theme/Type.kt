package com.example.tigerplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// --- THE RECTIFIED VANGUARD TYPE SYSTEM ---
// Precision-tuned for the S22 (Small Form Factor / High PPI)
val Typography = Typography(

    // USED FOR: Main Screen Headers (e.g., "TIGER PLAYER", "ARCHIVES")
    // S22 Optimization: Dropped to 24sp to keep "TIGER PLAYER" on one line.
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    ),

    // USED FOR: Section Titles (e.g., "THE VANGUARD", "STATISTICS")
    // S22 Optimization: 18sp prevents "STATISTICS" from crowding the horizontal padding.
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),

    // USED FOR: Song Titles in Lists and Player Metadata
    // S22 Optimization: 14sp allows more of the track name to be visible before truncation.
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // USED FOR: Lyrics and Artist Biographies
    // Keeping your "Sweet Spot" at 15sp but tightening line height for density.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.25.sp
    ),

    // USED FOR: Subtitles (Artist names in lists, Album names)
    // S22 Optimization: 12sp is the floor for readability with SemiBold weight.
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),

    // USED FOR: Thematic Badges (e.g., "HI-RES", "01 CHANT", "STATS")
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.2.sp
    ),

    // USED FOR: Micro-stats (Duration, Bitrate, Year)
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        letterSpacing = 0.5.sp
    )

)