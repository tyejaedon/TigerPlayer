package com.example.tigerplayer.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.ui.player.PlayerViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.home.SectionTitle
import com.example.tigerplayer.ui.player.LibraryArtist
import com.example.tigerplayer.ui.player.PlayerUiState
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import kotlinx.coroutines.launch
import kotlin.collections.emptyList
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: PlayerViewModel,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToPlaylist: (Long, String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("Songs", "Albums", "Artists", "Playlists")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    var isSearchActive by remember { mutableStateOf(false) }
    val query = uiState.searchQuery

    // THE CACHE RITUAL: Hoist filtering for high-speed scrolling
    val matchedArtists = remember(query, uiState.artists) {
        if (query.isBlank()) emptyList()
        else uiState.artists.filter { it.name.contains(query, ignoreCase = true) }
    }

    val matchedAlbums = remember(query, uiState.tracks) {
        if (query.isBlank()) emptyList()
        else uiState.tracks
            .filter { it.album.contains(query, ignoreCase = true) }
            .distinctBy { it.album.lowercase().trim() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- 1. VANGUARD HEADER (Library Implementation) ---
        LibraryHeader(
            title = "ARCHIVES",
            searchQuery = query,
            isSearchActive = isSearchActive,
            onSearchToggle = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) viewModel.clearSearch()
            },
            onSearchQueryChange = { viewModel.onSearchQueryChanged(it) }
        )

        // --- 2. TAB RITUAL: DYNAMIC FROST ---
        val isDark = isSystemInDarkTheme()
        val aardBlue = MaterialTheme.aardBlue

        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(RectangleShape),
            containerColor = Color.Transparent,
            edgePadding = 16.dp,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    height = 2.dp,
                    color = aardBlue
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = pagerState.currentPage == index
                val tabColor = if (isSelected) aardBlue
                else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.7f else 0.4f)

                Tab(
                    selected = isSelected,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = title.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = tabColor,
                            letterSpacing = 1.5.sp
                        )
                    }
                )
            }
        }

        // --- 3. THE VIEWPORT ---
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (query.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    renderSearchResults(
                        uiState = uiState,
                        viewModel = viewModel,
                        matchedArtists = matchedArtists,
                        matchedAlbums = matchedAlbums,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigatetoArtist = onNavigateToArtist
                    )
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> SongsTab(viewModel, onNavigateToAlbum)
                        1 -> AlbumsTab(viewModel, onNavigateToAlbum)
                        2 -> ArtistsList(viewModel, onNavigateToArtist)
                        3 -> PlaylistsTab(viewModel, onNavigateToPlaylist)
                    }
                }
            }
        }
    }
}
@Composable
fun AlbumsTab(
    viewModel: PlayerViewModel,
    onNavigateToAlbum: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val albums = uiState.albums

    // 1. THE SHARED GRID STATE: Essential for tracking scroll velocity and position
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        // THE FIX: Explicit padding labels to avoid the compilation error
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 24.dp,
            bottom = 140.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp) // Extra vertical space for 3D tilting
    ) {
        items(
            items = albums,
            // Salted key for absolute list stability
            key = { albumName -> "alb_${albumName.hashCode()}" }
        ) { albumName ->
            // --- DATA RITUAL ---
            val albumTrack = remember(albumName, uiState.tracks) {
                uiState.tracks.find { it.album == albumName }
            }
            val trackCount = remember(albumName, uiState.tracks) {
                uiState.tracks.count { it.album == albumName }
            }

            // --- 2. THE GEAR ROTATION MATH ---
            var itemYOffset by remember { mutableStateOf(0f) }

            AlbumGridCard(
                title = albumName,
                artist = albumTrack?.artist ?: "Unknown Artist",
                artworkUri = albumTrack?.artworkUri,
                trackCount = trackCount,
                modifier = Modifier
                    .animateItem() // Keeps grid changes smooth
                    .onGloballyPositioned { coordinates ->
                        // Tracks the item's vertical center relative to the window
                        itemYOffset = coordinates.positionInWindow().y + (coordinates.size.height / 2)
                    }
                    .graphicsLayer {
                        // We calculate the offset relative to the middle of the screen
                        // On an S22, roughly 1000px is the vertical center
                        val viewportCenter = size.height * 2.5f
                        val distanceFromCenter = (itemYOffset - viewportCenter) / viewportCenter
                        val coercedOffset = distanceFromCenter.coerceIn(-1f, 1f)

                        // 3D Tilt: The Gear Wheel Rotation
                        rotationX = coercedOffset * -35f

                        // Perspective: Prevents the 3D warp from looking "flat"
                        cameraDistance = 12f * density

                        // Edge Polish: Cards shrink and fade slightly as they roll away
                        val scale = 1f - (kotlin.math.abs(coercedOffset) * 0.15f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (kotlin.math.abs(coercedOffset) * 0.4f)
                    },
                onClick = { onNavigateToAlbum(albumName) }
            )
        }
    }
}







