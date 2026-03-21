package com.example.tigerplayer.ui.cloud

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tigerplayer.data.remote.model.SpotifyTrack
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyPlaylistScreen(
    playlistId: String,
    playlistName: String,
    playlistImageUrl: String?,
    viewModel: CloudViewModel,
    onBackClick: () -> Unit
) {
    val tracks by viewModel.currentPlaylistTracks.collectAsState()
    val isLoading by viewModel.isLoadingTracks.collectAsState()
    val context = LocalContext.current

    // --- DYNAMIC SPOTIFY COLOR EXTRACTION ---
    val fallbackColor = Color(0xFF121212) // Spotify Noir
    var dominantColor by remember { mutableStateOf(fallbackColor) }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(1000),
        label = "SpotifyPlaylistColor"
    )

    val imageRequest = remember(playlistImageUrl) {
        ImageRequest.Builder(context)
            .data(playlistImageUrl)
            .crossfade(true)
            .allowHardware(false)
            .listener(
                onSuccess = { _, result ->
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    bitmap?.let { b ->
                        Palette.from(b).generate { palette ->
                            val colorInt = palette?.dominantSwatch?.rgb
                                ?: palette?.mutedSwatch?.rgb
                                ?: palette?.vibrantSwatch?.rgb
                            colorInt?.let { dominantColor = Color(it) }
                        }
                    }
                }
            )
            .build()
    }

    // Determine Accent (Defaults to Spotify Green if extraction fails)
    val accentColor = if (dominantColor == fallbackColor) Color(0xFF1DB954) else animatedDominantColor

    LaunchedEffect(playlistId) {
        viewModel.fetchTracksForPlaylist(playlistId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. LIVE GRADIENT BACKGROUND
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            animatedDominantColor.copy(alpha = 0.6f),
                            fallbackColor
                        ),
                        startY = 0f,
                        endY = 1500f
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent, // Let the gradient shine through
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = playlistName,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(WitcherIcons.Back, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.background(animatedDominantColor.copy(alpha = 0.2f))
                )
            }
        ) { paddingValues ->
            if (isLoading && tracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(bottom = 120.dp) // Space for floating button
                ) {
                    // --- HERO IMAGE ---
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = "Header",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(260.dp)
                                    .padding(vertical = 24.dp)
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .shadow(32.dp, MaterialTheme.shapes.extraLarge, spotColor = accentColor)
                            )
                        }
                    }
                    // Inside your LazyColumn in SpotifyPlaylistScreen
                    if (!isLoading && tracks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "The archives are empty or inaccessible.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    itemsIndexed(tracks) { index, track ->
                        SpotifyTrackRow(
                            index = index + 1,
                            track = track,
                            accentColor = accentColor,
                            onClick = { viewModel.playSpotifyUri(track.uri) }
                        )
                    }
                }
            }
        }

        // --- THE FLOATING PLAY BUTTON ---
        if (tracks.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { viewModel.playSpotifyUri("spotify:playlist:$playlistId") },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(64.dp)
                        .shadow(16.dp, CircleShape, spotColor = accentColor)
                        .bounceClick { }
                ) {
                    Icon(WitcherIcons.Play, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "PLAY CLOUD ARCHIVE",
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SpotifyTrackRow(index: Int, track: SpotifyTrack, accentColor: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable { onClick() }
            .glassEffect(MaterialTheme.shapes.large),
        color = Color.Black.copy(alpha = 0.2f) // Deep glass
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Number
            Text(
                text = index.toString(),
                modifier = Modifier.width(32.dp),
                color = accentColor.copy(alpha = 0.8f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )

            // Track Art
            AsyncImage(
                model = track.album.images?.firstOrNull()?.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Track Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artists.joinToString { it.name },
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = WitcherIcons.Options,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}