package com.example.tigerplayer.ui.theme

import android.annotation.SuppressLint
import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
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
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
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
fun Modifier.glassEffect(shape: Shape) = this.composed {
    this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.02f)
                )
            )
        )
}
// ------------------------------
// SAMSUNG "DEPTH GLOW"
// ------------------------------
@SuppressLint("UnnecessaryComposedModifier")
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