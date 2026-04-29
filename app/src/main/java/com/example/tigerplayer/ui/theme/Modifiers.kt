package com.example.tigerplayer.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// ------------------------------
// SAMSUNG-STYLE CLICK DEPTH
// ------------------------------
fun Modifier.bounceClick(onClick: () -> Unit) = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "press"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
}

// ------------------------------
// SAMSUNG GLASS (REALISTIC LAYERING)
// ------------------------------
fun Modifier.glassEffect(shape: Shape) = composed {
    val dark = isSystemInDarkTheme()

    val base = if (dark) Color.White.copy(0.04f) else Color.Black.copy(0.03f)

    this
        .clip(shape)
        .background(base)
        .blur(0.6.dp) // subtle Samsung blur feel
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (dark) 0.06f else 0.12f),
                    Color.Transparent
                )
            )
        )
        .border(
            0.5.dp,
            Color.White.copy(alpha = if (dark) 0.12f else 0.08f),
            shape
        )
}

// ------------------------------
// SAMSUNG "DEPTH GLOW"
// ------------------------------
@Composable
fun Modifier.tigerGlow(
    color: Color = MaterialTheme.colorScheme.primary
) = composed {

    this.drawWithCache {

        val paint = androidx.compose.ui.graphics.Paint().apply {
            this.color = color.copy(alpha = 0.25f)
            asFrameworkPaint().apply {
                maskFilter =
                    android.graphics.BlurMaskFilter(
                        40f,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
            }
        }

        onDrawBehind {
            val radius = size.minDimension / 2f

            drawContext.canvas.drawCircle(
                center = center,
                radius = radius,
                paint = paint
            )
        }
    }
}