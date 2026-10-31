package com.tigerplayer.ui.player

import android.annotation.SuppressLint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.coverscreen.CoverScreenPerformanceManager
import com.tigerplayer.ui.coverscreen.populatePositiveWaveformPath
import com.tigerplayer.ui.theme.DominantColorExtractor
import com.tigerplayer.ui.theme.LocalCoverNeonPalette
import java.util.concurrent.TimeUnit

/**
 * Harmonized high-performance player seeker and audio-reactive waveform scrubber.
 *
 * Employs a 3-tier harmonic wave hierarchy oscillating smoothly over the slider track:
 * - Wave 1: Base amplitude, primary neon red/album color foundation.
 * - Wave 2: 4x amplitude, secondary neon orange/album crests with PI/4 rad phase lead.
 * - Wave 3: 12x amplitude, tertiary neon cyan/album cosine apex peaks.
 *
 * Enforces zero heap allocations inside the Canvas draw loop and features non-destructive
 * continuous dragging with tabular figure timestamps.
 */
@SuppressLint("AutoboxingStateCreation")
@Composable
fun FieryWavySeeker(
    uiState: PlayerUiState,
    track: AudioTrack,
    onSeek: (Long) -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier,
    artworkUri: Any? = track.artworkUri,
    energy: Float = uiState.audioReactiveFrame.energy,
    bass: Float = uiState.audioReactiveFrame.bass,
    baseWaveAmplitude: Dp = 8.dp
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val defaultPalette = LocalCoverNeonPalette.current
    val duration = track.durationMs.coerceAtLeast(1L)
    val shouldAnimate = uiState.isPlaying

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    // Dynamic album art palette extraction snapped to high-contrast neon tokens
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
        val transition = rememberInfiniteTransition(label = "WaveformTransition")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2.0).toFloat(),
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 820, easing = LinearEasing)),
            label = "WavePhase"
        )
        val energyState by animateFloatAsState(
            targetValue = (0.2f + energy * 0.9f).coerceIn(0.2f, 1.2f),
            animationSpec = tween(120),
            label = "WaveEnergy"
        )
        Pair(animatedPhase, energyState)
    } else {
        Pair(0f, 0.2f)
    }

    val points = remember(uiState.currentWaveform) {
        CoverScreenPerformanceManager.calculateWaveformPoints(uiState.currentWaveform, 32)
    }

    // Pre-allocated paths for zero heap allocations inside the 60/120 FPS render loop
    val wavePath1 = remember { Path() }
    val fillPath1 = remember { Path() }
    val wavePath2 = remember { Path() }
    val fillPath2 = remember { Path() }
    val wavePath3 = remember { Path() }
    val fillPath3 = remember { Path() }

    val rawProgress = (uiState.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f).takeIf { !it.isNaN() } ?: 0f
    val activeProgress = if (isDragging) dragProgress else rawProgress
    val displayPositionMs = if (isDragging) {
        (dragProgress * duration).toLong().coerceIn(0L, duration)
    } else {
        uiState.currentPosition.coerceIn(0L, duration)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics {
                    contentDescription = "Playback position slider"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = activeProgress,
                        range = 0f..1f
                    )
                }
                .pointerInput(duration) {
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
                                change.consume()
                                isDragging = false
                                val seekTargetMs = (dragProgress * duration).toLong().coerceIn(0L, duration)
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
                val bottomPadding = 4.dp.toPx()
                val trackCenterY = size.height - bottomPadding - trackRadius
                val trackTop = trackCenterY - trackRadius
                val overlapPx = 1.5.dp.toPx()
                val effectiveBaselineY = trackTop + overlapPx

                val wave1Amplitude = baseWaveAmplitude.toPx()
                val wave2Amplitude = wave1Amplitude * 4f
                val wave3Amplitude = wave2Amplitude * 3f
                val piOver4 = (Math.PI / 4.0).toFloat()

                // Base track background
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(trackRadius, trackRadius)
                )

                // Populate Wave 3: Cosine unconfined apex tier (12x Wave 1)
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
                    bass = if (shouldAnimate) bass else 0f,
                    energy = animatedEnergy
                )

                // Populate Wave 2: Middle tier (4x Wave 1, PI/4 phase lead)
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
                    bass = if (shouldAnimate) bass else 0f,
                    energy = animatedEnergy
                )

                // Populate Wave 1: Foundational tier (base amplitude)
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
                    bass = if (shouldAnimate) bass else 0f,
                    energy = animatedEnergy
                )

                // Active Played Zone (0 to progressX horizontally)
                clipRect(left = 0f, top = -10_000f, right = progressX, bottom = size.height + 1_000f) {
                    drawRoundRect(
                        color = neonPalette.primary.copy(alpha = 0.35f),
                        topLeft = Offset(0f, trackTop),
                        size = Size(progressX, trackHeight),
                        cornerRadius = CornerRadius(trackRadius, trackRadius)
                    )

                    val activeFillAlphaTop = 0.55f
                    val activeFillAlphaBottom = 0.15f
                    val activeGlowAlpha = 0.30f
                    val activeStrokeAlpha = 0.95f

                    // Wave 3 (Tertiary Neon)
                    drawPath(
                        path = fillPath3,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                neonPalette.tertiary.copy(alpha = activeFillAlphaTop),
                                neonPalette.tertiary.copy(alpha = activeFillAlphaBottom)
                            ),
                            startY = effectiveBaselineY - wave3Amplitude,
                            endY = effectiveBaselineY
                        )
                    )
                    drawPath(
                        path = wavePath3,
                        color = neonPalette.tertiary.copy(alpha = activeGlowAlpha),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = wavePath3,
                        color = neonPalette.tertiary.copy(alpha = activeStrokeAlpha),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Wave 2 (Secondary Neon)
                    drawPath(
                        path = fillPath2,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                neonPalette.secondary.copy(alpha = activeFillAlphaTop),
                                neonPalette.secondary.copy(alpha = activeFillAlphaBottom)
                            ),
                            startY = effectiveBaselineY - wave2Amplitude,
                            endY = effectiveBaselineY
                        )
                    )
                    drawPath(
                        path = wavePath2,
                        color = neonPalette.secondary.copy(alpha = activeGlowAlpha),
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = wavePath2,
                        color = neonPalette.secondary.copy(alpha = activeStrokeAlpha),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Wave 1 (Primary Neon)
                    drawPath(
                        path = fillPath1,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                neonPalette.primary.copy(alpha = activeFillAlphaTop),
                                neonPalette.primary.copy(alpha = activeFillAlphaBottom)
                            ),
                            startY = effectiveBaselineY - wave1Amplitude,
                            endY = effectiveBaselineY
                        )
                    )
                    drawPath(
                        path = wavePath1,
                        color = neonPalette.primary.copy(alpha = activeGlowAlpha),
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = wavePath1,
                        color = neonPalette.primary.copy(alpha = activeStrokeAlpha),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Scrubber Dot / Thumb
                val thumbRadius = if (isDragging) 6.dp.toPx() else 4.dp.toPx()
                val glowRadius = if (isDragging) 10.dp.toPx() else 7.dp.toPx()

                drawCircle(
                    color = neonPalette.primary.copy(alpha = if (isDragging) 0.65f else 0.40f),
                    radius = glowRadius,
                    center = Offset(progressX, trackCenterY)
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(progressX, trackCenterY)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(displayPositionMs),
                color = if (isDragging) neonPalette.primary else textColor.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = formatDuration(duration),
                color = textColor.copy(alpha = 0.60f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = "tnum"
                )
            )
        }
    }
}

/**
 * Formats duration in milliseconds into `mm:ss` display format.
 */
@SuppressLint("DefaultLocale")
fun formatDuration(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safeMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(safeMs) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
