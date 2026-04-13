package com.example.tigerplayer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.example.tigerplayer.ui.extras.NowBriefWidget
import com.example.tigerplayer.ui.extras.NowBriefWidgetWrapper
import com.example.tigerplayer.ui.extras.WeatherState
import com.example.tigerplayer.ui.library.*
import com.example.tigerplayer.ui.player.LibraryArtist
import com.example.tigerplayer.ui.player.PlayerUiState
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import java.util.Calendar
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
    onNavigatetoArtist: (String) -> Unit,
    homeViewModel: HomeViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val homeState by viewModel.homeUiState.collectAsState()
    var isStatsExpanded by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    // --- TACTILE SCROLL ENGINE ---
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val weatherUiState by homeViewModel.weatherUiState.collectAsState()


    // We use derivedStateOf so we only trigger haptics when a NEW item hits the top,
    // rather than recomposing on every single pixel of scroll.
    val firstVisibleItem by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    LaunchedEffect(firstVisibleItem) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    BackHandler(enabled = isStatsExpanded || isSearchActive) {
        if (isStatsExpanded) isStatsExpanded = false
        else if (isSearchActive) {
            isSearchActive = false
            viewModel.clearSearch()
        }
    }

    val query = uiState.searchQuery
    val matchedArtists = remember(query, uiState.artists) {
        uiState.artists.filter { it.name.contains(query, ignoreCase = true) }
    }
    val matchedAlbums = remember(query, uiState.tracks) {
        uiState.tracks.filter { it.album.contains(query, ignoreCase = true) }.distinctBy { it.album.lowercase() }
    }

    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val isDay = currentHour in 6..18


    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
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
                    uiState = uiState, viewModel = viewModel,
                    matchedArtists = matchedArtists, matchedAlbums = matchedAlbums,
                    onNavigateToAlbum = onNavigateToAlbum, onNavigatetoArtist = onNavigatetoArtist
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

                item { UserStatisticsHeader(statistics = homeState.statistics, onClick = { isStatsExpanded = true }) }

                if (homeState.discoverTracks.isNotEmpty()) {
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

                if (homeState.recentlyPlayedTracks.isNotEmpty()) {
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
    }
}

// ==========================================
// --- RECTIFIED COMPONENTS ---
// ==========================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecommendedAlbumsRow(
    albums: List<AudioTrack>,
    onAlbumClick: (AudioTrack) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { albums.size })
    val hapticFeedback = LocalHapticFeedback.current

    // THE FIX: Tactile Pager Snap
    // Fires a physical click every time the user swipes to a new 3D album card
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
            .shadow(20.dp, MaterialTheme.shapes.extraLarge, spotColor = AardBlue.copy(alpha = 0.4f))
            .clip(MaterialTheme.shapes.extraLarge)
            .bounceClick { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)),
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
                    .glassEffect(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
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
            .padding(horizontal = 24.dp, vertical = 8.dp) // Aligned strictly with screen padding
            .shadow(16.dp, MaterialTheme.shapes.extraLarge, spotColor = AardBlue.copy(alpha = 0.15f))
            .glassEffect(MaterialTheme.shapes.extraLarge)
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

        Spacer(modifier = Modifier.height(12.dp))

        StatGlassWidget(modifier = Modifier.fillMaxWidth(), title = "PREVAILED ARTIST (THIS WEEK)", value = statistics.topArtistThisWeek.uppercase(), icon = WitcherIcons.Artist, accentColor = SpotifyGreen, isFullWidth = true)
    }
}

@Composable
fun StatGlassWidget(
    title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color, modifier: Modifier = Modifier, isFullWidth: Boolean = false
) {
    Box(
        modifier = modifier
            .height(if (isFullWidth) 72.dp else 88.dp)
            .clip(MaterialTheme.shapes.large)
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
        text = title.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 12.dp)
    )
}

@Composable
fun HomeHeader(
    title: String, searchQuery: String, isSearchActive: Boolean,
    onSearchToggle: () -> Unit, onSearchQueryChange: (String) -> Unit, onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            this@Row.AnimatedVisibility(visible = !isSearchActive, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                Text(title.uppercase(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }

            this@Row.AnimatedVisibility(visible = isSearchActive, enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End), exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)) {
                BasicTextField(
                    value = searchQuery, onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape).glassEffect(CircleShape),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true, cursorBrush = SolidColor(AardBlue),
                    decorationBox = { innerTextField ->
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) Text("Search archives...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyLarge)
                                innerTextField()
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(24.dp)) {
                                    Icon(WitcherIcons.Close, "Clear", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isSearchActive) MaterialTheme.colorScheme.surfaceVariant else AardBlue).bounceClick { onSearchToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (isSearchActive) WitcherIcons.Collapse else WitcherIcons.Search, null, tint = if (isSearchActive) MaterialTheme.colorScheme.onSurface else Color.Black, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        IconButton(onClick = onSettingsClick, modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))) {
            Icon(WitcherIcons.Settings, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
        }
    }
}