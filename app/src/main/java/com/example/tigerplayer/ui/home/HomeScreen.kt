package com.example.tigerplayer.ui.home

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresExtension
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Timeline
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.components.DiscoverCarousel
import com.example.tigerplayer.ui.components.RecentlyPlayedRow
import com.example.tigerplayer.ui.constellation.ConstellationScreen
import com.example.tigerplayer.ui.constellation.ConstellationViewModel
import com.example.tigerplayer.ui.dashboard.DashboardViewModel
import com.example.tigerplayer.ui.extras.NowBriefWidgetWrapper
import com.example.tigerplayer.ui.library.*
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.prism.PrismInlineMixer
import com.example.tigerplayer.ui.prism.PrismViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import kotlin.math.absoluteValue

// --- VANGUARD THEME CONSTANTS ---
private val AardBlue = Color(0xFF4FC3F7)
private val IgniRed = Color(0xFFFF5252)
private val SpotifyGreen = Color(0xFF1DB954)
private val NeuralPurple = Color(0xFFB388FF)
private val SonicCyan = Color(0xFF00E5FF)

@androidx.annotation.OptIn(UnstableApi::class)
@RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 15)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigatetoArtist: (String) -> Unit,
    homeViewModel: HomeViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val artistDetails by viewModel.artistDetails.collectAsStateWithLifecycle()

    var isStatsExpanded by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    val playlists by viewModel.customPlaylists.collectAsState(initial = emptyList())

    var showConstellation by remember { mutableStateOf(false) }
    var showSonicFootprint by remember { mutableStateOf(false) }
    var searchTrackForOptions by remember { mutableStateOf<AudioTrack?>(null) }

    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val weatherUiState by homeViewModel.weatherUiState.collectAsStateWithLifecycle()
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val daylistTracks by dashboardViewModel.daylistTracks.collectAsStateWithLifecycle()
    val discoveryWeeklyTracks by dashboardViewModel.discoveryWeeklyTracks.collectAsStateWithLifecycle()

    val firstVisibleItem by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    LaunchedEffect(firstVisibleItem) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    BackHandler(enabled = isStatsExpanded || isSearchActive || showConstellation || showSonicFootprint) {
        if (showSonicFootprint) showSonicFootprint = false
        else if (showConstellation) showConstellation = false
        else if (isStatsExpanded) isStatsExpanded = false
        else if (isSearchActive) {
            isSearchActive = false
            viewModel.clearSearch()
        }
    }

    val query = uiState.searchQuery
    var localSearchQuery by remember(isSearchActive) { mutableStateOf(query) }

    LaunchedEffect(localSearchQuery) {
        if (localSearchQuery != uiState.searchQuery) {
            viewModel.onSearchQueryChanged(localSearchQuery)
        }
    }

    val matchedArtists = remember(localSearchQuery, uiState.artists) {
        if (localSearchQuery.isBlank()) emptyList()
        else uiState.artists.filter { it.name.contains(localSearchQuery, ignoreCase = true) }
    }
    val matchedAlbums = remember(localSearchQuery, uiState.tracks) {
        if (localSearchQuery.isBlank()) emptyList()
        else uiState.tracks.filter { it.album.contains(localSearchQuery, ignoreCase = true) }.distinctBy { it.album.lowercase().trim() }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            item {
                HomeHeader(
                    title = "TIGER PLAYER",
                    searchQuery = localSearchQuery,
                    isSearchActive = isSearchActive,
                    onSearchToggle = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            localSearchQuery = ""
                            viewModel.clearSearch()
                        }
                    },
                    onSearchQueryChange = { localSearchQuery = it },
                    onSettingsClick = onNavigateToSettings
                )
            }

            if (localSearchQuery.isNotEmpty()) {
                renderSearchResults(
                    uiState = uiState,
                    viewModel = viewModel,
                    matchedArtists = matchedArtists,
                    matchedAlbums = matchedAlbums,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigatetoArtist = onNavigatetoArtist,
                    artistDetails = artistDetails,
                    onOptionsClick = { track -> searchTrackForOptions = track }
                )
            } else {
                item {
                    NowBriefWidgetWrapper(
                        uiState = weatherUiState,
                        onWidgetClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            homeViewModel.fetchWeather()
                        }
                    )
                }

                item {
                    UserStatisticsHeader(
                        statistics = homeState.statistics,
                        onClick = { isStatsExpanded = true }
                    )
                }

                item {
                    SectionTitle("THE NEXUS")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        NexusGatewayCard(
                            modifier = Modifier.weight(1f),
                            title = "FOOTPRINT",
                            subtitle = "Listening DNA",
                            color = SonicCyan,
                            icon = Icons.Rounded.Timeline,
                            onClick = { showSonicFootprint = true }
                        )
                        NexusGatewayCard(
                            modifier = Modifier.weight(1f),
                            title = "COSMOS",
                            subtitle = "Neural Map",
                            color = NeuralPurple,
                            icon = Icons.Rounded.GraphicEq,
                            onClick = { showConstellation = true }
                        )
                    }
                }

                item {
                    val prismViewModel: PrismViewModel = hiltViewModel()
                    SonicPrismHubCard(viewModel = prismViewModel)
                }

                if (homeState.discoverTracks.isNotEmpty()) {
                    item { SectionTitle("VANGUARD DISCOVERY") }
                    item { DiscoverCarousel(tracks = homeState.discoverTracks, onTrackClick = { viewModel.playTrack(it) }) }
                }

                if (homeState.recommendedTracks.isNotEmpty()) {
                    item { SectionTitle("RECOMMENDED VOLUMES") }
                    item {
                        val uniqueAlbums = remember(homeState.recommendedTracks) {
                            homeState.recommendedTracks.distinctBy { it.album }
                        }
                        RecommendedAlbumsRow(albums = uniqueAlbums, onAlbumClick = { track -> onNavigateToAlbum(track.album) })
                    }
                }

                if (daylistTracks.isNotEmpty() || discoveryWeeklyTracks.isNotEmpty()) {
                    item { SectionTitle("NEURAL CURATIONS") }
                    item {
                        Column(
                            modifier = Modifier.padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (daylistTracks.isNotEmpty()) {
                                CurationRow(
                                    title = "DAYLIST",
                                    count = daylistTracks.size,
                                    color = AardBlue,
                                    onClick = { viewModel.setPlaylistAndPlay(daylistTracks, 0) }
                                )
                            }
                            if (discoveryWeeklyTracks.isNotEmpty()) {
                                CurationRow(
                                    title = "DISCOVERY WEEKLY",
                                    count = discoveryWeeklyTracks.size,
                                    color = SpotifyGreen,
                                    onClick = { viewModel.setPlaylistAndPlay(discoveryWeeklyTracks, 0) }
                                )
                            }
                        }
                    }
                }

                if (homeState.recentlyPlayedTracks.isNotEmpty()) {
                    item { SectionTitle("RECENT RITUALS") }
                    item {
                        RecentlyPlayedRow(
                            tracks = homeState.recentlyPlayedTracks,
                            onTrackClick = { viewModel.playTrack(it) },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isStatsExpanded,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            ExpandedStatsScreen(viewModel = viewModel, onClose = { isStatsExpanded = false })
        }

        AnimatedVisibility(
            visible = showSonicFootprint,
            enter = fadeIn(tween(500)) + scaleIn(initialScale = 0.95f, animationSpec = tween(500)),
            exit = fadeOut(tween(280)) + scaleOut(targetScale = 0.95f)
        ) {
            val sonicFootprintViewModel: SonicFootprintViewModel = hiltViewModel()
            SonicFootprintScreen(
                viewModel = sonicFootprintViewModel,
                onClose = { showSonicFootprint = false }
            )
        }

        AnimatedVisibility(
            visible = showConstellation,
            enter = fadeIn(tween(500)) + scaleIn(initialScale = 0.9f, animationSpec = tween(500)),
            exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.9f)
        ) {
            val constellationViewModel: ConstellationViewModel = hiltViewModel()
            ConstellationScreen(
                viewModel = constellationViewModel,
                onClose = { showConstellation = false }
            )
        }
    }

    searchTrackForOptions?.let { selectedTrack ->
        SongOptionsSheet(
            track = selectedTrack,
            playlists = playlists,
            onDismiss = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                searchTrackForOptions = null
            },
            onPlayNext = {
                viewModel.addNextToQueue(selectedTrack)
                searchTrackForOptions = null
            },
            onAddToPlaylist = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, selectedTrack)
                searchTrackForOptions = null
            },
            onGoToAlbum = { albumName ->
                isSearchActive = false
                viewModel.clearSearch()
                onNavigateToAlbum(albumName)
                searchTrackForOptions = null
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylist(name)
            }
        )
    }
}

