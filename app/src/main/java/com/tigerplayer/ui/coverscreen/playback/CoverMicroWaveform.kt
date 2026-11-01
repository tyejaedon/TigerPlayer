package com.tigerplayer.ui.coverscreen

import android.annotation.SuppressLint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import com.tigerplayer.ui.theme.CoverNeonPalette
import com.tigerplayer.ui.theme.DefaultCoverNeonPalette
import com.tigerplayer.ui.theme.DominantColorExtractor
import com.tigerplayer.ui.theme.LocalCoverNeonPalette
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerNeonRed
import com.tigerplayer.ui.theme.TigerPlayerTheme
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * High-performance cover-screen playback scrubber and audio-reactive waveform entity,
 * inspired by the Samsung Now Bar media playback controller.
 *
 * Blends a slider track with dynamic dual-tone album palette waveform visualization (crisp neon outline
 * and luminous inside gradient fill), paired with real-time non-destructive scrobbling timestamps.
 */
@Composable
fun CoverMicroWaveform(
    amplitudes: List<Float>,
    bass: Float,
    energy: Float,
    isPlaying: Boolean = true,
    isRenderingActive: Boolean = true,
    duration: Long = 1L,
    currentPosition: Long = 0L,
    onSeek: (Long) -> Unit = {},
    artworkUri: Any? = null,
    baseWaveAmplitude: Dp = 6.5.dp,
    showUnplayedZone: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val defaultPalette = LocalCoverNeonPalette.current
    val safeDuration = duration.coerceAtLeast(1L)
    val shouldAnimate = CoverScreenPerformanceManager.shouldAnimate(isPlaying, isRenderingActive)

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    // Dynamic album art palette extraction snapped to high-contrast neon tokens (defaulting to vibrant red neon palette)
    var neonPalette by remember(defaultPalette) {
        mutableStateOf(defaultPalette)
    }

    LaunchedEffect(artworkUri, defaultPalette) {
        if (isInspection || artworkUri == null) {
            neonPalette = defaultPalette
            return@LaunchedEffect
        }
        try {
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .allowHardware(false)
                .size(128)
                .build()
            val result = context.imageLoader.execute(request)
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                neonPalette = DominantColorExtractor.extractCoverNeonPalette(bitmap, defaultPalette)
            }
        } catch (_: Exception) {
            neonPalette = defaultPalette
        }
    }

    val (phase, animatedEnergy) = if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "CoverWave")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2.0).toFloat(),
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 820, easing = LinearEasing)),
            label = "CoverWavePhase"
        )
        val energyState by animateFloatAsState(
            targetValue = (0.2f + energy * 0.9f).coerceIn(0.2f, 1.2f),
            animationSpec = tween(120),
            label = "CoverWaveEnergy"
        )
        Pair(animatedPhase, energyState)
    } else {
        Pair(0f, 0.2f)
    }

    val points = remember(amplitudes) {
        CoverScreenPerformanceManager.calculateWaveformPoints(amplitudes, 32)
    }

    // Reusable paths for zero heap allocation across 60/120 FPS render loops (3 distinct harmonic waves)
    val wavePath1 = remember { Path() }
    val fillPath1 = remember { Path() }
    val wavePath2 = remember { Path() }
    val fillPath2 = remember { Path() }
    val wavePath3 = remember { Path() }
    val fillPath3 = remember { Path() }

    val rawProgress = (currentPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val activeProgress = if (isDragging) dragProgress else rawProgress
    val displayPositionMs = if (isDragging) {
        (dragProgress * safeDuration).toLong().coerceIn(0L, safeDuration)
    } else {
        currentPosition.coerceIn(0L, safeDuration)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Current / Scrubbed Timestamp (Far Left)
        Text(
            text = formatCoverDuration(displayPositionMs),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = if (isDragging) neonPalette.primary else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.testTag(CoverScreenTestTags.MICRO_WAVEFORM_CURRENT_TIME)
        )

        // 2. Interactive Unified Slider & Waveform Track (Center)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(CoverScreenTestTags.MICRO_WAVEFORM_SLIDER)
                .semantics {
                    contentDescription = "Playback position slider"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = activeProgress,
                        range = 0f..1f
                    )
                }
                .pointerInput(safeDuration) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val widthPx = size.width.toFloat()
                        if (widthPx > 0f) {
                            isDragging = true
                            dragProgress = (down.position.x / widthPx).coerceIn(0f, 1f)
                        }
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                // Finger released - dispatch seek now
                                change.consume()
                                isDragging = false
                                val seekTargetMs = (dragProgress * safeDuration).toLong().coerceIn(0L, safeDuration)
                                onSeek(seekTargetMs)
                                break
                            }
                            change.consume()
                            if (widthPx > 0f) {
                                dragProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                            }
                        }
                        isDragging = false
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val progressX = (size.width * activeProgress).coerceIn(0f, size.width)
                val trackHeight = 4.dp.toPx()
                val trackRadius = trackHeight / 2f
                val bottomPadding = 3.dp.toPx()
                val trackCenterY = size.height - bottomPadding - trackRadius
                val trackTop = trackCenterY - trackRadius
                val overlapPx = 1.5.dp.toPx()
                val effectiveBaselineY = trackTop + overlapPx
                // Harmonized relative wave amplitude hierarchy: Wave 2 is 2x Wave 1, Wave 3 is 1.5x Wave 2 (3x Wave 1)
                val wave1Amplitude = baseWaveAmplitude.toPx()
                val wave2Amplitude = wave1Amplitude * 2f
                val wave3Amplitude = wave2Amplitude * 1.5f
                val piOver4 = (Math.PI / 4.0).toFloat() // PI / 4 radians (45 degree phase shift)
                val piOver2 = (Math.PI / 2.0).toFloat() // PI / 2 radians (90 degree phase shift)

                // Base subtle track background
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(trackRadius, trackRadius)
                )

                // 1. Populate Wave 3: 1.5x larger than Wave 2 (3x Wave 1) - crowning cyan apex tier
                populatePositiveWaveformPath(
                    path = wavePath3,
                    fillPath = fillPath3,
                    points = points,
                    width = size.width,
                    maxHeight = wave3Amplitude,
                    baselineY = trackTop,
                    overlapPx = overlapPx,
                    phase = phase,
                    phaseOffset = 0f,
                    isCosine = true,
                    frequencyMultiplier = 1.0f,
                    waveCycles = 2.0f,
                    bass = if (shouldAnimate) bass else 0f,
                    energy = animatedEnergy
                )

                // 2. Populate Wave 2: 2x larger than Wave 1 - intermediate middle tier (PI / 4 phase lead)
                populatePositiveWaveformPath(
                    path = wavePath2,
                    fillPath = fillPath2,
                    points = points,
                    width = size.width,
                    maxHeight = wave2Amplitude,
                    baselineY = trackTop,
                    overlapPx = overlapPx,
                    phase = phase,
                    phaseOffset = piOver4,
                    isCosine = false,
                    frequencyMultiplier = 1.0f,
                    waveCycles = 2.0f,
                    bass = if (shouldAnimate) bass else 0f,
                    energy = animatedEnergy
                )

                // 3. Populate Wave 1: Base amplitude - foundational red foreground tier
                populatePositiveWaveformPath(
                    path = wavePath1,
                    fillPath = fillPath1,
                    points = points,
                    width = size.width,
                    maxHeight = wave1Amplitude,
                    baselineY = trackTop,
                    overlapPx = overlapPx,
                    phase = phase,
                    phaseOffset = 0f,
                    isCosine = false,
                    frequencyMultiplier = 1.0f,
                    waveCycles = 2.0f,
                    bass = if (shouldAnimate) bass else 0f,
                    energy = animatedEnergy
                )

                // Active Played Zone (0 to progressX horizontally, unconfined vertically by parent bounds)
                clipRect(left = 0f, top = -10_000f, right = progressX, bottom = size.height + 1_000f) {
                    // Played track background bar
                    drawRoundRect(
                        color = neonPalette.primary.copy(alpha = 0.35f),
                        topLeft = Offset(0f, trackTop),
                        size = Size(progressX, trackHeight),
                        cornerRadius = CornerRadius(trackRadius, trackRadius)
                    )

                    // Optimal transparency for 2.5D volumetric glassmorphic depth
                    val activeFillAlphaTop = 0.38f
                    val activeFillAlphaBottom = 0.12f
                    val activeGlowAlpha = 0.55f
                    val activeStrokeAlpha = 0.92f

                    // 2.5D depth shadow offsets and occlusion tones
                    val shadowOffset3 = 2.dp.toPx()
                    val shadowOffset2 = 1.8.dp.toPx()
                    val shadowOffset1 = 1.5.dp.toPx()
                    val shadowColor3 = Color.Black.copy(alpha = 0.20f)
                    val shadowColor2 = Color.Black.copy(alpha = 0.22f)
                    val shadowColor1 = Color.Black.copy(alpha = 0.25f)

                    // Wave 3 (Cosine Wave): Tertiary Neon Color (1.5x Wave 2, 3x Wave 1 - crowning cyan peaks)
                    // 2.5D ambient occlusion depth shadow beneath Wave 3
                    drawContext.canvas.save()
                    drawContext.canvas.translate(0f, shadowOffset3)
                    drawPath(
                        path = fillPath3,
                        color = shadowColor3
                    )
                    drawContext.canvas.restore()

                    // Wave 3 volumetric 2.5D translucent gradient fill
                    drawPath(
                        path = fillPath3,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = activeFillAlphaTop * 0.40f),
                                neonPalette.tertiary.copy(alpha = activeFillAlphaTop),
                                neonPalette.tertiary.copy(alpha = activeFillAlphaBottom)
                            ),
                            startY = effectiveBaselineY - wave3Amplitude,
                            endY = effectiveBaselineY
                        )
                    )
                    // Wave 3 pronounced glitter glow: wide atmospheric bloom + inner neon glow + crisp stroke + white glitter core
                    drawPath(
                        path = wavePath3,
                        color = neonPalette.tertiary.copy(alpha = 0.28f),
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = wavePath3,
                        color = neonPalette.tertiary.copy(alpha = activeGlowAlpha),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = wavePath3,
                        color = neonPalette.tertiary.copy(alpha = activeStrokeAlpha),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = wavePath3,
                        color = Color.White.copy(alpha = 0.85f),
                        style = Stroke(width = 0.75.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Wave 2 (Sine Wave at PI / 4 rad): Secondary Neon Color (2x Wave 1 - vibrant orange crests)
                    // 2.5D ambient occlusion depth shadow beneath Wave 2
                    drawContext.canvas.save()
                    drawContext.canvas.translate(0f, shadowOffset2)
                    drawPath(
                        path = fillPath2,
                        color = shadowColor2
                    )
                    drawContext.canvas.restore()

                    // Wave 2 volumetric 2.5D translucent gradient fill
                    drawPath(
                        path = fillPath2,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = activeFillAlphaTop * 0.35f),
                                neonPalette.secondary.copy(alpha = activeFillAlphaTop),
                                neonPalette.secondary.copy(alpha = activeFillAlphaBottom)
                            ),
                            startY = effectiveBaselineY - wave2Amplitude,
                            endY = effectiveBaselineY
                        )
                    )
                    // Wave 2 balanced neon glow halo
                    drawPath(
                        path = wavePath2,
                        color = neonPalette.secondary.copy(alpha = activeGlowAlpha * 0.75f),
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = wavePath2,
                        color = neonPalette.secondary.copy(alpha = activeStrokeAlpha * 0.90f),
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Wave 1 (Sine Wave): Primary Neon Color (Base amplitude - vibrant red foundation glow & solid)
                    // 2.5D ambient occlusion depth shadow beneath Wave 1
                    drawContext.canvas.save()
                    drawContext.canvas.translate(0f, shadowOffset1)
                    drawPath(
                        path = fillPath1,
                        color = shadowColor1
                    )
                    drawContext.canvas.restore()

                    // Wave 1 volumetric 2.5D translucent gradient fill
                    drawPath(
                        path = fillPath1,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = activeFillAlphaTop * 0.40f),
                                neonPalette.primary.copy(alpha = activeFillAlphaTop),
                                neonPalette.primary.copy(alpha = activeFillAlphaBottom)
                            ),
                            startY = effectiveBaselineY - wave1Amplitude,
                            endY = effectiveBaselineY
                        )
                    )
                    // Wave 1 pronounced glitter glow: wide atmospheric bloom + inner neon glow + crisp stroke + white glitter core
                    drawPath(
                        path = wavePath1,
                        color = neonPalette.primary.copy(alpha = 0.32f),
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = wavePath1,
                        color = neonPalette.primary.copy(alpha = activeGlowAlpha),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = wavePath1,
                        color = neonPalette.primary.copy(alpha = activeStrokeAlpha),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = wavePath1,
                        color = Color.White.copy(alpha = 0.90f),
                        style = Stroke(width = 0.85.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Inactive Unplayed Zone (progressX to width horizontally, unconfined vertically by parent bounds)
                if (showUnplayedZone) {
                    clipRect(left = progressX, top = -10_000f, right = size.width, bottom = size.height + 1_000f) {
                        // Soft atmospheric ghost wave so playhead transition has no jarring cutoff cliff
                        val unplayedFillAlphaTop = 0.05f
                        val unplayedFillAlphaBottom = 0.01f
                        val unplayedGlowAlpha = 0.08f
                        val unplayedStrokeAlpha = 0.16f

                        // Wave 3 (Cosine Wave): Tertiary Neon Color (Cyan)
                        drawPath(
                            path = fillPath3,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    neonPalette.tertiary.copy(alpha = unplayedFillAlphaTop),
                                    neonPalette.tertiary.copy(alpha = unplayedFillAlphaBottom)
                                ),
                                startY = effectiveBaselineY - wave3Amplitude,
                                endY = effectiveBaselineY
                            )
                        )
                        drawPath(
                            path = wavePath3,
                            color = neonPalette.tertiary.copy(alpha = unplayedGlowAlpha),
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = wavePath3,
                            color = neonPalette.tertiary.copy(alpha = unplayedStrokeAlpha),
                            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Wave 2 (Sine Wave at PI / 4 rad): Secondary Neon Color (Orange)
                        drawPath(
                            path = fillPath2,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    neonPalette.secondary.copy(alpha = unplayedFillAlphaTop),
                                    neonPalette.secondary.copy(alpha = unplayedFillAlphaBottom)
                                ),
                                startY = effectiveBaselineY - wave2Amplitude,
                                endY = effectiveBaselineY
                            )
                        )
                        drawPath(
                            path = wavePath2,
                            color = neonPalette.secondary.copy(alpha = unplayedGlowAlpha),
                            style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = wavePath2,
                            color = neonPalette.secondary.copy(alpha = unplayedStrokeAlpha),
                            style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Wave 1 (Sine Wave): Primary Neon Color (Red)
                        drawPath(
                            path = fillPath1,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    neonPalette.primary.copy(alpha = unplayedFillAlphaTop),
                                    neonPalette.primary.copy(alpha = unplayedFillAlphaBottom)
                                ),
                                startY = effectiveBaselineY - wave1Amplitude,
                                endY = effectiveBaselineY
                            )
                        )
                        drawPath(
                            path = wavePath1,
                            color = neonPalette.primary.copy(alpha = unplayedGlowAlpha),
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = wavePath1,
                            color = neonPalette.primary.copy(alpha = unplayedStrokeAlpha),
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Scrubber Dot / Thumb (held directly on the drawRoundRect track)
                val thumbRadius = if (isDragging) 6.dp.toPx() else 4.dp.toPx()
                val glowRadius = if (isDragging) 10.dp.toPx() else 7.dp.toPx()

                // Outer neon halo
                drawCircle(
                    color = neonPalette.primary.copy(alpha = if (isDragging) 0.65f else 0.40f),
                    radius = glowRadius,
                    center = Offset(progressX, trackCenterY)
                )

                // Core dot
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(progressX, trackCenterY)
                )
            }
        }

        // 3. Total Track Duration (Far Right)
        Text(
            text = formatCoverDuration(safeDuration),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = Color.White.copy(alpha = 0.60f),
            modifier = Modifier.testTag(CoverScreenTestTags.MICRO_WAVEFORM_END_TIME)
        )
    }
}

