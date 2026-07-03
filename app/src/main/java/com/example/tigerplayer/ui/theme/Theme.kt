package com.example.tigerplayer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.tigerplayer.data.local.TigerAccentStyle

// ------------------------------
// DARK / LIGHT SYSTEM SCHEMES
// ------------------------------
private val DarkColorScheme = darkColorScheme(
    primary = TigerNeonOrange,
    secondary = TigerElectricAmber,
    tertiary = Color(0xFFFFD0A6),

    background = TigerBlack,
    surface = TigerDeepGrey,
    surfaceVariant = TigerSurfaceCharcoal,
    primaryContainer = Color(0xFF4A2410),
    secondaryContainer = Color(0xFF4D3318),
    onPrimary = Color(0xFF2A1307),
    onSecondary = Color(0xFF2A1605),
    onPrimaryContainer = Color(0xFFFFDFC5),

    onBackground = TigerTextHigh,
    onSurface = TigerTextHigh,
    onSurfaceVariant = TigerTextMed,
)

// Extension for elevated surfaces in your Theme.kt
val MaterialTheme.elevatedSurface: Color
    @Composable
    get() = if (this.colorScheme.background.luminance() < 0.5f) TigerSurfaceCharcoal else TigerMutedSilk
private val LightColorScheme = lightColorScheme(
    primary = TigerNeonOrange,
    secondary = TigerElectricAmber,
    tertiary = Color(0xFFB75517),

    background = TigerIvory,
    surface = TigerPaper,
    surfaceVariant = TigerMutedSilk,
    primaryContainer = Color(0xFFFFE2C7),
    secondaryContainer = Color(0xFFFFEBCF),
    onPrimary = Color(0xFF3A1D0D),
    onSecondary = Color(0xFF3D230B),
    onPrimaryContainer = Color(0xFF4A2410),

    onBackground = TigerTextInverse,
    onSurface = TigerTextInverse,
    onSurfaceVariant = Color(0xFF6D5A49),
)

// ------------------------------
// THEME PROVIDER
// ------------------------------
@Composable
fun TigerPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureAmoledBlack: Boolean = false,
    accentStyle: TigerAccentStyle = TigerAccentStyle.NEON_ORANGE,
    content: @Composable () -> Unit
) {
    val accent = when (accentStyle) {
        TigerAccentStyle.NEON_ORANGE -> TigerNeonOrange
        TigerAccentStyle.CYBER_CYAN -> TigerCyberCyan
        TigerAccentStyle.TOXIC_LIME -> TigerToxicLime
        TigerAccentStyle.SPECTRAL_VIOLET -> TigerSpectralViolet
    }

    val scheme = if (darkTheme) {
        DarkColorScheme.copy(
            primary = accent,
            secondary = accent.copy(alpha = 0.82f),
            background = if (pureAmoledBlack) Color.Black else TigerBlack
        )
    } else {
        LightColorScheme.copy(
            primary = accent,
            secondary = accent.copy(alpha = 0.82f)
        )
    }
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
    get() = if (this.colorScheme.background.luminance() < 0.5f) AardBlueDark else AardBlueLight

val MaterialTheme.igniRed: Color
    @Composable
    get() = if (this.colorScheme.background.luminance() < 0.5f) IgniRedDark else IgniRedLight