@Composable
fun NexusGatewayCard(
    title: String,
    subtitle: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(110.dp)
            .shadow(12.dp, MaterialTheme.shapes.extraLarge, spotColor = color.copy(alpha = 0.25f))
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.15f), Color.Transparent)))
            .border(1.dp, color.copy(alpha = 0.15f), MaterialTheme.shapes.extraLarge)
            .bounceClick { onClick() }
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
            }
        }
    }
}

@Composable
fun SonicPrismHubCard(viewModel: PrismViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .shadow(16.dp, MaterialTheme.shapes.extraLarge, spotColor = SonicCyan.copy(alpha = 0.2f))
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), MaterialTheme.shapes.extraLarge)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(SonicCyan.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = SonicCyan, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("SONIC PRISM", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = Color.White)
                    Text("Real-time isolation hub", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                }
            }

            Switch(
                checked = isExpanded,
                onCheckedChange = { 
                    isExpanded = it
                    viewModel.setPrismEnabled(it)
                    if (!it) viewModel.disablePrismAndReset()
                },
                colors = SwitchDefaults.colors(checkedThumbColor = SonicCyan, checkedTrackColor = SonicCyan.copy(alpha = 0.3f))
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column {
                Spacer(modifier = Modifier.height(24.dp))
                PrismInlineMixer(
                    state = state,
                    onVocalsChange = viewModel::updateVocals,
                    onBeatsChange = viewModel::updateBeats,
                    onInstrumentsChange = viewModel::updateInstruments,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(Color.Black.copy(alpha = 0.2f))
                        .padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
fun CurationRow(
    title: String,
    count: Int,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(72.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, color.copy(alpha = 0.1f), MaterialTheme.shapes.large)
            .bounceClick { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(color.copy(alpha = 0.2f), MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            Icon(WitcherIcons.Playlist, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = Color.White)
            Text("$count Chants Manifested", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
        }
        Icon(WitcherIcons.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecommendedAlbumsRow(
    albums: List<AudioTrack>,
    onAlbumClick: (AudioTrack) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { albums.size })
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(pagerState.currentPage) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentPadding = PaddingValues(horizontal = 56.dp),
        pageSpacing = (-24).dp
    ) { page ->
        val track = albums[page]
        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)

        RecommendedAlbumCard(
            track = track,
            modifier = Modifier.graphicsLayer {
                cameraDistance = 12f * density
                rotationY = pageOffset.coerceIn(-1f, 1f) * -45f
                val scale = 1f - (absOffset * 0.15f)
                scaleX = scale
                scaleY = scale
                alpha = 1f - (absOffset * 0.5f)
            },
            onClick = { onAlbumClick(track) }
        )
    }
}

@Composable
fun RecommendedAlbumCard(track: AudioTrack, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val uppercaseArtist = remember(track.artist) { track.artist.uppercase() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .shadow(4.dp, MaterialTheme.shapes.extraLarge, ambientColor = Color.Transparent, spotColor = AardBlue.copy(alpha = 0.2f))
            .clip(MaterialTheme.shapes.extraLarge)
            .bounceClick { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)),
                shape = MaterialTheme.shapes.extraLarge
            )
    ) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            fallback = painterResource(R.drawable.ic_tiger_logo),
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.0f to Color.Transparent, 0.5f to Color.Black.copy(alpha = 0.2f), 1.0f to Color.Black.copy(alpha = 0.95f)))
        )

        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = track.album, style = MaterialTheme.typography.titleLarge,
                    color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uppercaseArtist, style = MaterialTheme.typography.labelMedium,
                    color = AardBlue.copy(alpha = 0.9f), fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(WitcherIcons.Play, null, tint = AardBlue, modifier = Modifier.size(16.dp).padding(start = 2.dp))
            }
        }
    }
}

