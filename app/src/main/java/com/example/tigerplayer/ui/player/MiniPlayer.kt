package com.example.tigerplayer.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick

// --- Thematic Witcher Colors ---
private val IgniRed = Color(0xFFF11F1A)
private val AardBlue = Color(0xFF4FC3F7)

@Composable
fun MiniPlayer(
    track: AudioTrack,
    isPlaying: Boolean,
    progress: Float = 0f,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onExpandClick: () -> Unit
) {
    val actionTint by animateColorAsState(
        targetValue = if (isPlaying) AardBlue else IgniRed,
        animationSpec = tween(500),
        label = "MiniPlayerActionColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .bounceClick { onExpandClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = track,
                transitionSpec = {
                    (slideInHorizontally { width -> width } + fadeIn(tween(400))) togetherWith
                            (slideOutHorizontally { width -> -width } + fadeOut(tween(400)))
                },
                label = "MiniAlbumArtAnimation"
            ) { animatedTrack ->
                AsyncImage(
                    model = animatedTrack.artworkUri.takeIf { it != android.net.Uri.EMPTY },
                    contentDescription = "Album Art",
                    fallback = painterResource(WitcherIcons.DefaultAlbumArt),
                    error = painterResource(WitcherIcons.DefaultAlbumArt),
                    placeholder = painterResource(WitcherIcons.DefaultAlbumArt),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surface)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

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

            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(actionTint.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = if (isPlaying) WitcherIcons.Pause else WitcherIcons.Play,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = actionTint
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onNextClick) {
                Icon(
                    imageVector = WitcherIcons.Next,
                    contentDescription = "Skip Next",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter),
            color = actionTint,
            trackColor = Color.Transparent,
        )
    }
}