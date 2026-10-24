package com.tigerplayer.ui.coverscreen

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.tigerplayer.ui.theme.glassEffect
import com.tigerplayer.ui.theme.TigerNeonOrange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tigerplayer.R
import com.tigerplayer.data.model.AudioTrack

@Composable
fun CoverScreenAlbumsView(
    album: CoverScreenAlbum,
    artistName: String,
    tracks: List<AudioTrack>,
    onBack: () -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onRemoveFromQueue: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier,
    onPlayAll: (List<AudioTrack>) -> Unit = {
        if (it.isNotEmpty()) {
            onPlayTrack(it.first())
            it.drop(1).forEach(onAddToQueue)
        }
    }
) {
    val view = LocalView.current
    var revealedTrackId by remember { mutableStateOf<String?>(null) }
    var optionsTrack by remember { mutableStateOf<AudioTrack?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dragDistance = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                    onDragEnd = {
                        if (dragDistance > 80.dp.toPx()) onBack()
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f }
                )
            }
    ) {
        val backToAlbumsText = stringResource(R.string.coverscreen_action_back_to_albums)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .minimumInteractiveComponentSize()
                    .semantics {
                        role = Role.Button
                        onClick(label = backToAlbumsText) {
                            onBack()
                            true
                        }
                    }
                    .testTag(CoverScreenTestTags.ALBUM_BACK)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backToAlbumsText
                )
            }
            Text(text = backToAlbumsText, style = MaterialTheme.typography.labelLarge)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(CoverScreenTestTags.ALBUM_TRACKS),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "album_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = album.artworkUrl,
                        contentDescription = stringResource(R.string.coverscreen_artwork_album_description, album.name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(148.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1
                    )
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val playAllLabel = stringResource(R.string.coverscreen_action_play_all)
                    Button(
                        onClick = {
                            if (tracks.isNotEmpty()) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onPlayAll(tracks)
                            }
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .minimumInteractiveComponentSize()
                            .semantics {
                                role = Role.Button
                                onClick(label = playAllLabel) {
                                    if (tracks.isNotEmpty()) {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        onPlayAll(tracks)
                                    }
                                    true
                                }
                            }
                            .testTag(CoverScreenTestTags.PLAY_ALL)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.coverscreen_action_play_all))
                    }
                }
            }
            items(tracks, key = AudioTrack::id) { track ->
                TrackRow(
                    track = track,
                    isRemoveRevealed = revealedTrackId == track.id,
                    isOptionsExpanded = optionsTrack?.id == track.id,
                    onPlay = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onPlayTrack(track)
                    },
                    onAddToQueue = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onAddToQueue(track)
                    },
                    onRemoveFromQueue = { onRemoveFromQueue(track) },
                    artistName = artistName,
                    onRevealRemove = { revealedTrackId = track.id },
                    onDismissRemove = { revealedTrackId = null },
                    onShowOptions = { optionsTrack = track },
                    onDismissOptions = { optionsTrack = null }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrackRow(
    track: AudioTrack,
    artistName: String,
    isRemoveRevealed: Boolean,
    isOptionsExpanded: Boolean,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    onRevealRemove: () -> Unit,
    onDismissRemove: () -> Unit,
    onShowOptions: () -> Unit,
    onDismissOptions: () -> Unit
) {
    var horizontalDrag by remember(track.id) { mutableStateOf(0f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                    onDragEnd = {
                        if (horizontalDrag < -64.dp.toPx()) onRevealRemove()
                        if (horizontalDrag > 64.dp.toPx()) onDismissRemove()
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(14.dp))
                .glassEffect(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.coverscreen_action_play_now),
                    onLongClickLabel = stringResource(R.string.coverscreen_action_remove_from_queue),
                    onClick = onPlay,
                    onLongClick = onShowOptions
                )
                .testTag(CoverScreenTestTags.track(track.id))
                .semantics {
                    contentDescription =
                        "${track.title}, $artistName, ${formatTrackDuration(track.durationMs)}"
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (track.artworkUri.toString().isBlank()) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        AsyncImage(
                            model = track.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    text = formatTrackDuration(track.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play ${track.title}"
                )
            }
        }
        val removeFromQueueLabel = stringResource(R.string.coverscreen_action_remove_from_queue)
        val addToQueueLabel = stringResource(R.string.coverscreen_action_add_to_queue)
        if (isRemoveRevealed) {
            TextButton(
                onClick = onRemoveFromQueue,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .minimumInteractiveComponentSize()
                    .semantics {
                        role = Role.Button
                        onClick(label = removeFromQueueLabel) {
                            onRemoveFromQueue()
                            true
                        }
                    }
                    .testTag(CoverScreenTestTags.removeTrack(track.id))
            ) {
                Text(stringResource(R.string.coverscreen_action_remove))
            }
        }
        Box {
            IconButton(
                onClick = onAddToQueue,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .minimumInteractiveComponentSize()
                    .size(48.dp)
                    .testTag(CoverScreenTestTags.queueTrack(track.id))
                    .semantics {
                        role = Role.Button
                        onClick(label = addToQueueLabel) {
                            onAddToQueue()
                            true
                        }
                        contentDescription = "Add ${track.title} to queue"
                    }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null
                )
            }
            DropdownMenu(
                expanded = isOptionsExpanded,
                onDismissRequest = onDismissOptions
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.coverscreen_action_play_now)) },
                    onClick = {
                        onDismissOptions()
                        onPlay()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.coverscreen_action_add_to_queue)) },
                    onClick = {
                        onDismissOptions()
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.coverscreen_action_remove_from_queue)) },
                    onClick = {
                        onDismissOptions()
                        onRemoveFromQueue()
                    }
                )
            }
        }
    }
}

internal fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
