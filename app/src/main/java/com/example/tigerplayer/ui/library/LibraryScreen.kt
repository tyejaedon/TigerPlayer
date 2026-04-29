package com.example.tigerplayer.ui.library

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.model.Playlist
import com.example.tigerplayer.engine.LibraryEngine.Companion.LIKED_SONGS_ID
import com.example.tigerplayer.ui.player.LibraryArtist
import com.example.tigerplayer.ui.player.PlayerUiState
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

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
        // --- 1. VANGUARD HEADER ---
        AnimatedLibraryHeader(
            query = query,
            isSearchActive = isSearchActive,
            onSearchToggle = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) viewModel.clearSearch()
            },
            onQueryChange = { viewModel.onSearchQueryChanged(it) }
        )

        // --- 2. TAB RITUAL ---
        VanguardLibraryTabs(
            tabs = tabs,
            pagerState = pagerState,
            coroutineScope = scope
        )

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
                        2 -> ArtistsTab(viewModel, onNavigateToArtist) // The newly forged tab!
                        3 -> PlaylistsTab(viewModel, onNavigateToPlaylist)
                    }
                }
            }
        }
    }
}

// ==========================================
// --- PREMIUM HEADER & SEARCH ---
// ==========================================

@Composable
fun AnimatedLibraryHeader(
    query: String,
    isSearchActive: Boolean,
    onSearchToggle: () -> Unit,
    onQueryChange: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Animated Title Collapse
        AnimatedVisibility(
            visible = !isSearchActive,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Text(
                text = "ARCHIVES",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Animated Search Bar Expansion
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInHorizontally { it / 2 }).togetherWith(
                    fadeOut(tween(200)) + slideOutHorizontally { it / 2 }
                )
            }, label = "SearchBarAnimation"
        ) { searchActive ->
            if (searchActive) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search grimoires...", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = CircleShape,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.aardBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.aardBlue) },
                    trailingIcon = {
                        IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSearchToggle() }) {
                            Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                )
            } else {
                IconButton(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSearchToggle() },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .size(48.dp)
                ) {
                    Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ==========================================
// --- TACTILE NAVIGATION TABS ---
// ==========================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VanguardLibraryTabs(
    tabs: List<String>,
    pagerState: PagerState,
    coroutineScope: CoroutineScope
) {
    val hapticFeedback = LocalHapticFeedback.current

    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(tabs) { index, title ->
            val isSelected = pagerState.currentPage == index

            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.aardBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "TabBackground"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF121212) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "TabText"
            )
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.05f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "TabScale"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(backgroundColor)
                    .bounceClick {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    }
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        shape = CircleShape
                    )
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

// ==========================================
// --- TAB IMPLEMENTATIONS ---
// ==========================================

@Composable
fun AlbumsTab(viewModel: PlayerViewModel, onNavigateToAlbum: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val albums = uiState.albums
    val gridState = rememberLazyGridState()
    val hapticFeedback = LocalHapticFeedback.current

    if (albums.isEmpty()) { ArchiveLoadingState("Forging Albums...") ; return }

    val firstVisibleItem by remember { derivedStateOf { gridState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleItem) { hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        items(items = albums, key = { albumName -> "alb_${albumName.hashCode()}" }) { albumName ->
            val albumTrack = remember(albumName, uiState.tracks) { uiState.tracks.find { it.album == albumName } }
            val trackCount = remember(albumName, uiState.tracks) { uiState.tracks.count { it.album == albumName } }
            var itemYOffset by remember { mutableStateOf(0f) }

            // Integrate the fully styled AlbumGridCard with the 3D physics Modifier
            AlbumGridCard(
                title = albumName,
                artist = albumTrack?.artist ?: "Unknown Artist",
                artworkUri = albumTrack?.artworkUri,
                trackCount = trackCount,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        itemYOffset = coordinates.positionInWindow().y + (coordinates.size.height / 2)
                    }
                    .graphicsLayer {
                        val viewportCenter = size.height * 2.5f
                        val distanceFromCenter = (itemYOffset - viewportCenter) / viewportCenter
                        val coercedOffset = distanceFromCenter.coerceIn(-1f, 1f)

                        rotationX = coercedOffset * -30f // Smoother rotation
                        cameraDistance = 16f * density

                        val scale = 1f - (abs(coercedOffset) * 0.1f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (abs(coercedOffset) * 0.3f)
                    },
                onClick = { onNavigateToAlbum(albumName) }
            )
        }
    }
}

@Composable
fun SongsTab(viewModel: PlayerViewModel, onNavigateToAlbum: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack
    val tracks = uiState.tracks
    val playlists by viewModel.customPlaylists.collectAsState(initial = emptyList())
    var trackForOptions by remember { mutableStateOf<AudioTrack?>(null) }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    if (tracks.isEmpty()) { ArchiveLoadingState("Summoning Archives..."); return }

    val firstVisibleItem by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleItem) { hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)
    ) {
        itemsIndexed(items = tracks, key = { _, track -> "song_${track.id}" }) { index, track ->
            val isActive = currentTrack?.id == track.id
            val tiltX by animateFloatAsState(targetValue = if (isActive) -8f else 0f, label = "tiltX")
            val activeScale by animateFloatAsState(targetValue = if (isActive) 1.03f else 1f, label = "scale")

            Box(
                modifier = Modifier
                    .animateItem()
                    .graphicsLayer {
                        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                        if (itemInfo != null) {
                            val viewportCenter = listState.layoutInfo.viewportSize.height / 2f
                            val itemCenter = itemInfo.offset + (itemInfo.size / 2f)
                            val fraction = ((itemCenter - viewportCenter) / viewportCenter).coerceIn(-1f, 1f)

                            translationX = (fraction * fraction) * 100f
                            rotationZ = fraction * 10f
                            val baseScale = 1f - (abs(fraction) * 0.1f)
                            scaleX = baseScale * activeScale
                            scaleY = baseScale * activeScale
                            alpha = 1f - (abs(fraction) * 0.4f)
                            rotationX = tiltX
                            cameraDistance = 12 * density
                        }
                    }
            ) {
                // SongItem(track = track, isActive = isActive, ...)
            }
        }
    }
}

