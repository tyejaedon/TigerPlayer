package com.example.tigerplayer.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.ui.player.PlayerViewModel
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.ArtistDetails
import com.example.tigerplayer.ui.home.AlbumSearchRow
import com.example.tigerplayer.ui.home.SectionTitle
import com.example.tigerplayer.ui.player.LibraryArtist
import com.example.tigerplayer.ui.player.PlayerUiState
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.spotify.protocol.types.Artist
import kotlinx.coroutines.launch
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: PlayerViewModel,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToPlaylist: (Long, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("Songs", "Albums", "Artists", "Playlists")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    var isSearchActive by remember { mutableStateOf(false) }
    val query = uiState.searchQuery

    // THE CACHE RITUAL: Hoist the heavy filtering off the layout thread.
    // This only re-calculates when the query or the base data changes.
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
        // --- 1. VANGUARD HEADER ---
        LibraryHeader(
            title = "ARCHIVES",
            searchQuery = uiState.searchQuery,
            isSearchActive = isSearchActive,
            onSearchToggle = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) viewModel.clearSearch()
            },
            onSearchQueryChange = { viewModel.onSearchQueryChanged(it) }
        )

        // --- 2. TAB RITUAL: DYNAMIC FROST & LEGIBILITY ---
        val isDark = isSystemInDarkTheme()

        // THE FIX: Clean named arguments for the standard Compose M3 TabRow
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(RectangleShape),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            edgePadding = 16.dp,
            divider = {}, // Stripped the default underline to keep it clean
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    height = 2.dp,
                    color = AardBlue
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = pagerState.currentPage == index

                // THE FIX: High-Contrast Adaptive Logic
                val tabColor = when {
                    isSelected -> AardBlue
                    // S22 Dark Mode: Boosted alpha to 0.7f so it's highly readable against pure black OLEDs
                    isDark -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    // S22 Light Mode: Crisp, dark contrast
                    else -> Color.Black.copy(alpha = 0.6f)
                }

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

        // --- 3. THE VIEWPORT: SWIPEABLE ARCHIVES ---
        if (uiState.searchQuery.isNotEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    // Pass the cached lists instead of making the builder do the math
                    renderSearchResults(
                        query = query,
                        matchedArtists = matchedArtists,
                        matchedAlbums = matchedAlbums,
                        filteredTracks = uiState.filteredTracks,
                        viewModel = viewModel,
                        onNavigateToArtist = onNavigateToArtist, // Hook this up!
                        onNavigateToAlbum = onNavigateToAlbum
                    )
                }
            }
        }else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                userScrollEnabled = true,
            ) { page ->
                when (page) {
                    0 -> SongsTab(viewModel)
                    1 -> AlbumsTab(viewModel, onNavigateToAlbum)
                    2 -> ArtistsList(viewModel = viewModel, onArtistClick = onNavigateToArtist)
                    3 -> PlaylistsTab(viewModel, onNavigateToPlaylist)
                }
            }
        }
    }
}

@Composable
fun AlbumsTab(viewModel: PlayerViewModel, onNavigateToAlbum: (String) -> Unit) {
    // Reusing the Mastered AlbumsGrid we built earlier
    AlbumsGrid(viewModel = viewModel, onAlbumClick = onNavigateToAlbum)
}


