package com.example.tigerplayer.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.R
// --- Thematic Witcher Colors ---
private val IgniRed = Color(0xFFF11F1A)
private val AardBlue = Color(0xFF4FC3F7)

@Composable
fun MiniPlayer(
    track: AudioTrack,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onExpandClick: () -> Unit
) {
    // Smoothly transition the color based on the playing state
    val actionTint by animateColorAsState(
        targetValue = if (isPlaying) AardBlue else IgniRed,
        animationSpec = tween(500),
        label = "MiniPlayerActionColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium) // Sharp Witcher cuts!
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .bounceClick { onExpandClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Animated Album Art
        AnimatedContent(
            targetState = track,
            transitionSpec = {
                (slideInHorizontally { width -> width } + fadeIn(tween(400))) togetherWith
                        (slideOutHorizontally { width -> -width } + fadeOut(tween(400)))
            },
            label = "MiniAlbumArtAnimation"
        ) { animatedTrack ->
            // Inside MiniPlayer.kt, replace AsyncImage with this logic:
            AsyncImage(
                model = if (animatedTrack.artworkUri == android.net.Uri.EMPTY) {
                    R.drawable.ic_launcher_tiger
                } else
                    animatedTrack.artworkUri,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface) // Placeholder background
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Animated Title and Artist
        AnimatedContent(
            targetState = track,
            transitionSpec = {
                (slideInHorizontally { width -> width } + fadeIn(tween(400))) togetherWith
                        (slideOutHorizontally { width -> -width } + fadeOut(tween(400)))
            },
            label = "MiniTrackInfoAnimation",
            modifier = Modifier.weight(1f)
        ) { animatedTrack ->
            Column {
                Text(
                    text = animatedTrack.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = animatedTrack.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Play/Pause Button with Igni/Aard Magic
        IconButton(
            onClick = onPlayPauseClick,
            // Adds a subtle colored aura matching the tint
            modifier = Modifier
                .clip(CircleShape)
                .background(actionTint.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = if (isPlaying) WitcherIcons.Pause else WitcherIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = actionTint // The dynamic color applied here
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Next Button
        IconButton(onClick = onNextClick) {
            Icon(
                imageVector = WitcherIcons.Next,
                contentDescription = "Skip Next",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}