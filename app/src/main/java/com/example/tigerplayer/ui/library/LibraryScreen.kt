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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.model.Playlist
import com.example.tigerplayer.data.repository.ArtistDetails
import com.example.tigerplayer.engine.LibraryEngine.Companion.LIKED_SONGS_ID
import com.example.tigerplayer.ui.player.LibraryArtist
import com.example.tigerplayer.ui.player.PlayerUiState
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.utils.ArtistUtils
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
    // 🔥 NEW: Pull pre-seeded artist profiles from the VM vault
    val artistDetails by viewModel.artistDetails.collectAsState()

    val tabs = listOf("Songs", "Albums", "Artists", "Playlists")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    var isSearchActive by remember { mutableStateOf(false) }
    val query = uiState.searchQuery

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
            .statusBarsPadding()
    ) {
        AnimatedLibraryHeader(
            query = query,
            isSearchActive = isSearchActive,
            onSearchToggle = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) viewModel.clearSearch()
            },
            onQueryChange = { viewModel.onSearchQueryChanged(it) }
        )

        VanguardLibraryTabs(
            tabs = tabs,
            pagerState = pagerState,
            coroutineScope = scope
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (query.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Passed artistDetails downstream to render HD search results
                    renderSearchResults(
                        uiState = uiState,
                        viewModel = viewModel,
                        matchedArtists = matchedArtists,
                        matchedAlbums = matchedAlbums,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigatetoArtist = onNavigateToArtist,
                        artistDetails = artistDetails
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
                        2 -> ArtistsTab(viewModel, onNavigateToArtist)
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
    val lazyListState = rememberLazyListState()

    // 🔥 THE FIX: Smoothly auto-centers the active tab in the viewport
    LaunchedEffect(pagerState.currentPage) {
        if (tabs.isNotEmpty()) {
            lazyListState.animateScrollToItem(
                index = pagerState.currentPage,
                scrollOffset = -180 // Approximate mid-offset to keep the tab nicely centered
            )
        }
    }

    LazyRow(
        state = lazyListState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(tabs) { index, title ->
            val isSelected = pagerState.currentPage == index

            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.aardBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "TabBackground"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF0F172A) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "TabText"
            )
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.08f else 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                label = "TabScale"
            )

            // Dynamic 3D depth parameters
            val elevation = if (isSelected) 8.dp else 2.dp
            val borderBrush = if (isSelected) {
                Brush.verticalGradient(listOf(Color.White.copy(0.4f), Color.Transparent))
            } else {
                Brush.verticalGradient(listOf(Color.White.copy(0.12f), Color.White.copy(0.02f)))
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(
                        elevation = elevation,
                        shape = CircleShape,
                        ambientColor = if (isSelected) MaterialTheme.aardBlue.copy(0.5f) else Color.Black.copy(0.2f),
                        spotColor = if (isSelected) MaterialTheme.aardBlue else Color.Black
                    )
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(backgroundColor)
                    .border(width = 1.dp, brush = borderBrush, shape = CircleShape)
                    .bounceClick {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.8.sp
                )
            }
        }
    }
}


// ==========================================
// --- TAB IMPLEMENTATIONS ---
// ==========================================

