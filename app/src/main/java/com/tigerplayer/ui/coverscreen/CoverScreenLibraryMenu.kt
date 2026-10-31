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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.tigerplayer.ui.theme.glassEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import com.tigerplayer.ui.theme.WitcherIcons
import com.tigerplayer.ui.theme.aardBlue
import com.tigerplayer.ui.theme.bounceClick

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.data.remote.model.SpotifyPlaylist
import com.tigerplayer.ui.cloud.CloudViewModel
import com.tigerplayer.ui.player.PlayerViewModel
import com.tigerplayer.ui.settings.SettingsViewModel
import com.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

enum class CoverScreenLibraryTab(val label: String) {
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
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val density = LocalDensity.current
    val context = LocalContext.current
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
            } else if (selectedAlbum != null || selectedPlaylist != null) {
                val album = selectedAlbum
                val playlist = selectedPlaylist
                val albumTracks = remember(album, playerState.allTracks) {
                    getCoverScreenAlbumTracks(album, playerState.allTracks)
                }
                val displayAlbum = remember(album, albumTracks.size, playlist, selectedPlaylistTracks.size) {
                    when {
                        album != null -> album.copy(trackCount = albumTracks.size)
                        playlist != null -> CoverScreenAlbum(
                            id = playlist.id.toString(),
                            artistId = "playlist",
                            name = playlist.name,
                            trackCount = selectedPlaylistTracks.size,
                            artworkUrl = playlist.artworkUri
                        )
                        else -> CoverScreenAlbum("", "", "", 0)
                    }
                }
                CoverScreenAlbumsView(
                    album = displayAlbum,
                    artistName = if (playlist != null) "Playlist" else album?.artistId.takeUnless { it.isNullOrBlank() } ?: albumTracks.firstOrNull()?.artist.orEmpty(),
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
            columns = GridCells.Adaptive(minSize = 136.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = CoverScreenAlbum::id) { album ->
                CoverScreenAlbumCard(
                    album = album,
                    modifier = Modifier.testTag(CoverScreenTestTags.libraryAlbum(album.id)),
                    onClick = { onAlbumSelected(album) }
                )
            }
        }
    }
}

@Composable
private fun CoverScreenAlbumCard(
    album: CoverScreenAlbum,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val description = "Album ${album.name}, ${album.trackCount} tracks"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .minimumInteractiveComponentSize()
            .bounceClick(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    onClick()
                    true
                }
            },
        horizontalAlignment = Alignment.Start
    ) {
        // Square artwork container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(18.dp),
                    spotColor = MaterialTheme.aardBlue.copy(alpha = 0.25f),
                    ambientColor = Color.Black
                )
                .clip(RoundedCornerShape(18.dp))
                .glassEffect(RoundedCornerShape(18.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = album.artworkUrl,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                error = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier.fillMaxSize()
            )

            // Bottom dark gradient overlay for badge readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            startY = 180f
                        )
                    )
            )

            // Track count pill badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .glassEffect(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.60f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${album.trackCount} CHANTS",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    fontSize = 8.5.sp,
                    letterSpacing = 0.8.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Artist ID / name in uppercase
        Text(
            text = album.artistId.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun LocalPlaylistPicker(
    playlists: List<Playlist>,
    onPlaylistSelected: (Playlist) -> Unit,
    onEditPhoto: (Playlist) -> Unit
) {
    if (playlists.isEmpty()) {
        EmptyLibraryMessage(stringResource(R.string.coverscreen_empty_playlists))
        return
    }

    // Sort alphabetically, with Liked Songs ALWAYS first
    val sortedPlaylists = remember(playlists) {
        sortCoverScreenPlaylists(playlists)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CoverScreenTestTags.PLAYLIST_CAROUSEL)
    ) {
        val totalWidth = maxWidth
        val cardWidth = 154.dp
        val horizontalPadding = ((totalWidth - cardWidth) / 2).coerceAtLeast(20.dp)

        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { sortedPlaylists.size }
        )
        val coroutineScope = rememberCoroutineScope()

        // 3D Horizontal Wheel Carousel
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val playlist = sortedPlaylists[page]
            val isLiked = playlist.id == PlaylistEntity.LIKED_SONGS_PLAYLIST_ID ||
                    playlist.name.equals(PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME, ignoreCase = true)

            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val absOffset = pageOffset.absoluteValue.coerceIn(0f, 2f)

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .graphicsLayer {
                        cameraDistance = 12f * density
                        // Cylindrical wheel rotation around Y axis
                        rotationY = (pageOffset * -36f).coerceIn(-65f, 65f)
                        // Scale contraction along cylindrical wheel
                        val scale = (1f - (absOffset * 0.16f)).coerceIn(0.68f, 1f)
                        scaleX = scale
                        scaleY = scale
                        // Depth alpha fade
                        alpha = (1f - (absOffset * 0.40f)).coerceIn(0.25f, 1f)
                        // Perspective translation
                        translationX = pageOffset * 6f
                    },
                contentAlignment = Alignment.Center
            ) {
                CoverScreenPlaylistCard(
                    playlist = playlist,
                    isLiked = isLiked,
                    onSelect = { onPlaylistSelected(playlist) },
                    onEditPhoto = { onEditPhoto(playlist) },
                    modifier = Modifier.testTag(CoverScreenTestTags.localPlaylist(playlist.id))
                )
            }
        }

        // Fast-scroll Slider on the Side (Right Edge)
        if (sortedPlaylists.size > 1) {
            PlaylistSideScrubber(
                totalCount = sortedPlaylists.size,
                currentPage = pagerState.currentPage,
                pageOffsetFraction = pagerState.currentPageOffsetFraction,
                onScrubTo = { targetIndex ->
                    coroutineScope.launch {
                        pagerState.scrollToPage(targetIndex)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
            )
        }
    }
}

