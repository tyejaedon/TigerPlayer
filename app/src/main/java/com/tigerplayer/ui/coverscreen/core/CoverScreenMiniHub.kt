@file:SuppressLint("NewApi")
package com.tigerplayer.ui.coverscreen

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tigerplayer.R
import com.tigerplayer.engine.FluidVortexView
import com.tigerplayer.ui.dashboard.DashboardViewModel
import com.tigerplayer.ui.player.PlayerViewModel
import com.tigerplayer.ui.prism.PrismViewModel
import com.tigerplayer.ui.theme.TigerSurfaceCharcoal

private const val TAG = "CoverScreenMiniHub"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverScreenMiniHub(
    playerViewModel: PlayerViewModel,
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    prismViewModel: PrismViewModel = hiltViewModel(),
    windowState: CoverScreenWindowState = rememberCoverScreenWindowState(),
    forcePreview: Boolean = false,
    modifier: Modifier = Modifier
) {
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val daylistTracks by dashboardViewModel.daylistTracks.collectAsStateWithLifecycle()
    val discoveryWeeklyTracks by dashboardViewModel.discoveryWeeklyTracks.collectAsStateWithLifecycle()
    val daylistMeta by dashboardViewModel.daylistMeta.collectAsStateWithLifecycle()
    val discoveryMeta by dashboardViewModel.discoveryMeta.collectAsStateWithLifecycle()

    val track = uiState.currentTrack
    val view = LocalView.current

    var libraryVisible by remember { mutableStateOf(false) }
    var searchOnLibraryOpen by remember { mutableStateOf(false) }
    var queueVisible by remember { mutableStateOf(false) }
    var playbackSettingsVisible by remember { mutableStateOf(false) }
    var nexusVisible by remember { mutableStateOf(false) }
    var prismVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var discoveryDetailVisible by remember { mutableStateOf(false) }
    var daylistDetailVisible by remember { mutableStateOf(false) }
    var homeVisible by remember { mutableStateOf(false) }
    var coverScreenCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var seekerTouchBounds by remember { mutableStateOf<Rect?>(null) }
    var mediaCycleTouchBounds by remember { mutableStateOf<Rect?>(null) }
    var fullBleedLyricsVisible by remember { mutableStateOf(false) }
    var isVortexImmersive by remember { mutableStateOf(false) }

    val vortexBlurRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = calculateVortexBlurRadius(isVortexImmersive),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 350),
        label = "VortexBlurAnimation"
    )

    val scrimAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = calculateVortexScrimAlpha(isVortexImmersive),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 350),
        label = "VortexScrimAnimation"
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleActive by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isLifecycleActive = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasModalOverlay = CoverScreenPerformanceManager.hasModalOverlay(
        libraryVisible = libraryVisible,
        queueVisible = queueVisible,
        playbackSettingsVisible = playbackSettingsVisible,
        prismVisible = prismVisible,
        nexusVisible = nexusVisible,
        settingsVisible = settingsVisible,
        discoveryDetailVisible = discoveryDetailVisible || daylistDetailVisible || homeVisible
    )
    val isRenderingActive = isLifecycleActive && !hasModalOverlay

    LaunchedEffect(uiState.audioReactiveFrame, isRenderingActive, uiState.isPlaying) {
        if (isRenderingActive) {
            val bands = if (uiState.isPlaying) {
                uiState.audioReactiveFrame.spectralBands.ifEmpty {
                    listOf(
                        uiState.audioReactiveFrame.bass,
                        uiState.audioReactiveFrame.bass,
                        uiState.audioReactiveFrame.mid,
                        uiState.audioReactiveFrame.mid,
                        uiState.audioReactiveFrame.treble,
                        uiState.audioReactiveFrame.treble
                    )
                }
            } else {
                emptyList()
            }
            playerViewModel.fluidRenderer.updateEnergyField(bands)
        }
    }

    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 48.dp.toPx() } }
    val tapSlopPx = remember(density) { with(density) { 8.dp.toPx() } }

    val gestureInstructions = stringResource(R.string.coverscreen_talkback_gesture_instructions)
    val tapPlayPauseLabel = stringResource(R.string.coverscreen_gesture_tap_play_pause)
    val swipeNextLabel = stringResource(R.string.coverscreen_gesture_swipe_next)
    val swipePrevLabel = stringResource(R.string.coverscreen_gesture_swipe_prev)
    val swipeUpQueueLabel = stringResource(R.string.coverscreen_gesture_swipe_up_queue)
    val swipeDownCloseLabel = stringResource(R.string.coverscreen_gesture_swipe_down_close)
    val longPressSettingsLabel = stringResource(R.string.coverscreen_gesture_long_press_settings)
    val nowPlayingStatus = stringResource(R.string.coverscreen_status_now_playing)
    val pausedStatus = stringResource(R.string.coverscreen_status_paused)

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(CoverScreenTestTags.MINI_HUB_ROOT)
            .onGloballyPositioned { coverScreenCoordinates = it }
            .semantics {
                contentDescription = gestureInstructions
                stateDescription = if (uiState.isPlaying) nowPlayingStatus else pausedStatus
                onClick(label = tapPlayPauseLabel) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    playerViewModel.togglePlayPause()
                    true
                }
                onLongClick(label = longPressSettingsLabel) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    queueVisible = false
                    libraryVisible = false
                    playbackSettingsVisible = true
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction(tapPlayPauseLabel) { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); playerViewModel.togglePlayPause(); true },
                    CustomAccessibilityAction(swipeNextLabel) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); playerViewModel.skipToNext(); true },
                    CustomAccessibilityAction(swipePrevLabel) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); playerViewModel.skipToPrevious(); true },
                    CustomAccessibilityAction(swipeUpQueueLabel) { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); queueVisible = true; true },
                    CustomAccessibilityAction(swipeDownCloseLabel) { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); queueVisible = false; searchOnLibraryOpen = true; libraryVisible = true; true },
                    CustomAccessibilityAction(longPressSettingsLabel) { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); queueVisible = false; libraryVisible = false; playbackSettingsVisible = true; true },
                    CustomAccessibilityAction("Open Home") { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); queueVisible = false; libraryVisible = false; playbackSettingsVisible = false; homeVisible = true; true }
                )
            }
            .coverScreenGestures(
                swipeThresholdPx = swipeThresholdPx,
                tapSlopPx = tapSlopPx,
                shouldIgnoreGestureAt = { position ->
                    val positionInRoot = coverScreenCoordinates?.localToRoot(position) ?: position
                    (seekerTouchBounds?.contains(positionInRoot) == true) ||
                            (mediaCycleTouchBounds?.contains(positionInRoot) == true)
                },
                onTap = {
                    if (libraryVisible) return@coverScreenGestures
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    playerViewModel.togglePlayPause()
                },
                onSwipeLeft = {
                    if (libraryVisible) return@coverScreenGestures
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    playerViewModel.skipToNext()
                },
                onSwipeRight = {
                    if (libraryVisible) return@coverScreenGestures
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    playerViewModel.skipToPrevious()
                },
                onSwipeUp = {
                    if (libraryVisible) return@coverScreenGestures
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    queueVisible = true
                },
                onSwipeDown = {
                    if (libraryVisible) return@coverScreenGestures
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    queueVisible = false
                    searchOnLibraryOpen = true
                    libraryVisible = true
                },
                onDoubleTap = if (isVortexImmersive) {
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        isVortexImmersive = false
                    }
                } else null
            )
    ) {
        val backgroundArtModel = remember(track?.artworkUri, uiState.artistImageUrl) {
            uiState.artistImageUrl?.takeIf { it.isNotBlank() } ?: track?.artworkUri
        }

        // BACKGROUND: Fluid Vortex (primary) with Artist/Artwork photo as fallback
        Box(modifier = Modifier.fillMaxSize()) {
            if (backgroundArtModel != null) {
                AsyncImage(
                    model = backgroundArtModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(16.dp)
                )
            }

            AndroidView(
                factory = { ctx ->
                    FluidVortexView(ctx).apply {
                        setFluidRenderer(playerViewModel.fluidRenderer)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .blur(vortexBlurRadius)
            )

            // SOFT SCRIM OVERLAY (animates to clear in immersive mode)
            if (scrimAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.25f * (scrimAlpha / 0.55f)),
                                    TigerSurfaceCharcoal.copy(alpha = scrimAlpha)
                                )
                            )
                        )
                )
            }
        }

        val showWaveform = shouldShowCoverWaveform(hasModalOverlay, fullBleedLyricsVisible, isVortexImmersive)
        val waveformBottomClearance = if (showWaveform) 34.dp else 0.dp

        // HINGE / FLEX-MODE AWARE DUAL DISPLAY
        if (windowState.hasSeparatingHinge) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Panel: Dynamic Visualizer Arena
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            FluidVortexView(ctx).apply {
                                setFluidRenderer(playerViewModel.fluidRenderer)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Right Panel: Core Player Controls & Lyrics
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            start = 8.dp,
                            end = 8.dp,
                            top = 0.dp,
                            bottom = waveformBottomClearance
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (isVortexImmersive) Arrangement.Top else Arrangement.Center
                ) {
                    TrackInfoText(
                        track = track,
                        subtitle = stringResource(R.string.coverscreen_status_flex_mode_active),
                        lyrics = uiState.currentLyrics,
                        currentPosition = uiState.currentPosition,
                        isLarge = true,
                        statsVisible = false,
                        isVortexImmersive = isVortexImmersive,
                        onToggleVortexImmersive = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            isVortexImmersive = !isVortexImmersive
                        },
                        onDoubleTapAlbumArt = {
                            track?.let { currentTrack ->
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                playerViewModel.likeTrack(currentTrack)
                            }
                        },
                        onOpenQueue = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            queueVisible = true
                        },
                        onOpenLibrary = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            queueVisible = false
                            searchOnLibraryOpen = false
                            libraryVisible = true
                        },
                        onMediaCyclePositioned = { mediaCycleTouchBounds = it },
                        onSeek = { playerViewModel.seekTo(it) },
                        screenHeightDp = windowState.heightDp.dp
                    )
                    if (shouldShowCoverControls(isVortexImmersive)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        CoverAccessibleControls(
                            isPlaying = uiState.isPlaying,
                            isShuffleEnabled = uiState.isShuffleEnabled,
                            repeatMode = uiState.repeatMode,
                            onToggleShuffle = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); playerViewModel.toggleShuffle() },
                            onCycleRepeat = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); playerViewModel.toggleRepeat() },
                            onLongPressPlayPause = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                queueVisible = false
                                libraryVisible = false
                                playbackSettingsVisible = true
                            },
                            onTogglePlayPause = { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); playerViewModel.togglePlayPause() },
                            onPrevious = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); playerViewModel.skipToPrevious() },
                            onNext = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); playerViewModel.skipToNext() }
                        )
                    }
                }
            }
        } else {
            // STANDARD / COMPACT COVER DISPLAY
            val isTightScreen = windowState.heightDp in 1..360
            val verticalPadding = if (isTightScreen) 4.dp else 8.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = verticalPadding,
                        bottom = verticalPadding + waveformBottomClearance
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (isVortexImmersive) Arrangement.Top else Arrangement.Center
            ) {
                TrackInfoText(
                    track = track,
                    subtitle = if (uiState.isPlaying) stringResource(R.string.coverscreen_status_now_playing) else stringResource(R.string.coverscreen_status_paused),
                    lyrics = uiState.currentLyrics,
                    currentPosition = uiState.currentPosition,
                    isLarge = false,
                    statsVisible = false,
                    isVortexImmersive = isVortexImmersive,
                    onToggleVortexImmersive = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        isVortexImmersive = !isVortexImmersive
                    },
                    onDoubleTapAlbumArt = {
                        track?.let { currentTrack ->
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            playerViewModel.likeTrack(currentTrack)
                        }
                    },
                    onOpenQueue = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        queueVisible = true
                    },
                    onOpenLibrary = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        queueVisible = false
                        searchOnLibraryOpen = false
                        libraryVisible = true
                    },
                    onMediaCyclePositioned = { mediaCycleTouchBounds = it },
                    onSeek = { playerViewModel.seekTo(it) },
                    screenHeightDp = windowState.heightDp.dp
                )
                if (shouldShowCoverControls(isVortexImmersive)) {
                    Spacer(modifier = Modifier.height(if (isTightScreen) 4.dp else 6.dp))
                    CoverAccessibleControls(
                        isPlaying = uiState.isPlaying,
                        isShuffleEnabled = uiState.isShuffleEnabled,
                        repeatMode = uiState.repeatMode,
                        onToggleShuffle = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); playerViewModel.toggleShuffle() },
                        onCycleRepeat = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); playerViewModel.toggleRepeat() },
                        onLongPressPlayPause = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            queueVisible = false
                            libraryVisible = false
                            playbackSettingsVisible = true
                        },
                        onTogglePlayPause = { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); playerViewModel.togglePlayPause() },
                        onPrevious = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); playerViewModel.skipToPrevious() },
                        onNext = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); playerViewModel.skipToNext() }
                    )
                }
            }
        }

        // WAVEFORM (Visual Anchor)
        if (showWaveform) {
            CoverMicroWaveform(
                amplitudes = uiState.currentWaveform,
                bass = uiState.audioReactiveFrame.bass,
                energy = uiState.audioReactiveFrame.energy,
                isPlaying = uiState.isPlaying,
                isRenderingActive = isRenderingActive,
                duration = track?.durationMs ?: 1L,
                currentPosition = uiState.currentPosition,
                onSeek = { playerViewModel.seekTo(it) },
                artworkUri = track?.artworkUri,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .onGloballyPositioned { seekerTouchBounds = it.boundsInRoot() }
                    .testTag(CoverScreenTestTags.MICRO_WAVEFORM)
            )
        }

        // MODAL OVERLAYS
        CoverScreenModalOverlays(
            playerViewModel = playerViewModel,
            dashboardViewModel = dashboardViewModel,
            prismViewModel = prismViewModel,
            track = track,
            uiState = uiState,
            daylistTracks = daylistTracks,
            discoveryWeeklyTracks = discoveryWeeklyTracks,
            daylistMeta = daylistMeta,
            discoveryMeta = discoveryMeta,
            fullBleedLyricsVisible = fullBleedLyricsVisible,
            onDismissFullBleedLyrics = { fullBleedLyricsVisible = false },
            queueVisible = queueVisible,
            onDismissQueue = { queueVisible = false },
            libraryVisible = libraryVisible,
            searchOnLibraryOpen = searchOnLibraryOpen,
            onDismissLibrary = { libraryVisible = false; searchOnLibraryOpen = false },
            playbackSettingsVisible = playbackSettingsVisible,
            onDismissPlaybackSettings = { playbackSettingsVisible = false },
            onOpenPrism = { playbackSettingsVisible = false; prismVisible = true },
            onOpenNexus = { playbackSettingsVisible = false; nexusVisible = true },
            onOpenSettings = { playbackSettingsVisible = false; settingsVisible = true },
            onOpenHome = { playbackSettingsVisible = false; homeVisible = true },
            prismVisible = prismVisible,
            onDismissPrism = { prismVisible = false },
            nexusVisible = nexusVisible,
            onDismissNexus = { nexusVisible = false },
            settingsVisible = settingsVisible,
            onDismissSettings = { settingsVisible = false },
            discoveryDetailVisible = discoveryDetailVisible,
            onDismissDiscoveryDetail = { discoveryDetailVisible = false },
            daylistDetailVisible = daylistDetailVisible,
            onDismissDaylistDetail = { daylistDetailVisible = false },
            homeVisible = homeVisible,
            onDismissHome = { homeVisible = false }
        )
    }
}

internal fun calculateVortexBlurRadius(isVortexImmersive: Boolean): androidx.compose.ui.unit.Dp =
    if (isVortexImmersive) 0.dp else 6.dp

internal fun calculateVortexScrimAlpha(isVortexImmersive: Boolean): Float =
    if (isVortexImmersive) 0f else 0.55f

internal fun shouldShowCoverControls(isVortexImmersive: Boolean): Boolean = !isVortexImmersive

internal fun shouldShowCoverWaveform(
    hasModalOverlay: Boolean,
    fullBleedLyricsVisible: Boolean,
    isVortexImmersive: Boolean
): Boolean = !hasModalOverlay && !fullBleedLyricsVisible && !isVortexImmersive
