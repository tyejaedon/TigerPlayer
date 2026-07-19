@file:SuppressLint("NewApi")
package com.example.tigerplayer.ui.coverscreen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import coil.compose.AsyncImage
import com.example.tigerplayer.ui.player.LyricsDisplay
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.TigerNeonOrange
import com.example.tigerplayer.ui.theme.TigerSurfaceCharcoal
import com.example.tigerplayer.ui.theme.glassEffect
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverScreenMiniHub(
    playerViewModel: PlayerViewModel,
    windowState: CoverScreenWindowState,
    modifier: Modifier = Modifier
) {
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val track = uiState.currentTrack
    val view = LocalView.current
    val density = LocalDensity.current

    // FIX: Interactive Sheet State (Enhanced for Cover Screen Stability)
    var queueVisible by remember { mutableStateOf(false) }
    
    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    val tapSlopPx = with(density) { 10.dp.toPx() }
    val artworkDescription = remember(track?.id, track?.title, track?.artist) {
        val title = track?.title?.ifBlank { "Unknown title" } ?: "Unknown title"
        val artist = track?.artist?.ifBlank { "Unknown artist" } ?: "Unknown artist"
        "Album art for $title by $artist"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics {
                contentDescription = "Cover mini player. Tap to play or pause. Swipe left for next, right for previous, up for queue."
            }
            .coverScreenGestures(
                swipeThresholdPx = swipeThresholdPx,
                tapSlopPx = tapSlopPx,
                onTap = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    playerViewModel.togglePlayPause()
                },
                onSwipeLeft = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    playerViewModel.skipToNext()
                },
                onSwipeRight = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    playerViewModel.skipToPrevious()
                },
                onSwipeUp = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    queueVisible = true
                }
            )
    ) {
        // BACKGROUND BLUR
        if (track != null) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            TigerSurfaceCharcoal.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        if (windowState.hasSeparatingHinge) {
            // FLEX MODE: Hinge-Aware Layout
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Half: Artwork & Visuals (Viewing Zone)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = track?.artworkUri,
                        contentDescription = artworkDescription,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize(0.85f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                // Keep a safe spacer around hinge split displays.
                Spacer(modifier = Modifier.height((windowState.heightDp * 0.04f).coerceIn(16f, 28f).dp))

                // Bottom Half: Info & Safe Controls (Touch Zone)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TrackInfoText(
                        track = track,
                        subtitle = "FLEX MODE ACTIVE",
                        lyrics = uiState.currentLyrics,
                        currentPosition = uiState.currentPosition,
                        isLarge = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    CoverAccessibleControls(
                        isPlaying = uiState.isPlaying,
                        onTogglePlayPause = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            playerViewModel.togglePlayPause()
                        },
                        onPrevious = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            playerViewModel.skipToPrevious()
                        },
                        onNext = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            playerViewModel.skipToNext()
                        },
                        onOpenQueue = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            queueVisible = true
                        }
                    )
                }
            }
        } else {
            // COVER SCREEN MODE: Centered & Glanceable
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TrackInfoText(
                    track = track,
                    subtitle = if (uiState.isPlaying) "NOW PLAYING" else "PAUSED",
                    lyrics = uiState.currentLyrics,
                    currentPosition = uiState.currentPosition,
                    isLarge = false
                )
                Spacer(modifier = Modifier.height(10.dp))
                CoverAccessibleControls(
                    isPlaying = uiState.isPlaying,
                    onTogglePlayPause = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        playerViewModel.togglePlayPause()
                    },
                    onPrevious = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        playerViewModel.skipToPrevious()
                    },
                    onNext = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        playerViewModel.skipToNext()
                    },
                    onOpenQueue = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        queueVisible = true
                    }
                )
            }
        }

        // WAVEFORM (Visual Anchor)
        if (!queueVisible) {
            CoverMicroWaveform(
                amplitudes = uiState.currentWaveform,
                bass = uiState.audioReactiveFrame.bass,
                energy = uiState.audioReactiveFrame.energy,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(if (windowState.hasSeparatingHinge) 16.dp else 12.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        // OPTIMIZED COVER QUEUE (Direct Overlay)
        AnimatedVisibility(
            visible = queueVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { queueVisible = false })
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .glassEffect(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .padding(top = 12.dp)
                ) {
                    // Drag Handle Visual
                    Box(
                        modifier = Modifier
                            .size(36.dp, 4.dp)
                            .background(Color.White.copy(alpha = 0.3f), CircleShape)
                            .align(Alignment.CenterHorizontally)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { queueVisible = false },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close queue",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    CoverQueueSheet(
                        queue = uiState.queue,
                        currentId = track?.id,
                        onTrackTapped = {
                            playerViewModel.playTrack(it)
                            queueVisible = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackInfoText(
    track: com.example.tigerplayer.data.model.AudioTrack?,
    subtitle: String,
    lyrics: String?,
    currentPosition: Long,
    isLarge: Boolean
) {
    Spacer(modifier = Modifier.height(if (isLarge) 12.dp else 8.dp))
    
    Text(
        text = track?.title ?: "TIGER PLAYER",
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = if (isLarge) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black
    )
    
    Text(
        text = track?.artist?.uppercase() ?: "WITCHER ARCHIVE",
        color = TigerNeonOrange,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )

    Spacer(modifier = Modifier.height(if (isLarge) 12.dp else 8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth(if (isLarge) 0.82f else 0.94f)
            .height(if (isLarge) 180.dp else 130.dp),
        contentAlignment = Alignment.Center
    ) {


        LyricsDisplay(
            lyrics = lyrics,
            currentPosition = currentPosition,
            textColor = Color.White,
            activeColor = TigerNeonOrange
        )
    }
    Spacer(modifier = Modifier.height(if (isLarge) 12.dp else 8.dp))

    Text(
        text = subtitle,
        color = Color.White.copy(alpha = 0.4f),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.sp
    )
}

@Composable
private fun CoverAccessibleControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverControlButton(
            icon = Icons.Filled.SkipPrevious,
            description = "Previous track",
            onClick = onPrevious
        )
        CoverControlButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            description = if (isPlaying) "Pause" else "Play",
            onClick = onTogglePlayPause
        )
        CoverControlButton(
            icon = Icons.Filled.SkipNext,
            description = "Next track",
            onClick = onNext
        )
        CoverControlButton(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            description = "Open queue",
            onClick = onOpenQueue
        )
    }
}

@Composable
private fun CoverControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(48.dp)
            .background(Color.White.copy(alpha = 0.12f), CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
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
            .heightIn(min = 160.dp, max = 260.dp)
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

        val queueListState = rememberLazyListState()

        LazyColumn(
            state = queueListState,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(upcoming, key = { it.id }) { track ->
                val isCurrent = track.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isCurrent) TigerNeonOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp)
                        )
                        .minimumInteractiveComponentSize()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Play ${track.title}"
                        ) { onTrackTapped(track) }
                        .semantics {
                            contentDescription = "Queue item ${track.title} by ${track.artist}"
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = track.artist,
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.labelSmall,
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
                val oscillation = sin(phase + index * 0.42f)
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
    onSwipeUp: () -> Unit
): Modifier {
    return pointerInput(swipeThresholdPx, tapSlopPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            // FIX: System Edge Exclusion Zone (ignore outer 12% of the screen)
            val edgeX = size.width * 0.12f
            val edgeY = size.height * 0.12f
            if (down.position.x < edgeX || down.position.x > size.width - edgeX ||
                down.position.y < edgeY || down.position.y > size.height - edgeY) {
                return@awaitEachGesture // Let Samsung's OS handle the edge swipe
            }

            var pointerId: PointerId = down.id
            var totalX = 0f
            var totalY = 0f

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }
                    ?: event.changes.firstOrNull()
                    ?: break

                if (!change.pressed) break

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
