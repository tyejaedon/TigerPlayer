package com.tigerplayer.ui.cloud

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.DeezerAlbum
import com.tigerplayer.data.repository.DeezerArtist
import com.tigerplayer.ui.theme.SpotifyGreen
import com.tigerplayer.ui.theme.WitcherIcons
import com.tigerplayer.ui.theme.glassEffect

@Composable
fun DeezerDiscoverySection(
    modifier: Modifier = Modifier,
    viewModel: DeezerDiscoveryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTab by viewModel.selectedSearchTab.collectAsState()
    val chartTracks by viewModel.chartTracks.collectAsState()
    val chartArtists by viewModel.chartArtists.collectAsState()
    val searchTracks by viewModel.searchTrackResults.collectAsState()
    val searchArtists by viewModel.searchArtistResults.collectAsState()
    val searchAlbums by viewModel.searchAlbumResults.collectAsState()
    val drilldownTitle by viewModel.drilldownTitle.collectAsState()
    val drilldownTracks by viewModel.drilldownTracks.collectAsState()
    val isLoadingCharts by viewModel.isLoadingCharts.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val previewTrack by viewModel.previewTrack.collectAsState()
    val isPreviewPlaying by viewModel.isPreviewPlaying.collectAsState()
    val previewRemainingSeconds by viewModel.previewRemainingSeconds.collectAsState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    DeezerDiscoveryContent(
        modifier = modifier,
        searchQuery = searchQuery,
        selectedTab = selectedTab,
        chartTracks = chartTracks,
        chartArtists = chartArtists,
        searchTracks = searchTracks,
        searchArtists = searchArtists,
        searchAlbums = searchAlbums,
        drilldownTitle = drilldownTitle,
        drilldownTracks = drilldownTracks,
        isLoading = isLoadingCharts || isSearching,
        previewTrack = previewTrack,
        isPreviewPlaying = isPreviewPlaying,
        previewRemainingSeconds = previewRemainingSeconds,
        onQueryChange = viewModel::onSearchQueryChange,
        onTabSelected = viewModel::onSearchTabSelected,
        onPlayPreview = viewModel::playPreview,
        onTogglePlayPause = viewModel::togglePreviewPlayPause,
        onStopPreview = viewModel::stopPreview,
        onArtistClick = viewModel::openArtistTopTracks,
        onAlbumClick = viewModel::openAlbumTracks,
        onCloseDrilldown = viewModel::closeDrilldown,
        onFindLocalMatch = viewModel::findAndPlayLocalMatch,
        onTriggerInfinitePlay = viewModel::triggerInfinitePlayFromPreview
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeezerDiscoveryContent(
    searchQuery: String,
    selectedTab: DeezerSearchTab,
    chartTracks: List<AudioTrack>,
    chartArtists: List<DeezerArtist>,
    searchTracks: List<AudioTrack>,
    searchArtists: List<DeezerArtist>,
    searchAlbums: List<DeezerAlbum>,
    drilldownTitle: String?,
    drilldownTracks: List<AudioTrack>,
    isLoading: Boolean,
    previewTrack: AudioTrack?,
    isPreviewPlaying: Boolean,
    previewRemainingSeconds: Int,
    onQueryChange: (String) -> Unit,
    onTabSelected: (DeezerSearchTab) -> Unit,
    onPlayPreview: (AudioTrack) -> Unit,
    onTogglePlayPause: () -> Unit,
    onStopPreview: () -> Unit,
    onArtistClick: (DeezerArtist) -> Unit,
    onAlbumClick: (DeezerAlbum) -> Unit,
    onCloseDrilldown: () -> Unit,
    onFindLocalMatch: (AudioTrack) -> Unit,
    onTriggerInfinitePlay: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = modifier.fillMaxSize()) {
        // Search Input
        TextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .height(52.dp)
                .glassEffect(CircleShape),
            placeholder = {
                Text(
                    "Discover tracks, artists, albums (Zero-Auth)...",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(WitcherIcons.Explore, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        keyboardController?.hide()
                        onQueryChange("")
                    }) {
                        Icon(WitcherIcons.Close, contentDescription = "Clear search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = SpotifyGreen
            ),
            shape = CircleShape,
            singleLine = true
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                drilldownTitle != null -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onCloseDrilldown) {
                                Icon(WitcherIcons.Back, contentDescription = "Back")
                            }
                            Text(
                                text = drilldownTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DeezerTrackList(
                            tracks = drilldownTracks,
                            activePreviewId = previewTrack?.id,
                            isPlaying = isPreviewPlaying,
                            onTrackClick = onPlayPreview
                        )
                    }
                }

                searchQuery.isNotBlank() -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SecondaryTabRow(
                            selectedTabIndex = DeezerSearchTab.entries.indexOf(selectedTab),
                            containerColor = Color.Transparent,
                            divider = {},
                            indicator = {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(DeezerSearchTab.entries.indexOf(selectedTab)),
                                    height = 2.dp,
                                    color = SpotifyGreen
                                )
                            }
                        ) {
                            DeezerSearchTab.entries.forEach { tab ->
                                Tab(
                                    selected = tab == selectedTab,
                                    onClick = { onTabSelected(tab) },
                                    text = {
                                        Text(
                                            text = tab.label.uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                )
                            }
                        }

                        when (selectedTab) {
                            DeezerSearchTab.TRACKS -> DeezerTrackList(
                                tracks = searchTracks,
                                activePreviewId = previewTrack?.id,
                                isPlaying = isPreviewPlaying,
                                onTrackClick = onPlayPreview
                            )
                            DeezerSearchTab.ARTISTS -> DeezerArtistList(
                                artists = searchArtists,
                                onArtistClick = onArtistClick
                            )
                            DeezerSearchTab.ALBUMS -> DeezerAlbumList(
                                albums = searchAlbums,
                                onAlbumClick = onAlbumClick
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (chartArtists.isNotEmpty()) {
                            item {
                                Text(
                                    text = "TRENDING ARTISTS",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(chartArtists, key = { it.id }) { artist ->
                                        Column(
                                            modifier = Modifier
                                                .width(84.dp)
                                                .clickable { onArtistClick(artist) },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            AsyncImage(
                                                model = artist.imageUrl,
                                                contentDescription = artist.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = artist.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "TOP CHARTS • 30S PREVIEWS",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                        }

                        items(chartTracks, key = { it.id }) { track ->
                            DeezerTrackRow(
                                track = track,
                                isActive = previewTrack?.id == track.id,
                                isPlaying = previewTrack?.id == track.id && isPreviewPlaying,
                                onClick = { onPlayPreview(track) }
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = SpotifyGreen
                )
            }
        }

        AnimatedVisibility(visible = previewTrack != null) {
            previewTrack?.let { track ->
                DeezerPreviewBar(
                    track = track,
                    isPlaying = isPreviewPlaying,
                    remainingSeconds = previewRemainingSeconds,
                    onTogglePlayPause = onTogglePlayPause,
                    onStop = onStopPreview,
                    onFindLocalMatch = { onFindLocalMatch(track) },
                    onTriggerInfinitePlay = { onTriggerInfinitePlay(track) }
                )
            }
        }
    }
}

@Composable
private fun DeezerTrackList(
    tracks: List<AudioTrack>,
    activePreviewId: String?,
    isPlaying: Boolean,
    onTrackClick: (AudioTrack) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(tracks, key = { it.id }) { track ->
            DeezerTrackRow(
                track = track,
                isActive = activePreviewId == track.id,
                isPlaying = activePreviewId == track.id && isPlaying,
                onClick = { onTrackClick(track) }
            )
        }
    }
}

@Composable
private fun DeezerTrackRow(
    track: AudioTrack,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) SpotifyGreen else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = if (isPlaying) WitcherIcons.Pause else WitcherIcons.Play,
            contentDescription = if (isPlaying) "Pause preview" else "Play 30s preview",
            tint = if (isActive) SpotifyGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun DeezerArtistList(
    artists: List<DeezerArtist>,
    onArtistClick: (DeezerArtist) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(artists, key = { it.id }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onArtistClick(artist) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = artist.imageUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (artist.albumCount > 0) {
                        Text(
                            text = "${artist.albumCount} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(WitcherIcons.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun DeezerAlbumList(
    albums: List<DeezerAlbum>,
    onAlbumClick: (DeezerAlbum) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAlbumClick(album) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(WitcherIcons.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
fun DeezerPreviewBar(
    track: AudioTrack,
    isPlaying: Boolean,
    remainingSeconds: Int,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onFindLocalMatch: () -> Unit,
    onTriggerInfinitePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniWaveformIndicator(isPlaying = isPlaying)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "0:${remainingSeconds.toString().padStart(2, '0')} • ${track.artist}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onFindLocalMatch) {
                Icon(WitcherIcons.Library, contentDescription = "Find in local library")
            }
            IconButton(onClick = onTriggerInfinitePlay) {
                Icon(WitcherIcons.Shuffle, contentDescription = "Start Infinite Play")
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (isPlaying) WitcherIcons.Pause else WitcherIcons.Play,
                    contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                    tint = SpotifyGreen
                )
            }
            IconButton(onClick = onStop) {
                Icon(WitcherIcons.Close, contentDescription = "Close preview")
            }
        }
    }
}

@Composable
private fun MiniWaveformIndicator(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "MiniWaveform")
    val bar1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "Bar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(340), RepeatMode.Reverse),
        label = "Bar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "Bar3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(12.dp)
    ) {
        val heights = if (isPlaying) listOf(bar1, bar2, bar3) else listOf(0.35f, 0.35f, 0.35f)
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((12f * fraction).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(SpotifyGreen)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeezerDiscoveryPreview() {
    val sampleTrack = AudioTrack(
        id = "deezer:1",
        title = "Midnight City",
        artist = "M83",
        album = "Hurry Up, We're Dreaming",
        durationMs = 243_000L,
        artworkUri = null
    )
    DeezerDiscoveryContent(
        searchQuery = "",
        selectedTab = DeezerSearchTab.TRACKS,
        chartTracks = listOf(sampleTrack),
        chartArtists = listOf(DeezerArtist(id = 1L, name = "M83", imageUrl = null)),
        searchTracks = emptyList(),
        searchArtists = emptyList(),
        searchAlbums = emptyList(),
        drilldownTitle = null,
        drilldownTracks = emptyList(),
        isLoading = false,
        previewTrack = sampleTrack,
        isPreviewPlaying = true,
        previewRemainingSeconds = 24,
        onQueryChange = {},
        onTabSelected = {},
        onPlayPreview = {},
        onTogglePlayPause = {},
        onStopPreview = {},
        onArtistClick = {},
        onAlbumClick = {},
        onCloseDrilldown = {},
        onFindLocalMatch = {},
        onTriggerInfinitePlay = {}
    )
}
