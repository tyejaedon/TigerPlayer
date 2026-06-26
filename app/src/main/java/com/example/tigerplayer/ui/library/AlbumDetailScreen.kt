package com.example.tigerplayer.ui.library

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresExtension
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.DominantColorExtractor
import com.example.tigerplayer.ui.theme.PremiumGlassCard
import com.example.tigerplayer.ui.theme.TigerNeonOrange
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.rememberTigerAmbientGradient
import kotlinx.coroutines.launch

@RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 15)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailsScreen(
    albumName: String,
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val colorScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val playlists by viewModel.customPlaylists.collectAsState(initial = emptyList())

    var trackForOptions by remember { mutableStateOf<AudioTrack?>(null) }

    val albumTracks = remember(uiState.tracks, albumName) {
        uiState.tracks.filter { it.album == albumName }.sortedBy { it.trackNumber }
    }
    val firstTrack = albumTracks.firstOrNull()

    // --- DYNAMIC COLOR EXTRACTION ---
    var dominantColor by remember(albumName, firstTrack?.artworkUri) { mutableStateOf(TigerNeonOrange) }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(1000),
        label = "AlbumColorAnimation"
    )
    val ambientBrush = rememberTigerAmbientGradient(animatedDominantColor, baseTopAlpha = 0.20f)

    val imageRequest = remember(firstTrack?.artworkUri) {
        ImageRequest.Builder(context)
            .data(firstTrack?.artworkUri)
            .crossfade(true)
            .allowHardware(false)
            .listener(
                onSuccess = { _, result ->
                    colorScope.launch {
                        dominantColor = DominantColorExtractor.extractSnappedNeon(
                            drawable = result.drawable,
                            fallback = TigerNeonOrange
                        )
                    }
                },
                onError = { _, _ ->
                    dominantColor = TigerNeonOrange
                }
            )
            .build()
    }

// --- THE CONTRAST RITUAL ---
    val accentColor = remember(dominantColor) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(dominantColor.toArgb(), hsl)

        // Check Lightness (hsl[2]). If it's below 50%, it's too dark for text on dark backgrounds.
        if (hsl[2] < 0.5f) {
            hsl[2] = 0.75f // Force it to be bright and punchy
            hsl[1] = (hsl[1] + 0.15f).coerceAtMost(1f) // Boost saturation for that "Witcher" glow
            Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
        } else {
            dominantColor
        }
    }
    // --- ROOT LAYER ---
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. DYNAMIC GRADIENT BACKGROUND
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ambientBrush)
        )

        // 2. SCROLLABLE CONTENT
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = albumName,
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
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.glassEffect(RectangleShape)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 180.dp) // Extra padding so the last song clears the floating button
            ) {
                // The Hero Image
                item {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Cover for $albumName",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(16.dp)
                            // THE FIX 1: Shadow goes BEFORE clip
                            .shadow(24.dp, MaterialTheme.shapes.extraLarge)
                            .clip(MaterialTheme.shapes.extraLarge),
                        contentScale = ContentScale.Crop
                    )
                }

                item {
                    // THE GLASS BUBBLE HEADER
                    PremiumGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    accentColor.copy(alpha = 0.15f),
                                    MaterialTheme.shapes.extraLarge
                                )
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = albumName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                letterSpacing = (-1).sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = firstTrack?.artist?.uppercase() ?: "UNKNOWN ARTIST",
                                style = MaterialTheme.typography.labelLarge,
                                color = accentColor,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // --- NEW METADATA ROW ---
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                MetadataItem(
                                    label = "CHANTS",
                                    value = albumTracks.size.toString(),
                                    accentColor = accentColor
                                )
                                MetadataItem(
                                    label = "DURATION",
                                    value = formatTotalDuration(albumTracks.sumOf { it.durationMs }),
                                    accentColor = accentColor
                                )
                                firstTrack?.year?.let { year ->
                                    MetadataItem(
                                        label = "RELEASED",
                                        value = year,
                                        accentColor = accentColor
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // THE TRACKS
                itemsIndexed(albumTracks) { index, track ->
                    val isCurrentTrack = uiState.currentTrack?.id == track.id
                    ChapterSongRow(
                        index = index,
                        track = track.copy(),
                        isCurrentTrack = isCurrentTrack,
                        isPlaying = uiState.isPlaying,
                        // THE QUEUE FIX: Load the entire album and start from the tapped track index
                        onClick = { viewModel.setPlaylistAndPlay(albumTracks, index) },
                        // OPTIONS FIX: Correctly pass the selected track
                        onOptionsClick = { trackForOptions = track }
                    )
                }
            }
            // --- 3. THE OPTIONS PORTAL ---
            trackForOptions?.let { selectedTrack ->
                SongOptionsSheet(
                    track = selectedTrack,
                    playlists = playlists,
                    onDismiss = { trackForOptions = null },
                    onPlayNext = {
                        viewModel.addNextToQueue(selectedTrack)
                    },
                    onAddToPlaylist = { playlistId ->
                        viewModel.addTrackToPlaylist(playlistId, selectedTrack)
                    },
                    onGoToAlbum = { albumName ->
                        Toast.makeText(context, "Album: $albumName", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // 3. THE FLOATING "START RITUAL" BUTTON
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Button(
                // Use the viewmodel's decoupled wrapper function
                onClick = { viewModel.setPlaylistAndPlay(albumTracks, 0) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(64.dp)
                    .shadow(16.dp, CircleShape, spotColor = accentColor)
                    .bounceClick { viewModel.setPlaylistAndPlay(albumTracks, 0) }
            ) {
                Icon(WitcherIcons.Play, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "START RITUAL",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun MetadataItem(label: String, value: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = accentColor,
            fontWeight = FontWeight.Black
        )
    }
}

private fun formatTotalDuration(ms: Long): String {
    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}