package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tigerplayer.R
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.theme.WitcherIcons
import com.tigerplayer.ui.theme.aardBlue
import com.tigerplayer.ui.theme.glassEffect

@Composable
internal fun SongPicker(
    tracks: List<AudioTrack>,
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    onPlayTrack: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyLibraryMessage(stringResource(R.string.coverscreen_empty_songs))
    } else {
        var revealedTrackId by remember { mutableStateOf<String?>(null) }
        var optionsTrack by remember { mutableStateOf<AudioTrack?>(null) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(CoverScreenTestTags.SONGS_LIST),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(tracks, key = AudioTrack::id) { track ->
                val isCurrent = currentTrackId != null && track.id == currentTrackId
                SongTrackRow(
                    track = track,
                    isCurrentTrack = isCurrent,
                    isPlaying = isPlaying && isCurrent,
                    isQueueRevealed = revealedTrackId == track.id,
                    isOptionsExpanded = optionsTrack?.id == track.id,
                    onPlay = { onPlayTrack(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onRevealQueue = { revealedTrackId = track.id },
                    onDismissQueue = { revealedTrackId = null },
                    onShowOptions = { optionsTrack = track },
                    onDismissOptions = { optionsTrack = null }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun SongTrackRow(
    track: AudioTrack,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    isQueueRevealed: Boolean,
    isOptionsExpanded: Boolean,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onRevealQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onShowOptions: () -> Unit,
    onDismissOptions: () -> Unit
) {
    var horizontalDrag by remember(track.id) { mutableStateOf(0f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                    onDragEnd = {
                        if (horizontalDrag < -64.dp.toPx()) onRevealQueue()
                        if (horizontalDrag > 64.dp.toPx()) onDismissQueue()
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(14.dp))
                .glassEffect(RoundedCornerShape(14.dp))
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.coverscreen_action_play_now),
                    onLongClickLabel = stringResource(R.string.coverscreen_action_add_to_queue),
                    onClick = onPlay,
                    onLongClick = {
                        onRevealQueue()
                        onShowOptions()
                    }
                )
                .testTag(CoverScreenTestTags.songTrack(track.id))
                .semantics {
                    contentDescription =
                        "${track.title}, ${track.artist}, ${formatTrackDuration(track.durationMs)}"
                },
            color = if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.12f)
            else Color.Black.copy(alpha = 0.28f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.45f)
                else Color.White.copy(alpha = 0.08f)
            )
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
                        .background(
                            if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.20f)
                            else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (track.artworkUri.toString().isBlank()) {
                        Icon(
                            imageVector = WitcherIcons.AudioTrack,
                            contentDescription = null,
                            tint = if (isCurrentTrack) MaterialTheme.aardBlue
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        AsyncImage(
                            model = track.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            fallback = painterResource(R.drawable.ic_tiger_logo),
                            error = painterResource(R.drawable.ic_tiger_logo),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (isCurrentTrack && isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = WitcherIcons.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.aardBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentTrack) MaterialTheme.aardBlue
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatTrackDuration(track.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play ${track.title}",
                    tint = if (isCurrentTrack) MaterialTheme.aardBlue
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val addToQueueLabel = stringResource(R.string.coverscreen_action_add_to_queue)
        if (isQueueRevealed) {
            TextButton(
                onClick = {
                    onAddToQueue()
                    onDismissQueue()
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .minimumInteractiveComponentSize()
                    .semantics {
                        role = Role.Button
                        onClick(label = addToQueueLabel) {
                            onAddToQueue()
                            onDismissQueue()
                            true
                        }
                    }
                    .testTag(CoverScreenTestTags.songQueue(track.id))
            ) {
                Text(addToQueueLabel)
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
            }
        }
    }
}

internal fun filterAndSortTracks(
    tracks: List<AudioTrack>,
    query: String = ""
): List<AudioTrack> {
    val trimmed = query.trim()
    val filtered = if (trimmed.isBlank()) {
        tracks
    } else {
        tracks.filter { track ->
            track.title.contains(trimmed, ignoreCase = true) ||
                track.artist.contains(trimmed, ignoreCase = true)
        }
    }
    return filtered.sortedBy { it.title.lowercase() }
}