/**
 * Formats a timestamp in milliseconds to standard `mm:ss` display for cover screen widgets.
 */
@SuppressLint("DefaultLocale")
fun formatCoverDuration(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safeMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(safeMs) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Modular waveform generator that creates smooth, continuous curved paths containing exclusively
 * positive amplitudes resting directly on top of the slider track with slight overlap for seamless
 * visual continuity.
 *
 * Mathematical details:
 * - Continuous raised sinusoidal wave: 0.5 * (1 + trig(theta)), oscillating smoothly between 0 and maxHeight.
 * - At troughs (trig = -1), the wave gently touches the baseline with a zero horizontal derivative (no flat clipping).
 * - At crests (trig = 1), the wave reaches its apex peak height smoothly.
 * - When [isCosine] is false and [phaseOffset] is 0, generates a standard sine wave.
 * - When [isCosine] is false and [phaseOffset] is PI / 4 radians, generates a phase-shifted wave (45-degree offset).
 * - When [isCosine] is true, generates a cosine wave (equivalent to 90-degree offset).
 * - All waves develop proper continuous curves in all scenarios (including zero audio energy, silence, or paused playback)
 *   without flattening or collapsing into straight lines.
 *
 * @param path Pre-allocated Path for the wave stroke outline.
 * @param fillPath Optional pre-allocated Path for the closed gradient fill geometry.
 * @param points Smoothed amplitude data points along the horizontal axis.
 * @param width Full horizontal drawing width in pixels.
 * @param maxHeight Maximum upward peak height in pixels above the baseline.
 * @param baselineY Baseline Y coordinate (top edge of the slider track).
 * @param overlapPx Overlap distance in pixels into the slider rect for seamless visual continuity.
 * @param phase Dynamic animated phase angle in radians.
 * @param phaseOffset Fixed phase offset in radians (e.g. PI / 4 for the second wave).
 * @param isCosine Whether this wave uses cosine (true) or sine (false).
 * @param frequencyMultiplier Harmonic frequency scaling factor.
 * @param waveCycles Number of complete wave cycles across the width (defaults to 2.0f for consistent sinusoidal wavelength).
 * @param bass Real-time low frequency energy (0..1).
 * @param energy Audio energy scaling factor (0.2..1.2).
 */
fun populatePositiveWaveformPath(
    path: Path,
    fillPath: Path? = null,
    points: FloatArray,
    width: Float,
    maxHeight: Float,
    baselineY: Float,
    overlapPx: Float,
    phase: Float,
    phaseOffset: Float = 0f,
    isCosine: Boolean = false,
    frequencyMultiplier: Float = 1f,
    waveCycles: Float = 2.0f,
    bass: Float = 0f,
    energy: Float = 1f
): Path {
    path.reset()
    fillPath?.reset()
    if (width <= 0f || maxHeight <= 0f) {
        return path
    }

    val effectiveBaselineY = baselineY + overlapPx
    val safePhase = if (phase.isFinite()) phase else 0f
    val safeBass = if (bass.isFinite()) bass.coerceIn(0f, 1f) else 0f
    val safeEnergy = if (energy.isFinite()) energy.coerceIn(0.2f, 1.2f) else 0.5f

    // Subtle audio energy breathing factor (0.90..1.00) ensuring waves ALWAYS develop at least 90%
    // of full height even during silence, paused playback, or empty amplitudes
    val energyModulation = 0.90f + 0.10f * (safeBass * 0.5f + ((safeEnergy - 0.2f) / 1.0f).coerceIn(0f, 1f) * 0.5f).coerceIn(0f, 1f)
    val effectiveHeight = maxHeight * energyModulation

    val twoPi = (Math.PI * 2.0).toFloat()
    val sampleCount = max(64, points.size)
    val step = width / sampleCount

    // Helper to compute wave Y coordinate at any horizontal pixel position x
    fun computeWaveY(x: Float): Float {
        val normalizedX = (x / width).coerceIn(0f, 1f)
        val angle = (safePhase * frequencyMultiplier) + phaseOffset + (normalizedX * waveCycles * twoPi * frequencyMultiplier)
        val rawTrig = if (isCosine) cos(angle.toDouble()).toFloat() else sin(angle.toDouble()).toFloat()
        // Raised sinusoid: continuous smooth wave in [0, 1] without flat-line clipping
        val smoothOscillation = 0.5f * (1f + rawTrig)
        val peakHeight = smoothOscillation * effectiveHeight
        return effectiveBaselineY - peakHeight
    }

    val y0 = computeWaveY(0f)
    path.moveTo(0f, y0)
    fillPath?.let { fill ->
        fill.moveTo(0f, effectiveBaselineY)
        fill.lineTo(0f, y0)
    }

    for (i in 1..sampleCount) {
        val x = if (i == sampleCount) width else i * step
        val y = computeWaveY(x)
        path.lineTo(x, y)
        fillPath?.lineTo(x, y)
    }

    fillPath?.let { fill ->
        fill.lineTo(width, effectiveBaselineY)
        fill.lineTo(0f, effectiveBaselineY)
        fill.close()
    }

    return path
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0C0A)
@Composable
fun CoverMicroWaveformPreview() {
    TigerPlayerTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E0C0A))
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            CoverMicroWaveform(
                amplitudes = listOf(
                    0.15f, 0.35f, 0.70f, 0.95f, 0.60f, 0.40f, 0.85f,
                    0.50f, 0.30f, 0.90f, 0.75f, 0.45f, 0.20f, 0.65f,
                    0.80f, 0.55f, 0.30f, 0.85f, 0.60f, 0.40f, 0.25f
                ),
                bass = 0.65f,
                energy = 0.80f,
                isPlaying = true,
                isRenderingActive = true,
                duration = 215_000L,
                currentPosition = 65_000L,
                onSeek = {}
            )
        }
    }
}
