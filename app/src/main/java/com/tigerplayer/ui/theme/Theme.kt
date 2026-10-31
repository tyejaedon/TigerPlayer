package com.tigerplayer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tigerplayer.data.local.TigerAccentStyle

// ------------------------------
// DARK / LIGHT SYSTEM SCHEMES
// ------------------------------
private val DarkColorScheme = darkColorScheme(
    primary = TigerNeonRed,
    secondary = TigerNeonOrange,
    tertiary = TigerCyberCyan,

    background = TigerBlack,
    surface = TigerDeepGrey,
    surfaceVariant = TigerSurfaceCharcoal,
    primaryContainer = Color(0xFF4D1218),
    secondaryContainer = Color(0xFF4A2410),
    onPrimary = Color(0xFF2B0007),
    onSecondary = Color(0xFF2A1307),
    onPrimaryContainer = Color(0xFFFFD9DC),

    onBackground = TigerTextHigh,
    onSurface = TigerTextHigh,
    onSurfaceVariant = TigerTextMed,
    error = IgniRedDark
)

// Extension for elevated surfaces in your Theme.kt
val MaterialTheme.elevatedSurface: Color
    @Composable
    get() = if (this.colorScheme.background.luminance() < 0.5f) TigerSurfaceCharcoal else Color(0xFFEFF3FA)

private val LightColorScheme = lightColorScheme(
    primary = TigerNeonRed,
    secondary = TigerNeonOrange,
    tertiary = TigerCyberCyan,

    background = TigerIvory,
    surface = TigerPaper,
    surfaceVariant = TigerMutedSilk,
    primaryContainer = Color(0xFFFFDADA),
    secondaryContainer = Color(0xFFFFE3CC),
    tertiaryContainer = Color(0xFFCCEFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF2F1809),
    onPrimaryContainer = Color(0xFF3A080E),
    onTertiary = Color(0xFF00363D),
    onTertiaryContainer = Color(0xFF004F58),

    onBackground = TigerTextInverse,
    onSurface = TigerTextInverse,
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFF8E98A7),
    outlineVariant = Color(0xFFC8D0DC),
    surfaceTint = TigerNeonRed,
    error = IgniRedLight,
    onError = Color.White
)

// ------------------------------
// THEME PROVIDER
// ------------------------------
@Composable
fun TigerPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureAmoledBlack: Boolean = false,
    accentStyle: TigerAccentStyle = TigerAccentStyle.NEON_RED,
    content: @Composable () -> Unit
) {
    val accent = when (accentStyle) {
        TigerAccentStyle.NEON_RED -> TigerNeonRed
        TigerAccentStyle.NEON_ORANGE -> TigerNeonOrange
        TigerAccentStyle.CYBER_CYAN -> TigerCyberCyan
        TigerAccentStyle.TOXIC_LIME -> TigerToxicLime
        TigerAccentStyle.SPECTRAL_VIOLET -> TigerSpectralViolet
    }

    val scheme = if (darkTheme) {
        DarkColorScheme.copy(
            primary = accent,
            secondary = when (accentStyle) {
                TigerAccentStyle.NEON_RED -> TigerNeonOrange
                else -> accent.copy(alpha = 0.82f)
            },
            background = if (pureAmoledBlack) Color.Black else TigerBlack
        )
    } else {
        LightColorScheme.copy(
            primary = accent,
            secondary = when (accentStyle) {
                TigerAccentStyle.NEON_RED -> TigerNeonOrange
                else -> accent.copy(alpha = 0.82f)
            }
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

    CompositionLocalProvider(
        LocalCoverNeonPalette provides DefaultCoverNeonPalette,
        LocalTigerDynamicAccent provides accent
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
// SAMSUNG-STYLE EXTENSIONS (Mapped Semantically)
// ------------------------------
val MaterialTheme.aardBlue: Color
    @Composable
    get() = this.colorScheme.tertiary

val MaterialTheme.igniRed: Color
    @Composable
    get() = this.colorScheme.error

