package com.tigerplayer.ui.coverscreen.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.cloud.DeezerDiscoveryViewModel
import com.tigerplayer.ui.theme.SpotifyGreen
import com.tigerplayer.ui.theme.WitcherIcons

/**
 * Cover-screen-optimized Deezer Free API discovery view with 30-second preview playback.
 */
@Composable
fun CoverScreenDiscoverView(
    modifier: Modifier = Modifier,
    viewModel: DeezerDiscoveryViewModel = hiltViewModel()
) {
    val chartTracks by viewModel.chartTracks.collectAsState()
    val isLoading by viewModel.isLoadingCharts.collectAsState()
    val previewTrack by viewModel.previewTrack.collectAsState()
    val isPreviewPlaying by viewModel.isPreviewPlaying.collectAsState()
    val remainingSeconds by viewModel.previewRemainingSeconds.collectAsState()

    CoverScreenDiscoverContent(
        modifier = modifier,
        tracks = chartTracks,
        isLoading = isLoading,
        previewTrack = previewTrack,
        isPreviewPlaying = isPreviewPlaying,
        remainingSeconds = remainingSeconds,
        onTrackClick = viewModel::playPreview,
        onTogglePlayPause = viewModel::togglePreviewPlayPause
    )
}

@Composable
fun CoverScreenDiscoverContent(
    tracks: List<AudioTrack>,
    isLoading: Boolean,
    previewTrack: AudioTrack?,
    isPreviewPlaying: Boolean,
    remainingSeconds: Int,
    onTrackClick: (AudioTrack) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading && tracks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = SpotifyGreen)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    val isActive = previewTrack?.id == track.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onTrackClick(track) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.artworkUri,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isActive) SpotifyGreen else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = if (isActive && isPreviewPlaying) WitcherIcons.Pause else WitcherIcons.Play,
                            contentDescription = "Preview",
                            tint = if (isActive) SpotifyGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            previewTrack?.let { active ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = active.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Preview 0:${remainingSeconds.toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpotifyGreen
                            )
                        }
                        IconButton(onClick = onTogglePlayPause) {
                            Icon(
                                imageVector = if (isPreviewPlaying) WitcherIcons.Pause else WitcherIcons.Play,
                                contentDescription = if (isPreviewPlaying) "Pause" else "Play",
                                tint = SpotifyGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CoverScreenDiscoverPreview() {
    val track = AudioTrack(
        id = "deezer:1",
        title = "Midnight City",
        artist = "M83",
        album = "Hurry Up, We're Dreaming",
        durationMs = 243_000L,
        artworkUri = null
    )
    CoverScreenDiscoverContent(
        tracks = listOf(track),
        isLoading = false,
        previewTrack = track,
        isPreviewPlaying = true,
        remainingSeconds = 25,
        onTrackClick = {},
        onTogglePlayPause = {}
    )
}
