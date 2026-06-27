package com.example.tigerplayer.ui.theme

import android.annotation.SuppressLint
import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
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
        NeonIntensityMode.SOFT -> 0.86f
        NeonIntensityMode.BALANCED -> 1f
        NeonIntensityMode.HIGH -> 1.18f
    }
    val shadowPrimary = if (darkTheme) {
        TigerRgbBlue.copy(alpha = (if (highContrast) 0.44f else 0.30f) * intensityBoost)
    } else {
        TigerRgbBlue.copy(alpha = (if (highContrast) 0.26f else 0.18f) * intensityBoost)
    }
    val shadowSecondary = if (darkTheme) {
        TigerHotPink.copy(alpha = (if (highContrast) 0.36f else 0.24f) * intensityBoost)
    } else {
        TigerHotPink.copy(alpha = (if (highContrast) 0.22f else 0.14f) * intensityBoost)
    }
    val glassFill = if (darkTheme) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (highContrast) 0.18f else 0.12f),
                Color(0xFF0E0E14).copy(alpha = if (highContrast) 0.66f else 0.54f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (highContrast) 0.86f else 0.74f),
                Color.White.copy(alpha = if (highContrast) 0.50f else 0.40f)
            )
        )
    }

    this
        .shadow(
            elevation = if (darkTheme) {
                if (highContrast) 26.dp * intensityBoost else 18.dp * intensityBoost
            } else {
                if (highContrast) 16.dp * intensityBoost else 12.dp * intensityBoost
            },
            shape = shape,
            ambientColor = shadowPrimary,
            spotColor = shadowSecondary,
            clip = false
        )
        .clip(shape)
        .background(glassFill)
        .drawWithCache {
            val borderPx = borderWidth.toPx()
            val outline = shape.createOutline(size, layoutDirection, this)
            val edgeHighlight = if (darkTheme) {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.05f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.60f),
                        Color.White.copy(alpha = 0.15f)
                    )
                )
            }

            onDrawWithContent {
                drawContent()
                if (showRgbBorder) {
                    drawOutline(
                        outline = outline,
                        brush = TigerRgbNeonBorderBrush,
                        style = Stroke(width = borderPx * (if (highContrast) 1.12f else 0.94f))
                    )
                    drawOutline(
                        outline = outline,
                        brush = TigerRgbNeonBorderSoftBrush,
                        style = Stroke(width = borderPx)
                    )
                }
                drawOutline(
                    outline = outline,
                    brush = edgeHighlight,
                    style = Stroke(width = borderPx * 0.72f)
                )
            }
        }
}
// ------------------------------
// SAMSUNG "DEPTH GLOW"
// ------------------------------
@SuppressLint("UnnecessaryComposedModifier")
@Composable
@Suppress("DEPRECATION")
fun Modifier.tigerGlow(
    color: Color = MaterialTheme.colorScheme.primary
) = composed {
    val contrastMode = LocalTigerNeonContrastMode.current
    val intensityMode = LocalTigerNeonIntensityMode.current
    val highContrast = contrastMode == NeonContrastMode.HIGH
    val intensityBoost = when (intensityMode) {
        NeonIntensityMode.SOFT -> 0.86f
        NeonIntensityMode.BALANCED -> 1f
        NeonIntensityMode.HIGH -> 1.22f
    }

    this.drawWithCache {

        val paint = Paint().apply {
            this.color = color.copy(alpha = (if (highContrast) 0.38f else 0.28f) * intensityBoost)
            asFrameworkPaint().apply {
                maskFilter =
                    BlurMaskFilter(
                        (if (highContrast) 60f else 46f) * intensityBoost,
                        BlurMaskFilter.Blur.NORMAL
                    )
            }
        }

        val bloomPaint = Paint().apply {
            this.color = color.copy(alpha = (if (highContrast) 0.24f else 0.14f) * intensityBoost)
            asFrameworkPaint().apply {
                maskFilter =
                    BlurMaskFilter(
                        (if (highContrast) 84f else 66f) * intensityBoost,
                        BlurMaskFilter.Blur.NORMAL
                    )
            }
        }

        onDrawBehind {
            val radius = size.minDimension / 2f

            drawContext.canvas.drawCircle(
                center = center,
                radius = radius * 1.12f,
                paint = bloomPaint
            )

            drawContext.canvas.drawCircle(
                center = center,
                radius = radius,
                paint = paint
            )
        }
    }
}