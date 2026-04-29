package com.example.tigerplayer.ui.library

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlaylistDetailsScreen(
    playlistId: Long,
    playlistName: String,
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val rawPlaylistTracks by viewModel.getPlaylistTracks(playlistId).collectAsState(initial = emptyList())
    val customPlaylists by viewModel.customPlaylists.collectAsState()

    val currentPlaylist = customPlaylists.find { it.id == playlistId }
    val playlistArtworkUri = currentPlaylist?.artworkUri

    // View States
    var isEditMode by remember { mutableStateOf(false) }
    var selectedTrackForOptions by remember { mutableStateOf<AudioTrack?>(null) }
    var mutableTracks by remember(rawPlaylistTracks) { mutableStateOf(rawPlaylistTracks) }

    val scrollState = rememberLazyListState()
    val context = LocalContext.current

    // Generate a consistent "hash" color based on the playlist name for fallbacks
    val fallbackColors = listOf(
        Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFF3949AB),
        Color(0xFF00897B), Color(0xFFF4511E), Color(0xFF546E7A)
    )
    val nameHashColor = remember(playlistName) {
        fallbackColors[kotlin.math.abs(playlistName.hashCode()) % fallbackColors.size]
    }

    val primaryFromTheme = MaterialTheme.colorScheme.primary
    var dominantColor by remember { mutableStateOf(nameHashColor) }

    // Determine the art to display. If the playlist has a custom image, use it.
    // If not, we do NOT fall back to the first track's art here anymore, we let the UI render the gradient.
    val displayArt = playlistArtworkUri

    // Only extract palette if we actually have an image URL
    LaunchedEffect(displayArt) {
        if (displayArt != null) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context).data(displayArt).allowHardware(false).build()
            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap
            bitmap?.let { b ->
                Palette.from(b).generate { palette ->
                    val swatch = palette?.vibrantSwatch ?: palette?.dominantSwatch
                    swatch?.rgb?.let { dominantColor = Color(it) }
                }
            }
        } else {
            // Revert to generated hash color if art is cleared
            dominantColor = nameHashColor
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.updatePlaylistImage(context, playlistId, it) }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(dominantColor.copy(alpha = 0.25f), Color.Transparent), endY = 1000f))
        )

        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                PlaylistParallaxHeader(
                    scrollState = scrollState,
                    playlistId = playlistId,
                    playlistName = playlistName,
                    trackCount = mutableTracks.size,
                    accentColor = dominantColor,
                    artworkUri = displayArt,
                    isEditMode = isEditMode,
                    onChangeCoverClick = { imagePickerLauncher.launch("image/*") },
                    onPlayAll = { if (mutableTracks.isNotEmpty()) viewModel.setPlaylistAndPlay(mutableTracks, 0) }
                )
            }

            if (mutableTracks.isEmpty()) {
                item { EmptyArchiveState("The grimoire is empty.") }
            } else {
                itemsIndexed(items = mutableTracks, key = { _, track -> track.id }) { index, track ->
                    val isCurrent = uiState.currentTrack?.id == track.id
                    var offsetY by remember { mutableStateOf(0f) }

                    Box(
                        modifier = Modifier.fillMaxWidth().graphicsLayer { translationY = offsetY }
                    ) {
                        ChapterSongRow(
                            index = index, track = track, isCurrentTrack = isCurrent,
                            isPlaying = uiState.isPlaying, isEditMode = isEditMode,
                            onClick = { if (!isEditMode) viewModel.setPlaylistAndPlay(mutableTracks, index) },
                            onOptionsClick = { selectedTrackForOptions = track },
                            dragModifier = if (isEditMode) Modifier.pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        offsetY += dragAmount.y
                                        val itemHeight = 72.dp.toPx()
                                        if (offsetY > itemHeight && index < mutableTracks.size - 1) {
                                            mutableTracks = mutableTracks.toMutableList().apply { add(index + 1, removeAt(index)) }
                                            offsetY -= itemHeight
                                        } else if (offsetY < -itemHeight && index > 0) {
                                            mutableTracks = mutableTracks.toMutableList().apply { add(index - 1, removeAt(index)) }
                                            offsetY += itemHeight
                                        }
                                    },
                                    onDragEnd = { offsetY = 0f },
                                    onDragCancel = { offsetY = 0f }
                                )
                            } else Modifier
                        )
                    }
                }
            }
        }

        PlaylistTopBar(
            name = playlistName, scrollState = scrollState, isEditMode = isEditMode,
            onBackClick = onBackClick,
            onEditClick = {
                if (isEditMode) viewModel.savePlaylistOrder(playlistId, mutableTracks)
                isEditMode = !isEditMode
            }
        )

        if (selectedTrackForOptions != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedTrackForOptions = null },
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(selectedTrackForOptions!!.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ListItem(headlineContent = { Text("Play Next") }, modifier = Modifier.clickable { viewModel.addNextToQueue(selectedTrackForOptions!!); selectedTrackForOptions = null })
                    ListItem(headlineContent = { Text("Remove from Playlist", color = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable {
                        viewModel.removeTrackFromPlaylist(playlistId, selectedTrackForOptions!!)
                        mutableTracks = mutableTracks.filter { it.id != selectedTrackForOptions!!.id }
                        selectedTrackForOptions = null
                    })
                }
            }
        }
    }
}