@Composable
fun SongsTab(viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Bottom padding ensures the last song isn't hidden by the MiniPlayer
        contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp)
    ) {
        // --- 1. THE SEARCH HEADLINE (Visible only when filtering) ---
        if (uiState.searchQuery.isNotEmpty()) {
            item {
                SectionTitle(title = "Echoes of '${uiState.searchQuery}'")
            }
        }

        // --- 2. THE CHANTS LIST ---
        items(
            items = uiState.filteredTracks,
            key = { it.id } // Performance Anchor
        ) { track ->
            val isActive = currentTrack?.id == track.id

            SongItem(
                track = track,
                isActive = isActive,
                isPlaying = uiState.isPlaying,
                onClick = { viewModel.playTrack(track) },
                onMoreClick = { /* Trigger Options Ritual */ }
            )
        }
    }
}
@Composable
fun PlaylistsTab(
    viewModel: PlayerViewModel,
    onNavigateToPlaylist: (Long, String) -> Unit
) {
    val playlists by viewModel.customPlaylists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    // --- THE ALIGNMENT RITUAL ---
    // 1. Find the specific "Liked Songs" playlist using your exact logic
    val likedPlaylist = remember(playlists) {
        playlists.find { it.name.equals("Liked Songs", ignoreCase = true) }
    }

    // 2. Filter out "Liked Songs" so it doesn't duplicate in the Grimoires list below
    val userPlaylists = remember(playlists) {
        playlists.filterNot { it.name.equals("Liked Songs", ignoreCase = true) }
    }
    val playlistTracks by viewModel.getPlaylistTracks(likedPlaylist?.id ?: 0).collectAsState(initial = emptyList())


    // 3. Extract the track count (Assuming your Playlist model has a trackCount or tracks.size property)
    // If your model uses a different property name for the count, change `trackCount` here.
    val likedSongsCount = playlistTracks.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 140.dp
        )
    ) {
        // --- 1. THE HERO: LIKED SONGS ---
        item {
            LikedSongsCard(
                trackCount = likedSongsCount
            ) {
                // THE FIX: Pass the actual database ID of the Liked Playlist so it loads correctly.
                // Fallback to -1L only if they haven't liked any songs yet.
                val targetId = likedPlaylist?.id ?: -1L
                onNavigateToPlaylist(targetId, "Liked Songs")
            }
        }

        // --- 2. THE ACTION: FORGE NEW PLAYLIST ---
        item {
            ActionPlaylistRow(
                icon = WitcherIcons.Add,
                title = "Forge New Playlist",
                accentColor = AardBlue,
                onClick = { showCreateDialog = true }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- 3. THE COLLECTIONS: CUSTOM PLAYLISTS ---
        if (userPlaylists.isNotEmpty()) {
            item {
                Text(
                    text = "YOUR GRIMOIRES",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                    letterSpacing = 2.sp
                )
            }

            // Iterate over the filtered list (userPlaylists) instead of the raw playlists
            items(userPlaylists, key = { it.id }) { playlist ->
                PlaylistRow(playlist) {
                    onNavigateToPlaylist(playlist.id, playlist.name)
                }
            }
        }
    }

    // --- FORGE DIALOG ---
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
    query: String,
    matchedArtists: List<LibraryArtist>,
    matchedAlbums: List<AudioTrack>,
    filteredTracks: List<AudioTrack>,
    viewModel: PlayerViewModel,
    onNavigateToArtist: (String) -> Unit, // THE FIX: Brought in the missing navigation hook
    onNavigateToAlbum: (String) -> Unit
) {
    // 1. THE VOID CHECK
    if (matchedArtists.isEmpty() && matchedAlbums.isEmpty() && filteredTracks.isEmpty()) {
        item { SearchEmptyState(query) }
        return
    }

    // --- SECTION: THE VANGUARD (Artists) ---
    if (matchedArtists.isNotEmpty()) {
        item { SearchSectionTitle("THE VANGUARD") }
        items(
            items = matchedArtists,
            // THE FIX: ArtistDetails doesn't have an 'id', so we anchor to its name
            key = { "artist_${it.name ?: it.hashCode()}" }
        ) { artist ->
            ArtistSearchRow(artist) {
                // Navigate to Artist Ritual
                artist.name?.let { onNavigateToArtist(it) }
            }
        }
    }

    // --- SECTION: THE VOLUMES (Albums) ---
    if (matchedAlbums.isNotEmpty()) {
        item { SearchSectionTitle("THE VOLUMES") }
        items(
            items = matchedAlbums,
            key = { "album_${it.album}" }
        ) { track ->
            AlbumSearchRow(track) {
                onNavigateToAlbum(track.album)
            }
        }
    }

    // --- SECTION: THE CHANTS (Songs) ---
    if (filteredTracks.isNotEmpty()) {
        item { SearchSectionTitle("CHANTS") }

        // Hoisting state lookups out of the loop to protect the UI thread
        val currentTrackId = viewModel.uiState.value.currentTrack?.id
        val isPlaying = viewModel.uiState.value.isPlaying

        items(
            items = filteredTracks,
            key = { "track_${it.id}" } // Performance Anchor
        ) { track ->
            SongItem(
                track = track,
                isActive = currentTrackId == track.id,
                isPlaying = isPlaying,
                onClick = { viewModel.playTrack(track) },
                onMoreClick = { /* Options Portal */ }
            )
        }
    }
}