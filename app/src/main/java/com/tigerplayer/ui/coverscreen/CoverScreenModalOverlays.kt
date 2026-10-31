package com.tigerplayer.ui.coverscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tigerplayer.R
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.dashboard.CurationFeedMeta
import com.tigerplayer.ui.dashboard.DashboardViewModel
import com.tigerplayer.ui.equalizer.AuralNexusViewModel
import com.tigerplayer.ui.player.PlayerUiState
import com.tigerplayer.ui.player.PlayerViewModel
import com.tigerplayer.ui.prism.PrismViewModel
import com.tigerplayer.ui.settings.SettingsViewModel
import com.tigerplayer.ui.theme.glassEffect

/**
 * Orchestrates all full-screen and bottom-sheet modal overlays hosted inside [CoverScreenMiniHub].
 */
@Composable
internal fun CoverScreenModalOverlays(
    playerViewModel: PlayerViewModel,
    dashboardViewModel: DashboardViewModel,
    prismViewModel: PrismViewModel,
    track: AudioTrack?,
    uiState: PlayerUiState,
    daylistTracks: List<AudioTrack>,
    discoveryWeeklyTracks: List<AudioTrack>,
    daylistMeta: CurationFeedMeta,
    discoveryMeta: CurationFeedMeta,
    fullBleedLyricsVisible: Boolean,
    onDismissFullBleedLyrics: () -> Unit,
    queueVisible: Boolean,
    onDismissQueue: () -> Unit,
    libraryVisible: Boolean,
    searchOnLibraryOpen: Boolean,
    onDismissLibrary: () -> Unit,
    playbackSettingsVisible: Boolean,
    onDismissPlaybackSettings: () -> Unit,
    onOpenPrism: () -> Unit,
    onOpenNexus: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHome: () -> Unit = {},
    prismVisible: Boolean,
    onDismissPrism: () -> Unit,
    nexusVisible: Boolean,
    onDismissNexus: () -> Unit,
    settingsVisible: Boolean,
    onDismissSettings: () -> Unit,
    discoveryDetailVisible: Boolean,
    onDismissDiscoveryDetail: () -> Unit,
    daylistDetailVisible: Boolean,
    onDismissDaylistDetail: () -> Unit,
    homeVisible: Boolean = false,
    onDismissHome: () -> Unit = {},
) {
    // 1. FULL-BLEED LYRICS MODE
    AnimatedVisibility(
        visible = fullBleedLyricsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        CoverScreenLyricsView(
            lyrics = uiState.currentLyrics,
            currentPosition = uiState.currentPosition,
            onDoubleTap = onDismissFullBleedLyrics,
            onSeek = { timeMs -> playerViewModel.seekTo(timeMs) }
        )
    }

    // 2. OPTIMIZED COVER QUEUE (Direct Overlay)
    AnimatedVisibility(
        visible = queueVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismissQueue() })
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .glassEffect(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(top = 12.dp)
            ) {
                // Drag Handle Visual
                Box(
                    modifier = Modifier
                        .size(36.dp, 4.dp)
                        .background(Color.White.copy(alpha = 0.3f), CircleShape)
                        .align(Alignment.CenterHorizontally)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    val closeQueueLabel = stringResource(R.string.coverscreen_action_close_queue)
                    IconButton(
                        onClick = onDismissQueue,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                            .semantics {
                                role = Role.Button
                                onClick(label = closeQueueLabel) {
                                    onDismissQueue()
                                    true
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = closeQueueLabel,
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                CoverScreenQueueView(
                    queue = uiState.queue,
                    currentTrackId = track?.id,
                    currentIndex = uiState.currentQueueIndex,
                    shuffleModeEnabled = uiState.isShuffleEnabled,
                    onTrackTapped = {
                        playerViewModel.playTrack(it)
                        onDismissQueue()
                    },
                    onRemoveFromQueue = playerViewModel::removeFromQueue,
                    onMoveQueueItem = playerViewModel::moveQueueItem,
                    onClearQueue = playerViewModel::clearQueue
                )
            }
        }
    }

    // 3. LIBRARY
    AnimatedVisibility(
        visible = libraryVisible,
        enter = fadeIn() + slideInVertically { it / 8 },
        exit = fadeOut() + slideOutVertically { it / 8 },
        modifier = Modifier.fillMaxSize()
    ) {
        CoverScreenLibraryMenu(
            playerViewModel = playerViewModel,
            onDismiss = onDismissLibrary,
            showSearchInitially = searchOnLibraryOpen
        )
    }

    // 4. PLAYBACK SETTINGS
    AnimatedVisibility(
        visible = playbackSettingsVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        CoverScreenPlaybackSettings(
            isShuffleEnabled = uiState.isShuffleEnabled,
            repeatMode = uiState.repeatMode,
            volume = uiState.coverScreenVolume,
            selectedEqPreset = uiState.coverScreenEqPreset.displayName,
            onToggleShuffle = playerViewModel::toggleShuffle,
            onCycleRepeat = playerViewModel::toggleRepeat,
            onAdjustVolume = playerViewModel::adjustCoverScreenVolume,
            onSelectEqPreset = playerViewModel::setCoverScreenEqPreset,
            onOpenPrism = onOpenPrism,
            onOpenNexus = onOpenNexus,
            onOpenSettings = onOpenSettings,
            onOpenHome = onOpenHome,
            onClose = onDismissPlaybackSettings
        )
    }

    // 5. PRISM
    AnimatedVisibility(
        visible = prismVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        CoverScreenPrismView(
            prismViewModel = prismViewModel,
            onClose = onDismissPrism
        )
    }

    // 6. NEXUS
    AnimatedVisibility(
        visible = nexusVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        CoverScreenNexusView(
            viewModel = hiltViewModel<AuralNexusViewModel>(),
            onClose = onDismissNexus
        )
    }

    // 7. SETTINGS
    AnimatedVisibility(
        visible = settingsVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        CoverScreenSettingsView(
            viewModel = hiltViewModel<SettingsViewModel>(),
            onClose = onDismissSettings
        )
    }

    // 8. DISCOVER WEEKLY DETAIL
    AnimatedVisibility(
        visible = discoveryDetailVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        CoverScreenDiscoverWeeklyDetailView(
            tracks = discoveryWeeklyTracks,
            meta = discoveryMeta,
            onPlayTrack = { trk ->
                playerViewModel.playTrack(trk)
                onDismissDiscoveryDetail()
            },
            onPlayFeed = {
                if (discoveryWeeklyTracks.isNotEmpty()) {
                    if (discoveryWeeklyTracks.first().id.startsWith("spotify:")) {
                        playerViewModel.playTrack(discoveryWeeklyTracks.first())
                    } else {
                        playerViewModel.setPlaylistAndPlay(discoveryWeeklyTracks, 0)
                    }
                    onDismissDiscoveryDetail()
                }
            },
            onShuffleFeed = {
                if (discoveryWeeklyTracks.isNotEmpty()) {
                    val shuffled = discoveryWeeklyTracks.shuffled()
                    if (shuffled.first().id.startsWith("spotify:")) {
                        playerViewModel.playTrack(shuffled.first())
                    } else {
                        playerViewModel.setPlaylistAndPlay(shuffled, 0)
                    }
                    onDismissDiscoveryDetail()
                }
            },
            onRefresh = dashboardViewModel::refreshForYou,
            onClose = onDismissDiscoveryDetail
        )
    }

    // 9. DAYLIST DETAIL
    AnimatedVisibility(
        visible = daylistDetailVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        CoverScreenDaylistDetailView(
            tracks = daylistTracks,
            meta = daylistMeta,
            onPlayTrack = { trk ->
                playerViewModel.playTrack(trk)
                onDismissDaylistDetail()
            },
            onPlayFeed = {
                if (daylistTracks.isNotEmpty()) {
                    if (daylistTracks.first().id.startsWith("spotify:")) {
                        playerViewModel.playTrack(daylistTracks.first())
                    } else {
                        playerViewModel.setPlaylistAndPlay(daylistTracks, 0)
                    }
                    onDismissDaylistDetail()
                }
            },
            onShuffleFeed = {
                if (daylistTracks.isNotEmpty()) {
                    val shuffled = daylistTracks.shuffled()
                    if (shuffled.first().id.startsWith("spotify:")) {
                        playerViewModel.playTrack(shuffled.first())
                    } else {
                        playerViewModel.setPlaylistAndPlay(shuffled, 0)
                    }
                    onDismissDaylistDetail()
                }
            },
            onRefresh = dashboardViewModel::refreshForYou,
            onClose = onDismissDaylistDetail
        )
    }

    // 10. HOME VIEW
    AnimatedVisibility(
        visible = homeVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        CoverScreenHomeView(
            playerViewModel = playerViewModel,
            dashboardViewModel = dashboardViewModel,
            onClose = onDismissHome
        )
    }
}
