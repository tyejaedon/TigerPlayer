package com.example.tigerplayer.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ------------------------------
// SAMSUNG-STYLE CLICK DEPTH
// ------------------------------
fun Modifier.bounceClick(onClick: () -> Unit) = composed {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "press"
    )

    this
        .scale(scale)
        // 🔥 THE CRASH FIX: Bypasses the broken Foundation `clickable` node
        // causing the "getPan" crash by using lower-level raw pointer inputs.
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = {
                    onClick()
                }
            )
        }
}

// ------------------------------
// SAMSUNG GLASS (REALISTIC LAYERING)
// ------------------------------
fun Modifier.glassEffect(
    shape: Shape,
    showRgbBorder: Boolean = false,
    borderWidth: Dp = 1.3.dp
) = this.composed {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val contrastMode = LocalTigerNeonContrastMode.current
    val intensityMode = LocalTigerNeonIntensityMode.current
    val highContrast = contrastMode == NeonContrastMode.HIGH
    val intensityBoost = when (intensityMode) {
        NeonIntensityMode.SOFT -> 0.92f
        NeonIntensityMode.BALANCED -> 1f
        NeonIntensityMode.HIGH -> 1.08f
    }
    val glassFill = if (darkTheme) {
        Color.White.copy(alpha = (if (highContrast) 0.08f else 0.06f) * intensityBoost)
    } else {
        Color.White.copy(alpha = (if (highContrast) 0.76f else 0.68f) * intensityBoost)
    }
    val borderAlpha = if (showRgbBorder) {
        if (highContrast) 0.34f else 0.20f
    } else {
        if (highContrast) 0.30f else 0.18f
    }
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)

    this
        .clip(shape)
        .background(glassFill)
        .border(width = borderWidth, color = borderColor, shape = shape)
}
// ------------------------------
// SAMSUNG "DEPTH GLOW"
// ------------------------------
@Suppress("UNUSED_PARAMETER")
fun Modifier.tigerGlow(
    color: Color = Color.Unspecified
) = composed {
    // Deliberately no-op for cleaner, shadow-free surfaces.
    this
}