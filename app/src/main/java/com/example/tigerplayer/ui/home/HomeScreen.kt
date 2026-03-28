package com.example.tigerplayer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.components.DiscoverCarousel
import com.example.tigerplayer.ui.components.RecentlyPlayedRow
import com.example.tigerplayer.ui.library.*
import com.example.tigerplayer.ui.player.PlayerUiState
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

// --- VANGUARD THEME CONSTANTS ---
private val AardBlue = Color(0xFF4FC3F7)
private val IgniRed = Color(0xFFFF5252)
private val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val homeState by viewModel.homeUiState.collectAsState()
    var isStatsExpanded by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    BackHandler(enabled = isStatsExpanded || isSearchActive) {
        if (isStatsExpanded) isStatsExpanded = false
        else if (isSearchActive) {
            isSearchActive = false
            viewModel.clearSearch()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // --- 1. THE HEADER RITUAL ---
            item {
                HomeHeader(
                    title = "TIGER PLAYER",
                    searchQuery = uiState.searchQuery,
                    isSearchActive = isSearchActive,
                    onSearchToggle = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) viewModel.clearSearch()
                    },
                    onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onSettingsClick = onNavigateToSettings
                )
            }

            if (uiState.searchQuery.isNotEmpty()) {
                // --- 2. THE SEARCH VISION (Rectified: Flattened List) ---
                renderSearchResults(uiState, viewModel, onNavigateToAlbum)
            } else {
                // --- 3. THE ANALYTICS (Glow Enhanced) ---
                item {
                    UserStatisticsHeader(
                        statistics = homeState.statistics,
                        onClick = { isStatsExpanded = true }
                    )
                }

                // --- 4. THE VANGUARD (Carousel) ---
                if (homeState.discoverTracks.isNotEmpty()) {
                    item { SectionTitle("THE VANGUARD") }
                    item {
                        DiscoverCarousel(
                            tracks = homeState.discoverTracks,
                            onTrackClick = { viewModel.playTrack(it) }
                        )
                    }
                }

                // --- 5. RECENT RITUALS ---
                if (homeState.recentlyPlayedTracks.isNotEmpty()) {
                    item { SectionTitle("RECENT RITUALS") }
                   item {
                       RecentlyPlayedRow(
                           tracks = homeState.recentlyPlayedTracks,
                           onTrackClick = { viewModel.playTrack(it) },
                           viewModel = viewModel,
                           modifier = Modifier
                       )
                   }
                    }
                }

                // --- 6. RECOMMENDED VOLUMES (Rectified UI) ---
                if (homeState.recommendedTracks.isNotEmpty()) {
                    item { SectionTitle("RECOMMENDED VOLUMES") }
                    item {
                        val uniqueAlbums = homeState.recommendedTracks.distinctBy { it.album }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            items(uniqueAlbums) { track ->
                                RecommendedAlbumCard(
                                    track = track,
                                    onClick = { onNavigateToAlbum(track.album) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Stats Overlay
        AnimatedVisibility(
            visible = isStatsExpanded,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            ExpandedStatsScreen(viewModel = viewModel, onClose = { isStatsExpanded = false })
        }
    }


// ==========================================
// --- RECTIFIED COMPONENTS ---
// ==========================================

/**
 * RECTIFIED SEARCH: Flattened into the main LazyColumn to fix scroll issues.
 */
private fun LazyListScope.renderSearchResults(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onNavigateToAlbum: (String) -> Unit
) {
    val query = uiState.searchQuery
    val matchedArtists = uiState.artists.filter { it.name.contains(query, ignoreCase = true) }
    val matchedAlbums = uiState.tracks
        .filter { it.album.contains(query, ignoreCase = true) }
        .distinctBy { it.album.lowercase() }

    if (matchedArtists.isEmpty() && matchedAlbums.isEmpty() && uiState.filteredTracks.isEmpty()) {
        item { SearchEmptyState(query) }
    } else {
        if (matchedArtists.isNotEmpty()) {
            item { SearchSectionTitle("THE VANGUARD") }
            items(matchedArtists) { artist ->
                ArtistSearchRow(artist) { /* Navigate */ }
            }
        }
        if (matchedAlbums.isNotEmpty()) {
            item { SearchSectionTitle("THE VOLUMES") }
            items(matchedAlbums) { track ->
                AlbumSearchRow(track) { onNavigateToAlbum(track.album) }
            }
        }
        item { SearchSectionTitle("CHANTS") }
        items(uiState.filteredTracks) { track ->
            SongItem(
                track = track,
                isActive = uiState.currentTrack?.id == track.id,
                isPlaying = uiState.isPlaying,
                onClick = { viewModel.playTrack(track) },

                onMoreClick = { /* Track Options Portal */ }
            )
        }
    }
}

/**
 * RECTIFIED RECOMMENDED CARD: High-visibility grid style
 */


@Composable
fun RecommendedAlbumCard(track: AudioTrack, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .bounceClick { onClick() }
            .padding(bottom = 8.dp) // Prevents the shadow from clipping into items below it
    ) {
        // --- THE TACTILE VIEWPORT ---
        Box(
            modifier = Modifier
                .size(160.dp)
                // 1. The Shadow Bleed (Must be before clip)
                // Using a slight AardBlue tint for the shadow makes the UI feel alive
                .shadow(
                    elevation = 16.dp,
                    shape = MaterialTheme.shapes.large,
                    spotColor = MaterialTheme.aardBlue.copy(alpha = 0.4f)
                )
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                // Inner armor border for contrast
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), MaterialTheme.shapes.large)
        ) {
            // The Canvas
            AsyncImage(
                model = track.artworkUri,
                contentDescription = "Cover for ${track.album}",
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier.fillMaxSize()
            )

            // 2. The Gradient Grounding
            // Ensures the glass button always has enough contrast, even on white album covers
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            startY = 200f // Starts the gradient near the bottom
                        )
                    )
            )

            // 3. The Glass Action Anchor
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(32.dp)
                    .glassEffect(CircleShape) // Uses your custom frosted glass modifier
                    .background(Color.Black.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = WitcherIcons.Play,
                    contentDescription = null,
                    tint = MaterialTheme.aardBlue,
                    // Nudge the play icon slightly right so it looks optically centered
                    modifier = Modifier.size(14.dp).padding(start = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- TYPOGRAPHY ---
        Text(
            text = track.album,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            // Adaptive tinting
            color = MaterialTheme.aardBlue.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun UserStatisticsHeader(
    statistics: UserStatistics,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp) // Tighter vertical
            .glassEffect(MaterialTheme.shapes.extraLarge)
            .bounceClick { onClick() }
            .padding(16.dp) // Dropped from 24dp for S22 width
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ARCHIVE ANALYTICS",
                    style = MaterialTheme.typography.labelLarge, // 11sp from our new theme
                    color = AardBlue,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Your sessional wisdom",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Icon(WitcherIcons.Expand, null, tint = AardBlue, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.height(16.dp)) // Dropped from 24dp

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatGlassWidget(
                modifier = Modifier.weight(1f),
                title = "CHANTED TODAY",
                value = statistics.listeningTimeToday,
                icon = WitcherIcons.Duration,
                accentColor = AardBlue
            )
            StatGlassWidget(
                modifier = Modifier.weight(1f),
                title = "ARCHIVE SIZE",
                value = "${statistics.totalTracksCount}",
                icon = WitcherIcons.Library,
                accentColor = IgniRed
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        StatGlassWidget(
            modifier = Modifier.fillMaxWidth(),
            title = "PREVAILED ARTIST (THIS WEEK)",
            value = statistics.topArtistThisWeek.uppercase(),
            icon = WitcherIcons.Artist,
            accentColor = SpotifyGreen,
            isFullWidth = true
        )
    }
}
@Composable
fun StatGlassWidget(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isFullWidth: Boolean = false
) {
    Surface(
        modifier = modifier.height(if (isFullWidth) 72.dp else 88.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)) // Slightly thicker for definition
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize() // Force it to use the full height of the Surface
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isFullWidth) 40.dp else 44.dp) // Scaled appropriately
                    .background(accentColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Scaled from 16dp to 20dp. 16dp is too small for a primary visual anchor
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title.uppercase(),
                    // THE FIX: Semantic Typography
                    style = MaterialTheme.typography.labelSmall,
                    // THE FIX: 60% opacity guarantees 4.5:1 contrast ratios on AMOLED
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp, // Makes tiny uppercase text readable
                    maxLines = 1
                )
                Text(
                    text = value,
                    // THE FIX: Semantic Typography for ~16-18sp
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black, // The Vanguard aesthetic
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
@Composable
fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.titleMedium, // Semantic match for 18sp
        // Dimmed slightly to 80% so it doesn't visually overpower the actual album art below it
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp, // THE FIX: Premium spacing for section headers
        modifier = Modifier.padding(
            start = 24.dp, // Aligned with your global screen padding
            end = 24.dp,
            top = 32.dp, // Gives the section above it room to breathe
            bottom = 12.dp
        )
    )
}
@Composable
fun HomeHeader(
    title: String,
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp), // Tighter header
    ) {
        Box(modifier = Modifier.weight(1f)) {
            // --- TITLE DISPLAY ---
            this@Row.AnimatedVisibility(
                visible = !isSearchActive,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            // --- SEARCH INPUT ---
            this@Row.AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp) // Dropped from 52dp
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                        .glassEffect(CircleShape),
                    placeholder = {
                        Text("Search archives...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(WitcherIcons.Close, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = AardBlue
                    ),
                    singleLine = true,
                    shape = CircleShape
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Search Toggle Button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AardBlue)
                .bounceClick { onSearchToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSearchActive) WitcherIcons.Collapse else WitcherIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Settings Medallion
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            Icon(WitcherIcons.Settings, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun AlbumSearchRow(track: AudioTrack, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .glassEffect(MaterialTheme.shapes.large)
            .bounceClick { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reusing our "Armor" logic for the cover
        AsyncImage(
            model = track.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            fallback = painterResource(R.drawable.ic_tiger_logo),
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), MaterialTheme.shapes.medium)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.album,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = AardBlue,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Icon(
            imageVector = WitcherIcons.Library,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            modifier = Modifier.size(20.dp)
        )
    }
}