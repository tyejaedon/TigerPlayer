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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.CachePolicy
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
    val artistDetails by viewModel.artistDetails.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val normalizedKey = remember(artistName) {
        ArtistUtils.getBaseArtist(artistName).lowercase().trim()
    }

    // THE FIX: Lookup using the normalizedKey, NOT the raw artistName
    val profile = artistDetails[normalizedKey]

    val playlists by viewModel.customPlaylists.collectAsStateWithLifecycle(initialValue = emptyList())
    var trackForOptions by remember { mutableStateOf<AudioTrack?>(null) }
    // --- 1. THE DATA ARCHIVE ---
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

    // --- 2. THE DYNAMIC PALETTE RITUAL (Fixed & Keyed) ---
    val fallbackColor = MaterialTheme.colorScheme.background

    // THE FIX: Keying 'remember' to artistName ensures the color resets
    // to fallback the moment you switch artists.
    var dominantColor by remember(artistName) { mutableStateOf(fallbackColor) }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(1000), // Smooth transition as the palette is forged
        label = "DominantColorTransition"
    )

    val imageUrl = remember(profile?.imageUrl, artistTracks) {
        // Priority 1: The official Artist Lore image (Last.fm)
        // Priority 2: The most recent track's artwork (Local)
        profile?.imageUrl?.takeIf { it.isNotBlank() }
            ?: artistTracks.firstOrNull()?.artworkUri?.toString()
    }

    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(800)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .allowHardware(false) // Required for Palette to read the bitmap
            .listener(onSuccess = { _, result ->
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { b ->
                    Palette.from(b).generate { palette ->
                        // Prioritize Vibrant or Dominant swatches
                        val colorInt = palette?.vibrantSwatch?.rgb
                            ?: palette?.dominantSwatch?.rgb
                            ?: palette?.mutedSwatch?.rgb

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
            .background(fallbackColor) // Use fallback as base
    ) {
        // AMBIENT GLOW: Now correctly uses fallbackColor to prevent harsh edges
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            animatedDominantColor.copy(alpha = 0.35f),
                            fallbackColor // The gradient now "sinks" into the background
                        ),
                        endY = 1400f
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = artistName.uppercase(),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(WitcherIcons.Back, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.glassEffect(RectangleShape)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item { ArtistHeroImage(imageRequest, artistName) }

                item { ArtistVanguardStats(profile, animatedDominantColor) }

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
                            onOptionsClick = { trackForOptions = track }
                        )
                    }
                }
            }

            trackForOptions?.let { selectedTrack ->
                SongOptionsSheet(
                    track = selectedTrack,
                    playlists = playlists,
                    onDismiss = { trackForOptions = null },
                    onPlayNext = {
                        viewModel.addToQueue(selectedTrack) // Ensure your VM supports 'Play Next'
                        trackForOptions = null
                    },
                    onAddToPlaylist = { playlistId ->
                        viewModel.addTrackToPlaylist(playlistId, selectedTrack)
                        trackForOptions = null
                    },
                    onGoToAlbum = { albumName ->
                        trackForOptions = null
                        onAlbumClick(albumName)
                    }
                )
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

