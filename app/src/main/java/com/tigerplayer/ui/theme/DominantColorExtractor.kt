package com.tigerplayer.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

data class CoverNeonPalette(
    val primary: Color,
    val secondary: Color,
    val trackBackground: Color = Color.White.copy(alpha = 0.12f)
)

object DominantColorExtractor {

    private val neonTokens: List<Color> = listOf(
        TigerCyberCyan,
        TigerToxicLime,
        TigerHotPink,
        TigerElectricAmber,
        TigerNeonOrange,
        TigerSpectralViolet
    )

    suspend fun extractSnappedNeon(
        drawable: Drawable?,
        fallback: Color = TigerNeonOrange
    ): Color = withContext(Dispatchers.Default) {
        extractSnappedNeonInternal(drawable?.toBitmapSafely(), fallback)
    }

    suspend fun extractSnappedNeon(
        bitmap: Bitmap?,
        fallback: Color = TigerNeonOrange
    ): Color = withContext(Dispatchers.Default) {
        extractSnappedNeonInternal(bitmap, fallback)
    }

    suspend fun extractCoverNeonPalette(
        drawable: Drawable?,
        fallback: CoverNeonPalette = CoverNeonPalette(TigerNeonOrange, TigerElectricAmber)
    ): CoverNeonPalette = withContext(Dispatchers.Default) {
        extractCoverNeonPaletteInternal(drawable?.toBitmapSafely(), fallback)
    }

    suspend fun extractCoverNeonPalette(
        bitmap: Bitmap?,
        fallback: CoverNeonPalette = CoverNeonPalette(TigerNeonOrange, TigerElectricAmber)
    ): CoverNeonPalette = withContext(Dispatchers.Default) {
        extractCoverNeonPaletteInternal(bitmap, fallback)
    }

    fun snapToNearestNeon(extracted: Color): Color {
        return neonTokens.minByOrNull { candidate ->
            neonDistance(extracted, candidate)
        } ?: TigerNeonOrange
    }

    private fun extractSnappedNeonInternal(bitmap: Bitmap?, fallback: Color): Color {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return fallback

        val palette = Palette.from(bitmap)
            .maximumColorCount(16)
            .clearFilters()
            .generate()

        val sourceColorInt = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: return fallback

        return snapToNearestNeon(Color(sourceColorInt))
    }

    private fun extractCoverNeonPaletteInternal(
        bitmap: Bitmap?,
        fallback: CoverNeonPalette
    ): CoverNeonPalette {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return fallback

        val palette = Palette.from(bitmap)
            .maximumColorCount(16)
            .clearFilters()
            .generate()

        val primaryInt = palette.vibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: return fallback

        val primaryNeon = snapToNearestNeon(Color(primaryInt))

        val secondaryInt = palette.lightVibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb

        val secondaryCandidate = if (secondaryInt != null) snapToNearestNeon(Color(secondaryInt)) else null
        val secondaryNeon = if (secondaryCandidate != null && secondaryCandidate != primaryNeon) {
            secondaryCandidate
        } else {
            getHarmonizingNeon(primaryNeon)
        }

        return CoverNeonPalette(primary = primaryNeon, secondary = secondaryNeon)
    }

    fun getHarmonizingNeon(primary: Color): Color {
        val idx = neonTokens.indexOf(primary)
        if (idx == -1) return TigerElectricAmber
        return neonTokens[(idx + 2) % neonTokens.size]
    }

    private fun neonDistance(a: Color, b: Color): Float {
        val hsvA = a.toHsv()
        val hsvB = b.toHsv()

        val rawHueDiff = abs(hsvA[0] - hsvB[0])
        val hueDiff = min(rawHueDiff, 360f - rawHueDiff) / 180f
        val satDiff = abs(hsvA[1] - hsvB[1])
        val valueDiff = abs(hsvA[2] - hsvB[2])

        // Hue carries most of the perceptual identity, then saturation, then brightness.
        return (hueDiff * 0.62f) + (satDiff * 0.28f) + (valueDiff * 0.10f)
    }

    private fun Color.toHsv(): FloatArray {
        val r = red
        val g = green
        val b = blue

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val hue = when {
            delta == 0f -> 0f
            max == r -> (((g - b) / delta) % 6f) * 60f
            max == g -> (((b - r) / delta) + 2f) * 60f
            else -> (((r - g) / delta) + 4f) * 60f
        }.let { if (it < 0f) it + 360f else it }

        val saturation = if (max == 0f) 0f else delta / max
        val value = max
        return floatArrayOf(hue, saturation, value)
    }

    private fun Drawable.toBitmapSafely(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap

        val safeWidth = intrinsicWidth.coerceAtLeast(1)
        val safeHeight = intrinsicHeight.coerceAtLeast(1)
        val out = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return out
    }
}
