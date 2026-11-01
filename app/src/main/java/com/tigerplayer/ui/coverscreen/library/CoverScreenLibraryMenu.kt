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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.R
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.ui.cloud.CloudViewModel
import com.tigerplayer.ui.dashboard.DashboardViewModel
import com.tigerplayer.ui.player.PlayerViewModel
import com.tigerplayer.ui.settings.SettingsViewModel
import com.tigerplayer.ui.theme.WitcherIcons
import com.tigerplayer.ui.theme.aardBlue
import com.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

enum class CoverScreenLibraryTab(val label: String) {
    HOME("Home"),
    HISTORY("Stats"),
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
                CoverScreenLibraryTab.HOME -> stringResource(R.string.coverscreen_tab_home)
                CoverScreenLibraryTab.HISTORY -> "Stats"
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
    cloudViewModel: CloudViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val resources = context.resources
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val artists by libraryViewModel.artists.collectAsStateWithLifecycle()
    val playlists by playerViewModel.customPlaylists.collectAsStateWithLifecycle()
    val spotifyPlaylists by cloudViewModel.userPlaylists.collectAsStateWithLifecycle()
    val spotifyAuthenticated by cloudViewModel.isSpotifyAuthenticated.collectAsStateWithLifecycle()
    val selectedTab by libraryViewModel.selectedTab.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var selectedAlbum by remember { mutableStateOf<CoverScreenAlbum?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var selectedPlaylistTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var playlistToEdit by remember { mutableStateOf<Playlist?>(null) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val importM3uLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = playerViewModel.importM3uPlaylist(uri)
                result.onSuccess { importResult ->
                    importStatusMessage = resources.getString(
                        R.string.coverscreen_import_playlist_success,
                        importResult.playlistName,
                        importResult.importedCount
                    )
                }.onFailure { err ->
                    importStatusMessage = resources.getString(
                        R.string.coverscreen_import_playlist_failure,
                        err.message ?: "Unknown error"
                    )
                }
            }
        }
    }
    val playlistImagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val target = playlistToEdit
        if (uri != null && target != null) {
            playerViewModel.updatePlaylistImage(context, target.id, uri)
            if (selectedPlaylist?.id == target.id) {
                selectedPlaylist = selectedPlaylist?.copy(artworkUri = uri.toString())
            }
        }
        playlistToEdit = null
    }
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
        buildCoverScreenAlbums(playerState.allTracks)
    }
    val filteredLocalAlbums = remember(localAlbums, debouncedSearchQuery, playerState.allTracks) {
        filterCoverScreenAlbums(localAlbums, playerState.allTracks, debouncedSearchQuery)
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
            showSettings -> showSettings = false
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
            if (showSettings) {
                CoverScreenSettingsView(
                    viewModel = settingsViewModel,
                    onClose = { showSettings = false }
                )
            } else if (selectedPlaylist != null) {
                val playlist = selectedPlaylist!!
                val isLiked = playlist.id == com.tigerplayer.data.local.entity.PlaylistEntity.LIKED_SONGS_PLAYLIST_ID ||
                        playlist.name.equals(com.tigerplayer.data.local.entity.PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME, ignoreCase = true)
                CoverScreenPlaylistDetailView(
                    playlist = playlist,
                    tracks = selectedPlaylistTracks,
                    isLikedPlaylist = isLiked,
                    currentTrackId = playerState.currentTrack?.id,
                    isPlaying = playerState.isPlaying,
                    onBack = { selectedPlaylist = null },
                    onPlayTrack = playerViewModel::playTrack,
                    onAddToQueue = playerViewModel::addToQueue,
                    onPlayAll = playerViewModel::playAll,
                    onShufflePlay = { tracksToPlay ->
                        if (tracksToPlay.isNotEmpty()) {
                            val shuffled = tracksToPlay.shuffled()
                            playerViewModel.playTrack(shuffled.first())
                            shuffled.drop(1).forEach(playerViewModel::addToQueue)
                        }
                    },
                    onRenamePlaylist = { newName ->
                        playerViewModel.renamePlaylist(playlist.id, newName)
                        selectedPlaylist = playlist.copy(name = newName)
                    },
                    onDeletePlaylist = {
                        playerViewModel.deletePlaylist(playlist.id)
                        selectedPlaylist = null
                    },
                    onSaveOrder = { reorderedTracks ->
                        playerViewModel.savePlaylistOrder(playlist.id, reorderedTracks)
                        selectedPlaylistTracks = reorderedTracks
                    },
                    onRemoveTrack = { track ->
                        playerViewModel.removeTrackFromPlaylist(playlist.id, track)
                        selectedPlaylistTracks = selectedPlaylistTracks.filter { it.id != track.id }
                    },
                    onChangeCoverPhoto = {
                        playlistToEdit = playlist
                        playlistImagePickerLauncher.launch("image/*")
                    }
                )
            } else if (selectedAlbum != null) {
                val album = selectedAlbum!!
                val albumTracks = remember(album, playerState.allTracks) {
                    getCoverScreenAlbumTracks(album, playerState.allTracks)
                }
                val displayAlbum = remember(album, albumTracks.size) {
                    album.copy(trackCount = albumTracks.size)
                }
                CoverScreenAlbumsView(
                    album = displayAlbum,
                    artistName = album.artistId.takeUnless { it.isNullOrBlank() } ?: albumTracks.firstOrNull()?.artist.orEmpty(),
                    tracks = albumTracks,
                    onBack = {
                        selectedAlbum = null
                    },
                    onPlayTrack = playerViewModel::playTrack,
                    onAddToQueue = playerViewModel::addToQueue,
                    onRemoveFromQueue = { track ->
                        val index = playerState.queue.indexOfFirst { it.id == track.id }
                        if (index >= 0) playerViewModel.removeFromQueue(index)
                    },
                    onPlayAll = playerViewModel::playAll,
                    currentTrackId = playerState.currentTrack?.id,
                    isPlaying = playerState.isPlaying,
                    onShufflePlay = { tracksToPlay ->
                        if (tracksToPlay.isNotEmpty()) {
                            val shuffled = tracksToPlay.shuffled()
                            playerViewModel.playTrack(shuffled.first())
                            shuffled.drop(1).forEach(playerViewModel::addToQueue)
                        }
                    }
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
                        val settingsLabel = "Settings"
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                showSettings = true
                            },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .minimumInteractiveComponentSize()
                                .semantics {
                                    role = Role.Button
                                    onClick(label = settingsLabel) {
                                        showSettings = true
                                        true
                                    }
                                    contentDescription = settingsLabel
                                }
                                .testTag("cover_screen_settings_open")
                        ) {
                            Icon(
                                imageVector = WitcherIcons.Settings,
                                contentDescription = settingsLabel,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (searchActive && debouncedSearchQuery.isNotBlank()) {
                    CoverScreenSearchResults(
                        query = debouncedSearchQuery,
                        artists = artists,
                        albums = filteredLocalAlbums,
                        tracks = playerState.allTracks,
                        onArtistSelected = { artist ->
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            playerState.allTracks.firstOrNull { track ->
                                ArtistUtils.getBaseArtist(track.artist)
                                    .equals(artist.name, ignoreCase = true)
                            }?.let { track ->
                                playerViewModel.playTrack(track)
                                onDismiss()
                            }
                        },
                        onAlbumSelected = { album ->
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            selectedAlbum = album
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
                            libraryViewModel.selectTab(tab)
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
                            CoverScreenLibraryTab.HOME -> CoverScreenHomeView(
                                playerViewModel = playerViewModel,
                                dashboardViewModel = dashboardViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                            CoverScreenLibraryTab.HISTORY -> CoverScreenStatsView(
                                viewModel = playerViewModel,
                                onPlayTrack = { track ->
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    playerViewModel.playTrack(track)
                                },
                                onArtistSelected = { artistName ->
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    playerViewModel.onSearchQueryChanged(artistName)
                                    libraryViewModel.selectTab(CoverScreenLibraryTab.ARTISTS)
                                }
                            )
                            CoverScreenLibraryTab.ARTISTS -> CoverScreenArtistsGrid(
                                artists = artists,
                                albums = filteredLocalAlbums,
                                tracks = playerState.allTracks,
                                onAlbumSelected = { album ->
                                    selectedAlbum = album
                                },
                                onPlayTrack = { track ->
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    playerViewModel.playTrack(track)
                                }
                            )

                            CoverScreenLibraryTab.ALBUMS -> AlbumPicker(
                                albums = filteredLocalAlbums,
                                onAlbumSelected = { selectedAlbum = it }
                            )

                            CoverScreenLibraryTab.SONGS -> SongPicker(
                                tracks = filteredSongs,
                                currentTrackId = playerState.currentTrack?.id,
                                isPlaying = playerState.isPlaying,
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
                                onPlaylistSelected = { selectedPlaylist = it },
                                onEditPhoto = { playlist ->
                                    playlistToEdit = playlist
                                    playlistImagePickerLauncher.launch("image/*")
                                },
                                onImportPlaylist = {
                                    importM3uLauncher.launch(
                                        arrayOf(
                                            "audio/x-mpegurl",
                                            "audio/mpegurl",
                                            "application/vnd.apple.mpegurl",
                                            "application/x-mpegurl",
                                            "text/plain",
                                            "*/*"
                                        )
                                    )
                                },
                                onCreatePlaylist = { name ->
                                    playerViewModel.createPlaylist(name)
                                }
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

            // Import status notification overlay
            importStatusMessage?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(3500)
                    importStatusMessage = null
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E232E))
                            .border(1.dp, MaterialTheme.aardBlue, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag(CoverScreenTestTags.IMPORT_STATUS_OVERLAY)
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
