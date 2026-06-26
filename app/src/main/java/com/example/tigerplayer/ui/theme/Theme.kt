package com.example.tigerplayer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ------------------------------
// DARK / LIGHT SYSTEM SCHEMES
// ------------------------------
private val DarkColorScheme = darkColorScheme(
    primary = TigerCyberCyan,
    secondary = TigerNeonOrange,
    tertiary = TigerHotPink,

    background = TigerBlack,
    surface = TigerDeepGrey,
    surfaceVariant = TigerSurfaceCharcoal,
    surfaceTint = TigerCyberCyan,

    onPrimary = Color(0xFF041014),
    onSecondary = Color(0xFF160700),
    onTertiary = Color(0xFF18000B),

    onBackground = TigerTextHigh,
    onSurface = TigerTextHigh,
    onSurfaceVariant = TigerTextMed,
    outline = TigerRgbBlue.copy(alpha = 0.65f),
    outlineVariant = TigerHotPink.copy(alpha = 0.45f)
)

// Extension for elevated surfaces in your Theme.kt
val MaterialTheme.elevatedSurface: Color
    @Composable
    get() = if (colorScheme.background.luminance() < 0.5f) TigerSurfaceCharcoal else TigerMutedSilk
private val LightColorScheme = lightColorScheme(
    primary = TigerCyberCyan,
    secondary = TigerNeonOrange,
    tertiary = TigerSpectralViolet,

    background = TigerIvory,
    surface = TigerPaper,
    surfaceVariant = TigerMutedSilk,

    onBackground = TigerTextInverse,
    onSurface = TigerTextInverse,
    onSurfaceVariant = Color(0xFF555555),
    outline = TigerRgbBlue.copy(alpha = 0.55f),
    outlineVariant = TigerHotPink.copy(alpha = 0.35f)
)

// ------------------------------
// THEME PROVIDER
// ------------------------------
@Composable
fun TigerPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    neonContrastMode: NeonContrastMode = NeonContrastMode.BALANCED,
    neonIntensityMode: NeonIntensityMode = NeonIntensityMode.BALANCED,
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalTigerNeonContrastMode provides neonContrastMode,
        LocalTigerNeonIntensityMode provides neonIntensityMode
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            shapes = ModernShapes,
            content = content
        )
    }
}

// ------------------------------
// SAMSUNG-STYLE EXTENSIONS
// ------------------------------
val MaterialTheme.aardBlue: Color
    @Composable
    get() = if (colorScheme.background.luminance() < 0.5f) AardBlueDark else AardBlueLight

val MaterialTheme.igniRed: Color
    @Composable
    get() = if (colorScheme.background.luminance() < 0.5f) IgniRedDark else IgniRedLight