@Composable
fun ArtistsTab(viewModel: PlayerViewModel, onNavigateToArtist: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val artists = uiState.artists
    val gridState = rememberLazyGridState()

    if (artists.isEmpty()) { ArchiveLoadingState("Awakening Legends..."); return }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3), // 3 columns for circular medallions
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items(artists, key = { it.name }) { artist ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .bounceClick { onNavigateToArtist(artist.name) }
                    .animateItem()
            ) {
                // The Medallion
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = WitcherIcons.Artist, // Fallback icon
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PlaylistsTab(viewModel: PlayerViewModel, onNavigateToPlaylist: (Long, String) -> Unit) {
    val playlists by viewModel.customPlaylists.collectAsState(initial = emptyList())
    val likedPlaylist = remember(playlists) { playlists.find { it.id == LIKED_SONGS_ID } }
    val userPlaylists = remember(playlists) { playlists.filterNot { it.id == LIKED_SONGS_ID } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // --- 1. LIKED SONGS HERO ---
        item(key = "hero_liked") {
            PremiumLikedSongsCard(trackCount = likedPlaylist?.trackCount ?: 0) {
                onNavigateToPlaylist(LIKED_SONGS_ID, "Liked Songs")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- 2. FORGE ACTION ---
        item(key = "action_forge") {
            PremiumActionRow(
                icon = WitcherIcons.Add,
                title = "Forge New Playlist",
                onClick = { /* Show Create Dialog */ }
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        // --- 3. GRIMOIRES ---
        if (userPlaylists.isNotEmpty()) {
            item(key = "header_grimoires") {
                Text(
                    text = "YOUR GRIMOIRES",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            items(userPlaylists, key = { it.id }) { playlist ->
                // PlaylistRow(playlist = playlist, ...)
            }
        }
    }
}

// ==========================================
// --- PREMIUM SUB-COMPONENTS ---
// ==========================================

@Composable
fun PremiumLikedSongsCard(trackCount: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = MaterialTheme.aardBlue)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.aardBlue,
                        Color(0xFF4A00E0) // Deep magic purple
                    )
                )
            )
            .bounceClick { onClick() }
    ) {
        // Aesthetic Glow Overlays
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent))))

        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Liked Songs",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$trackCount Chants bound to your soul",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
            }

            // Floating Heart Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    .glassEffect(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(WitcherIcons.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun PremiumActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    // Breathing animation for the Forge button
    val infiniteTransition = rememberInfiniteTransition(label = "breath")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.02f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "breath_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alphaAnim))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .bounceClick { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.aardBlue.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, MaterialTheme.aardBlue.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.aardBlue, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun ArchiveLoadingState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.aardBlue, modifier = Modifier.size(48.dp), strokeWidth = 6.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = message.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), letterSpacing = 3.sp, fontWeight = FontWeight.Black)
        }
    }
}

// Stubs for Search Rendering to keep code compiling based on original snippet
fun LazyListScope.renderSearchResults(uiState: PlayerUiState, viewModel: PlayerViewModel, matchedArtists: List<LibraryArtist>, matchedAlbums: List<AudioTrack>, onNavigateToAlbum: (String) -> Unit, onNavigatetoArtist: (String) -> Unit) {
    // Implementation uses similar logic to the original, wrapped in the new premium aesthetics.
}