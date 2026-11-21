package com.tigerplayer.ui.theme

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
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = bounceClick(onClick = onClick, onLongClick = null)

fun Modifier.bounceClick(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier = composed {
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
                },
                onLongPress = {
                    onLongClick?.invoke()
                }
            )
        }
}

// ------------------------------
// SAMSUNG GLASS (REALISTIC LAYERING)
// ------------------------------
fun Modifier.glassEffect(
    shape: Shape,
    isOpaque: Boolean = false,
    opaqueAlpha: Float? = null
) = this.composed {
    val isSystemDark = isSystemInDarkTheme()
    val tint = if (isSystemDark) Color.White else Color.Black

    if (isOpaque) {
        val glassBg = if (isSystemDark) Color(0xFF13141C) else Color(0xFFEBF0F5)
        val topAlpha = opaqueAlpha ?: if (isSystemDark) 0.94f else 0.90f
        val bottomAlpha = opaqueAlpha?.times(0.93f) ?: if (isSystemDark) 0.88f else 0.84f

        this
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        glassBg.copy(alpha = topAlpha),
                        glassBg.copy(alpha = bottomAlpha)
                    )
                )
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = if (isSystemDark) 0.06f else 0.03f),
                        Color.Transparent
                    )
                )
            )
    } else {
        this
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = if (isSystemDark) 0.08f else 0.04f),
                        tint.copy(alpha = if (isSystemDark) 0.02f else 0.01f)
                    )
                )
            )
    }
}

fun Modifier.opaqueGlassEffect(
    shape: Shape,
    opaqueAlpha: Float? = null
) = this.glassEffect(shape = shape, isOpaque = true, opaqueAlpha = opaqueAlpha)
// ------------------------------
// SAMSUNG "DEPTH GLOW"
// ------------------------------
@SuppressLint("UnnecessaryComposedModifier")
@Composable
@Suppress("DEPRECATION")
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
