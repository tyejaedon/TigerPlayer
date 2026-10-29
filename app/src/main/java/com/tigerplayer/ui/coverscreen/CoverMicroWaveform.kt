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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import com.tigerplayer.ui.theme.CoverNeonPalette
import com.tigerplayer.ui.theme.DominantColorExtractor
import com.tigerplayer.ui.theme.TigerElectricAmber
import com.tigerplayer.ui.theme.TigerNeonOrange
import java.util.concurrent.TimeUnit

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val safeDuration = duration.coerceAtLeast(1L)
    val shouldAnimate = CoverScreenPerformanceManager.shouldAnimate(isPlaying, isRenderingActive)

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    // Dynamic album art palette extraction snapped to high-contrast neon tokens
    var neonPalette by remember {
        mutableStateOf(CoverNeonPalette(primary = TigerNeonOrange, secondary = TigerElectricAmber))
    }

    LaunchedEffect(artworkUri) {
        if (artworkUri == null) {
            neonPalette = CoverNeonPalette(primary = TigerNeonOrange, secondary = TigerElectricAmber)
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
                neonPalette = DominantColorExtractor.extractCoverNeonPalette(bitmap)
            }
        } catch (_: Exception) {
            neonPalette = CoverNeonPalette(primary = TigerNeonOrange, secondary = TigerElectricAmber)
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

    // Reusable paths for zero heap allocation across 60/120 FPS render loops
    val wavePath = remember { Path() }
    val fillPath = remember { Path() }

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
                val centerY = size.height / 2f
                val progressX = (size.width * activeProgress).coerceIn(0f, size.width)
                val trackHeight = 4.dp.toPx()
                val trackRadius = trackHeight / 2f

                // Base subtle track background
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(0f, centerY - trackRadius),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(trackRadius, trackRadius)
                )

                // Populate waveform geometry
                CoverScreenPerformanceManager.populateWaveformPath(
                    path = wavePath,
                    points = points,
                    width = size.width,
                    height = size.height,
                    phase = phase,
                    bass = if (shouldAnimate) bass else 0f,
                    energy = animatedEnergy
                )

                // Populate inside fill geometry (closing wave to baseline)
                fillPath.reset()
                fillPath.addPath(wavePath)
                fillPath.lineTo(size.width, centerY + (trackHeight * 1.5f))
                fillPath.lineTo(0f, centerY + (trackHeight * 1.5f))
                fillPath.close()

                // Active Played Zone (0 to progressX)
                clipRect(left = 0f, top = 0f, right = progressX, bottom = size.height) {
                    // Played track background bar
                    drawRoundRect(
                        color = neonPalette.primary.copy(alpha = 0.35f),
                        topLeft = Offset(0f, centerY - trackRadius),
                        size = Size(progressX, trackHeight),
                        cornerRadius = CornerRadius(trackRadius, trackRadius)
                    )

                    // Inside Waveform Fill (Vertical Neon Gradient)
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                neonPalette.primary.copy(alpha = 0.40f),
                                neonPalette.secondary.copy(alpha = 0.08f)
                            ),
                            startY = 0f,
                            endY = size.height
                        )
                    )

                    // Active Waveform Outline Glow
                    drawPath(
                        path = wavePath,
                        color = neonPalette.secondary.copy(alpha = 0.32f),
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Active Waveform Outline (Crisp Neon Primary)
                    drawPath(
                        path = wavePath,
                        color = neonPalette.primary,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Inactive Unplayed Zone (progressX to width)
                clipRect(left = progressX, top = 0f, right = size.width, bottom = size.height) {
                    drawPath(
                        path = wavePath,
                        color = Color.White.copy(alpha = 0.18f),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Scrubber Dot / Thumb
                val thumbRadius = if (isDragging) 6.dp.toPx() else 4.dp.toPx()
                val glowRadius = if (isDragging) 10.dp.toPx() else 7.dp.toPx()

                // Outer neon halo
                drawCircle(
                    color = neonPalette.primary.copy(alpha = if (isDragging) 0.65f else 0.40f),
                    radius = glowRadius,
                    center = Offset(progressX, centerY)
                )

                // Core dot
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(progressX, centerY)
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
