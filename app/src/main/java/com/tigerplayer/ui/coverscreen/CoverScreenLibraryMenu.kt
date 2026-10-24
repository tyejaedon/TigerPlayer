package com.tigerplayer.ui.coverscreen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
import com.tigerplayer.ui.theme.glassEffect
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tigerplayer.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.data.remote.model.SpotifyPlaylist
import com.tigerplayer.ui.cloud.CloudViewModel
import com.tigerplayer.ui.player.PlayerViewModel
import com.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

enum class CoverScreenLibraryTab(val label: String) {
    HISTORY("History"),
    CONSTELLATION("Constellation"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    SONGS("Songs"),
    PLAYLISTS("Playlists"),
    SPOTIFY_PLAYLISTS("Spotify Playlists")
}

@Composable
fun CoverScreenLibraryTabRow(
    selectedTab: CoverScreenLibraryTab,
    onTabSelected: (CoverScreenLibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = CoverScreenLibraryTab.entries.indexOf(selectedTab),
        edgePadding = 8.dp,
        modifier = modifier.testTag(CoverScreenTestTags.LIBRARY_TABS)
    ) {
        CoverScreenLibraryTab.entries.forEach { tab ->
            val tabTitle = when (tab) {
                CoverScreenLibraryTab.HISTORY -> "History"
                CoverScreenLibraryTab.CONSTELLATION -> "Constellation"
                CoverScreenLibraryTab.ARTISTS -> stringResource(R.string.coverscreen_tab_artists)
                CoverScreenLibraryTab.ALBUMS -> stringResource(R.string.coverscreen_tab_albums)
                CoverScreenLibraryTab.SONGS -> stringResource(R.string.coverscreen_tab_songs)
                CoverScreenLibraryTab.PLAYLISTS -> stringResource(R.string.coverscreen_tab_playlists)
                CoverScreenLibraryTab.SPOTIFY_PLAYLISTS -> stringResource(R.string.coverscreen_tab_spotify_playlists)
            }
            Tab(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(CoverScreenTestTags.libraryTab(tab.name)),
                text = { Text(tabTitle) }
            )
        }
    }
}

@Composable
fun CoverScreenLibraryMenu(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showSearchInitially: Boolean = false,
    libraryViewModel: CoverScreenLibraryViewModel = hiltViewModel(),
    cloudViewModel: CloudViewModel = hiltViewModel()
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val artists by libraryViewModel.artists.collectAsStateWithLifecycle()
    val selectedSource by libraryViewModel.selectedSource.collectAsStateWithLifecycle()
    val playlists by playerViewModel.customPlaylists.collectAsStateWithLifecycle()
    val spotifyPlaylists by cloudViewModel.userPlaylists.collectAsStateWithLifecycle()
    val spotifyAlbums by cloudViewModel.userAlbums.collectAsStateWithLifecycle()
    val spotifyAuthenticated by cloudViewModel.isSpotifyAuthenticated.collectAsStateWithLifecycle()
    val recentTracks by playerViewModel.recentTracks.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(CoverScreenLibraryTab.HISTORY) }
    var selectedAlbum by remember { mutableStateOf<CoverScreenAlbum?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var selectedPlaylistTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var searchActive by remember { mutableStateOf(showSearchInitially) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var voiceSearchError by remember { mutableStateOf<String?>(null) }
    val voiceSearchNoSpeechError = stringResource(R.string.coverscreen_voice_search_no_speech)
    val searchPrompt = stringResource(R.string.coverscreen_search_prompt)
    val voiceSearchUnavailableError = stringResource(R.string.coverscreen_voice_search_unavailable)
    val voiceSearchAvailable = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context)
    }
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognizedText = result.data
                ?.getStringArrayListExtra(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!recognizedText.isNullOrBlank()) {
                searchQuery = recognizedText
                voiceSearchError = null
            } else {
                voiceSearchError = voiceSearchNoSpeechError
            }
        }
    }

    @OptIn(FlowPreview::class)
    LaunchedEffect(searchActive) {
        if (!searchActive) {
            searchQuery = ""
            debouncedSearchQuery = ""
            libraryViewModel.setSearchQuery("")
            return@LaunchedEffect
        }
        snapshotFlow { searchQuery }
            .debounce(300)
            .distinctUntilChanged()
            .collect { query ->
                debouncedSearchQuery = query.trim()
                libraryViewModel.setSearchQuery(query)
            }
    }

    LaunchedEffect(showSearchInitially) {
        if (showSearchInitially) searchActive = true
    }

    val localAlbums = remember(playerState.allTracks) {
        playerState.allTracks
            .filter { it.album.isNotBlank() }
            .groupBy { track ->
                "${ArtistUtils.getBaseArtist(track.artist).lowercase()}|${track.album.lowercase()}"
            }
            .map { (id, tracks) ->
                val first = tracks.first()
                CoverScreenAlbum(
                    id = id,
                    artistId = ArtistUtils.getBaseArtist(first.artist).lowercase(),
                    name = first.album,
                    trackCount = tracks.size,
                    artworkUrl = first.artworkUri.toString().takeUnless { it.isBlank() }
                )
            }
            .sortedBy { it.name.lowercase() }
    }
    val spotifyCoverAlbums = remember(spotifyAlbums) {
        spotifyAlbums.flatMap { album ->
            album.artists.map { artist ->
                CoverScreenAlbum(
                    id = album.id,
                    artistId = artist.id,
                    name = album.name,
                    trackCount = album.totalTracks ?: 0,
                    artworkUrl = album.images?.firstOrNull()?.url
                )
            }
        }
    }
    val filteredLocalAlbums = remember(localAlbums, debouncedSearchQuery, playerState.allTracks) {
        if (debouncedSearchQuery.isBlank()) localAlbums else localAlbums.filter { album ->
            album.name.contains(debouncedSearchQuery, ignoreCase = true) ||
                playerState.allTracks.any { track ->
                    track.album == album.name &&
                        (track.artist.contains(debouncedSearchQuery, ignoreCase = true) ||
                            track.title.contains(debouncedSearchQuery, ignoreCase = true))
                }
        }
    }
    val filteredPlaylists = remember(playlists, debouncedSearchQuery) {
        if (debouncedSearchQuery.isBlank()) playlists else playlists.filter {
            it.name.contains(debouncedSearchQuery, ignoreCase = true)
        }
    }
    val filteredSpotifyPlaylists = remember(spotifyPlaylists, debouncedSearchQuery) {
        if (debouncedSearchQuery.isBlank()) spotifyPlaylists else spotifyPlaylists.filter {
            it.name.contains(debouncedSearchQuery, ignoreCase = true)
        }
    }
    val filteredSongs = remember(playerState.allTracks, debouncedSearchQuery) {
        filterAndSortTracks(playerState.allTracks, debouncedSearchQuery)
    }

    BackHandler(enabled = true) {
        when {
            selectedAlbum != null -> selectedAlbum = null
            selectedPlaylist != null -> selectedPlaylist = null
            searchActive -> {
                keyboardController?.hide()
                focusManager.clearFocus()
                searchActive = false
                voiceSearchError = null
            }
            else -> onDismiss()
        }
    }

    LaunchedEffect(selectedPlaylist?.id) {
        val playlistId = selectedPlaylist?.id ?: return@LaunchedEffect
        playerViewModel.getPlaylistTracks(playlistId).collect { selectedPlaylistTracks = it }
    }

    LaunchedEffect(spotifyAuthenticated) {
        if (spotifyAuthenticated) cloudViewModel.loadArchives()
    }

    LaunchedEffect(selectedTab, spotifyAuthenticated) {
        if (selectedTab == CoverScreenLibraryTab.SPOTIFY_PLAYLISTS && spotifyAuthenticated) {
            cloudViewModel.loadPlaylists()
        }
    }

    val libraryDesc = stringResource(R.string.coverscreen_description_library)
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(CoverScreenTestTags.LIBRARY_MENU)
            .semantics { contentDescription = libraryDesc },
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            if (selectedAlbum != null || selectedPlaylist != null) {
                val album = selectedAlbum
                val playlist = selectedPlaylist
                val albumTracks = if (album == null) {
                    emptyList()
                } else {
                    playerState.allTracks.filter { track ->
                        track.album == album.name &&
                            ArtistUtils.getBaseArtist(track.artist).equals(
                                album.artistId,
                                ignoreCase = true
                            ) &&
                            (debouncedSearchQuery.isBlank() ||
                                track.title.contains(debouncedSearchQuery, ignoreCase = true) ||
                                track.artist.contains(debouncedSearchQuery, ignoreCase = true))
                    }.sortedBy { it.trackNumber }
                }
                CoverScreenAlbumsView(
                    album = album ?: CoverScreenAlbum(
                        id = playlist!!.id.toString(),
                        artistId = "playlist",
                        name = playlist.name,
                        trackCount = selectedPlaylistTracks.size,
                        artworkUrl = playlist.artworkUri
                    ),
                    artistName = if (playlist != null) "Playlist" else albumTracks.firstOrNull()?.artist.orEmpty(),
                    tracks = when {
                        playlist != null -> selectedPlaylistTracks
                        album != null -> albumTracks
                        else -> emptyList()
                    },
                    onBack = {
                        selectedAlbum = null
                        selectedPlaylist = null
                    },
                    onPlayTrack = playerViewModel::playTrack,
                    onAddToQueue = playerViewModel::addToQueue,
                    onRemoveFromQueue = { track ->
                        val index = playerState.queue.indexOfFirst { it.id == track.id }
                        if (index >= 0) playerViewModel.removeFromQueue(index)
                    },
                    onPlayAll = playerViewModel::playAll
                )
            } else {
                if (searchActive) {
                    CoverScreenSearchBar(
                        query = searchQuery,
                        onQueryChange = {
                            searchQuery = it
                            voiceSearchError = null
                        },
                        onVoiceSearch = {
                            voiceSearchError = null
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_PROMPT, searchPrompt)
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (error: ActivityNotFoundException) {
                                voiceSearchError = voiceSearchUnavailableError
                            }
                        },
                        onClose = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            searchActive = false
                            voiceSearchError = null
                        },
                        voiceSearchAvailable = voiceSearchAvailable,
                        voiceSearchError = voiceSearchError,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 12.dp, top = 4.dp)
                            .pointerInput(Unit) {
                                var totalDrag = 0f
                                val swipeThreshold = with(density) { 80.dp.toPx() }
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, amount -> totalDrag += amount },
                                    onDragEnd = {
                                        if (totalDrag > swipeThreshold) onDismiss()
                                        totalDrag = 0f
                                    },
                                    onDragCancel = { totalDrag = 0f }
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val backToPlaybackLabel = stringResource(R.string.coverscreen_action_back_to_playback)
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .minimumInteractiveComponentSize()
                                .semantics {
                                    role = Role.Button
                                    onClick(label = backToPlaybackLabel) {
                                        onDismiss()
                                        true
                                    }
                                }
                                .testTag(CoverScreenTestTags.LIBRARY_BACK)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = backToPlaybackLabel
                            )
                        }
                        Text(
                            text = stringResource(R.string.coverscreen_title_library),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        val searchLibraryLabel = stringResource(R.string.coverscreen_action_search_library)
                        IconButton(
                            onClick = { searchActive = true },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .minimumInteractiveComponentSize()
                                .semantics {
                                    role = Role.Button
                                    onClick(label = searchLibraryLabel) {
                                        searchActive = true
                                        true
                                    }
                                }
                                .testTag(CoverScreenTestTags.SEARCH_OPEN)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = searchLibraryLabel
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.LibraryMusic,
                            contentDescription = null
                        )
                    }
                }

                if (searchActive && debouncedSearchQuery.isNotBlank()) {
                    CoverScreenSearchResults(
                        query = debouncedSearchQuery,
                        artists = artists,
                        albums = if (selectedSource == CoverScreenLibrarySource.LOCAL) {
                            filteredLocalAlbums
                        } else {
                            spotifyCoverAlbums.filter {
                                it.name.contains(debouncedSearchQuery, ignoreCase = true)
                            }
                        },
                        tracks = playerState.allTracks,
                        onArtistSelected = { artist ->
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (selectedSource == CoverScreenLibrarySource.SPOTIFY) {
                                spotifyAlbums.firstOrNull { album ->
                                    album.artists.any { it.id == artist.id }
                                }?.let { album ->
                                    cloudViewModel.playSpotifyCollection(album.uri, album.name)
                                    onDismiss()
                                }
                            } else {
                                playerState.allTracks.firstOrNull { track ->
                                    ArtistUtils.getBaseArtist(track.artist)
                                        .equals(artist.name, ignoreCase = true)
                                }?.let { track ->
                                    playerViewModel.playTrack(track)
                                    onDismiss()
                                }
                            }
                        },
                        onAlbumSelected = { album ->
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (selectedSource == CoverScreenLibrarySource.SPOTIFY) {
                                spotifyAlbums.firstOrNull { it.id == album.id }?.let {
                                    cloudViewModel.playSpotifyCollection(it.uri, it.name)
                                    onDismiss()
                                }
                            } else {
                                selectedAlbum = album
                            }
                        },
                        onPlayTrack = { track ->
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            playerViewModel.playTrack(track)
                            onDismiss()
                        },
                        onQueueTrack = { track ->
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            playerViewModel.addToQueue(track)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                } else {
                    CoverScreenLibraryTabRow(
                        selectedTab = selectedTab,
                        onTabSelected = { tab ->
                            selectedTab = tab
                            if (tab == CoverScreenLibraryTab.ARTISTS) {
                                libraryViewModel.selectSource(CoverScreenLibrarySource.LOCAL)
                            }
                        }
                    )

                    AnimatedContent(
                        targetState = selectedTab,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(CoverScreenTestTags.LIBRARY_CONTENT),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "Cover screen library tab"
                    ) { tab ->
                        when (tab) {
                            CoverScreenLibraryTab.HISTORY -> HistoryPicker(
                                tracks = recentTracks,
                                onPlayTrack = { track ->
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    playerViewModel.playTrack(track)
                                }
                            )
                            CoverScreenLibraryTab.CONSTELLATION -> CoverScreenConstellationView(
                                viewModel = hiltViewModel(),
                                onArtistSelected = { artistName ->
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    playerViewModel.onSearchQueryChanged(artistName)
                                    selectedTab = CoverScreenLibraryTab.ARTISTS
                                }
                            )
                            CoverScreenLibraryTab.ARTISTS -> Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedSource == CoverScreenLibrarySource.LOCAL,
                                        onClick = {
                                            libraryViewModel.selectSource(CoverScreenLibrarySource.LOCAL)
                                        },
                                        label = { Text(stringResource(R.string.coverscreen_source_local)) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag(CoverScreenTestTags.LOCAL_SOURCE)
                                    )
                                    FilterChip(
                                        selected = selectedSource == CoverScreenLibrarySource.SPOTIFY,
                                        onClick = {
                                            libraryViewModel.selectSource(CoverScreenLibrarySource.SPOTIFY)
                                        },
                                        label = { Text(stringResource(R.string.coverscreen_source_spotify)) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag(CoverScreenTestTags.SPOTIFY_SOURCE)
                                    )
                                }
                                CoverScreenArtistsGrid(
                                    artists = artists,
                                    albums = if (selectedSource == CoverScreenLibrarySource.LOCAL) {
                                        filteredLocalAlbums
                                    } else {
                                        spotifyCoverAlbums.filter {
                                            it.name.contains(debouncedSearchQuery, ignoreCase = true)
                                        }
                                    },
                                    onAlbumSelected = { album ->
                                        if (selectedSource == CoverScreenLibrarySource.SPOTIFY) {
                                            val spotifyAlbum = spotifyAlbums.firstOrNull { it.id == album.id }
                                            spotifyAlbum?.let {
                                                cloudViewModel.playSpotifyCollection(it.uri, it.name)
                                                onDismiss()
                                            }
                                        } else {
                                            selectedAlbum = album
                                        }
                                    }
                                )
                            }

                            CoverScreenLibraryTab.ALBUMS -> AlbumPicker(
                                albums = filteredLocalAlbums,
                                onAlbumSelected = { selectedAlbum = it }
                            )

                            CoverScreenLibraryTab.SONGS -> SongPicker(
                                tracks = filteredSongs,
                                onPlayTrack = { track ->
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    playerViewModel.playTrack(track)
                                },
                                onAddToQueue = { track ->
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    playerViewModel.addToQueue(track)
                                }
                            )

                            CoverScreenLibraryTab.PLAYLISTS -> LocalPlaylistPicker(
                                playlists = filteredPlaylists,
                                onPlaylistSelected = { selectedPlaylist = it }
                            )

                            CoverScreenLibraryTab.SPOTIFY_PLAYLISTS -> SpotifyPlaylistPicker(
                                authenticated = spotifyAuthenticated,
                                playlists = filteredSpotifyPlaylists,
                                onPlaylistSelected = { playlist ->
                                    cloudViewModel.playSpotifyCollection(playlist.uri, playlist.name)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumPicker(
    albums: List<CoverScreenAlbum>,
    onAlbumSelected: (CoverScreenAlbum) -> Unit
) {
    if (albums.isEmpty()) {
        EmptyLibraryMessage(stringResource(R.string.coverscreen_empty_albums))
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 144.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(albums, key = CoverScreenAlbum::id) { album ->
                LibraryTile(
                    title = album.name,
                    subtitle = "${album.trackCount} tracks",
                    description = "Album ${album.name}",
                    imageUrl = album.artworkUrl,
                    icon = Icons.Filled.Album,
                    modifier = Modifier.testTag(CoverScreenTestTags.libraryAlbum(album.id)),
                    onClick = { onAlbumSelected(album) }
                )
            }
        }
    }
}

@Composable
private fun LocalPlaylistPicker(
    playlists: List<Playlist>,
    onPlaylistSelected: (Playlist) -> Unit
) {
    if (playlists.isEmpty()) {
        EmptyLibraryMessage(stringResource(R.string.coverscreen_empty_playlists))
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(playlists.size, key = { playlists[it].id }) { index ->
                val playlist = playlists[index]
                LibraryTile(
                    title = playlist.name,
                    subtitle = "${playlist.trackCount} tracks",
                    description = "Playlist ${playlist.name}",
                    imageUrl = playlist.artworkUri,
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    modifier = Modifier.testTag(CoverScreenTestTags.localPlaylist(playlist.id)),
                    onClick = { onPlaylistSelected(playlist) }
                )
            }
        }
    }
}

@Composable
internal fun SpotifyPlaylistPicker(
    authenticated: Boolean,
    playlists: List<SpotifyPlaylist>,
    onPlaylistSelected: (SpotifyPlaylist) -> Unit
) {
    if (!authenticated) {
        EmptyLibraryMessage(
            message = stringResource(R.string.coverscreen_auth_required),
            testTag = CoverScreenTestTags.LIBRARY_SPOTIFY_AUTH_REQUIRED
        )
    } else if (playlists.isEmpty()) {
        EmptyLibraryMessage(stringResource(R.string.coverscreen_empty_spotify_playlists))
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(playlists.size, key = { playlists[it].id }) { index ->
                val playlist = playlists[index]
                LibraryTile(
                    title = playlist.name,
                    subtitle = playlist.owner.displayName,
                    description = "Spotify playlist ${playlist.name}",
                    imageUrl = playlist.images?.firstOrNull()?.url,
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    modifier = Modifier.testTag(CoverScreenTestTags.spotifyPlaylist(playlist.id)),
                    onClick = { onPlaylistSelected(playlist) }
                )
            }
        }
    }
}

@Composable
private fun LibraryTile(
    title: String,
    subtitle: String,
    description: String,
    imageUrl: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .minimumInteractiveComponentSize()
            .padding(vertical = 4.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageUrl.isNullOrBlank()) {
                Icon(icon, contentDescription = null)
            } else {
                coil.compose.AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryMessage(message: String, testTag: String? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            modifier = Modifier
                .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
                .semantics { contentDescription = message },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun HistoryPicker(
    tracks: List<AudioTrack>,
    onPlayTrack: (AudioTrack) -> Unit
) {
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No recent history",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tracks, key = { it.id }) { track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .glassEffect(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(
                        onClickLabel = "Play ${track.title}",
                        onClick = { onPlayTrack(track) }
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
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
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SongPicker(
    tracks: List<AudioTrack>,
    onPlayTrack: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyLibraryMessage(stringResource(R.string.coverscreen_empty_songs))
    } else {
        var revealedTrackId by remember { mutableStateOf<String?>(null) }
        var optionsTrack by remember { mutableStateOf<AudioTrack?>(null) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(CoverScreenTestTags.SONGS_LIST),
            contentPadding = PaddingValues(12.dp)
        ) {
            items(tracks, key = AudioTrack::id) { track ->
                SongTrackRow(
                    track = track,
                    isQueueRevealed = revealedTrackId == track.id,
                    isOptionsExpanded = optionsTrack?.id == track.id,
                    onPlay = { onPlayTrack(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onRevealQueue = { revealedTrackId = track.id },
                    onDismissQueue = { revealedTrackId = null },
                    onShowOptions = { optionsTrack = track },
                    onDismissOptions = { optionsTrack = null }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SongTrackRow(
    track: AudioTrack,
    isQueueRevealed: Boolean,
    isOptionsExpanded: Boolean,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onRevealQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onShowOptions: () -> Unit,
    onDismissOptions: () -> Unit
) {
    var horizontalDrag by remember(track.id) { mutableStateOf(0f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                    onDragEnd = {
                        if (horizontalDrag < -64.dp.toPx()) onRevealQueue()
                        if (horizontalDrag > 64.dp.toPx()) onDismissQueue()
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.coverscreen_action_play_now),
                    onLongClickLabel = stringResource(R.string.coverscreen_action_add_to_queue),
                    onClick = onPlay,
                    onLongClick = {
                        onRevealQueue()
                        onShowOptions()
                    }
                )
                .testTag(CoverScreenTestTags.songTrack(track.id))
                .semantics {
                    contentDescription =
                        "${track.title}, ${track.artist}, ${formatTrackDuration(track.durationMs)}"
                },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (track.artworkUri.toString().isBlank()) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        coil.compose.AsyncImage(
                            model = track.artworkUri,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    text = formatTrackDuration(track.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play ${track.title}"
                )
            }
        }
        val addToQueueLabel = stringResource(R.string.coverscreen_action_add_to_queue)
        if (isQueueRevealed) {
            TextButton(
                onClick = {
                    onAddToQueue()
                    onDismissQueue()
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .minimumInteractiveComponentSize()
                    .semantics {
                        role = Role.Button
                        onClick(label = addToQueueLabel) {
                            onAddToQueue()
                            onDismissQueue()
                            true
                        }
                    }
                    .testTag(CoverScreenTestTags.songQueue(track.id))
            ) {
                Text(addToQueueLabel)
            }
        }
        Box {
            IconButton(
                onClick = onAddToQueue,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .minimumInteractiveComponentSize()
                    .size(48.dp)
                    .testTag(CoverScreenTestTags.queueTrack(track.id))
                    .semantics {
                        role = Role.Button
                        onClick(label = addToQueueLabel) {
                            onAddToQueue()
                            true
                        }
                        contentDescription = "Add ${track.title} to queue"
                    }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null
                )
            }
            DropdownMenu(
                expanded = isOptionsExpanded,
                onDismissRequest = onDismissOptions
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.coverscreen_action_play_now)) },
                    onClick = {
                        onDismissOptions()
                        onPlay()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.coverscreen_action_add_to_queue)) },
                    onClick = {
                        onDismissOptions()
                        onAddToQueue()
                    }
                )
            }
        }
    }
}

internal fun filterAndSortTracks(
    tracks: List<AudioTrack>,
    query: String = ""
): List<AudioTrack> {
    val trimmed = query.trim()
    val filtered = if (trimmed.isBlank()) {
        tracks
    } else {
        tracks.filter { track ->
            track.title.contains(trimmed, ignoreCase = true) ||
                track.artist.contains(trimmed, ignoreCase = true)
        }
    }
    return filtered.sortedBy { it.title.lowercase() }
}
