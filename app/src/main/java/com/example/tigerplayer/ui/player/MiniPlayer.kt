package com.example.tigerplayer.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import com.example.tigerplayer.ui.theme.bounceClick

// --- Thematic Witcher Colors ---
private val IgniRed = Color(0xFFF11F1A)
private val AardBlue = Color(0xFF4FC3F7)

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

    // Dynamic color for playback state (Igni/Aard)
    val actionColor by animateColorAsState(
        targetValue = if (isPlaying) IgniRed else AardBlue,
        animationSpec = tween(500),
        label = "MiniPlayerActionColor"
    )

    // The entire MiniPlayer is now transparent to blend into the MainScreen's Glass Bar
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onExpandClick() }
        // No background here — let the Glass through!
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- ALBUM ART ---
                AnimatedContent(
                    targetState = track.artworkUri,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "MiniArt"
                ) { uri ->
                    AsyncImage(
                        model = uri.takeIf { it != android.net.Uri.EMPTY },
                        contentDescription = null,
                        fallback = painterResource(R.drawable.ic_tiger_logo),
                        error = painterResource(R.drawable.ic_tiger_logo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // --- TRACK INFO (Visibility Optimized) ---
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        // Using 'onSurface' instead of 'onSurfaceVariant' for max contrast on glass
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
            // Sitting at the very bottom of the MiniPlayer section
            LinearProgressIndicator(
                progress = { actualProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp), // Slightly thicker for visibility
                color = actionColor,
                trackColor = actionColor.copy(alpha = 0.1f),
            )
        }
    }
}