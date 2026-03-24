package com.example.tigerplayer.ui.library

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.ArtistDetails
import com.example.tigerplayer.ui.home.SectionTitle
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.utils.ArtistUtils
import java.text.NumberFormat
import java.util.Locale
import com.example.tigerplayer.ui.library.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailsScreen(
    artistName: String,
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val artistDetails by viewModel.artistDetails.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val profile = artistDetails[artistName]

    // 1. THE ALL-SONGS HIERARCHY: Every track by this artist
    val artistTracks = remember(uiState.tracks, artistName) {
        uiState.tracks.filter { track ->
            ArtistUtils.getBaseArtist(track.artist).equals(artistName, ignoreCase = true)
        }
    }

    // 2. THE ALBUM AGGREGATION (RECTIFIED):
    // We group by album name to show unique albums in the horizontal row
    val artistAlbums = remember(artistTracks) {
        artistTracks
            .distinctBy { it.album.lowercase().trim() }
            .sortedByDescending { it.album } // Or sortedBy { it.year } if available
    }

    // 3. THE PALETTE & IMAGE RITUAL
    val imageUrl = profile?.imageUrl?.takeIf { it.isNotBlank() } ?: artistTracks.firstOrNull()?.artworkUri
    val fallbackColor = MaterialTheme.colorScheme.background
    var dominantColor by remember { mutableStateOf(fallbackColor) }

    // Palette Engine: Extract colors from the high-res cloud art
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(800)
            .allowHardware(false) // Required for Palette to work
            .listener(onSuccess = { _, result ->
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { b ->
                    Palette.from(b).generate { palette ->
                        val colorInt = palette?.dominantSwatch?.rgb ?: palette?.mutedSwatch?.rgb
                        colorInt?.let { dominantColor = Color(it) }
                    }
                }
            })
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(dominantColor.copy(alpha = 0.4f), fallbackColor),
                    endY = 1200f
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(artistName.uppercase(), fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(WitcherIcons.Back, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.glassEffect(RectangleShape)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // --- HERO SECTION ---
                item { ArtistHeroImage(imageUrl, artistName) }

                if (profile?.genres?.isNotEmpty() == true) {
                    item { ArtistGenreCloud(profile.genres) }
                }

                item { ArtistVanguardStats(profile) }

                // --- HORIZONTAL ALBUMS ROW ---
                if (artistAlbums.isNotEmpty()) {
                    item {
                        SectionTitle(title = "Discography")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            items(artistAlbums) { albumTrack ->
                                ArtistAlbumCard(
                                    track = albumTrack,
                                    onClick = { onAlbumClick(albumTrack.album) }
                                )
                            }
                        }
                    }
                }

                // --- VERTICAL TRACK LIST ---
                if (artistTracks.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        SectionTitle(title = "All Manifestations")
                    }

                    items(artistTracks) { track ->
                        // Using our Unified SongListItem for aesthetic consistency
                        SongListItem(
                            track = track,
                            isCurrentTrack = uiState.currentTrack?.id == track.id,
                            isPlaying = uiState.isPlaying,
                            onClick = { viewModel.playTrack(track) },
                            onOptionsClick = { /* Track Options */ }
                        )
                    }
                }
            }
        }
    }
}

// --- NEW COMPONENT: THE ALBUM CARD ---
