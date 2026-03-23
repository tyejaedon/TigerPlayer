package com.example.tigerplayer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.components.DiscoverCarousel
import com.example.tigerplayer.ui.components.RecentlyPlayedRow
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

// --- Thematic Witcher Colors ---
private val IgniRed = Color(0xFFF11F1A)
private val AardBlue = Color(0xFF4FC3F7)
private val SpotifyGreen = Color(0xFF1DB954)

@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToSettings: () -> Unit // NEW: Support navigation to settings
) {
    val uiState by viewModel.uiState.collectAsState()
    val homeState by viewModel.homeUiState.collectAsState()
    var isStatsExpanded by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    // --- SEARCH LOGIC ---
    val searchResults = uiState.filteredTracks
    val songsFound = if (uiState.searchQuery.isNotEmpty()) {
        searchResults.filter { it.title.contains(uiState.searchQuery, ignoreCase = true) }
    } else emptyList()

    BackHandler(enabled = isStatsExpanded || isSearchActive) {
        if (isStatsExpanded) {
            isStatsExpanded = false
        } else if (isSearchActive) {
            isSearchActive = false
            viewModel.onSearchQueryChanged("")
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                HomeHeader(
                    title = "Tiger Player",
                    searchQuery = uiState.searchQuery,
                    isSearchActive = isSearchActive,
                    onSearchToggle = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) viewModel.onSearchQueryChanged("")
                    },
                    onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onSettingsClick = onNavigateToSettings
                )
            }

            if (uiState.searchQuery.isNotEmpty()) {
                if (songsFound.isEmpty()) {
                    item {
                        Text(
                            text = "No echoes found for '${uiState.searchQuery}'",
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }

                if (songsFound.isNotEmpty()) {
                    item { SectionTitle("Songs") }
                    items(songsFound) { track ->
                        SearchResultItem(
                            track = track,
                            isAlbum = false,
                            onClick = { viewModel.playTrack(track) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }

            } else {
                item {
                    UserStatisticsHeader(
                        statistics = homeState.statistics,
                        onClick = { isStatsExpanded = true }
                    )
                }

                if (homeState.discoverTracks.isNotEmpty()) {
                    item {
                        SectionTitle("Discover")
                        DiscoverCarousel(
                            tracks = homeState.discoverTracks,
                            onTrackClick = { viewModel.playTrack(it) }
                        )
                    }
                }

                if (homeState.recentlyPlayedTracks.isNotEmpty()) {
                    item {
                        SectionTitle("Recently Played")
                        RecentlyPlayedRow(
                            tracks = homeState.recentlyPlayedTracks,
                            onTrackClick = { viewModel.playTrack(it) }
                        )
                    }
                }

                if (homeState.recommendedTracks.isNotEmpty()) {
                    item {
                        SectionTitle("Recommended Albums")
                        RecommendedAlbumsList(
                            tracks = homeState.recommendedTracks,
                            onAlbumClick = { albumName ->
                                onNavigateToAlbum(albumName)
                            }
                        )
                    }
                }
            }
        }

        // --- STATS OVERLAY ---
        AnimatedVisibility(
            visible = isStatsExpanded,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            ExpandedStatsScreen(
                viewModel = viewModel,
                onClose = { isStatsExpanded = false }
            )
        }
    }
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
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isSearchActive,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape)
                        .glassEffect(CircleShape),
                    placeholder = {
                        Text("Search your realm...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    },
                    trailingIcon = {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = WitcherIcons.Close,
                                    contentDescription = "Clear Search",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
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

        // Search Toggle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AardBlue)
                .bounceClick { onSearchToggle() },
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = isSearchActive, label = "") { active ->
                Icon(
                    imageVector = if (active) WitcherIcons.Collapse else WitcherIcons.Search,
                    contentDescription = "Search Toggle",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Settings Button
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        ) {
            Icon(
                imageVector = WitcherIcons.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun UniversalHeader(
    title: String,
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isSearchActive,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape)
                        .glassEffect(CircleShape),
                    placeholder = {
                        Text("Search your realm...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    },
                    trailingIcon = {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = WitcherIcons.Close,
                                    contentDescription = "Clear Search",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
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

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AardBlue)
                .bounceClick { onSearchToggle() },
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = isSearchActive, label = "") { active ->
                Icon(
                    imageVector = if (active) WitcherIcons.Collapse else WitcherIcons.Search,
                    contentDescription = "Search Toggle",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun SearchResultItem(track: AudioTrack, isAlbum: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .glassEffect(MaterialTheme.shapes.medium)
            .bounceClick { onClick() }
            .padding(12.dp),
        color = Color.Transparent
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(if (isAlbum) 60.dp else 48.dp)
                    .clip(if (isAlbum) MaterialTheme.shapes.medium else MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = if (isAlbum) track.album else track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
            .glassEffect(MaterialTheme.shapes.extraLarge)
            .clip(MaterialTheme.shapes.extraLarge)
            .bounceClick { onClick() }
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library Analytics",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
            )
            Icon(
                imageVector = WitcherIcons.Expand,
                contentDescription = "Expand",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatGlassWidget(
                modifier = Modifier.weight(1f),
                title = "Listening Today",
                value = statistics.listeningTimeToday,
                icon = WitcherIcons.Duration,
                accentColor = AardBlue
            )

            StatGlassWidget(
                modifier = Modifier.weight(1f),
                title = "Lossless Archives",
                value = "${statistics.losslessTrackCount} FLAC",
                icon = WitcherIcons.Album,
                accentColor = IgniRed
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        StatGlassWidget(
            modifier = Modifier.fillMaxWidth(),
            title = "Top Artist (This Week)",
            value = statistics.topArtistThisWeek,
            icon = WitcherIcons.Library,
            accentColor = SpotifyGreen
        )
    }
}

@Composable
fun StatGlassWidget(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color
) {
    Box(
        modifier = modifier
            .glassEffect(MaterialTheme.shapes.medium)
            .background(accentColor.copy(alpha = 0.08f), MaterialTheme.shapes.medium)
            .border(1.dp, accentColor.copy(alpha = 0.2f), MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
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

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        letterSpacing = (-1).sp
    )
}

@Composable
fun RecommendedAlbumsList(
    tracks: List<AudioTrack>,
    onAlbumClick: (String) -> Unit
) {
    // The ViewModel now provides tracks pre-grouped/selected for the 2-hour window.
    // We just need to group them by album name to display them as albums.
    val albumGroups = remember(tracks) {
        tracks.groupBy { it.album }.values.toList()
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        albumGroups.forEach { albumTracks ->
            val firstTrack = albumTracks.first()
            val albumName = firstTrack.album.ifBlank { "Unknown Album" }
            val artistName = firstTrack.artist

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .glassEffect(MaterialTheme.shapes.medium)
                    .clip(MaterialTheme.shapes.medium)
                    .bounceClick { onAlbumClick(firstTrack.album) },
                color = Color.Transparent
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    AsyncImage(
                        model = firstTrack.artworkUri,
                        contentDescription = "Cover for $albumName",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}