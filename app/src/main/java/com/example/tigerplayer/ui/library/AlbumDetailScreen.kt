package com.example.tigerplayer.ui.library

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.RectangleShape
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
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailsScreen(
    albumName: String,
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val albumTracks = remember(uiState.tracks, albumName) {
        uiState.tracks.filter { it.album == albumName }.sortedBy { it.trackNumber }
    }
    val firstTrack = albumTracks.firstOrNull()

    // --- DYNAMIC COLOR EXTRACTION ---
    val fallbackColor = MaterialTheme.colorScheme.background
    var dominantColor by remember { mutableStateOf(fallbackColor) }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(1000),
        label = "AlbumColorAnimation"
    )

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

    val accentColor = if (dominantColor == fallbackColor) MaterialTheme.colorScheme.primary else animatedDominantColor

    // --- ROOT LAYER ---
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. DYNAMIC GRADIENT BACKGROUND
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            animatedDominantColor.copy(alpha = 0.5f),
                            fallbackColor
                        ),
                        startY = 0f,
                        endY = 1500f
                    )
                )
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .glassEffect(MaterialTheme.shapes.extraLarge)
                            .background(accentColor.copy(alpha = 0.15f), MaterialTheme.shapes.extraLarge)
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = firstTrack?.artist ?: "Unknown Artist",
                            style = MaterialTheme.typography.titleLarge,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // THE TRACKS
                itemsIndexed(albumTracks) { index, track ->
                    val isCurrentTrack = uiState.currentTrack?.id == track.id

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            // THE FIX 2: Replace .clickable with .bounceClick
                            .bounceClick { viewModel.playTrack(track) }
                            .clip(MaterialTheme.shapes.extraLarge)
                            .glassEffect(MaterialTheme.shapes.extraLarge)
                            .border(
                                width = 1.dp,
                                color = if (isCurrentTrack) accentColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f),
                                shape = MaterialTheme.shapes.extraLarge
                            ),
                        color = if (isCurrentTrack) accentColor.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (isCurrentTrack) accentColor else Color.White.copy(alpha = 0.1f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (isCurrentTrack) Color.White else MaterialTheme.colorScheme.onBackground
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isCurrentTrack) accentColor else MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
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
                onClick = { viewModel.mediaControllerManager.setPlaylistAndPlay(albumTracks, 0) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(64.dp)
                    .shadow(16.dp, CircleShape, spotColor = accentColor)
                // THE FIX 3: Removed redundant .bounceClick
            ) {
                Icon(WitcherIcons.Play, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "START RITUAL",
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}