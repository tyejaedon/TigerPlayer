package com.example.tigerplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

private const val DEFAULT_HEX_PROXIMITY_THRESHOLD = 28

private fun Color.toOpaqueContrastBackgroundArgb(): Int {
    val argb = toArgb()
    if (alpha >= 0.999f) return argb

    // ColorUtils contrast APIs require an opaque background. For translucent colors,
    // composite over a likely base derived from luminance to keep behavior stable.
    val assumedBase = if (luminance() > 0.5f) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    return ColorUtils.compositeColors(argb, assumedBase)
}

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
    val opaqueBackgroundArgb = background.toOpaqueContrastBackgroundArgb()
    val contrast = ColorUtils.calculateContrast(this.toArgb(), opaqueBackgroundArgb)
    val isTooClose = this.isHexRangeSimilarTo(background, perChannelThreshold = similarityThreshold)
    if (!isTooClose && contrast >= minContrast) return this

    val lightContrast = ColorUtils.calculateContrast(lightFallback.toArgb(), opaqueBackgroundArgb)
    val darkContrast = ColorUtils.calculateContrast(darkFallback.toArgb(), opaqueBackgroundArgb)
    return if (lightContrast >= darkContrast) lightFallback else darkFallback
}

fun Color.withSafeAlpha(alpha: Float): Color = copy(alpha = alpha.coerceIn(0f, 1f))

