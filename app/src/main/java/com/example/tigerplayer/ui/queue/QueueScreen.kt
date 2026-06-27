package com.example.tigerplayer.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick

@Composable
fun QueueScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val queue = uiState.queue
    val currentTrackId = uiState.currentTrack?.id

    if (queue.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Queue is empty",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val currentIndex = queue.indexOfFirst { it.id == currentTrackId }
        .takeIf { it >= 0 }
        ?: 0
    val nowPlaying = queue.getOrNull(currentIndex) ?: uiState.currentTrack
    val upcoming = queue.drop((currentIndex + 1).coerceAtMost(queue.size))

    var localUpcoming by remember(upcoming) { mutableStateOf(upcoming) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragCurrentIndex by remember { mutableIntStateOf(-1) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(upcoming) {
        if (dragStartIndex == -1) {
            localUpcoming = upcoming
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        Text(
            text = "Queue",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(12.dp))

        nowPlaying?.let {
            NowPlayingHeader(
                track = it,
                isPlaying = uiState.isPlaying,
                onClick = {
                    viewModel.setPlaylistAndPlay(queue, currentIndex)
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(8.dp))

        if (localUpcoming.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No upcoming tracks",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                itemsIndexed(localUpcoming, key = { _, track -> track.id }) { index, track ->
                    val isDragging = dragCurrentIndex == index && dragStartIndex != -1
                    QueueRow(
                        track = track,
                        isDragging = isDragging,
                        onClick = {
                            val absoluteIndex = currentIndex + 1 + index
                            viewModel.setPlaylistAndPlay(queue, absoluteIndex)
                        },
                        onRemove = { viewModel.removeFromQueue(track) },
                        modifier = Modifier.pointerInput(localUpcoming) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragStartIndex = index
                                    dragCurrentIndex = index
                                    draggedDistance = 0f
                                },
                                onDragEnd = {
                                    if (dragStartIndex != -1 && dragCurrentIndex != -1 && dragStartIndex != dragCurrentIndex) {
                                        val from = currentIndex + 1 + dragStartIndex
                                        val to = currentIndex + 1 + dragCurrentIndex
                                        viewModel.moveQueueItem(from, to)
                                    }
                                    dragStartIndex = -1
                                    dragCurrentIndex = -1
                                    draggedDistance = 0f
                                },
                                onDragCancel = {
                                    localUpcoming = upcoming
                                    dragStartIndex = -1
                                    dragCurrentIndex = -1
                                    draggedDistance = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    draggedDistance += dragAmount.y
                                    val itemHeight = 72.dp.toPx()
                                    val offsetSteps = (draggedDistance / itemHeight).toInt()

                                    if (offsetSteps != 0) {
                                        val targetIndex = (dragCurrentIndex + offsetSteps)
                                            .coerceIn(0, localUpcoming.lastIndex)
                                        if (targetIndex != dragCurrentIndex) {
                                            val mutable = localUpcoming.toMutableList()
                                            val movedItem = mutable.removeAt(dragCurrentIndex)
                                            mutable.add(targetIndex, movedItem)
                                            localUpcoming = mutable
                                            dragCurrentIndex = targetIndex
                                            draggedDistance -= offsetSteps * itemHeight
                                        }
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingHeader(
    track: AudioTrack,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (isPlaying) WitcherIcons.VolumeUp else WitcherIcons.Play,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun QueueRow(
    track: AudioTrack,
    isDragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isDragging) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = WitcherIcons.Menu,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = WitcherIcons.Close,
                    contentDescription = "Remove from queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

