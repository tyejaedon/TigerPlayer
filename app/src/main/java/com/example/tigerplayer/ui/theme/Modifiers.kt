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
import androidx.compose.ui.draw.shadow
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
            indication = ripple(bounded = true),
            onClick = onClick
        )
}

/**
 * A dynamic "Glass" effect that reacts to the system theme.
 * Light Mode: Subtle dark frosting.
 * Dark Mode: Subtle light frosting.
 */
/**
 * A dynamic "Glass" effect that reacts to the system theme.
 * Light Mode: Subtle dark frosting with an elegant drop shadow.
 * Dark Mode: Subtle light frosting, floating in the AMOLED void.
 */
fun Modifier.glassEffect(
    shape: Shape
) = composed {
    val isDark = isSystemInDarkTheme()

    // Switch the base pigment depending on the active theme
    val baseTint = if (isDark) Color.White else Color.Black

    // Light mode needs less alpha, but significantly more shadow
    val topAlpha = if (isDark) 0.12f else 0.04f
    val bottomAlpha = if (isDark) 0.05f else 0.01f
    val borderAlpha = if (isDark) 0.2f else 0.1f

    // The Elevation Lift: 0dp in Dark Mode, 8dp in Light Mode
    val shadowElevation = if (isDark) 0.dp else 8.dp

    val glassColor = baseTint.copy(alpha = topAlpha)
    val borderColor = baseTint.copy(alpha = borderAlpha)

    this
        // 1. Cast the shadow first, before we clip the bounds
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false
        )
        // 2. Shape the glass
        .clip(shape)
        // 3. Apply the frosted gradient
        .background(
            Brush.verticalGradient(
                colors = listOf(glassColor, baseTint.copy(alpha = bottomAlpha))
            )
        )
        // 4. Trace the polished edge
        .border(1.dp, borderColor, shape)
}
/**
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