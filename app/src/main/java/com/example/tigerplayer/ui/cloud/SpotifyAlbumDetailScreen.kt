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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun SpotifyAlbumDetailScreen(
    albumId: String,
    albumName: String,
    albumImageUrl: String?,
    viewModel: CloudViewModel,
    onBackClick: () -> Unit
) {
    val tracks by viewModel.currentPlaylistTracks.collectAsState()
    val isLoading by viewModel.isLoadingTracks.collectAsState()
    val context = LocalContext.current

    // THE AMBIENT GLOW RITUAL (Ported from Playlist Screen)
    val fallbackColor = MaterialTheme.colorScheme.background
    var dominantColor by remember { mutableStateOf(fallbackColor) }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(1000),
        label = "SpotifyAlbumColor"
    )

    val imageRequest = remember(albumImageUrl) {
        ImageRequest.Builder(context)
            .data(albumImageUrl)
            .crossfade(true)
            .allowHardware(false)
            .listener(onSuccess = { _, result ->
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { b ->
                    Palette.from(b).generate { palette ->
                        val colorInt = palette?.dominantSwatch?.rgb
                            ?: palette?.mutedSwatch?.rgb
                        colorInt?.let { dominantColor = Color(it) }
                    }
                }
            })
            .build()
    }

    val accentColor = if (dominantColor == fallbackColor) SpotifyGreen else animatedDominantColor

    LaunchedEffect(albumId) {
        viewModel.fetchTracksForAlbum(albumId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dynamic Ambient Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(animatedDominantColor.copy(alpha = 0.5f), fallbackColor),
                        endY = 1200f
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent, // Let the gradient shine through
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = albumName,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(WitcherIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = "Cover for $albumName",
                            modifier = Modifier
                                .size(260.dp)
                                // THE FIX: Shadow BEFORE Clip ensures the neon bleed works
                                .shadow(32.dp, MaterialTheme.shapes.extraLarge, spotColor = accentColor)
                                .clip(MaterialTheme.shapes.extraLarge),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = { viewModel.playSpotifyUri("spotify:album:$albumId") },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .height(56.dp)
                                .width(180.dp),
                            shape = CircleShape
                        ) {
                            Icon(WitcherIcons.Play, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PLAY ALBUM", color = Color.Black, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                    }
                }

                if (isLoading && tracks.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accentColor)
                        }
                    }
                } else {
                    itemsIndexed(
                        items = tracks,
                        // THE FIX: Add keys to prevent UI stutter
                        key = { _, track -> track.id }
                    ) { index, track ->
                        SpotifyAlbumTrackRow(
                            index = index + 1,
                            track = track,
                            onClick = { viewModel.playSpotifyUri(track.uri) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpotifyAlbumTrackRow(index: Int, track: SpotifyTrack, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .bounceClick { onClick() }
            .glassEffect(MaterialTheme.shapes.medium),
        color = Color.Black.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = index.toString(),
                modifier = Modifier.width(32.dp),
                color = SpotifyGreen.copy(alpha = 0.8f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
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
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}