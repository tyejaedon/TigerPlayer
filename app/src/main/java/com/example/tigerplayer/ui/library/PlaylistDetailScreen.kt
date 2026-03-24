package com.example.tigerplayer.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailsScreen(
    playlistId: Long,
    playlistName: String,
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val playlistTracks by viewModel.getPlaylistTracks(playlistId).collectAsState(initial = emptyList())
    val scrollState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // --- PARALLAX ARTIFACT HEADER ---
        PlaylistParallaxHeader(
            scrollState = scrollState,
            playlistId = playlistId,
            playlistName = playlistName,
            trackCount = playlistTracks.size,
            onPlayAll = { viewModel.mediaControllerManager.setPlaylistAndPlay(playlistTracks, 0) }
        )

        // --- TRACKS LIST ---
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 400.dp, bottom = 120.dp)
        ) {
            itemsIndexed(playlistTracks) { index, track ->
                val isCurrent = uiState.currentTrack?.id == track.id

                // Unified Song Item with Index numbering
                SongListItem(
                    track = track,
                    isCurrentTrack = isCurrent,
                    isPlaying = uiState.isPlaying,
                    indexNumber = (index + 1).toString().padStart(2, '0'), // Pass index to the component
                    onClick = {
                        viewModel.mediaControllerManager.setPlaylistAndPlay(playlistTracks, index)
                    },
                    onOptionsClick = { /* Track Options */ }
                )
            }
        }

        // --- STICKY TOP BAR ---
        PlaylistTopBar(
            name = playlistName,
            scrollState = scrollState,
            onBackClick = onBackClick
        )
    }
}
@Composable
fun ActionPlaylistRow(
    icon: ImageVector,
    title: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .glassEffect(MaterialTheme.shapes.medium)
            .bounceClick { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(accentColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
@Composable
fun PlaylistParallaxHeader(
    scrollState: LazyListState,
    playlistId: Long,
    playlistName: String,
    trackCount: Int,
    onPlayAll: () -> Unit
) {
    val scrollOffset = scrollState.firstVisibleItemScrollOffset
    val isScrolled = scrollState.firstVisibleItemIndex > 0

    // Animates the icon smaller as you scroll up
    val artScale by animateFloatAsState(
        targetValue = if (isScrolled) 0.8f else 1f,
        animationSpec = tween(500),
        label = "artScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .graphicsLayer {
                translationY = -scrollOffset.toFloat() * 0.45f
                alpha = (1f - (scrollOffset / 800f)).coerceIn(0f, 1f)
                scaleX = artScale
                scaleY = artScale
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier
                    .size(200.dp)
                    .shadow(32.dp, RoundedCornerShape(28.dp), spotColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (playlistId == -1L) WitcherIcons.Favorite else WitcherIcons.Playlist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = playlistName.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "$trackCount TRACKS RECORDED",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onPlayAll,
                shape = CircleShape,
                modifier = Modifier.width(220.dp).height(52.dp).bounceClick { onPlayAll() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(WitcherIcons.Play, null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("COMMENCE RITUAL", fontWeight = FontWeight.Black)
            }
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
    // Derived state for performance: True when we've scrolled enough to frost the bar
    val isScrolled by remember {
        derivedStateOf { scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 300 }
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
                    letterSpacing = 1.sp
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(8.dp)
                    .background(
                        if (isScrolled) Color.Transparent else Color.Black.copy(alpha = 0.2f),
                        CircleShape
                    )
            ) {
                Icon(WitcherIcons.Back, "Back", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (isScrolled)
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            else Color.Transparent
        ),
        modifier = Modifier
            .statusBarsPadding()
            // Apply glass only when scrolled to maintain the transparent hero look at the top
            .then(if (isScrolled) Modifier.glassEffect(RectangleShape) else Modifier)
    )
}