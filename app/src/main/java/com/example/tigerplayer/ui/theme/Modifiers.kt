package com.example.tigerplayer.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * A custom modifier that physically depresses the UI element when pressed,
 * adding a weighty, tactile feel to the interaction.
 */
fun Modifier.bounceClick(
    onClick: () -> Unit
) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // S22 Optimization: Subtler scale (0.96f) for high-density targets
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true),
            onClick = onClick
        )
}

/**
 * REFACTORED: Visibility-First Glass Effect
 * Drops opacity to actual glass levels while using a gradient to protect text legibility.
 */
fun Modifier.glassEffect(
    shape: Shape
) = composed {
    val isDark = isSystemInDarkTheme()
    val surfaceBase = MaterialTheme.colorScheme.surface

    // THE FIX: True glass needs lower opacities.
    // alphaTop acts as the frosted glare, alphaBottom acts as the base translucency.
    val alphaTop = if (isDark) 0.65f else 0.85f
    val alphaBottom = if (isDark) 0.40f else 0.70f

    val borderColor = if (isDark) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f) // Crisp edge for AMOLED
    } else {
        Color.Black.copy(alpha = 0.15f)
    }

    this
        .clip(shape)
        // 1. The Translucent Base
        .background(surfaceBase.copy(alpha = alphaBottom))
        // 2. The Directional Glare
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    surfaceBase.copy(alpha = alphaTop), // Frosted top edge catches "light"
                    Color.Transparent, // Clear middle to let artwork bleed
                    // Dark anchor at the bottom ensures white typography remains readable
                    if (isDark) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )
            )
        )
        // 3. The Specular Edge Highlights
        .border(0.5.dp, borderColor, shape)
}

/**
 * Hardware-accelerated neon glow using native Canvas drawing.
 */
fun Modifier.tigerGlow() = composed {
    val glowColor = MaterialTheme.colorScheme.primary
    this.drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                asFrameworkPaint().apply {
                    // S22 Optimization: Blur radius kept at 20f to prevent GPU overdraw
                    maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
                    this.color = glowColor.toArgb()
                }
            }
            canvas.drawCircle(center, size.width / 2, paint)
        }
    }
}