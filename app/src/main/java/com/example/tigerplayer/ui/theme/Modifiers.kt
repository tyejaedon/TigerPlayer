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
fun Modifier.glassEffect(
    shape: Shape,
    color: Color = Color.White.copy(alpha = 0.12f),
    borderColor: Color = Color.White.copy(alpha = 0.2f)
) = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            colors = listOf(color, color.copy(alpha = 0.05f))
        )
    )
    .border(1.dp, borderColor, shape)

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
