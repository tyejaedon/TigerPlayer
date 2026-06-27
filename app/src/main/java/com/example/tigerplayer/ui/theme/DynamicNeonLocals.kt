package com.example.tigerplayer.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush

val LocalTigerDynamicAccent = staticCompositionLocalOf { TigerNeonOrange }

val LocalTigerAmbientBrush = staticCompositionLocalOf<Brush> { TigerAmbientGradient }

val LocalTigerNeonContrastMode = staticCompositionLocalOf { NeonContrastMode.BALANCED }

val LocalTigerNeonIntensityMode = staticCompositionLocalOf { NeonIntensityMode.BALANCED }

