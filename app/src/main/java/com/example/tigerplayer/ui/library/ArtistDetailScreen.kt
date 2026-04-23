package com.example.tigerplayer.ui.library

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.ArtistDetails
import com.example.tigerplayer.ui.home.SectionTitle
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.utils.ArtistUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    // 1. DATA AGGREGATION
    val artistTracks = remember(uiState.tracks, artistName) {
        uiState.tracks.filter { track ->
            ArtistUtils.getBaseArtist(track.artist).equals(artistName, ignoreCase = true)
        }
    }

    val artistAlbums = remember(artistTracks) {
        artistTracks
            .distinctBy { it.album.lowercase().trim() }
            .sortedByDescending { it.year ?: "" }
    }

    // 2. THE DYNAMIC PALETTE RITUAL
    val imageUrl = profile?.imageUrl?.takeIf { it.isNotBlank() } ?: artistTracks.firstOrNull()?.artworkUri
    val fallbackColor = MaterialTheme.colorScheme.background
    var dominantColor by remember { mutableStateOf(fallbackColor) }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(1000),
        label = "DominantColorTransition"
    )

    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(800)
            .allowHardware(false)
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

    LaunchedEffect(artistName) {
        if (profile == null) viewModel.fetchArtistProfile(artistName)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(animatedDominantColor.copy(alpha = 0.4f), fallbackColor),
                    endY = 1200f
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = artistName.uppercase(),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(WitcherIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.glassEffect(RectangleShape)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // --- 1. HERO IMAGE ---
                item { ArtistHeroImage(imageRequest, artistName) }

                // --- 2. GENRE CLOUD ---
                if (profile?.genres?.isNotEmpty() == true) {
                    item { ArtistGenreCloud(profile.genres) }
                }

                // --- 3. VANGUARD STATS ---
                item { ArtistVanguardStats(
profile
                ) }

                // --- 4. DISCOGRAPHY (Albums) ---
                if (artistAlbums.isNotEmpty()) {
                    item {
                        SectionTitle(title = "DISCOGRAPHY")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
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

                // --- 5. ALL MANIFESTATIONS (Songs) ---
                if (artistTracks.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        SectionTitle(title = "ALL MANIFESTATIONS")
                    }

                    items(artistTracks) { track ->
                        ArchiveSongRow(
                            track = track,
                            isCurrentTrack = uiState.currentTrack?.id == track.id,
                            isPlaying = uiState.isPlaying,
                            onClick = { viewModel.playTrack(track) },
                            onOptionsClick = { /* Track Options Ritual */ }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun ArtistAlbumCard(
    track: AudioTrack,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp) // Standardized width for the Horizontal Discography Row
            .bounceClick { onClick() }
    ) {
        // --- THE VOLUME COVER: Armor Plated ---
        AsyncImage(
            model = track.artworkUri,
            contentDescription = "Cover for ${track.album}",
            contentScale = ContentScale.Crop,
            fallback = painterResource(R.drawable.ic_tiger_logo),
            error = painterResource(R.drawable.ic_tiger_logo),
            modifier = Modifier
                .size(150.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                // THE ARMOR BORDER: Critical for defining edges in Dark Mode
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.large
                )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- THE VOLUME NAME: Primary Hierarchy ---
        Text(
            text = track.album,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // --- THE ERA: Secondary Hierarchy (Aard Blue) ---
        Text(
            text = track.year?.uppercase() ?: "RECORDED",
            style = MaterialTheme.typography.labelSmall,
            color = AardBlue, // High-visibility blue constant
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}
// ==========================================
// --- RECONFIGURED COMPONENTS ---
// ==========================================