@Composable
fun UserStatisticsHeader(statistics: UserStatistics, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .shadow(4.dp, MaterialTheme.shapes.extraLarge, ambientColor = Color.Transparent, spotColor = AardBlue.copy(alpha = 0.1f))
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), MaterialTheme.shapes.extraLarge)
            .bounceClick { onClick() }
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ARCHIVE ANALYTICS", style = MaterialTheme.typography.labelMedium, color = AardBlue, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Text("Your sessional wisdom", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(WitcherIcons.Expand, null, tint = AardBlue.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatGlassWidget(modifier = Modifier.weight(1f), title = "CHANTED TODAY", value = statistics.listeningTimeToday, icon = WitcherIcons.Duration, accentColor = AardBlue)
            StatGlassWidget(modifier = Modifier.weight(1f), title = "ARCHIVE SIZE", value = "${statistics.totalTracksCount}", icon = WitcherIcons.Library, accentColor = IgniRed)
        }
    }
}

@Composable
fun StatGlassWidget(
    title: String, value: String, icon: ImageVector,
    accentColor: Color, modifier: Modifier = Modifier, isFullWidth: Boolean = false
) {
    Box(
        modifier = modifier
            .height(if (isFullWidth) 72.dp else 88.dp)
            .clip(MaterialTheme.shapes.large)
            .glassEffect(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), MaterialTheme.shapes.large)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(if (isFullWidth) 40.dp else 44.dp).background(accentColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(verticalArrangement = Arrangement.Center) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, maxLines = 1)
                Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 12.dp)
    )
}

@Composable
fun HomeHeader(
    title: String, searchQuery: String, isSearchActive: Boolean,
    onSearchToggle: () -> Unit, onSearchQueryChange: (String) -> Unit, onSettingsClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        AnimatedVisibility(
            visible = !isSearchActive,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInHorizontally { it / 2 }).togetherWith(
                    fadeOut(tween(200)) + slideOutHorizontally { it / 2 }
                )
            }, label = "HeaderSearchAnimation"
        ) { searchactive ->
            if (searchactive) {
                androidx.compose.material3.OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "Search grimoires...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
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
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            null,
                            tint = MaterialTheme.aardBlue
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSearchToggle() }) {
                            Icon(
                                Icons.Default.Close,
                                "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSearchToggle() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSettingsClick() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Icon(WitcherIcons.Settings, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
