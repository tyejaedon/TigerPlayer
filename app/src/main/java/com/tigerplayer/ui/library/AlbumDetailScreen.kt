@file:SuppressLint("NewApi")
package com.tigerplayer.ui.library

import android.annotation.SuppressLint
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresExtension
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
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
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.player.PlayerViewModel
import com.tigerplayer.ui.theme.PremiumGlassCard
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.WitcherIcons
import com.tigerplayer.ui.theme.bounceClick
import com.tigerplayer.ui.theme.glassEffect
import com.tigerplayer.ui.theme.rememberTigerAmbientGradient
import com.tigerplayer.ui.theme.tigerGlow
import kotlin.math.sin

@RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 15)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailsScreen(
    albumName: String,
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playlists by viewModel.customPlaylists.collectAsState(initial = emptyList())

    val scrollState = rememberLazyListState()
    val density = LocalDensity.current

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
    val ambientBrush = rememberTigerAmbientGradient(animatedDominantColor, baseTopAlpha = 0.22f)

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

    // Dynamic Top Bar Opacity based on scroll position
    val topBarAlpha by remember {
        derivedStateOf {
            if (firstVisibleIndex > 0) 1f
            else (scrollOffset / 350f).coerceIn(0f, 1f)
        }
    }

    // Continuous subtle ambient float for 3D depth effect
    val infiniteTransition = rememberInfiniteTransition(label = "Ambient3DFloat")
    val ambientFloatOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient3dFloat"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. DYNAMIC 3D ATMOSPHERIC GLASS BACKGROUND
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ambientBrush)
        ) {
            // Parallax Blurred Artwork Background
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val parallaxOffset = if (firstVisibleIndex == 0) scrollOffset * 0.40f else 0f
                        translationY = -parallaxOffset
                        alpha = 0.32f
                    }
                    .blur(80.dp)
            )

            // Animated 3D Light Refraction Orbs
            Canvas(modifier = Modifier.fillMaxSize()) {
                val orbRadius = size.width * 0.6f
                // Top-Left Glowing Refraction Orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.25f, size.height * 0.20f + ambientFloatOffset * 4f),
                        radius = orbRadius
                    ),
                    center = Offset(size.width * 0.25f, size.height * 0.20f + ambientFloatOffset * 4f),
                    radius = orbRadius
                )
                // Center-Right Soft Prism Light Orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.55f - ambientFloatOffset * 3f),
                        radius = orbRadius * 0.9f
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.55f - ambientFloatOffset * 3f),
                    radius = orbRadius * 0.9f
                )
            }
        }

        // 2. MAIN SCROLLABLE CONTENT
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = albumName.uppercase(),
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 2.5.sp,
                            fontSize = 17.sp,
                            modifier = Modifier.graphicsLayer { alpha = topBarAlpha }
                        )
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(42.dp)
                                .clip(CircleShape)
                                .glassEffect(CircleShape)
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = (0.25f * (1f - topBarAlpha * 0.5f)).coerceAtLeast(0.10f)),
                                    CircleShape
                                )
                                .bounceClick { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                WitcherIcons.Back,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = (topBarAlpha * 0.75f).coerceIn(0f, 0.75f)),
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier
                        .glassEffect(RectangleShape)
                        .border(
                            width = 0.5.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.15f * topBarAlpha),
                                    Color.Transparent
                                )
                            ),
                            shape = RectangleShape
                        )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 190.dp)
            ) {
                // --- 1. HERO 3D FLOATING GLASS ARTWORK ---
                item {
                    val heroAlpha = (1f - (scrollOffset / 800f)).coerceIn(0f, 1f)
                    val heroScale = (1f - (scrollOffset / 2600f)).coerceIn(0.88f, 1f)
                    val rotationX = (scrollOffset / 35f).coerceIn(0f, 16f) + (ambientFloatOffset * 0.2f)
                    val rotationY = sin(scrollOffset / 120f) * 3f

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp, start = 28.dp, end = 28.dp, bottom = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient Projected Backdrop Glow
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    alpha = heroAlpha * 0.75f
                                    scaleX = heroScale * 1.06f
                                    scaleY = heroScale * 1.06f
                                }
                                .shadow(
                                    56.dp,
                                    RoundedCornerShape(36.dp),
                                    spotColor = accentColor.copy(alpha = 0.70f),
                                    ambientColor = Color.Black
                                )
                                .background(accentColor.copy(alpha = 0.25f), RoundedCornerShape(36.dp))
                        )

                        // 3D Glass Framed Artwork
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    alpha = heroAlpha
                                    scaleX = heroScale
                                    scaleY = heroScale
                                    this.rotationX = rotationX
                                    this.rotationY = rotationY
                                    cameraDistance = 16f * density.density
                                }
                                .clip(RoundedCornerShape(32.dp))
                                .border(
                                    width = 1.5.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.50f),
                                            accentColor.copy(alpha = 0.30f),
                                            Color.White.copy(alpha = 0.10f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(32.dp)
                                )
                        ) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = "Cover for $albumName",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // 3D Glass Specular Reflection Streak Across Artwork
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.25f),
                                                Color.White.copy(alpha = 0.05f),
                                                Color.Transparent
                                            ),
                                            start = Offset(0f, 0f),
                                            end = Offset(400f, 400f)
                                        )
                                    )
                            )
                        }
                    }
                }

                // --- 2. THE 3D FLOATING GLASS HEADER CARD ---
                item {
                    val headerTranslationY = (scrollOffset * 0.10f).coerceAtMost(28f)

                    PremiumGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .graphicsLayer {
                                translationY = -headerTranslationY
                            },
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            accentColor.copy(alpha = 0.16f),
                                            Color.Black.copy(alpha = 0.25f)
                                        )
                                    ),
                                    MaterialTheme.shapes.extraLarge
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.25f),
                                            accentColor.copy(alpha = 0.15f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = MaterialTheme.shapes.extraLarge
                                )
                                .padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = albumName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                letterSpacing = (-0.5).sp,
                                color = MaterialTheme.colorScheme.onSurface
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

                            // --- 3D FLOATING METADATA BADGES ---
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                GlassMetadataBadge(
                                    label = "CHANTS",
                                    value = albumTracks.size.toString(),
                                    accentColor = accentColor
                                )
                                GlassMetadataBadge(
                                    label = "DURATION",
                                    value = formatTotalDuration(albumTracks.sumOf { it.durationMs }),
                                    accentColor = accentColor
                                )
                                firstTrack?.year?.let { year ->
                                    if (year.isNotBlank()) {
                                        GlassMetadataBadge(
                                            label = "RELEASED",
                                            value = year,
                                            accentColor = accentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // --- 3. THE 3D GLASS TRACK LIST ---
                itemsIndexed(
                    items = albumTracks,
                    key = { index, track -> "${track.id}_$index" }
                ) { index, track ->
                    val isCurrentTrack = uiState.currentTrack?.id == track.id
                    Glass3DTrackRow(
                        index = index,
                        track = track,
                        isCurrentTrack = isCurrentTrack,
                        isPlaying = uiState.isPlaying,
                        accentColor = accentColor,
                        onClick = { viewModel.setPlaylistAndPlay(albumTracks, index) },
                        onOptionsClick = { trackForOptions = track }
                    )
                }
            }

            // Options sheet for selected track
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

        // --- 4. FLOATING 3D GLASS CONTROL DOCK ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .shadow(
                        28.dp,
                        CircleShape,
                        spotColor = accentColor.copy(alpha = 0.55f),
                        ambientColor = Color.Black
                    )
                    .glassEffect(CircleShape)
                    .border(
                        width = 1.2.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.40f),
                                accentColor.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.15f)
                            )
                        ),
                        shape = CircleShape
                    )
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // "START RITUAL" Primary Play Button
                    Button(
                        onClick = { viewModel.setPlaylistAndPlay(albumTracks, 0) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .bounceClick { viewModel.setPlaylistAndPlay(albumTracks, 0) }
                    ) {
                        Icon(
                            WitcherIcons.Play,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "START RITUAL",
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            letterSpacing = 1.5.sp
                        )
                    }

                    // "SHUFFLE" Glass Action Button
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .glassEffect(CircleShape)
                            .background(accentColor.copy(alpha = 0.12f))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.20f),
                                CircleShape
                            )
                            .bounceClick {
                                if (albumTracks.isNotEmpty()) {
                                    viewModel.setPlaylistAndPlay(albumTracks.shuffled(), 0)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            WitcherIcons.Shuffle,
                            contentDescription = "Shuffle Album",
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------
// 3D GLASS METADATA BADGE
// ------------------------------
@Composable
private fun GlassMetadataBadge(
    label: String,
    value: String,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .glassEffect(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontSize = 9.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = accentColor,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
        }
    }
}

// ------------------------------
// 3D GLASS TRACK ROW
// ------------------------------
@Composable
private fun Glass3DTrackRow(
    index: Int,
    track: AudioTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    val displayIndex = (index + 1).toString().padStart(2, '0')
    val displayDuration = remember(track.durationMs) { formatTotalDuration(track.durationMs) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .then(
                if (isCurrentTrack) Modifier.tigerGlow(accentColor) else Modifier
            )
            .clip(RoundedCornerShape(20.dp))
            .glassEffect(RoundedCornerShape(20.dp))
            .background(
                if (isCurrentTrack) {
                    Brush.horizontalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.18f),
                            accentColor.copy(alpha = 0.06f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.04f),
                            Color.White.copy(alpha = 0.01f)
                        )
                    )
                }
            )
            .border(
                width = if (isCurrentTrack) 1.2.dp else 1.dp,
                brush = if (isCurrentTrack) {
                    Brush.horizontalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.60f),
                            accentColor.copy(alpha = 0.20f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.03f)
                        )
                    )
                },
                shape = RoundedCornerShape(20.dp)
            )
            .bounceClick { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Index / Active Equalizer Capsule
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .glassEffect(CircleShape)
                    .background(
                        if (isCurrentTrack) accentColor.copy(alpha = 0.20f)
                        else Color.White.copy(alpha = 0.05f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentTrack && isPlaying) {
                    Icon(
                        imageVector = WitcherIcons.VolumeUp,
                        contentDescription = "Playing",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = displayIndex,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCurrentTrack) accentColor
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Track Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrentTrack) FontWeight.Black else FontWeight.Bold,
                    color = if (isCurrentTrack) accentColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = " • $displayDuration",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Options Icon Button
            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.03f))
            ) {
                Icon(
                    imageVector = WitcherIcons.Options,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatTotalDuration(ms: Long): String {
    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
}
