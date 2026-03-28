package com.example.tigerplayer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.components.DiscoverCarousel
import com.example.tigerplayer.ui.components.RecentlyPlayedRow
import com.example.tigerplayer.ui.library.*
import com.example.tigerplayer.ui.player.LibraryArtist
import com.example.tigerplayer.ui.player.PlayerUiState
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue

// --- VANGUARD THEME CONSTANTS ---
private val AardBlue = Color(0xFF4FC3F7)
private val IgniRed = Color(0xFFFF5252)
private val SpotifyGreen = Color(0xFF1DB954)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigatetoArtist: (String) -> Unit
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
    val query = uiState.searchQuery
// Calculate the heavy lists here using `remember` so it ONLY runs when the query actually changes!
    val matchedArtists = remember(query, uiState.artists) {
        uiState.artists.filter { it.name.contains(query, ignoreCase = true) }
    }
    val matchedAlbums = remember(query, uiState.tracks) {
        uiState.tracks
            .filter { it.album.contains(query, ignoreCase = true) }
            .distinctBy { it.album.lowercase() }
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

            if (query.isNotEmpty()) {
                renderSearchResults(
                    uiState = uiState,
                    viewModel = viewModel,
                    matchedArtists = matchedArtists, // Pass the remembered list
                    matchedAlbums = matchedAlbums,   // Pass the remembered list
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigatetoArtist = onNavigatetoArtist
                )
            } else {
                // --- 3. THE ANALYTICS ---
                item {
                    UserStatisticsHeader(
                        statistics = homeState.statistics,
                        onClick = { isStatsExpanded = true }
                    )
                }

                // --- 4. THE VANGUARD ---
                if (homeState.discoverTracks.isNotEmpty()) {
                    item {
                        DiscoverCarousel(
                            tracks = homeState.discoverTracks,
                            onTrackClick = { viewModel.playTrack(it) }
                        )
                    }
                }
                if (homeState.recommendedTracks.isNotEmpty()) {
                    item { SectionTitle("RECOMMENDED VOLUMES") }
                    item {
                        // Calculate unique albums once before passing to the row
                        val uniqueAlbums = remember(homeState.recommendedTracks) {
                            homeState.recommendedTracks.distinctBy { it.album }
                        }

                        RecommendedAlbumsRow(
                            albums = uniqueAlbums,
                            onAlbumClick = { track -> onNavigateToAlbum(track.album) }
                        )
                    }
                }

                // --- 5. RECENT RITUALS ---
                if (homeState.recentlyPlayedTracks.isNotEmpty()) {
                    item {
                        RecentlyPlayedRow(
                            tracks = homeState.recentlyPlayedTracks,
                            onTrackClick = { viewModel.playTrack(it) },
                            viewModel = viewModel,
                            modifier = Modifier
                        )
                    }
                } // <- THE FIX: Removed the rogue extra brace that was here

                // --- 6. RECOMMENDED VOLUMES (The Premium Snap Row) ---

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
    matchedArtists: List<LibraryArtist>, // Received from parent
    matchedAlbums: List<AudioTrack>, // Received from parent
    onNavigateToAlbum: (String) -> Unit,
    onNavigatetoArtist: (String) -> Unit
) {
    val query = uiState.searchQuery

    if (matchedArtists.isEmpty() && matchedAlbums.isEmpty() && uiState.filteredTracks.isEmpty()) {
        item { SearchEmptyState(query) }
    } else {

        // --- ARTISTS ---
        if (matchedArtists.isNotEmpty()) {
            item { SearchSectionTitle("THE VANGUARD") }
            items(
                items = matchedArtists,
            ) { artist ->
                // Adding Modifier.animateItem() here (if supported by your Compose version)
                // makes them slide in/out beautifully as the user types.
                ArtistSearchRow(
                    artist = artist,
                    modifier = Modifier.animateItem(),
                    onClick = { onNavigatetoArtist(artist.name) }
                )
            }
        }

        // --- ALBUMS ---
        if (matchedAlbums.isNotEmpty()) {
            item { SearchSectionTitle("THE VOLUMES") }
            items(
                items = matchedAlbums,
                // Using the album name as a key since tracks in this list were filtered by distinct album
                key = { track -> "album_${track.album}" }
            ) { track ->
                AlbumSearchRow(
                    track = track,
                    modifier = Modifier.animateItem(),
                    onClick = { onNavigateToAlbum(track.album) }
                )
            }
        }

        // --- TRACKS ---
        if (uiState.filteredTracks.isNotEmpty()) { // THE FIX: Only show if tracks exist
            item { SearchSectionTitle("CHANTS") }
            items(
                items = uiState.filteredTracks,
                key = { track -> "track_${track.id}" } // STABLE KEY
            ) { track ->
                SongItem(
                    track = track,
                    isActive = uiState.currentTrack?.id == track.id,
                    isPlaying = uiState.isPlaying,
                    modifier = Modifier.animateItem(),
                    onClick = { viewModel.playTrack(track) },
                    onMoreClick = { /* Track Options Portal */ }
                )
            }
        }
    }
}

/**
 * RECTIFIED RECOMMENDED CARD: High-visibility grid style
 */


@Composable
fun RecommendedAlbumCard(
    track: AudioTrack,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // THE FIX 2: Lock the string allocation in memory so the 3D scroll doesn't stutter
    val uppercaseArtist = remember(track.artist) { track.artist.uppercase() }

    Box(
        // We chain the incoming 3D graphicsLayer modifier here
        modifier = modifier
            // THE FIX 1: Let the Pager control the width for perfect 3D math
            .fillMaxWidth()
            .height(210.dp)
            .shadow(
                elevation = 20.dp,
                shape = MaterialTheme.shapes.extraLarge,
                spotColor = MaterialTheme.aardBlue.copy(alpha = 0.4f)
            )
            .clip(MaterialTheme.shapes.extraLarge)
            // THE FIX 3: bounceClick MUST come after clip so the ripple respects the glass curves
            .bounceClick { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                ),
                shape = MaterialTheme.shapes.extraLarge
            )
    ) {
        // --- LAYER 1: THE CANVAS ---
        AsyncImage(
            model = track.artworkUri,
            contentDescription = "Cover for ${track.album}",
            contentScale = ContentScale.Crop,
            fallback = painterResource(R.drawable.ic_tiger_logo),
            modifier = Modifier.fillMaxSize()
        )

        // --- LAYER 2: THE DEEP SCRIM ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.5f to Color.Black.copy(alpha = 0.2f),
                        1.0f to Color.Black.copy(alpha = 0.95f)
                    )
                )
        )

        // --- LAYER 3: FOREGROUND METADATA & ACTION ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = track.album,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = uppercaseArtist, // THE FIX 2: Using the cached string here
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.aardBlue.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // The Glass Action Anchor
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .glassEffect(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = WitcherIcons.Play,
                    contentDescription = null,
                    tint = MaterialTheme.aardBlue,
                    modifier = Modifier.size(16.dp).padding(start = 2.dp)
                )
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecommendedAlbumsRow(
    albums: List<AudioTrack>,
    onAlbumClick: (AudioTrack) -> Unit
) {
    // PagerState gives us absolute precision over which item is centered
    val pagerState = rememberPagerState(pageCount = { albums.size })

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        // Exposes the edges of the previous/next cards on the sides of the screen
        contentPadding = PaddingValues(horizontal = 56.dp),
        // A negative page spacing pulls the cards slightly over each other
        // to enhance the 3D overlapping wheel effect.
        pageSpacing = (-24).dp
    ) { page ->
        val track = albums[page]

        // --- THE 3D ROTATION MATH ---
        // Calculates how far this specific card is from the exact center of the screen
        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)

        RecommendedAlbumCard(
            track = track,
            modifier = Modifier.graphicsLayer {
                // 1. The Perspective Depth
                // Adjusts the camera distance so the 3D rotation doesn't warp unnaturally
                cameraDistance = 12f * density

                // 2. The iPod Wheel Rotation
                // Rotates the card on the Y-axis up to 45 degrees based on its distance from center
                rotationY = pageOffset.coerceIn(-1f, 1f) * -45f

                // 3. The Push-Back Scale
                // Shrinks the side cards to 85% of their size to push them into the background
                val scale = 1f - (absOffset * 0.15f)
                scaleX = scale
                scaleY = scale

                // 4. The Glass Fade
                // Dims the cards that are out of focus
                alpha = 1f - (absOffset * 0.5f)
            },
            onClick = { onAlbumClick(track) }
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // THE FIX 1: Let glassEffect handle the clip, the background, AND the border.
            // No more double-stacking modifiers.
            .glassEffect(MaterialTheme.shapes.extraLarge)
            .bounceClick { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ARCHIVE ANALYTICS",
                    style = MaterialTheme.typography.labelLarge,
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

        Spacer(modifier = Modifier.height(16.dp))

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
    Box(
        modifier = modifier
            .height(if (isFullWidth) 72.dp else 88.dp)
            // THE FIX 2: Lowered elevation so it doesn't fight the parent glass,
            // and removed ambientColor = Color.Black so the glow stays pure.
            .shadow(
                elevation = 0.dp,
                shape = MaterialTheme.shapes.large,
                spotColor = accentColor
            )
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
                    )
                )
            )
            // This inner border is fine because this specific widget doesn't use `glassEffect`
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.large
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isFullWidth) 40.dp else 44.dp)
                    .background(accentColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
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
        style = MaterialTheme.typography.titleLarge, // Semantic match for 18sp
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically // Ensures the Row children stay aligned
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

            // --- SEARCH INPUT (The Fix) ---
            this@Row.AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
            ) {
                // BasicTextField strips away the rigid Material padding
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp) // Now perfectly respects the 48.dp height
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                        .glassEffect(CircleShape),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(AardBlue),
                    decorationBox = { innerTextField ->
                        // This Row acts as your custom search bar layout
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp), // Perfect horizontal padding
                            verticalAlignment = Alignment.CenterVertically // Centers text dead-center
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search archives...",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                // This is where the actual typed text gets rendered
                                innerTextField()
                            }

                            // Trailing Icon
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.size(24.dp) // Tighter tap target to fit 48dp
                                ) {
                                    Icon(
                                        imageVector = WitcherIcons.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
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

