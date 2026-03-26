package com.example.tigerplayer.ui.library

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val playlistTracks by viewModel.getPlaylistTracks(playlistId).collectAsState(initial = emptyList())
    val scrollState = rememberLazyListState()
    val context = LocalContext.current

    val primaryFromTheme = MaterialTheme.colorScheme.primary
    var dominantColor by remember { mutableStateOf(primaryFromTheme) }

    val firstTrackArt = remember(playlistTracks) { playlistTracks.firstOrNull()?.artworkUri }

    LaunchedEffect(firstTrackArt) {
        if (firstTrackArt != null) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context).data(firstTrackArt).allowHardware(false).build()
            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap
            bitmap?.let { b ->
                Palette.from(b).generate { palette ->
                    // Favor vibrant over dominant for better UI pop
                    val swatch = palette?.vibrantSwatch ?: palette?.dominantSwatch
                    swatch?.rgb?.let { dominantColor = Color(it) }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // --- AMBIENT AURA ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(dominantColor.copy(alpha = 0.25f), Color.Transparent),
                        endY = 1000f
                    )
                )
        )

        // --- THE ARCHIVE LIST (Header is now inside!) ---
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp) // Removed the massive top padding
        ) {
            // 1. THE PARALLAX HEADER
            item {
                PlaylistParallaxHeader(
                    scrollState = scrollState,
                    playlistId = playlistId,
                    playlistName = playlistName,
                    trackCount = playlistTracks.size,
                    accentColor = dominantColor,
                    onPlayAll = {
                        if (playlistTracks.isNotEmpty()) {
                            viewModel.mediaControllerManager.setPlaylistAndPlay(playlistTracks, 0)
                        }
                    }
                )
            }

            // 2. THE CHANTS
            if (playlistTracks.isEmpty()) {
                item { EmptyArchiveState("The grimoire is empty.") }
            } else {
                itemsIndexed(items = playlistTracks, key = { _, track -> track.id }) { index, track ->
                    val isCurrent = uiState.currentTrack?.id == track.id

                    ChapterSongRow(
                        indexString = (index + 1).toString().padStart(2, '0'),
                        index = index,
                        track = track,
                        isCurrentTrack = isCurrent,
                        isPlaying = uiState.isPlaying,
                        onClick = {
                            viewModel.mediaControllerManager.setPlaylistAndPlay(playlistTracks, index)
                        },
                        onOptionsClick = { /* Track Portal Ritual */ }
                    )
                }
            }
        }

        // --- TOP BAR: VANGUARD GLASS ---
        PlaylistTopBar(
            name = playlistName,
            scrollState = scrollState,
            onBackClick = onBackClick
        )
    }
}

// ==========================================
// --- THE COMPONENTS ---
// ==========================================

@Composable
fun ChapterSongRow(
    indexString: String,
    index: Int,
    track: AudioTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    val activeTint = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp), // S22: Tightened horizontal padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        // S22: Reduced width to 32dp to give more room to song titles
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
            if (isCurrentTrack && isPlaying) {
                Icon(WitcherIcons.VolumeUp, null, tint = activeTint, modifier = Modifier.size(18.dp))
            } else {
                Text(
                    text = indexString,
                    style = MaterialTheme.typography.labelMedium, // Scaled down for sleekness
                    color = if (isCurrentTrack) activeTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Black
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrentTrack) FontWeight.Black else FontWeight.Bold,
                color = if (isCurrentTrack) activeTint else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onOptionsClick, modifier = Modifier.size(32.dp)) {
            Icon(WitcherIcons.Options, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistTopBar(
    name: String,
    scrollState: LazyListState,
    onBackClick: () -> Unit
) {
    // S22: Trigger the frost earlier because the header is smaller
    val isScrolled by remember {
        derivedStateOf { scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 250 }
    }

    TopAppBar(
        title = {
            AnimatedVisibility(
                visible = isScrolled,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Text(
                    text = name.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp, // Tighter for S22
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(8.dp)
                    .background(
                        if (isScrolled) Color.Transparent else Color.Black.copy(alpha = 0.3f),
                        CircleShape
                    )
            ) {
                Icon(WitcherIcons.Back, "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (isScrolled) MaterialTheme.colorScheme.surface.copy(alpha = 0.85f) else Color.Transparent
        ),
        modifier = Modifier
            .then(if (isScrolled) Modifier.glassEffect(RectangleShape) else Modifier)
    )
}

@Composable
fun PlaylistParallaxHeader(
    scrollState: LazyListState,
    playlistId: Long,
    playlistName: String,
    trackCount: Int,
    accentColor: Color,
    onPlayAll: () -> Unit
) {
    // Calculate precise offset only when the header is actually the first item
    val offset = if (scrollState.firstVisibleItemIndex == 0) scrollState.firstVisibleItemScrollOffset else 1000

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp) // S22 Optimization: Dropped from 420.dp
            .padding(top = 40.dp) // Push down slightly to clear status bar
            .graphicsLayer {
                // The Parallax Math: Scroll slower than the list
                translationY = offset * 0.4f
                alpha = (1f - (offset / 600f)).coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                // S22 Optimization: Scaled art size down to 140.dp
                modifier = Modifier.size(140.dp).shadow(32.dp, RoundedCornerShape(24.dp), spotColor = accentColor),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (playlistId == -1L) WitcherIcons.Favorite else WitcherIcons.Playlist,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = playlistName.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = "$trackCount CHANTS COLLECTED",
                style = MaterialTheme.typography.labelMedium, // Sleeker subtext
                color = accentColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Contrast Check: Ensure button text is readable against the accent color
            val buttonTextColor = if (ColorUtils.calculateLuminance(accentColor.value.toLong().toInt()) > 0.5)
                Color.Black else Color.White

            Button(
                onClick = onPlayAll,
                shape = CircleShape,
                modifier = Modifier
                    .width(220.dp) // S22: Slimmer button
                    .height(48.dp)
                    .bounceClick { onPlayAll() },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(WitcherIcons.Play, null, tint = buttonTextColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "COMMENCE RITUAL",
                    fontWeight = FontWeight.Black,
                    color = buttonTextColor,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun EmptyArchiveState(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = WitcherIcons.Search,
            contentDescription = null,
            modifier = Modifier.size(56.dp).graphicsLayer { alpha = 0.15f },
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}