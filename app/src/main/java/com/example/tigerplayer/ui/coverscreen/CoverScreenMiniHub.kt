package com.example.tigerplayer.ui.coverscreen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import coil.compose.AsyncImage
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.TigerNeonOrange
import com.example.tigerplayer.ui.theme.TigerSurfaceCharcoal
import com.example.tigerplayer.ui.theme.bounceClick
import kotlinx.coroutines.flow.collect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class CoverScreenWindowState(
    val widthDp: Int,
    val heightDp: Int,
    val isCoverScreen: Boolean,
    val hasSeparatingHinge: Boolean
)

fun isCoverScreenHeuristic(widthDp: Int, heightDp: Int): Boolean {
    val shortEdge = min(widthDp, heightDp)
    val longEdge = max(widthDp, heightDp)
    if (shortEdge <= 0 || longEdge <= 0) return false
    val aspectRatio = longEdge.toFloat() / shortEdge.toFloat()
    return shortEdge in 220..399 && longEdge <= 450 && aspectRatio <= 1.35f
}

@Composable
fun rememberCoverScreenWindowState(): CoverScreenWindowState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current

    val windowLayoutInfo by produceState<WindowLayoutInfo?>(initialValue = null, activity) {
        if (activity == null) {
            value = null
            return@produceState
        }
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .collect { value = it }
    }

    val hasSeparatingHinge = windowLayoutInfo
        ?.displayFeatures
        ?.any { it is FoldingFeature && it.isSeparating } == true

    val isCover = isCoverScreenHeuristic(configuration.screenWidthDp, configuration.screenHeightDp) && !hasSeparatingHinge

    return remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        isCover,
        hasSeparatingHinge
    ) {
        CoverScreenWindowState(
            widthDp = configuration.screenWidthDp,
            heightDp = configuration.screenHeightDp,
            isCoverScreen = isCover,
            hasSeparatingHinge = hasSeparatingHinge
        )
    }
}

@Composable
fun CoverScreenMiniHub(
    playerViewModel: PlayerViewModel,
    windowState: CoverScreenWindowState,
    modifier: Modifier = Modifier
) {
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val track = uiState.currentTrack
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var queueVisible by remember { mutableStateOf(false) }

    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    val tapSlopPx = with(density) { 10.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .coverScreenGestures(
                swipeThresholdPx = swipeThresholdPx,
                tapSlopPx = tapSlopPx,
                onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    playerViewModel.togglePlayPause()
                },
                onSwipeLeft = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    playerViewModel.skipToNext()
                },
                onSwipeRight = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    playerViewModel.skipToPrevious()
                },
                onSwipeUp = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    queueVisible = true
                },
                onSwipeDown = {
                    if (queueVisible) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        queueVisible = false
                    }
                }
            )
    ) {
        if (track != null) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(28.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TigerSurfaceCharcoal.copy(alpha = 0.78f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                text = track?.title ?: "TIGER PLAYER",
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    shadow = Shadow(
                        color = TigerNeonOrange.copy(alpha = 0.75f),
                        offset = Offset.Zero,
                        blurRadius = 18f
                    )
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.Text(
                text = track?.artist ?: "Swipe up for queue",
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(10.dp))
            androidx.compose.material3.Text(
                text = if (windowState.isCoverScreen) "Tap: Play/Pause  •  Swipe: Prev/Next/Queue" else "Compact Cover Mode",
                color = Color.White.copy(alpha = 0.55f),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
        }

        AnimatedVisibility(
            visible = queueVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CoverQueueSheet(
                queue = uiState.queue,
                currentId = track?.id,
                onTrackTapped = {
                    playerViewModel.playTrack(it)
                    queueVisible = false
                }
            )
        }

        CoverMicroWaveform(
            amplitudes = uiState.currentWaveform,
            bass = uiState.audioReactiveFrame.bass,
            energy = uiState.audioReactiveFrame.energy,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(10.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CoverQueueSheet(
    queue: List<com.example.tigerplayer.data.model.AudioTrack>,
    currentId: String?,
    onTrackTapped: (com.example.tigerplayer.data.model.AudioTrack) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.08f),
                        Color.Black.copy(alpha = 0.72f),
                        Color.Black.copy(alpha = 0.84f)
                    )
                )
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        val upcoming = remember(queue, currentId) {
            queue.filter { it.id != currentId }.take(8)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(upcoming, key = { it.id }) { track ->
                val isCurrent = track.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCurrent) TigerNeonOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .bounceClick { onTrackTapped(track) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Text(
                        text = track.title,
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.Text(
                        text = track.artist,
                        color = Color.White.copy(alpha = 0.68f),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverMicroWaveform(
    amplitudes: List<Float>,
    bass: Float,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "CoverWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 820, easing = LinearEasing)),
        label = "CoverWavePhase"
    )
    val animatedEnergy by animateFloatAsState(
        targetValue = (0.2f + energy * 0.9f).coerceIn(0.2f, 1.2f),
        animationSpec = tween(120),
        label = "CoverWaveEnergy"
    )

    val points = remember(amplitudes) {
        if (amplitudes.isEmpty()) {
            List(32) { 0.2f }
        } else {
            List(32) { idx ->
                val sourceIndex = ((idx / 31f) * (amplitudes.lastIndex.coerceAtLeast(0))).toInt()
                amplitudes[sourceIndex].coerceIn(0f, 1f)
            }
        }
    }

    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val step = size.width / (points.size - 1).coerceAtLeast(1)

        val path = Path().apply {
            moveTo(0f, centerY)
            points.forEachIndexed { index, amp ->
                val x = index * step
                val oscillation = sin(phase + index * 0.42f).toFloat()
                val reactive = (amp * 0.65f + bass * 0.35f) * animatedEnergy
                val y = centerY + oscillation * reactive * (size.height * 0.48f)
                lineTo(x, y)
            }
        }

        drawLine(
            color = TigerNeonOrange.copy(alpha = 0.2f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1f
        )

        drawPath(
            path = path,
            color = TigerNeonOrange.copy(alpha = 0.32f),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            color = TigerNeonOrange,
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
    }
}

private fun Modifier.coverScreenGestures(
    swipeThresholdPx: Float,
    tapSlopPx: Float,
    onTap: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit
): Modifier {
    return pointerInput(swipeThresholdPx, tapSlopPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var pointerId: PointerId = down.id
            var totalX = 0f
            var totalY = 0f

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }
                    ?: event.changes.firstOrNull()
                    ?: break

                if (!change.pressed) {
                    break
                }

                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                pointerId = change.id
            }

            val absX = abs(totalX)
            val absY = abs(totalY)

            when {
                absX <= tapSlopPx && absY <= tapSlopPx -> onTap()
                absX > absY && totalX <= -swipeThresholdPx -> onSwipeLeft()
                absX > absY && totalX >= swipeThresholdPx -> onSwipeRight()
                absY > absX && totalY <= -swipeThresholdPx -> onSwipeUp()
                absY > absX && totalY >= swipeThresholdPx -> onSwipeDown()
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}


