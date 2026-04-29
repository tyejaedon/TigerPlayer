package com.example.tigerplayer.ui.cloud

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tigerplayer.data.remote.model.SpotifyTrack
import com.example.tigerplayer.ui.theme.SpotifyGreen
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

    val fallbackColor = Color(0xFF121212)
    var dominantColor by remember { mutableStateOf(fallbackColor) }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(1000),
        label = "SpotifyPlaylistColor"
    )

    // Palette Ritual: Extracting essence from the artwork
    val imageRequest = remember(playlistImageUrl) {
        ImageRequest.Builder(context)
            .data(playlistImageUrl)
            .crossfade(true)
            .allowHardware(false)
            .listener(onSuccess = { _, result ->
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { b ->
                    Palette.from(b).generate { palette ->
                        val colorInt = palette?.dominantSwatch?.rgb
                            ?: palette?.vibrantSwatch?.rgb
                            ?: palette?.mutedSwatch?.rgb
                        colorInt?.let { dominantColor = Color(it) }
                    }
                }
            })
            .build()
    }

    val accentColor = if (dominantColor == fallbackColor) SpotifyGreen else animatedDominantColor

    LaunchedEffect(playlistId) {
        viewModel.fetchTracksForPlaylist(playlistId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dynamic Ambient Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(animatedDominantColor.copy(alpha = 0.6f), fallbackColor),
                        endY = 1500f
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = playlistName,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(WitcherIcons.Back, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Unspecified,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = Color.Unspecified
                    )
                )
            }
        ) { paddingValues ->
            if (isLoading && tracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    item {
                        Box(modifier = Modifier
                            .size(260.dp)
                            // SHADOW MUST BE BEFORE CLIP
                            .shadow(32.dp, MaterialTheme.shapes.extraLarge, spotColor = accentColor)
                            .clip(MaterialTheme.shapes.extraLarge),) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = "Playlist Art",
                                modifier = Modifier
                                    .size(260.dp)
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .shadow(32.dp, MaterialTheme.shapes.extraLarge, spotColor = accentColor),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    itemsIndexed(
                        items = tracks,
                        key = { index, track -> "${track.id}_$index" }
                    ) { index, track ->
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

        // Floating Action Portal
        if (tracks.isNotEmpty()) {
            Button(
                onClick = { viewModel.playSpotifyUri("spotify:playlist:$playlistId") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .fillMaxWidth(0.7f)
                    .height(64.dp)
                    .shadow(24.dp, CircleShape, spotColor = accentColor),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = CircleShape
            ) {
                Icon(WitcherIcons.Play, null, tint = Color.Black)
                Spacer(modifier = Modifier.width(12.dp))
                Text("PLAY ARCHIVE", fontWeight = FontWeight.Black, color = Color.Black)
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
            .bounceClick { onClick() }
            .glassEffect(MaterialTheme.shapes.large),
        color = Color.Black.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = index.toString(),
                modifier = Modifier.width(32.dp),
                color = accentColor.copy(alpha = 0.8f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )

            AsyncImage(
                // SAFE CALL: Added '?.' before images to prevent NullPointerExceptions
                model = track.album?.images?.firstOrNull()?.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artists.joinToString { it.name },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = WitcherIcons.Options,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}