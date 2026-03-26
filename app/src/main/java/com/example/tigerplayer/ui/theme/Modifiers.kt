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

    // S22 Optimization: Subtler scale (0.96f) for high-density targets
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium // Increased stiffness for faster recovery
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
    val surfaceBase = MaterialTheme.colorScheme.surface

    // S22 Optimization: Higher opacities for better legibility in bright Nairobi sun
    val alphaTop = if (isDark) 0.94f else 0.88f
    val alphaBottom = if (isDark) 0.99f else 0.96f

    val borderColor = if (isDark) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    } else {
        Color.Black.copy(alpha = 0.1f)
    }

    this
        .clip(shape)
        .background(surfaceBase.copy(alpha = alphaTop))
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    surfaceBase.copy(alpha = 0.0f),
                    if (isDark) Color.Black.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )
            )
        )
        // S22 Optimization: 0.5.dp border for high-res precision
        .border(0.5.dp, borderColor, shape)
}
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
