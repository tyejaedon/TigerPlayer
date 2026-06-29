@file:SuppressLint("NewApi")
package com.example.tigerplayer.ui.library

import android.annotation.SuppressLint
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresExtension
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.player.PlayerViewModel
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

    val scrollState = rememberLazyListState()
    val density = LocalDensity.current

    // Derived values for animations
    val scrollOffset by remember { derivedStateOf { scrollState.firstVisibleItemScrollOffset } }
    val firstVisibleIndex by remember { derivedStateOf { scrollState.firstVisibleItemIndex } }

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
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    bitmap?.let { b ->
                        Palette.from(b).generate { palette ->
                            val colorInt = palette?.vibrantSwatch?.rgb
                                ?: palette?.lightVibrantSwatch?.rgb
                                ?: palette?.dominantSwatch?.rgb
                            
                            colorInt?.let { dominantColor = Color(it) }
                        }
                    }
                },
                onError = { _, _ ->
                    dominantColor = TigerNeonOrange
                }
            )
            .build()
    }

    val accentColor = remember(dominantColor) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(dominantColor.toArgb(), hsl)
        if (hsl[2] < 0.5f) {
            hsl[2] = 0.75f
            hsl[1] = (hsl[1] + 0.15f).coerceAtMost(1f)
            Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
        } else {
            dominantColor
        }
    }

    val topBarAlpha by remember {
        derivedStateOf {
            if (firstVisibleIndex > 0) 1f
            else (scrollOffset / 400f).coerceIn(0f, 1f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. DYNAMIC PARALLAX BACKGROUND
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ambientBrush)
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val parallaxOffset = if (firstVisibleIndex == 0) scrollOffset * 0.45f else 0f
                        translationY = -parallaxOffset
                        alpha = 0.35f
                    }
                    .blur(72.dp)
            )
        }

        // 2. SCROLLABLE CONTENT
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = albumName.uppercase(),
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 2.sp,
                            modifier = Modifier.graphicsLayer { alpha = topBarAlpha }
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .background(
                                    Color.Black.copy(alpha = (0.3f * (1f - topBarAlpha)).coerceAtLeast(0f)),
                                    CircleShape
                                )
                        ) {
                            Icon(WitcherIcons.Back, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = (topBarAlpha * 0.7f).coerceIn(0f, 0.7f)),
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.glassEffect(RectangleShape)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 180.dp)
            ) {
                // The Hero Image (Semi-3D Tilt)
                item {
                    val heroAlpha = (1f - (scrollOffset / 800f)).coerceIn(0f, 1f)
                    val heroScale = (1f - (scrollOffset / 2500f)).coerceIn(0.88f, 1f)
                    val rotationX = (scrollOffset / 40f).coerceIn(0f, 12f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, start = 32.dp, end = 32.dp, bottom = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = "Cover for $albumName",
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    alpha = heroAlpha
                                    scaleX = heroScale
                                    scaleY = heroScale
                                    this.rotationX = rotationX
                                    cameraDistance = 14f * density.density
                                }
                                .shadow(
                                    48.dp,
                                    RoundedCornerShape(32.dp),
                                    spotColor = accentColor.copy(alpha = 0.6f)
                                )
                                .clip(RoundedCornerShape(32.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                item {
                    // THE SEMI-3D FLOATING HEADER
                    val headerTranslationY = (scrollOffset * 0.12f).coerceAtMost(30f)

                    PremiumGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .graphicsLayer {
                                translationY = -headerTranslationY
                            },
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    accentColor.copy(alpha = 0.12f),
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
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = firstTrack?.artist?.uppercase() ?: "UNKNOWN ARTIST",
                                style = MaterialTheme.typography.labelLarge,
                                color = accentColor,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            // --- LAYERED METADATA ROW ---
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
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
                        onClick = { viewModel.setPlaylistAndPlay(albumTracks, index) },
                        onOptionsClick = { trackForOptions = track }
                    )
                }
            }

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
