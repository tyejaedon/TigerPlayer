package com.example.tigerplayer.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    // --- PROGRESS LOGIC (Smoothed) ---
    val duration = track.durationMs.coerceAtLeast(1L)
    val progressValue = (uiState.currentPosition.toFloat() / duration)
    val actualProgress = if (progressValue.isNaN()) 0f else progressValue.coerceIn(0f, 1f)

    // THE FIX: Animate the progress bar so it glides at 120Hz instead of ticking
    val animatedProgress by animateFloatAsState(
        targetValue = actualProgress,
        animationSpec = tween(500),
        label = "MiniProgress"
    )

    // Adaptive action colors for Light/Dark mode safety
    val actionColor by animateColorAsState(
        targetValue = if (isPlaying) MaterialTheme.igniRed else MaterialTheme.aardBlue,
        animationSpec = tween(500),
        label = "MiniPlayerActionColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // THE FIX: Swapped bounceClick for standard clickable.
            // A bounce on the MiniPlayer clashes visually with an upward slide animation.
            .clickable { onExpandClick() }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- ALBUM ART ---
                AsyncImage(
                    model = track.artworkUri.takeIf { it != android.net.Uri.EMPTY },
                    contentDescription = null,
                    fallback = painterResource(R.drawable.ic_tiger_logo),
                    error = painterResource(R.drawable.ic_tiger_logo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                // --- TRACK INFO ---
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // --- CONTROLS ---
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(actionColor.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) WitcherIcons.Pause else WitcherIcons.Play,
                        contentDescription = "Play/Pause",
                        tint = actionColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(
                        imageVector = WitcherIcons.Next,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            // --- SEAMLESS PROGRESS BAR ---
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = actionColor,
                trackColor = actionColor.copy(alpha = 0.1f),
            )
        }
    }
}