// ==========================================
// --- THE COMPONENTS ---
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistTopBar(name: String, scrollState: LazyListState, isEditMode: Boolean, onBackClick: () -> Unit, onEditClick: () -> Unit) {
    val isScrolled by remember { derivedStateOf { scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 250 } }
    TopAppBar(
        title = { AnimatedVisibility(visible = isScrolled, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) { Text(name.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        navigationIcon = { IconButton(onClick = onBackClick, modifier = Modifier.padding(8.dp).background(if (isScrolled) Color.Transparent else Color.Black.copy(alpha = 0.3f), CircleShape)) { Icon(WitcherIcons.Back, "Back", tint = MaterialTheme.colorScheme.onSurface) } },
        actions = { TextButton(onClick = onEditClick) { Text(text = if (isEditMode) "Done" else "Edit", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isScrolled) MaterialTheme.colorScheme.surface.copy(alpha = 0.85f) else Color.Transparent),
        modifier = Modifier.then(if (isScrolled) Modifier.glassEffect(RectangleShape) else Modifier)
    )
}

@Composable
fun PlaylistParallaxHeader(
    scrollState: LazyListState, playlistId: Long, playlistName: String, trackCount: Int,
    accentColor: Color, artworkUri: String?, isEditMode: Boolean,
    onChangeCoverClick: () -> Unit, onPlayAll: () -> Unit
) {
    val offset = if (scrollState.firstVisibleItemIndex == 0) scrollState.firstVisibleItemScrollOffset else 1000

    Box(
        modifier = Modifier.fillMaxWidth().height(340.dp).padding(top = 40.dp)
            .graphicsLayer { translationY = offset * 0.4f; alpha = (1f - (offset / 600f)).coerceIn(0f, 1f) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // ARTWORK CONTAINER
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .shadow(32.dp, RoundedCornerShape(24.dp), spotColor = accentColor)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(enabled = isEditMode) { onChangeCoverClick() },
                contentAlignment = Alignment.Center
            ) {
                if (artworkUri != null) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = "Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // INDUSTRY STANDARD UI FALLBACK
                    // A sleek gradient using the hash color generated for this playlist
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        accentColor,
                                        accentColor.copy(alpha = 0.6f)
                                    )
                                )
                            )
                    )
                    Icon(
                        imageVector = if (playlistId == -1L) WitcherIcons.Favorite else WitcherIcons.Playlist,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(64.dp)
                    )
                }

                // Edit Overlay
                Row{
                    AnimatedVisibility(visible = isEditMode, enter = fadeIn(), exit = fadeOut()) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Change Cover",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(text = playlistName.uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            Text(text = "$trackCount CHANTS COLLECTED", style = MaterialTheme.typography.labelMedium, color = accentColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(24.dp))

            val buttonTextColor = if (ColorUtils.calculateLuminance(accentColor.value.toLong().toInt()) > 0.5) Color.Black else Color.White
            AnimatedVisibility(visible = !isEditMode) {
                Button(
                    onClick = onPlayAll, shape = CircleShape, modifier = Modifier.width(220.dp).height(48.dp).bounceClick { onPlayAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Icon(WitcherIcons.Play, null, tint = buttonTextColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("COMMENCE RITUAL", fontWeight = FontWeight.Black, color = buttonTextColor, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun EmptyArchiveState(message: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(WitcherIcons.Search, null, modifier = Modifier.size(56.dp).graphicsLayer { alpha = 0.15f }, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(16.dp))
        Text(message.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
fun ChapterSongRow(
    index: Int, track: AudioTrack, isCurrentTrack: Boolean, isPlaying: Boolean, isEditMode: Boolean,
    onClick: () -> Unit, onOptionsClick: () -> Unit, dragModifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(if (isCurrentTrack) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = isEditMode) {
            Icon(Icons.Default.DragHandle, "Reorder", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = dragModifier.padding(end = 16.dp).size(28.dp))
        }

        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (isCurrentTrack && isPlaying) {
                Icon(WitcherIcons.Play, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            } else {
                Text(text = "${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium, color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        AnimatedVisibility(visible = !isEditMode) {
            IconButton(onClick = onOptionsClick) { Icon(Icons.Default.MoreVert, "Options", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }
    }
}