package com.tigerplayer.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush

val LocalTigerDynamicAccent = staticCompositionLocalOf { TigerNeonRed }

val LocalTigerAmbientBrush = staticCompositionLocalOf<Brush> { TigerAmbientGradient }

val LocalTigerNeonContrastMode = staticCompositionLocalOf { NeonContrastMode.BALANCED }

val LocalTigerNeonIntensityMode = staticCompositionLocalOf { NeonIntensityMode.BALANCED }

val LocalCoverNeonPalette = staticCompositionLocalOf { DefaultCoverNeonPalette }

