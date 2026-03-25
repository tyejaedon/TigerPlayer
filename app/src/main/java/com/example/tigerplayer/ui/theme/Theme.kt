package com.example.tigerplayer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- THE INK (Theme-Aware Variables) ---

private val DarkColorScheme = darkColorScheme(
    primary = TigerNeonOrange,
    onPrimary = Color.Black,
    secondary = TigerElectricAmber,
    tertiary = TigerSpectralViolet,
    background = TigerBlack,
    surface = TigerDeepGrey,
    surfaceVariant = TigerSurfaceCharcoal,
    onBackground = TigerTextHigh,
    onSurface = TigerTextHigh,
    onSurfaceVariant = TigerTextMed
)

private val LightColorScheme = lightColorScheme(
    primary = TigerNeonOrange,
    onPrimary = Color.White,
    secondary = TigerElectricAmber,
    onSecondary = Color.White,
    tertiary = TigerSpectralViolet,
    onTertiary = Color.White,
    background = TigerIvory,
    surface = TigerPaper,
    surfaceVariant = TigerMutedSilk,
    onBackground = TigerTextInverse,
    onSurface = TigerTextInverse,
    onSurfaceVariant = Color(0xFF5A5A5A)
)

// --- ADAPTIVE SIGNS (The Vanguard's Touch) ---
// By hooking these to MaterialTheme, they automatically recompose when the theme changes.
val MaterialTheme.aardBlue: Color
    @Composable
    get() = if (isSystemInDarkTheme()) AardBlueDark else AardBlueLight

val MaterialTheme.igniRed: Color
    @Composable
    get() = if (isSystemInDarkTheme()) IgniRedDark else IgniRedLight

// Helper function kept intact so it doesn't break any of your existing screens
@Composable
fun getVanguardColors() = Pair(
    MaterialTheme.colorScheme.onSurface,
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
            val insetsController = WindowCompat.getInsetsController(window, view)

            // Ensures status bar icons are dark in Light Mode so they are visible!
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        // Assuming Typography and ModernShapes are defined in your other theme files
        // typography = Typography,
        // shapes = ModernShapes,
        content = content
    )
}