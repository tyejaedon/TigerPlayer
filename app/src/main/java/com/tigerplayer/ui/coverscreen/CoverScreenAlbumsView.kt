package com.tigerplayer.ui.coverscreen

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tigerplayer.R
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.theme.WitcherIcons
import com.tigerplayer.ui.theme.aardBlue
import com.tigerplayer.ui.theme.glassEffect
import com.tigerplayer.ui.theme.rememberTigerAmbientGradient

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
    },
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    onShufflePlay: (List<AudioTrack>) -> Unit = {
        if (it.isNotEmpty()) {
            val shuffled = it.shuffled()
            onPlayTrack(shuffled.first())
            shuffled.drop(1).forEach(onAddToQueue)
        }
    }
) {
    val view = LocalView.current
    var revealedTrackId by remember { mutableStateOf<String?>(null) }
    var optionsTrack by remember { mutableStateOf<AudioTrack?>(null) }

    val ambientBrush = rememberTigerAmbientGradient(
        dominantColor = MaterialTheme.aardBlue,
        baseTopAlpha = 0.22f
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ambientBrush)
            .pointerInput(album.id) {
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
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP BAR NAVIGATION
            val backToAlbumsText = stringResource(R.string.coverscreen_action_back_to_albums)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
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
                            contentDescription = backToAlbumsText
                        }
                        .testTag(CoverScreenTestTags.ALBUM_BACK)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backToAlbumsText,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = album.name.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp, end = 12.dp)
                )
            }

            // 2. SCROLLABLE ALBUM HERO & TRACK LIST
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(CoverScreenTestTags.ALBUM_TRACKS),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 3D FLOATING HERO ARTWORK
                item(key = "album_hero_artwork") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient Backdrop Glow
                        Box(
                            modifier = Modifier
                                .size(136.dp)
                                .shadow(
                                    elevation = 20.dp,
                                    shape = RoundedCornerShape(26.dp),
                                    spotColor = MaterialTheme.aardBlue.copy(alpha = 0.45f),
                                    ambientColor = Color.Black
                                )
                                .background(
                                    MaterialTheme.aardBlue.copy(alpha = 0.20f),
                                    RoundedCornerShape(26.dp)
                                )
                        )

                        // 3D Framed Glass Cover
                        Box(
                            modifier = Modifier
                                .size(144.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .border(
                                    width = 1.2.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.45f),
                                            MaterialTheme.aardBlue.copy(alpha = 0.25f),
                                            Color.White.copy(alpha = 0.10f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = album.artworkUrl,
                                contentDescription = stringResource(
                                    R.string.coverscreen_artwork_album_description,
                                    album.name
                                ),
                                contentScale = ContentScale.Crop,
                                fallback = painterResource(R.drawable.ic_tiger_logo),
                                error = painterResource(R.drawable.ic_tiger_logo),
                                modifier = Modifier.fillMaxSize()
                            )

                            // Specular Light Streak
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.22f),
                                                Color.White.copy(alpha = 0.04f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }

                // VANGUARD DOSSIER CARD (Album metadata & actions)
                item(key = "album_dossier") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        border = BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.20f), Color.Transparent)
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = (if (artistName.isNotBlank()) artistName else "UNKNOWN ARTIST").uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.aardBlue,
                                letterSpacing = 1.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AlbumDetailBadge(
                                    label = "CHANTS",
                                    value = tracks.size.toString()
                                )
                                AlbumDetailBadge(
                                    label = "DURATION",
                                    value = formatTotalDuration(tracks.sumOf { it.durationMs })
                                )
                                tracks.firstOrNull()?.year?.takeIf { it.isNotBlank() }?.let { year ->
                                    AlbumDetailBadge(
                                        label = "RELEASED",
                                        value = year
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Play All & Shuffle Dock
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val playAllLabel = stringResource(R.string.coverscreen_action_play_all)
                                Button(
                                    onClick = {
                                        if (tracks.isNotEmpty()) {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            onPlayAll(tracks)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
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
                                        .testTag(CoverScreenTestTags.PLAY_ALL),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.aardBlue,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(
                                        imageVector = WitcherIcons.Play,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = playAllLabel.uppercase(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        letterSpacing = 1.sp
                                    )
                                }

                                // Shuffle Glass Button
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .glassEffect(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = "Shuffle",
                                            onClick = {
                                                if (tracks.isNotEmpty()) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                    onShufflePlay(tracks)
                                                }
                                            }
                                        )
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = "Shuffle Album"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = WitcherIcons.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // TRACK ROWS
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    val isCurrent = currentTrackId != null && track.id == currentTrackId
                    TrackRow(
                        index = index,
                        track = track,
                        artistName = artistName,
                        isCurrentTrack = isCurrent,
                        isPlaying = isPlaying && isCurrent,
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
                        onRevealRemove = { revealedTrackId = track.id },
                        onDismissRemove = { revealedTrackId = null },
                        onShowOptions = { optionsTrack = track },
                        onDismissOptions = { optionsTrack = null }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrackRow(
    index: Int,
    track: AudioTrack,
    artistName: String,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
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
            .padding(horizontal = 12.dp, vertical = 3.dp)
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
                .background(
                    if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.12f)
                    else Color.Black.copy(alpha = 0.28f)
                )
                .border(
                    BorderStroke(
                        1.dp,
                        if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.45f)
                        else Color.White.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
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
                // Index pill or active playing icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.20f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrentTrack && isPlaying) {
                        Icon(
                            imageVector = WitcherIcons.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.aardBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "%02d".format(index + 1),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = if (isCurrentTrack) MaterialTheme.aardBlue
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun AlbumDetailBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.45f),
        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatTotalDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

internal fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}