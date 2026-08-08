package com.example.tigerplayer.ui.library

import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
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
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.home.SectionTitle
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.DominantColorExtractor
import com.example.tigerplayer.ui.theme.TigerNeonOrange
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.rememberTigerAmbientGradient
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.launch

@RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 15)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArtistDetailsScreen(
    artistName: String,
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onAlbumClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colorScope = rememberCoroutineScope()

    val normalizedArtistName = remember(artistName) {
        ArtistUtils.getBaseArtist(artistName).trim()
    }
    val artistProfileFlow = remember(artistName) {
        viewModel.observeArtistProfile(artistName)
    }
    val profile by artistProfileFlow.collectAsStateWithLifecycle(initialValue = null)

    val playlists by viewModel.customPlaylists.collectAsStateWithLifecycle(initialValue = emptyList())
    var trackForOptions by remember { mutableStateOf<AudioTrack?>(null) }
    // --- 1. THE DATA ARCHIVE ---
    val artistTracks = remember(uiState.tracks, artistName) {
        uiState.tracks.filter { track ->
            ArtistUtils.getBaseArtist(track.artist).equals(normalizedArtistName, ignoreCase = true)
        }
    }

    val artistAlbumsWithCounts = remember(artistTracks) {
        artistTracks
            .groupBy { it.album.lowercase().trim() }
            .map { (_, tracks) ->
                tracks.first() to tracks.size
            }
            .sortedByDescending { it.first.year ?: "" }
    }

    // --- 2. THE DYNAMIC PALETTE RITUAL (Fixed & Keyed) ---
    val fallbackColor = MaterialTheme.colorScheme.background

    // THE FIX: Keying 'remember' to artistName ensures the color resets
    // to fall back the moment you switch artists.
    var dominantColor by remember(artistName) { mutableStateOf(fallbackColor) }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(1000), // Smooth transition as the palette is forged
        label = "DominantColorTransition"
    )
    val ambientBrush = rememberTigerAmbientGradient(animatedDominantColor, baseTopAlpha = 0.18f)

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
                colorScope.launch {
                    dominantColor = DominantColorExtractor.extractSnappedNeon(
                        drawable = result.drawable,
                        fallback = TigerNeonOrange
                    )
                }
            }, onError = { _, _ ->
                dominantColor = fallbackColor
            })
            .build()
    }

    LaunchedEffect(artistName) {
        viewModel.fetchArtistProfile(artistName)
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
                .background(ambientBrush)
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Unspecified,
                        navigationIconContentColor = Color.Unspecified,
                        titleContentColor = Color.Unspecified,
                        actionIconContentColor = Color.Unspecified
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

                if (artistAlbumsWithCounts.isNotEmpty()) {
                    item {
                        SectionTitle(title = "DISCOGRAPHY")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            items(artistAlbumsWithCounts) { (albumTrack, count) ->
                                ArtistAlbumCard(
                                    track = albumTrack,
                                    trackCount = count,
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
                        viewModel.addNextToQueue(selectedTrack)
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
    trackCount: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .bounceClick { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .shadow(16.dp, MaterialTheme.shapes.large, spotColor = MaterialTheme.aardBlue.copy(0.2f))
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.large
                )
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = "Cover for ${track.album}",
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                error = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier.fillMaxSize()
            )
            
            // --- TRACK COUNT BADGE ---
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$trackCount",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = track.album,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = track.year ?: "RECORDED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.aardBlue,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

