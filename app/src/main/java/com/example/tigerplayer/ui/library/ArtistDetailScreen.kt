package com.example.tigerplayer.ui.library

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.tigerplayer.ui.home.SearchResultItem
import com.example.tigerplayer.ui.home.SectionTitle
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.utils.ArtistUtils
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailsScreen(
    artistName: String,
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val artistDetails by viewModel.artistDetails.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val profile = artistDetails[artistName]
    val artistTracks = remember(uiState.tracks, artistName) {
        uiState.tracks.filter { track ->
            ArtistUtils.getBaseArtist(track.artist).equals(artistName, ignoreCase = true)
        }
    }

    LaunchedEffect(artistName) {
        viewModel.fetchArtistProfile(artistName)
    }

    val imageUrl = profile?.imageUrl ?: artistTracks.firstOrNull()?.artworkUri
    val fallbackColor = MaterialTheme.colorScheme.background
    var dominantColor by remember { mutableStateOf(fallbackColor) }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 1000),
        label = "DominantColorAnimation"
    )

    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
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
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = artistName,
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
                        containerColor = Color.Transparent
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
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // 1. HERO IMAGE
                item {
                    ArtistHeroImage(imageRequest, artistName)
                }

                // 2. GENRE CLOUD
                if (profile?.genres?.isNotEmpty() == true) {
                    item {
                        ArtistGenreCloud(profile.genres)
                    }
                }

                // 3. VANGUARD STATS & BIO
                item {
                    ArtistVanguardStats(profile)
                }

                // 4. LOCAL ARCHIVES
                if (artistTracks.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        SectionTitle(title = "Local Archives")
                    }

                    items(artistTracks) { track ->
                        SearchResultItem(
                            track = track,
                            isAlbum = false,
                            onClick = { viewModel.playTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

// --- EXTRACTED COMPONENTS ---

@Composable
private fun ArtistHeroImage(imageRequest: ImageRequest, artistName: String) {
    AsyncImage(
        model = imageRequest,
        contentDescription = "Image of $artistName",
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .padding(16.dp)
            .shadow(24.dp, MaterialTheme.shapes.extraLarge)
            .clip(MaterialTheme.shapes.extraLarge),
        contentScale = ContentScale.Crop
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtistGenreCloud(genres: List<String>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        genres.take(4).forEach { genre ->
            ArtistMetadataBadge(text = genre, isHighlight = true)
        }
    }
}

@Composable
private fun ArtistVanguardStats(profile: ArtistDetails?) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .glassEffect(MaterialTheme.shapes.large)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                MaterialTheme.shapes.large
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VANGUARD STATS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            val formattedListeners = NumberFormat.getNumberInstance(Locale.US).format(profile?.popularity ?: 0)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtistMetadataBadge(
                    text = "LISTENERS: $formattedListeners",
                    textColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val bio = profile?.bio
        if (!bio.isNullOrBlank()) {
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Consulting the Archives...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
private fun ArtistMetadataBadge(
    text: String,
    isHighlight: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        color = if (isHighlight) textColor.copy(alpha = 0.2f) else textColor.copy(alpha = 0.1f),
        shape = CircleShape,
        border = BorderStroke(
            width = 1.dp,
            color = if (isHighlight) textColor else textColor.copy(alpha = 0.3f)
        )
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            letterSpacing = 1.sp
        )
    }
}