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
import androidx.compose.foundation.isSystemInDarkTheme


/**
 * A custom modifier that physically depresses the UI element when pressed,
 * adding a weighty, tactile feel to the interaction.
 */
fun Modifier.bounceClick(
    onClick: () -> Unit
) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animates the scale from 100% down to 92% when pressed
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
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
            indication = ripple(bounded = true), // New Material 3 ripple API
            onClick = onClick
        )
}

/**
 * A modifier that adds a "Glass" effect appearance.
 * To prevent text blurring, we do NOT apply .blur() here.
 * Instead, we use semi-transparency and a subtle border.
 */
// ... your other imports ...

/**
 * A dynamic "Glass" effect that reacts to the system theme without boxy shadows.
 * Light Mode: Subtle dark frosting.
 * Dark Mode: Subtle light frosting.
 */
/**
 * REFACTORED: Visibility-First Glass Effect
 * Uses Surface colors to ensure contrast while maintaining the glass "frost" look.
 */
fun Modifier.glassEffect(
    shape: Shape
) = composed {
    val isDark = isSystemInDarkTheme()

    // Use the theme's surface color as the base for maximum readability
    val surfaceBase = MaterialTheme.colorScheme.surface

    // Higher opacities (0.8f - 0.95f) ensure content underneath
    // doesn't interfere with icon/text legibility.
    val alphaTop = if (isDark) 0.92f else 0.85f
    val alphaBottom = if (isDark) 0.98f else 0.95f

    // Brighter, more distinct border for separation
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    this
        .clip(shape)
        // Layer 1: Solid-ish Surface (The "Visibility" layer)
        .background(surfaceBase.copy(alpha = alphaTop))
        // Layer 2: The Gradient (The "Aesthetic" layer)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    surfaceBase.copy(alpha = 0.0f), // Let the base show through
                    if (isDark) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
                )
            )
        )
        // Layer 3: High-contrast border
        .border(1.dp, borderColor, shape)
}/**
 * A custom modifier to give cards a "Glowing Border" when in dark mode
 */
fun Modifier.tigerGlow() = composed {
    val glowColor = MaterialTheme.colorScheme.primary
    this.drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                asFrameworkPaint().apply {
                    maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
                    this.color = glowColor.toArgb()
                }
            }
            canvas.drawCircle(center, size.width / 2, paint)
        }
    }
}
