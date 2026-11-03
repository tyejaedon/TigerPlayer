package com.tigerplayer.ui.coverscreen

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tigerplayer.R
import androidx.compose.material3.ExperimentalMaterial3Api
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.ui.theme.WitcherIcons
import com.tigerplayer.ui.theme.aardBlue
import com.tigerplayer.ui.theme.bounceClick
import com.tigerplayer.ui.theme.glassEffect
import com.tigerplayer.ui.theme.rememberTigerAmbientGradient
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverScreenPlaylistDetailView(
    playlist: Playlist,
    tracks: List<AudioTrack>,
    isLikedPlaylist: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onPlayAll: (List<AudioTrack>) -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onSaveOrder: (List<AudioTrack>) -> Unit,
    onRemoveTrack: (AudioTrack) -> Unit,
    onChangeCoverPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isEditMode by remember { mutableStateOf(false) }
    var mutableTracks by remember(tracks) { mutableStateOf(tracks) }

    LaunchedEffect(tracks) {
        if (!isEditMode) {
            mutableTracks = tracks
        }
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var trackForOptions by remember { mutableStateOf<AudioTrack?>(null) }

    // Reorder dragging state
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragCurrentIndex by remember { mutableIntStateOf(-1) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }

    val likedHeartColor = Color(0xFFE91E63)
    val accentColor = if (isLikedPlaylist) likedHeartColor else MaterialTheme.aardBlue

    val ambientBrush = rememberTigerAmbientGradient(
        accent = accentColor,
        baseTopAlpha = 0.22f
    )

    BackHandler(enabled = true) {
        when {
            showRenameDialog -> showRenameDialog = false
            showDeleteDialog -> showDeleteDialog = false
            trackForOptions != null -> trackForOptions = null
            isEditMode -> {
                onSaveOrder(mutableTracks)
                isEditMode = false
            }
            else -> onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(CoverScreenTestTags.PLAYLIST_DETAIL)
            .background(ambientBrush)
            .pointerInput(playlist.id) {
                var dragDistance = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                    onDragEnd = {
                        if (dragDistance > 80.dp.toPx() && !isEditMode) onBack()
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP BAR NAVIGATION & ACTIONS
            val backToPlaylistsText = stringResource(R.string.coverscreen_action_back_to_playlists)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(RectangleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isEditMode) {
                            onSaveOrder(mutableTracks)
                            isEditMode = false
                        }
                        onBack()
                    },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .minimumInteractiveComponentSize()
                        .semantics {
                            role = Role.Button
                            onClick(label = backToPlaylistsText) {
                                onBack()
                                true
                            }
                            contentDescription = backToPlaylistsText
                        }
                        .testTag(CoverScreenTestTags.PLAYLIST_BACK)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backToPlaylistsText,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = playlist.name.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )

                // Edit / Done toggle button
                TextButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        if (isEditMode) {
                            onSaveOrder(mutableTracks)
                        }
                        isEditMode = !isEditMode
                    },
                    modifier = Modifier.testTag(CoverScreenTestTags.PLAYLIST_EDIT_TOGGLE)
                ) {
                    Text(
                        text = if (isEditMode) stringResource(R.string.coverscreen_action_done) else stringResource(R.string.coverscreen_action_edit),
                        fontWeight = FontWeight.Black,
                        color = accentColor
                    )
                }

                // Overflow menu for non-liked playlists (Rename, Cover, Delete)
                if (!isLikedPlaylist && !isEditMode) {
                    Box {
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                showOverflowMenu = true
                            },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                .testTag(CoverScreenTestTags.PLAYLIST_OPTIONS_MENU)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Playlist Options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier.background(Color(0xFF1B1F2A))
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.coverscreen_action_rename_playlist), color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = accentColor)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showRenameDialog = true
                                },
                                modifier = Modifier.testTag(CoverScreenTestTags.PLAYLIST_RENAME)
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.coverscreen_action_edit_playlist_photo), color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Image, contentDescription = null, tint = accentColor)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onChangeCoverPhoto()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.coverscreen_action_delete_playlist), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showDeleteDialog = true
                                },
                                modifier = Modifier.testTag(CoverScreenTestTags.PLAYLIST_DELETE)
                            )
                        }
                    }
                }
            }

            // 2. SCROLLABLE PLAYLIST CONTENT
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(CoverScreenTestTags.PLAYLIST_TRACKS),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 3D FLOATING HERO ARTWORK & METADATA
                item(key = "playlist_hero_artwork") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient Backdrop Glow
                        Box(
                            modifier = Modifier
                                .size(126.dp)
                                .shadow(
                                    elevation = 20.dp,
                                    shape = RoundedCornerShape(24.dp),
                                    spotColor = accentColor.copy(alpha = 0.45f),
                                    ambientColor = Color.Black
                                )
                                .background(
                                    accentColor.copy(alpha = 0.20f),
                                    RoundedCornerShape(24.dp)
                                )
                        )

                        // Framed Artwork Container
                        Box(
                            modifier = Modifier
                                .size(134.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .border(
                                    width = 1.2.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.45f),
                                            accentColor.copy(alpha = 0.25f),
                                            Color.White.copy(alpha = 0.10f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    if (!isLikedPlaylist) {
                                        onChangeCoverPhoto()
                                    }
                                }
                        ) {
                            if (!playlist.artworkUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = playlist.artworkUri,
                                    contentDescription = playlist.name,
                                    contentScale = ContentScale.Crop,
                                    fallback = painterResource(R.drawable.ic_tiger_logo),
                                    error = painterResource(R.drawable.ic_tiger_logo),
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (isLikedPlaylist) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    likedHeartColor.copy(alpha = 0.50f),
                                                    Color.Black.copy(alpha = 0.90f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = likedHeartColor,
                                        modifier = Modifier.size(54.dp)
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = null,
                                    contentDescription = playlist.name,
                                    contentScale = ContentScale.Crop,
                                    fallback = painterResource(R.drawable.ic_tiger_logo),
                                    error = painterResource(R.drawable.ic_tiger_logo),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

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

                // PLAYLIST TITLE & INFO
                item(key = "playlist_metadata_bar") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val totalDurationMs = remember(mutableTracks) { mutableTracks.sumOf { it.durationMs } }
                        val minutes = (totalDurationMs / 60_000L).coerceAtLeast(0L)
                        Text(
                            text = "${mutableTracks.size} CHANTS • ${minutes} MIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // PLAY & SHUFFLE CONTROLS (Only visible when not reordering)
                if (!isEditMode && mutableTracks.isNotEmpty()) {
                    item(key = "playlist_action_controls") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onPlayAll(mutableTracks)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .bounceClick {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        onPlayAll(mutableTracks)
                                    }
                                    .testTag(CoverScreenTestTags.PLAYLIST_PLAY_ALL),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Play All",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onShufflePlay(mutableTracks)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .bounceClick {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        onShufflePlay(mutableTracks)
                                    }
                                    .testTag(CoverScreenTestTags.PLAYLIST_SHUFFLE),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.7f))
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.coverscreen_action_shuffle),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                if (mutableTracks.isEmpty()) {
                    item(key = "playlist_empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.coverscreen_empty_playlist_tracks),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = mutableTracks,
                        key = { _, track -> track.id }
                    ) { index, track ->
                        val latestIndex by rememberUpdatedState(index)
                        val isCurrent = track.id == currentTrackId

                        val dragModifier = if (!isEditMode || mutableTracks.size < 2) {
                            Modifier
                        } else {
                            Modifier.pointerInput(track.id, index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragStartIndex = latestIndex
                                        dragCurrentIndex = latestIndex
                                        draggedDistance = 0f
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    },
                                    onDragEnd = {
                                        if (dragStartIndex >= 0 &&
                                            dragCurrentIndex >= 0 &&
                                            dragStartIndex != dragCurrentIndex
                                        ) {
                                            onSaveOrder(mutableTracks)
                                        }
                                        dragStartIndex = -1
                                        dragCurrentIndex = -1
                                        draggedDistance = 0f
                                    },
                                    onDragCancel = {
                                        dragStartIndex = -1
                                        dragCurrentIndex = -1
                                        draggedDistance = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        draggedDistance += amount.y
                                        val rowHeightPx = 54.dp.toPx()
                                        val rowShift = (draggedDistance / rowHeightPx).toInt()
                                        if (rowShift != 0) {
                                            val newIndex = (dragCurrentIndex + rowShift)
                                                .coerceIn(0, mutableTracks.lastIndex)
                                            if (newIndex != dragCurrentIndex) {
                                                mutableTracks = mutableTracks.toMutableList().apply {
                                                    add(newIndex, removeAt(dragCurrentIndex))
                                                }
                                                dragCurrentIndex = newIndex
                                                draggedDistance -= rowShift * rowHeightPx
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        CoverScreenPlaylistTrackRow(
                            index = index,
                            track = track,
                            isCurrent = isCurrent,
                            isPlaying = isPlaying,
                            isEditMode = isEditMode,
                            accentColor = accentColor,
                            onClick = {
                                if (!isEditMode) {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onPlayTrack(track)
                                }
                            },
                            onOptionsClick = {
                                trackForOptions = track
                            },
                            onMoveUp = if (index > 0) {
                                {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    mutableTracks = mutableTracks.toMutableList().apply {
                                        add(index - 1, removeAt(index))
                                    }
                                    onSaveOrder(mutableTracks)
                                }
                            } else null,
                            onMoveDown = if (index < mutableTracks.lastIndex) {
                                {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    mutableTracks = mutableTracks.toMutableList().apply {
                                        add(index + 1, removeAt(index))
                                    }
                                    onSaveOrder(mutableTracks)
                                }
                            } else null,
                            onRemoveClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onRemoveTrack(track)
                                mutableTracks = mutableTracks.filter { it.id != track.id }
                            },
                            dragModifier = dragModifier
                        )
                    }
                }
            }
        }
    }

    // 3. DIALOGS
    if (showRenameDialog) {
        CoverScreenRenamePlaylistDialog(
            initialName = playlist.name,
            accentColor = accentColor,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                onRenamePlaylist(newName)
            }
        )
    }

    if (showDeleteDialog) {
        CoverScreenDeletePlaylistDialog(
            playlistName = playlist.name,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDeletePlaylist()
            }
        )
    }

    // Track options bottom sheet
    trackForOptions?.let { track ->
        ModalBottomSheet(
            onDismissRequest = { trackForOptions = null },
            containerColor = Color(0xFF1B1F2A)
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp, start = 16.dp, end = 16.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onAddToQueue(track)
                            trackForOptions = null
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = accentColor)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Add to Queue", color = Color.White, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onRemoveTrack(track)
                            mutableTracks = mutableTracks.filter { it.id != track.id }
                            trackForOptions = null
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                        .testTag(CoverScreenTestTags.playlistTrackRemove(track.id)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.coverscreen_action_remove_from_playlist),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverScreenPlaylistTrackRow(
    index: Int,
    track: AudioTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isEditMode: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemoveClick: () -> Unit,
    dragModifier: Modifier = Modifier
) {
    val displayIndex = (index + 1).toString().padStart(2, '0')
    val displayDuration = remember(track.durationMs) { formatDuration(track.durationMs) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .bounceClick { onClick() }
            .testTag(CoverScreenTestTags.playlistTrack(track.id)),
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) accentColor.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditMode) {
                // Drag handle for reordering
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .then(dragModifier)
                        .testTag(CoverScreenTestTags.playlistTrackDragHandle(track.id)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            } else {
                // Index number or active play icon
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrent && isPlaying) {
                        Icon(
                            imageVector = WitcherIcons.VolumeUp,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = displayIndex,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = if (isCurrent) accentColor else Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Track details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                    color = if (isCurrent) accentColor else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isEditMode) {
                // Move Up button
                IconButton(
                    onClick = { onMoveUp?.invoke() },
                    enabled = onMoveUp != null,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag(CoverScreenTestTags.playlistTrackMoveUp(track.id))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Move Up",
                        tint = if (onMoveUp != null) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Move Down button
                IconButton(
                    onClick = { onMoveDown?.invoke() },
                    enabled = onMoveDown != null,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag(CoverScreenTestTags.playlistTrackMoveDown(track.id))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = "Move Down",
                        tint = if (onMoveDown != null) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Remove track button
                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag(CoverScreenTestTags.playlistTrackRemove(track.id))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove Track",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Text(
                    text = displayDuration,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                IconButton(
                    onClick = onOptionsClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = WitcherIcons.Options,
                        contentDescription = "Track Options",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CoverScreenRenamePlaylistDialog(
    initialName: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = {
            keyboardController?.hide()
            onDismiss()
        },
        containerColor = Color(0xFF16181E),
        modifier = Modifier.testTag(CoverScreenTestTags.PLAYLIST_RENAME_DIALOG),
        title = {
            Text(
                text = stringResource(R.string.coverscreen_dialog_rename_playlist_title),
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.coverscreen_dialog_playlist_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag(CoverScreenTestTags.PLAYLIST_RENAME_INPUT),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (name.isNotBlank()) {
                                keyboardController?.hide()
                                onConfirm(name.trim())
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        cursorColor = accentColor,
                        focusedLabelColor = accentColor,
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        keyboardController?.hide()
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag(CoverScreenTestTags.PLAYLIST_RENAME_CONFIRM)
            ) {
                Text(stringResource(R.string.coverscreen_action_save), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    keyboardController?.hide()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.coverscreen_action_cancel), color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
fun CoverScreenDeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16181E),
        modifier = Modifier.testTag(CoverScreenTestTags.PLAYLIST_DELETE_DIALOG),
        title = {
            Text(
                text = stringResource(R.string.coverscreen_dialog_delete_playlist_title),
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        },
        text = {
            Text(
                text = stringResource(R.string.coverscreen_dialog_delete_playlist_message, playlistName),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag(CoverScreenTestTags.PLAYLIST_DELETE_CONFIRM)
            ) {
                Text(stringResource(R.string.coverscreen_action_delete), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.coverscreen_action_cancel), color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
fun CoverScreenCreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = {
            keyboardController?.hide()
            onDismiss()
        },
        containerColor = Color(0xFF16181E),
        modifier = Modifier.testTag(CoverScreenTestTags.CREATE_PLAYLIST_DIALOG),
        title = {
            Text(
                text = stringResource(R.string.coverscreen_dialog_create_playlist_title),
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.coverscreen_dialog_playlist_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag(CoverScreenTestTags.CREATE_PLAYLIST_INPUT),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (name.isNotBlank()) {
                                keyboardController?.hide()
                                onConfirm(name.trim())
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.aardBlue,
                        cursorColor = MaterialTheme.aardBlue,
                        focusedLabelColor = MaterialTheme.aardBlue,
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        keyboardController?.hide()
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.aardBlue,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag(CoverScreenTestTags.CREATE_PLAYLIST_CONFIRM)
            ) {
                Text(stringResource(R.string.coverscreen_action_save), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    keyboardController?.hide()
                    onDismiss()
                },
                modifier = Modifier.testTag(CoverScreenTestTags.CREATE_PLAYLIST_CANCEL)
            ) {
                Text(stringResource(R.string.coverscreen_action_cancel), color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
