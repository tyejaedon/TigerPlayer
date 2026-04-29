package com.example.tigerplayer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ------------------------------
// DARK / LIGHT SYSTEM SCHEMES
// ------------------------------
private val DarkColorScheme = darkColorScheme(
    primary = TigerNeonOrange,
    secondary = TigerElectricAmber,
    tertiary = TigerSpectralViolet,

    background = TigerBlack,
    surface = TigerSurfaceCharcoal,
    surfaceVariant = TigerSurfaceElevated,

    onBackground = TigerTextHigh,
    onSurface = TigerTextHigh,
    onSurfaceVariant = TigerTextMed,
)

private val LightColorScheme = lightColorScheme(
    primary = TigerNeonOrange,
    secondary = TigerElectricAmber,
    tertiary = TigerSpectralViolet,

    background = TigerIvory,
    surface = TigerPaper,
    surfaceVariant = TigerMutedSilk,

    onBackground = TigerTextInverse,
    onSurface = TigerTextInverse,
    onSurfaceVariant = Color(0xFF555555),
)

// ------------------------------
// THEME PROVIDER
// ------------------------------
@Composable
fun TigerPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        shapes = ModernShapes,
        content = content
    )
}

// ------------------------------
// SAMSUNG-STYLE EXTENSIONS
// ------------------------------
val MaterialTheme.aardBlue: Color
    @Composable
    get() = if (isSystemInDarkTheme()) AardBlueDark else AardBlueLight

val MaterialTheme.igniRed: Color
    @Composable
    get() = if (isSystemInDarkTheme()) IgniRedDark else IgniRedLight

val MaterialTheme.elevatedSurface: Color
    @Composable
    get() = if (isSystemInDarkTheme()) TigerSurfaceElevated else TigerMutedSilk