package com.example.tigerplayer.ui.library

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

// Witcher Theme Constants
private val AardBlue = Color(0xFF007AFF)
private val IgniRed = Color(0xFFF11F1A)

@OptIn(ExperimentalMaterial3Api::class)
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
    val coroutineScope = rememberCoroutineScope()

    var isSearchActive by remember { mutableStateOf(false) }

    // THE MASTER CONTAINER
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
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

                // --- SEARCH MODE ---
                if (uiState.searchQuery.isNotEmpty()) {
                    val query = uiState.searchQuery

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

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 120.dp) // Clearance for MiniPlayer
                    ) {
                        if (matchedArtists.isNotEmpty()) {
                            item { SearchSectionTitle("THE VANGUARD (Artists)") }
                            items(matchedArtists) { artist ->
                                ArtistSearchRow(artist) { onNavigateToArtist(artist.name) }
                            }
                        }

                        if (matchedAlbums.isNotEmpty()) {
                            item { SearchSectionTitle("THE VOLUMES (Albums)") }
                            items(matchedAlbums) { track ->
                                AlbumSearchRow(track) { onNavigateToAlbum(track.album) }
                            }
                        }

                        if (matchedSongs.isNotEmpty()) {
                            item { SearchSectionTitle("THE CHANTS (Songs)") }
                            items(matchedSongs) { track ->
                                SearchResultItem(track, isAlbum = false, onClick = { viewModel.playTrack(track) })
                            }
                        }

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
                    // --- STANDARD TAB MODE ---
                    SecondaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        edgePadding = 16.dp,
                        divider = {},
                        indicator = {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(pagerState.currentPage),
                                height = 4.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
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
                                    text = title.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> SongsList(viewModel, onNavigateToAlbum)
                            1 -> AlbumsGrid(viewModel, onNavigateToAlbum)
                            2 -> ArtistsList(viewModel, onNavigateToArtist)
                            3 -> PlaylistsList(viewModel = viewModel, onNavigateToPlaylist = onNavigateToPlaylist)
                        }
                    }
                }
            }
        }

        // --- THE SCANNING OVERLAY RITUAL ---
        AnimatedVisibility(
            visible = uiState.isScanning,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ScanningOverlay(
                progress = uiState.scanProgress,
                total = uiState.totalFilesToScan
            )
        }
    }
}

@Composable
fun ScanningOverlay(progress: Int, total: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .glassEffect(MaterialTheme.shapes.extraLarge)
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Aard Blue Glowing Medallion
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = AardBlue,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "INDEXING ARCHIVES",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Igni Red Progress Bar
            LinearProgressIndicator(
                progress = { if (total > 0) progress.toFloat() / total.toFloat() else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = IgniRed,
                trackColor = Color.White.copy(alpha = 0.1f)
            )

            Text(
                text = "Manifesting track $progress of $total...",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                imageVector = WitcherIcons.Artist,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
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
private fun AlbumSearchRow(track: com.example.tigerplayer.data.model.AudioTrack, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = track.album,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LikedSongsCard(
    trackCount: Int,
    onClick: () -> Unit
) {
    val igniGradient = Brush.linearGradient(
        colors = listOf(
            IgniRed.copy(alpha = 0.8f),
            IgniRed.copy(alpha = 0.3f),
            Color.Transparent
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .background(igniGradient)
            .glassEffect(MaterialTheme.shapes.large)
            .bounceClick { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Liked Songs",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "$trackCount saved tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}