@Composable
private fun CoverScreenPlaylistCard(
    playlist: Playlist,
    isLiked: Boolean,
    onSelect: () -> Unit,
    onEditPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editPhotoLabel = stringResource(R.string.coverscreen_action_edit_playlist_photo)
    val likedHeartColor = Color(0xFFE91E63)

    Column(
        modifier = modifier
            .width(154.dp)
            .bounceClick { onSelect() }
    ) {
        Box(
            modifier = Modifier
                .size(154.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = if (isLiked) likedHeartColor.copy(alpha = 0.35f) else MaterialTheme.aardBlue.copy(alpha = 0.25f),
                    ambientColor = Color.Black
                )
                .clip(RoundedCornerShape(22.dp))
                .glassEffect(RoundedCornerShape(22.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Artwork or stylized fallback
            if (!playlist.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.artworkUri,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    fallback = painterResource(R.drawable.ic_tiger_logo),
                    error = painterResource(R.drawable.ic_tiger_logo),
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isLiked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    likedHeartColor.copy(alpha = 0.45f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = likedHeartColor,
                        modifier = Modifier.size(54.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = null,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    fallback = painterResource(R.drawable.ic_tiger_logo),
                    error = painterResource(R.drawable.ic_tiger_logo),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom gradient overlay for badge readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                            startY = 140f
                        )
                    )
            )

            // Track count pill badge (bottom start)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .glassEffect(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.60f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${playlist.trackCount} CHANTS",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    fontSize = 8.5.sp,
                    letterSpacing = 0.8.sp
                )
            }

            // Liked badge (bottom end) if liked
            if (isLiked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(likedHeartColor.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(9.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "LIKED",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 8.sp,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            // Edit photo action button (top end)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .glassEffect(CircleShape)
                    .background(Color.Black.copy(alpha = 0.60f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = editPhotoLabel
                    ) {
                        onEditPhoto()
                    }
                    .testTag(CoverScreenTestTags.editPlaylistPhoto(playlist.id)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = editPhotoLabel,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Subtitle
        Text(
            text = if (isLiked) "FAVORITES" else "${playlist.trackCount} TRACKS",
            style = MaterialTheme.typography.labelSmall,
            color = if (isLiked) likedHeartColor else MaterialTheme.aardBlue,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun PlaylistSideScrubber(
    totalCount: Int,
    currentPage: Int,
    pageOffsetFraction: Float,
    onScrubTo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var scrubberHeightPx by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .height(130.dp)
            .width(28.dp)
            .onSizeChanged { scrubberHeightPx = it.height.toFloat() }
            .pointerInput(totalCount) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val fraction = (offset.y / scrubberHeightPx).coerceIn(0f, 1f)
                        val targetIndex = (fraction * (totalCount - 1)).roundToInt()
                        onScrubTo(targetIndex)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.y / scrubberHeightPx).coerceIn(0f, 1f)
                        val targetIndex = (fraction * (totalCount - 1)).roundToInt()
                        onScrubTo(targetIndex)
                    }
                )
            }
            .pointerInput(totalCount) {
                detectTapGestures { offset ->
                    val fraction = (offset.y / scrubberHeightPx).coerceIn(0f, 1f)
                    val targetIndex = (fraction * (totalCount - 1)).roundToInt()
                    onScrubTo(targetIndex)
                }
            }
            .testTag(CoverScreenTestTags.PLAYLIST_SLIDER),
        contentAlignment = Alignment.Center
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.16f))
        )

        // Thumb indicator
        val progressFraction = ((currentPage + pageOffsetFraction) / (totalCount - 1).coerceAtLeast(1))
            .coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val thumbHeightPx = 22.dp.toPx()
                        val travelRangePx = (scrubberHeightPx - thumbHeightPx).coerceAtLeast(0f)
                        translationY = progressFraction * travelRangePx
                        if (isDragging) {
                            scaleX = 1.25f
                            scaleY = 1.15f
                        }
                    }
                    .width(if (isDragging) 9.dp else 6.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.aardBlue)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.5f),
                        RoundedCornerShape(3.dp)
                    )
            )
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
            .clip(RoundedCornerShape(14.dp))
            .glassEffect(RoundedCornerShape(14.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        color = Color.Black.copy(alpha = 0.30f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    fallback = painterResource(R.drawable.ic_tiger_logo),
                    error = painterResource(R.drawable.ic_tiger_logo),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
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
private fun SongPicker(
    tracks: List<AudioTrack>,
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
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
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(tracks, key = AudioTrack::id) { track ->
                val isCurrent = currentTrackId != null && track.id == currentTrackId
                SongTrackRow(
                    track = track,
                    isCurrentTrack = isCurrent,
                    isPlaying = isPlaying && isCurrent,
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
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
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
            .padding(vertical = 3.dp)
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
                .glassEffect(RoundedCornerShape(14.dp))
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
            color = if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.12f)
            else Color.Black.copy(alpha = 0.28f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.45f)
                else Color.White.copy(alpha = 0.08f)
            )
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
                        .background(
                            if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.20f)
                            else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (track.artworkUri.toString().isBlank()) {
                        Icon(
                            imageVector = WitcherIcons.AudioTrack,
                            contentDescription = null,
                            tint = if (isCurrentTrack) MaterialTheme.aardBlue
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        AsyncImage(
                            model = track.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            fallback = painterResource(R.drawable.ic_tiger_logo),
                            error = painterResource(R.drawable.ic_tiger_logo),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (isCurrentTrack && isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = WitcherIcons.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.aardBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentTrack) MaterialTheme.aardBlue
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentTrack) MaterialTheme.aardBlue.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatTrackDuration(track.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play ${track.title}",
                    tint = if (isCurrentTrack) MaterialTheme.aardBlue
                    else MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * Sorts playlists alphabetically by name (case-insensitive), ensuring the
 * singleton Liked Songs playlist is always placed first.
 */
internal fun sortCoverScreenPlaylists(playlists: List<Playlist>): List<Playlist> {
    return playlists.sortedWith { a, b ->
        val aIsLiked = a.id == PlaylistEntity.LIKED_SONGS_PLAYLIST_ID ||
                a.name.equals(PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME, ignoreCase = true)
        val bIsLiked = b.id == PlaylistEntity.LIKED_SONGS_PLAYLIST_ID ||
                b.name.equals(PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME, ignoreCase = true)
        when {
            aIsLiked && bIsLiked -> 0
            aIsLiked -> -1
            bIsLiked -> 1
            else -> a.name.compareTo(b.name, ignoreCase = true)
        }
    }
}

/**
 * Builds unique aggregated cover screen albums from all library tracks, borrowing the aggregation
 * mechanism from [com.tigerplayer.engine.LibraryEngine] and ui/library to ensure exact track counts
 * and preventing duplicate or fragmented album entries across tracks with featured artists.
 */
internal fun buildCoverScreenAlbums(tracks: List<AudioTrack>): List<CoverScreenAlbum> {
    return tracks
        .distinctBy { it.id }
        .filter { it.album.isNotBlank() }
        .groupBy { it.album.trim().lowercase() }
        .map { (key, albumTracks) ->
            val first = albumTracks.first()
            val dominantArtist = albumTracks
                .map { ArtistUtils.getBaseArtist(it.artist).trim() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }?.key
                ?.takeIf { it.isNotBlank() }
                ?: first.artist.trim().ifBlank { "Unknown Artist" }

            CoverScreenAlbum(
                id = "album_${key.hashCode()}",
                artistId = dominantArtist,
                name = first.album.trim(),
                trackCount = albumTracks.size,
                artworkUrl = albumTracks.firstNotNullOfOrNull { it.artworkUri }?.toString()?.takeUnless { it.isBlank() }
            )
        }
        .sortedBy { it.name.lowercase() }
}

/**
 * Filters albums by search query borrowing from [com.tigerplayer.engine.LibraryEngine], matching
 * album name, album artist, or any track title/artist on the album.
 */
internal fun filterCoverScreenAlbums(
    albums: List<CoverScreenAlbum>,
    tracks: List<AudioTrack>,
    query: String
): List<CoverScreenAlbum> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return albums

    return albums.filter { album ->
        album.name.contains(trimmed, ignoreCase = true) ||
            album.artistId.contains(trimmed, ignoreCase = true) ||
            tracks.any { track ->
                track.album.trim().equals(album.name.trim(), ignoreCase = true) &&
                    (track.title.contains(trimmed, ignoreCase = true) ||
                        track.artist.contains(trimmed, ignoreCase = true))
            }
    }
}

/**
 * Retrieves all tracks belonging to an album sorted by disc, track number, and title, borrowing
 * the picking mechanism from [com.tigerplayer.ui.library.AlbumDetailsScreen].
 * Importantly, this never filters out songs by active search queries or featured artists so all
 * songs belonging to the album are reliably presented.
 */
internal fun getCoverScreenAlbumTracks(
    album: CoverScreenAlbum?,
    tracks: List<AudioTrack>
): List<AudioTrack> {
    if (album == null) return emptyList()
    val targetAlbum = album.name.trim()
    return tracks
        .distinctBy { it.id }
        .filter { it.album.trim().equals(targetAlbum, ignoreCase = true) }
        .sortedWith(
            compareBy<AudioTrack> { it.trackNumber.takeIf { t -> t > 0 } ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
}
