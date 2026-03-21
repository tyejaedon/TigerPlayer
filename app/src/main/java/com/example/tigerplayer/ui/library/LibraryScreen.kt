package com.example.tigerplayer.ui.library

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.ui.home.SearchResultItem
import com.example.tigerplayer.ui.home.UniversalHeader
import com.example.tigerplayer.ui.player.LibraryArtist
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.tigerplayer.ui.theme.glassEffect

@Composable
fun LibraryScreen(
    viewModel: PlayerViewModel,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToPlaylist: (Long, String) -> Unit // <-- Make sure it has String!
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("Songs", "Albums", "Artists", "Playlists")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    var isSearchActive by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Enforce max size on the parent column
        Column(modifier = Modifier.fillMaxSize()) {
            UniversalHeader(
                title = "Library Archives",
                searchQuery = uiState.searchQuery,
                isSearchActive = isSearchActive,
                onSearchToggle = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) viewModel.onSearchQueryChanged("")
                },
                onSearchQueryChange = { viewModel.onSearchQueryChanged(it) }
            )

            if (uiState.searchQuery.isNotEmpty()) {
                val query = uiState.searchQuery

                // 1. DISTINGUISH DATA: Filter the unified archive into sections
                val matchedArtists = remember(query, uiState.artists) {
                    uiState.artists.filter { it.name.contains(query, ignoreCase = true) }
                }
                val matchedAlbums = remember(query, uiState.tracks) {
                    uiState.tracks
                        .filter { it.album.contains(query, ignoreCase = true) }
                        .distinctBy { it.album.lowercase().trim() }
                }
                val matchedSongs = remember(query, uiState.filteredTracks) {
                    uiState.filteredTracks.filter { it.title.contains(query, ignoreCase = true) }
                }

                // THE FIX: Replaced fillMaxSize() with weight(1f) and fillMaxWidth()
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // SECTION 1: ARTISTS (The Sovereigns)
                    if (matchedArtists.isNotEmpty()) {
                        item { SearchSectionTitle("THE VANGUARD (Artists)") }
                        items(matchedArtists) { artist ->
                            // Assuming you have an ArtistSearchRow composable
                            ArtistSearchRow(artist) { onNavigateToArtist(artist.name) }
                        }
                    }

                    // SECTION 2: ALBUMS (The Collections)
                    if (matchedAlbums.isNotEmpty()) {
                        item { SearchSectionTitle("THE VOLUMES (Albums)") }
                        items(matchedAlbums) { track ->
                            // Assuming you have an AlbumSearchRow composable
                            AlbumSearchRow(track) { onNavigateToAlbum(track.album) }
                        }
                    }

                    // SECTION 3: SONGS (The Essences)
                    if (matchedSongs.isNotEmpty()) {
                        item { SearchSectionTitle("THE CHANTS (Songs)") }
                        items(matchedSongs) { track ->
                            SearchResultItem(track, isAlbum = false, onClick = { viewModel.playTrack(track) })
                        }
                    }

                    // Empty State
                    if (matchedArtists.isEmpty() && matchedAlbums.isEmpty() && matchedSongs.isEmpty()) {
                        item {
                            Text(
                                text = "No echoes found for '$query'",
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Standard Library Tabs
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                height = 4.dp, // Thicker indicator for a more solid feel
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .glassEffect(MaterialTheme.shapes.extraLarge)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = pagerState.currentPage == index
                        Tab(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            modifier = Modifier.height(72.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                fontSize = 20.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // THE FIX: Replaced fillMaxSize() with weight(1f) and fillMaxWidth()
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> SongsList(viewModel, onNavigateToAlbum)
                        1 -> AlbumsGrid(viewModel, onNavigateToAlbum)
                        2 -> ArtistsList(viewModel, onNavigateToArtist)
// Inside LibraryScreen.kt -> HorizontalPager
                        3 -> PlaylistsList(
                            viewModel = viewModel,
                            onNavigateToPlaylist = onNavigateToPlaylist
                        )                    }
                }
            }
        }
    }
}
@Composable
private fun SearchSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

@Composable
private fun ArtistSearchRow(artist: LibraryArtist, onClick: () -> Unit) {
    // A slimmed-down version of ArtistListItem for search results
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Icon(WitcherIcons.Artist, null, modifier = Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = artist.name,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LikedSongsCard(
    trackCount: Int,
    onClick: () -> Unit
) {
    // A fiery gradient sweeping across the card
    val igniGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFF11F1A).copy(alpha = 0.8f), // Igni Red
            Color(0xFFFF5722).copy(alpha = 0.4f), // Fiery Orange
            Color.Transparent
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .background(igniGradient)
            .glassEffect(MaterialTheme.shapes.large)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Liked Songs",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$trackCount saved tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
            }

            // The Glowing Heart
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = "Liked Songs",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}


@Composable
private fun AlbumSearchRow(track: com.example.tigerplayer.data.model.AudioTrack, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(track.album, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
