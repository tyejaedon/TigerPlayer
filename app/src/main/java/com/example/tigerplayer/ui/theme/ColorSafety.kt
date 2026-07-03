package com.example.tigerplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

private const val DEFAULT_HEX_PROXIMITY_THRESHOLD = 28

fun Color.isHexRangeSimilarTo(other: Color, perChannelThreshold: Int = DEFAULT_HEX_PROXIMITY_THRESHOLD): Boolean {
    val rDelta = abs((red * 255f).toInt() - (other.red * 255f).toInt())
    val gDelta = abs((green * 255f).toInt() - (other.green * 255f).toInt())
    val bDelta = abs((blue * 255f).toInt() - (other.blue * 255f).toInt())
    return rDelta <= perChannelThreshold && gDelta <= perChannelThreshold && bDelta <= perChannelThreshold
}

fun Color.ensureVisibleOn(
    background: Color,
    minContrast: Double = 4.0,
    similarityThreshold: Int = DEFAULT_HEX_PROXIMITY_THRESHOLD,
    lightFallback: Color = Color(0xFFF7F5F2),
    darkFallback: Color = Color(0xFF18130F)
): Color {
    val contrast = ColorUtils.calculateContrast(this.toArgb(), background.toArgb())
    val isTooClose = this.isHexRangeSimilarTo(background, perChannelThreshold = similarityThreshold)
    if (!isTooClose && contrast >= minContrast) return this

    val lightContrast = ColorUtils.calculateContrast(lightFallback.toArgb(), background.toArgb())
    val darkContrast = ColorUtils.calculateContrast(darkFallback.toArgb(), background.toArgb())
    return if (lightContrast >= darkContrast) lightFallback else darkFallback
}

fun Color.withSafeAlpha(alpha: Float): Color = copy(alpha = alpha.coerceIn(0f, 1f))

