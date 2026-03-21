package com.example.tigerplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = TigerNeonOrange,
    onPrimary = Color.Black,
    secondary = TigerElectricAmber,
    tertiary = TigerSpectralViolet, // The "Vibe" accent
    background = TigerBlack,
    surface = TigerDeepGrey,
    surfaceVariant = TigerSurfaceCharcoal,
    onBackground = TigerTextHigh,
    onSurface = TigerTextHigh,
    onSurfaceVariant = TigerTextMed
)

private val LightColorScheme = lightColorScheme(
    primary = TigerNeonOrange,
    secondary = TigerElectricAmber,
    background = TigerIvory,
    surface = TigerPaper,
    surfaceVariant = TigerMutedSilk,
    onBackground = TigerTextInverse,
    onSurface = TigerTextInverse,
    onSurfaceVariant = Color(0xFF5A5A5A)
)

@Composable
fun TigerPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val viewInsetsController = WindowCompat.getInsetsController(window, view)

            // Instead of setting colors manually, we just tell the system
            // whether to use Light or Dark icons for the status/nav bars.
            viewInsetsController.isAppearanceLightStatusBars = !darkTheme
            viewInsetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ModernShapes,
        content = content
    )
}

