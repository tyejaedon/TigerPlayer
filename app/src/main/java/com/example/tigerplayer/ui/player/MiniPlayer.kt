package com.example.tigerplayer.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.igniRed

@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    onExpandClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val track = uiState.currentTrack ?: return
    val isPlaying = uiState.isPlaying

    // --- PROGRESS LOGIC ---
    val duration = track.durationMs.coerceAtLeast(1L)
    val progressValue = (uiState.currentPosition.toFloat() / duration)
    val actualProgress = if (progressValue.isNaN()) 0f else progressValue.coerceIn(0f, 1f)

    // LinearEasing ensures the bar glides seamlessly without stuttering between updates
    val animatedProgress by animateFloatAsState(
        targetValue = actualProgress,
        animationSpec = tween(500, easing = LinearEasing),
        label = "MiniProgress"
    )

    // Adaptive action colors for Light/Dark mode safety
    val actionColor by animateColorAsState(
        targetValue = if (isPlaying) MaterialTheme.igniRed else MaterialTheme.aardBlue, // Assuming globally accessible
        animationSpec = tween(500),
        label = "MiniPlayerActionColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandClick() }
            // 1. NEON BLEED: A subtle gradient wash that ties the player to the active color
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        actionColor.copy(alpha = 0.05f)
                    )
                )
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp), // Slightly tightened vertical padding
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- 2. GLASS ARTWORK ARMOR ---
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        // Casts a faint, colored glow behind the album art
                        .shadow(8.dp, RoundedCornerShape(10.dp), spotColor = actionColor.copy(alpha = 0.4f))
                        .clip(RoundedCornerShape(10.dp))
                        // The specular border highlights the edge of the "glass"
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    AsyncImage(
                        model = track.artworkUri.takeIf { it != android.net.Uri.EMPTY },
                        contentDescription = null,
                        fallback = painterResource(R.drawable.ic_tiger_logo),
                        error = painterResource(R.drawable.ic_tiger_logo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isPlaying) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // --- TRACK INFO ---
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium, // Bumped from Small to Medium for presence
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist.uppercase(), // Uppercase for the Vanguard aesthetic
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // --- 3. THE GLASS ORB PLAY BUTTON ---
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(actionColor.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, actionColor.copy(alpha = 0.3f), CircleShape) // Crisp ring
                ) {
                    Icon(
                        imageVector = if (isPlaying) WitcherIcons.Pause else WitcherIcons.Play,
                        contentDescription = "Play/Pause",
                        tint = actionColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { viewModel.skipToNext() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = WitcherIcons.Next,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // --- 4. THE NEON THREAD ---
            // Replaced the heavy LinearProgressIndicator with a precision Canvas line
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp) // Ultra-thin 2dp line
            ) {
                // Background Track
                drawLine(
                    color = actionColor.copy(alpha = 0.15f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = size.height
                )
                // Active Glowing Thread
                drawLine(
                    color = actionColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width * animatedProgress, 0f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}