@Composable
fun SongsTab(viewModel: PlayerViewModel, onNavigateToAlbum: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack
    val tracks = uiState.tracks
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current.density

    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Summoning Archives...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    val firstVisibleItem by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleItem) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)
    ) {
        itemsIndexed(items = tracks, key = { _, track -> "song_${track.id}" }) { index, track ->
            val isActive = currentTrack?.id == track.id
            val tiltX by animateFloatAsState(targetValue = if (isActive) -12f else 0f, label = "tiltX")
            val activeScale by animateFloatAsState(targetValue = if (isActive) 1.04f else 1f, label = "scale")

            Box(
                modifier = Modifier
                    .animateItem()
                    .graphicsLayer {
                        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                        if (itemInfo != null) {
                            val viewportCenter = listState.layoutInfo.viewportSize.height / 2f
                            val itemCenter = itemInfo.offset + (itemInfo.size / 2f)
                            val fraction = ((itemCenter - viewportCenter) / viewportCenter).coerceIn(-1f, 1f)

                            // 🔥 TRUE 3D CYLINDER PHYSICS
                            translationX = (fraction * fraction) * 45f // Slides outwards at screen boundaries
                            rotationX = fraction * -28f // Roll tilt backwards/forwards on scroll
                            rotationY = fraction * -8f  // Dynamic yaw angle
                            rotationZ = fraction * 2.5f // Slight twist

                            val baseScale = 1f - (abs(fraction) * 0.12f)
                            scaleX = baseScale * activeScale
                            scaleY = baseScale * activeScale
                            alpha = (1f - (abs(fraction) * 0.45f)).coerceIn(0.3f, 1f)
                            cameraDistance = 14f * density
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SongItem(
                    track = track,
                    isActive = isActive,
                    isPlaying = uiState.isPlaying && isActive,
                    onClick = { viewModel.playTrack(track) }
                )
            }
        }
    }
}

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

                        rotationX = coercedOffset * -30f
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
fun ArtistsTab(viewModel: PlayerViewModel, onNavigateToArtist: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val artistDetailsMap by viewModel.artistDetails.collectAsState()

    val artists = uiState.artists
    val gridState = rememberLazyGridState()
    val context = LocalContext.current

    if (artists.isEmpty()) { ArchiveLoadingState("Awakening Legends..."); return }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items(artists, key = { it.name }) { artist ->

            val normalizedKey = remember(artist.name) {
                ArtistUtils.getBaseArtist(artist.name).lowercase().trim()
            }

            val profile = artistDetailsMap[normalizedKey]

            val artistCover = remember(profile?.imageUrl, artist.name, uiState.tracks) {
                profile?.imageUrl?.takeIf { it.isNotBlank() }
                    ?: uiState.tracks.firstOrNull { it.artist.equals(artist.name, ignoreCase = true) }?.artworkUri?.toString()
            }

            val imageRequest = remember(artistCover) {
                ImageRequest.Builder(context)
                    .data(artistCover)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .bounceClick { onNavigateToArtist(artist.name) }
                    .animateItem()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(8.dp, CircleShape, spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (artistCover != null) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = WitcherIcons.Artist,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
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
        contentPadding = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 140.dp)
    ) {
        item(key = "hero_liked") {
            PremiumLikedSongsCard(trackCount = likedPlaylist?.trackCount ?: 0) {
                onNavigateToPlaylist(LIKED_SONGS_ID, "Liked Songs")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item(key = "action_forge") {
            PremiumActionRow(
                icon = WitcherIcons.Add,
                title = "Forge New Playlist",
                onClick = { /* Handle Create Flow */ }
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

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
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onNavigateToPlaylist(playlist.id, playlist.name) }
                )
            }
        }
    }
}

// ==========================================
// --- PREMIUM SUB-COMPONENTS ---
// ==========================================


@Composable
fun SongItem(
    track: AudioTrack,
    isActive: Boolean,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onOptionsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val titleColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.aardBlue else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "titleColorAnim"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.aardBlue.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "bgColorAnim"
    )

    val cardElevation = if (isActive) 4.dp else 0.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = cardElevation,
                shape = RoundedCornerShape(16.dp),
                ambientColor = MaterialTheme.aardBlue.copy(0.1f),
                spotColor = MaterialTheme.aardBlue.copy(0.25f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                brush = if (isActive) {
                    Brush.verticalGradient(listOf(MaterialTheme.aardBlue.copy(0.3f), Color.Transparent))
                } else {
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                },
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- 3D-GLOW ARTWORK PORTAL ---
        Box(
            modifier = Modifier
                .size(54.dp)
                .shadow(
                    elevation = if (isActive) 6.dp else 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    spotColor = if (isActive) MaterialTheme.aardBlue else Color.Black
                )
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    MiniEqualizer(isAnimating = isPlaying, color = MaterialTheme.aardBlue)
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // --- TITLE & METADATA COLUMN ---
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = track.artist.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // --- ⏰ DURATION DISPLAY ---
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.aardBlue.copy(0.9f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        // --- OPTIONS DOTS TRIGGER ---
        if (onOptionsClick != null) {
            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = WitcherIcons.Options,
                    contentDescription = "Track Menu Options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


/**
 * A sleek, 3-bar animated equalizer to indicate playback state on list items.
 */
@Composable
fun MiniEqualizer(isAnimating: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "MiniEqualizerTransition")

    // Create rhythmic, staggered heights
    val bar1Height by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isAnimating) 0.9f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar1"
    )
    val bar2Height by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = if (isAnimating) 0.8f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar2"
    )
    val bar3Height by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isAnimating) 0.95f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bar3"
    )

    Row(
        modifier = Modifier
            .width(18.dp)
            .height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(bar1Height)
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(bar2Height)
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(bar3Height)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * Parses track durations safely into a standardized layout format.
 */
private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
@Composable
fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .bounceClick { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = playlist.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${playlist.trackCount} CHANTS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

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
                        Color(0xFF4A00E0)
                    )
                )
            )
            .bounceClick { onClick() }
    ) {
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

// ==========================================
// --- SEARCH RENDERER ---
// ==========================================

fun LazyListScope.renderSearchResults(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    matchedArtists: List<LibraryArtist>,
    matchedAlbums: List<AudioTrack>,
    onNavigateToAlbum: (String) -> Unit,
    onNavigatetoArtist: (String) -> Unit,
    artistDetails: Map<String, ArtistDetails>
) {
    if (matchedArtists.isNotEmpty()) {
        item {
            Text(
                text = "ARTISTS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
        items(matchedArtists, key = { "artist_${it.name}" }) { artist ->

            // 🔥 THE FIX: Extract HD images using the identical Lore logic
            val normalizedKey = remember(artist.name) {
                ArtistUtils.getBaseArtist(artist.name).lowercase().trim()
            }
            val profile = artistDetails[normalizedKey]
            val artistCover = remember(profile?.imageUrl, artist.name, uiState.tracks) {
                profile?.imageUrl?.takeIf { it.isNotBlank() }
                    ?: uiState.tracks.firstOrNull { it.artist.equals(artist.name, ignoreCase = true) }?.artworkUri?.toString()
            }
            val context = LocalContext.current
            val imageRequest = remember(artistCover) {
                ImageRequest.Builder(context)
                    .data(artistCover)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .bounceClick { onNavigatetoArtist(artist.name) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (artistCover != null) {
                        AsyncImage(model = imageRequest, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(WitcherIcons.Artist, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(artist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("${artist.trackCount} CHANTS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (matchedAlbums.isNotEmpty()) {
        item {
            Text(
                text = "ALBUMS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
        items(matchedAlbums, key = { "album_${it.album}" }) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .bounceClick { onNavigateToAlbum(album.album) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = album.artworkUri, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(album.album, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(album.artist.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    val matchedTracks = uiState.tracks.filter { it.title.contains(uiState.searchQuery, ignoreCase = true) }
    if (matchedTracks.isNotEmpty()) {
        item {
            Text(
                text = "CHANTS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
        items(matchedTracks, key = { "track_${it.id}" }) { track ->
            SongItem(
                track = track,
                isActive = uiState.currentTrack?.id == track.id,
                onClick = { viewModel.playTrack(track) }
            )
        }
    }
}