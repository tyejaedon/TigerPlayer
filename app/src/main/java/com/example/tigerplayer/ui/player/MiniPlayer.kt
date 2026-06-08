@file:SuppressLint("NewApi")
package com.example.tigerplayer.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.igniRed
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * ⚡️ HIGH-PERFORMANCE INTERACTIVE MINI PLAYER
 * Features:
 * 1. Swipe Left/Right to skip tracks with carousel-carousel transition animations.
 * 2. Double Tap to Favorite with an on-screen flying heart animation.
 * 3. Lossless Hi-Res badge indication based on mimeType.
 * 4. Micro-thin glowing neon thread.
 * 5. Tap-to-Expand to FullPlayerScreen.
 * 6. Smooth visibility transitions based on loaded track state.
 */
@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val track = uiState.currentTrack
    val isPlaying = uiState.isPlaying

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Drag and slide physics for horizontal skipping
    val offsetX = remember { Animatable(0f) }
    val dragThreshold = with(density) { 120.dp.toPx() }

    // Double-tap flying heart animation triggers
    var showFlyingHeart by remember { mutableStateOf(false) }
    val heartAlpha = remember { Animatable(0f) }
    val heartScale = remember { Animatable(0f) }
    val heartOffsetY = remember { Animatable(0f) }

    // --- VISIBILITY GATEWAY (Smooth transitions on mount/unmount) ---
    AnimatedVisibility(
        visible = track != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        if (track != null) {
            val duration = track.durationMs.coerceAtLeast(1L)
            val progressValue = (uiState.currentPosition.toFloat() / duration)
            val actualProgress = if (progressValue.isNaN()) 0f else progressValue.coerceIn(0f, 1f)

            val animatedProgress by animateFloatAsState(
                targetValue = actualProgress,
                animationSpec = tween(500, easing = LinearEasing),
                label = "MiniProgress"
            )

            val actionColor by animateColorAsState(
                targetValue = if (isPlaying) MaterialTheme.igniRed else MaterialTheme.aardBlue,
                animationSpec = tween(500),
                label = "MiniPlayerActionColor"
            )

            val glassBg = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = actionColor.copy(alpha = 0.3f))
                    .clip(RoundedCornerShape(24.dp))
                    .glassEffect(RoundedCornerShape(24.dp))
                    .background(glassBg)
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .pointerInput(track.id) {
                        detectTapGestures(
                            onTap = { onExpandClick() },
                            onDoubleTap = {
                                viewModel.toggleTrackLikeStatus(track)
                                coroutineScope.launch {
                                    showFlyingHeart = true
                                    heartAlpha.snapTo(1f)
                                    heartScale.snapTo(0.3f)
                                    heartOffsetY.snapTo(0f)

                                    launch { heartScale.animateTo(1.2f, spring(dampingRatio = 0.6f)) }
                                    launch { heartOffsetY.animateTo(-80f, tween(600, easing = FastOutSlowInEasing)) }

                                    heartAlpha.animateTo(0f, tween(600, 200))
                                    showFlyingHeart = false
                                }
                            }
                        )
                    }
                    .pointerInput(track.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (offsetX.value > dragThreshold) {
                                        offsetX.animateTo(size.width.toFloat(), spring(stiffness = Spring.StiffnessMedium))
                                        viewModel.skipToPrevious()
                                        offsetX.snapTo(-size.width.toFloat())
                                        offsetX.animateTo(0f, spring(dampingRatio = 0.8f))
                                    } else if (offsetX.value < -dragThreshold) {
                                        offsetX.animateTo(-size.width.toFloat(), spring(stiffness = Spring.StiffnessMedium))
                                        viewModel.skipToNext()
                                        offsetX.snapTo(size.width.toFloat())
                                        offsetX.animateTo(0f, spring(dampingRatio = 0.8f))
                                    } else {
                                        offsetX.animateTo(0f, spring(dampingRatio = 0.7f))
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch { offsetX.animateTo(0f, spring()) }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val dragFriction = 0.7f
                                    offsetX.snapTo(offsetX.value + dragAmount * dragFriction)
                                }
                            }
                        )
                    }
            ) {
                // --- 1. NEON THREAD PROGRESS LINE ---
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.TopCenter)
                ) {
                    drawLine(
                        color = actionColor.copy(alpha = 0.08f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = size.height
                    )
                    drawLine(
                        color = actionColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width * animatedProgress, 0f),
                        strokeWidth = size.height,
                        cap = StrokeCap.Round
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // --- 2. CHRONICLE ARTWORK CONTAINER ---
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = track.artworkUri.toString().takeIf { it.isNotBlank() },
                            contentDescription = null,
                            fallback = painterResource(R.drawable.ic_tiger_logo),
                            error = painterResource(R.drawable.ic_tiger_logo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (showFlyingHeart) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.igniRed,
                                modifier = Modifier
                                    .offset { IntOffset(0, heartOffsetY.value.roundToInt()) }
                                    .scale(heartScale.value)
                                    .alpha(heartAlpha.value)
                                    .size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // --- 3. METADATA & LOSSLESS FORMAT ROW ---
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                letterSpacing = (-0.3).sp,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            if (track.mimeType.contains("flac", ignoreCase = true)) {
                                Box(
                                    modifier = Modifier
                                        .background(actionColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, actionColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "FLAC",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = actionColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = track.artist.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // --- 4. OPTIMIZED CONTROLS ---
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleTrackLikeStatus(track) },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = "Favorite",
                                tint = if (track.isLiked) MaterialTheme.igniRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .bounceClick { viewModel.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = actionColor.copy(alpha = 0.12f),
                                        shape = CircleShape
                                    )
                            )
                            Icon(
                                imageVector = if (isPlaying) WitcherIcons.Pause else WitcherIcons.Play,
                                contentDescription = "Toggle Ritual",
                                tint = if (isPlaying) MaterialTheme.igniRed else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}