@Composable
fun SongsTab(
    viewModel: PlayerViewModel,
    onNavigateToAlbum: (String) -> Unit
) {
    // --- 1. THE SINGLE SOURCE OF TRUTH ---
    val uiState by viewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack
    val tracks = uiState.tracks // We display the master list here

    // Fetch playlists for the options portal
    val playlists by viewModel.customPlaylists.collectAsState(initial = emptyList())

    // State for the Options Sheet
    var trackForOptions by remember { mutableStateOf<AudioTrack?>(null) }

    // --- 2. THE VIEWPORT ---
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Bottom padding ensures the last song breathes above the MiniPlayer
        contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp)
    ) {
        // We use itemsIndexed to solve the "Duplicate Key" crash.
        // Even if two files have the same ID, their index is unique.
        items(
            items = tracks,
            key = { track -> "song_${track.id}_${track.path.hashCode()}" }
        ) { track ->
            val isActive = currentTrack?.id == track.id

            SongItem(
                track = track,
                isActive = isActive,
                isPlaying = uiState.isPlaying,
                modifier = Modifier.animateItem(), // Enables smooth list reordering
                onClick = { viewModel.playTrack(track) },
                onMoreClick = { trackForOptions = track },
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
                viewModel.addToQueue(selectedTrack)
            },
            onAddToPlaylist = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, selectedTrack)
            },
            onGoToAlbum = { albumName ->
                onNavigateToAlbum(albumName)
            }
        )
    }
}

@Composable
fun PlaylistsTab(
    viewModel: PlayerViewModel,
    onNavigateToPlaylist: (Long, String) -> Unit
) {
    val playlists by viewModel.customPlaylists.collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }

    // THE ALIGNMENT RITUAL: Filtering Liked Songs
    val likedPlaylist = remember(playlists) {
        playlists.find { it.name.equals("Liked Songs", ignoreCase = true) }
    }
    val userPlaylists = remember(playlists) {
        playlists.filterNot { it.name.equals("Liked Songs", ignoreCase = true) }
    }

    // Dynamic count for Liked Songs
    val playlistTracks by viewModel.getPlaylistTracks(likedPlaylist?.id ?: 0)
        .collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // --- 1. THE HERO: LIKED SONGS ---
        item(key = "hero_liked") {
            LikedSongsCard(trackCount = playlistTracks.size) {
                onNavigateToPlaylist(likedPlaylist?.id ?: -1L, "Liked Songs")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- 2. THE ACTION: FORGE ---
        item(key = "action_forge") {
            ActionPlaylistRow(
                icon = WitcherIcons.Add,
                title = "Forge New Playlist",
                accentColor = MaterialTheme.aardBlue,
                onClick = { showCreateDialog = true }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- 3. THE COLLECTIONS ---
        if (userPlaylists.isNotEmpty()) {
            item(key = "header_grimoires") {
                Text(
                    text = "YOUR GRIMOIRES",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                    letterSpacing = 2.sp
                )
            }

            items(
                items = userPlaylists,
                // THE FIX: Salting the playlist ID key
                key = { playlist -> "plist_${playlist.id}" }
            ) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    modifier = Modifier.animateItem(),
                    onClick = { onNavigateToPlaylist(playlist.id, playlist.name) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Final spacing for the MiniPlayer
        item { Spacer(modifier = Modifier.height(140.dp)) }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun ActionPlaylistRow(
    icon: ImageVector,
    title: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // S22 Optimization: Removed the double horizontal padding!
            // Just using vertical padding to separate it from the LikedSongsCard
            .padding(vertical = 8.dp)
            .glassEffect(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), MaterialTheme.shapes.large)
            .bounceClick { onClick() }
            .padding(16.dp), // Inner padding keeps the text and icon away from the border
        verticalAlignment = Alignment.CenterVertically
    ) {
        // THE MEDALLION: Icon with an elemental aura
        Box(
            modifier = Modifier
                .size(40.dp) // Scaled down slightly for a sleeker profile
                .background(accentColor.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // THE COMMAND: High-visibility uppercase title
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge, // 11sp or 12sp based on our theme
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp // Tightened for the S22
        )

        Spacer(modifier = Modifier.weight(1f))

        // Navigation hint
        Icon(
            imageVector = WitcherIcons.Headphones, // Or WitcherIcons.Next if you have an arrow
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * THE SEARCH VISION:
 * A flattened list extension to prevent nested scroll conflicts on the S22.
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