@file:SuppressLint("NewApi")
package com.tigerplayer.ui.coverscreen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tigerplayer.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import androidx.window.layout.WindowMetricsCalculator
import coil.compose.AsyncImage
import com.tigerplayer.ui.dashboard.DashboardViewModel
import com.tigerplayer.ui.prism.PrismViewModel
import com.tigerplayer.ui.player.LyricsDisplay
import com.tigerplayer.ui.player.PlayerViewModel
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerSurfaceCharcoal
import com.tigerplayer.ui.theme.glassEffect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val TAG = "CoverScreenMiniHub"

data class CoverScreenWindowState(
    val widthDp: Int,
    val heightDp: Int,
    val isCoverScreen: Boolean,
    val hasSeparatingHinge: Boolean,
    val displayId: Int = Display.DEFAULT_DISPLAY,
    val isSecondaryDisplay: Boolean = false,
    val isInMultiWindowMode: Boolean = false
)

/**
 * dp band tuned against known cover/outer display profiles across OEMs (approximate,
 * short-edge x long-edge dp at each device's reported density):
 * - Samsung Galaxy Z Flip4/5/6 cover: ~260x260 to ~306x316dp (roughly square).
 * - Motorola Razr (2022) cover: ~246x343dp, aspect ratio ~1.39.
 * - Motorola Razr+ / Razr 50 Ultra (2023/2024) cover: ~409x413dp, aspect ratio ~1.01 - this is
 *   the profile that motivated broadening the original 220..399 / <=450 / <=1.35f band (issue
 *   #125), which only covered the Z Flip family and missed this larger, near-square panel.
 *
 * This heuristic is intentionally permissive on its own; it is only ever consulted as a
 * fallback when [resolveIsCoverScreen] has already ruled out a genuine secondary display and a
 * user-resized multi-window state (split-screen/freeform/DeX - see issue #122), so a wider band
 * does not reopen the false-positive surface those checks close.
 */
fun isCoverScreenHeuristic(widthDp: Int, heightDp: Int): Boolean {
    val shortEdge = min(widthDp, heightDp)
    val longEdge = max(widthDp, heightDp)
    if (shortEdge <= 0 || longEdge <= 0) return false
    val aspectRatio = longEdge.toFloat() / shortEdge.toFloat()
    return shortEdge in 200..420 && longEdge <= 460 && aspectRatio <= 1.5f
}

/**
 * True when the window is hosted on a genuinely distinct physical [Display] (the Motorola-style
 * true-secondary-display model) rather than merely being resized on the default display (the
 * Samsung Z Flip/Fold resize model, where no second display ever exists). This is the
 * authoritative signal on devices that expose it; the dp/hinge heuristic is only a fallback for
 * devices where it does not apply.
 */
fun isSecondaryDisplayIdentity(displayId: Int): Boolean {
    return displayId != Display.DEFAULT_DISPLAY
}

/**
 * Combines the displayId identity signal with the dp/hinge heuristic and rejects ordinary
 * user-resize states. Precedence:
 * 1. A true secondary display ([isSecondaryDisplay]) is authoritative — there is no ambiguity,
 *    so it wins even if the window happens to also report multi-window mode.
 * 2. Multi-window mode (split-screen, freeform/desktop-mode, Samsung DeX pop-up view) means the
 *    small size is a user resize choice on the *same* display, not a real device posture, so it
 *    is rejected outright.
 * 3. Otherwise, resize-model devices (no second display ever exists) fall back to the dp-size +
 *    hinge heuristic.
 */
internal fun resolveIsCoverScreen(
    widthDp: Int,
    heightDp: Int,
    hasSeparatingHinge: Boolean,
    isSecondaryDisplay: Boolean,
    isInMultiWindowMode: Boolean = false,
    imeBottomDp: Int = 0
): Boolean {
    if (isSecondaryDisplay) return true
    if (isInMultiWindowMode) return false
    val effectiveHeight = heightDp + imeBottomDp
    return isCoverScreenHeuristic(widthDp, effectiveHeight) && !hasSeparatingHinge
}

/**
 * Computes base display/window dimensions in dp immune to soft keyboard (IME) resizing.
 * Precedence:
 * 1. [WindowMetricsCalculator.computeMaximumWindowMetrics]: Physical display bounds for the activity.
 * 2. Un-insetted fallback: configuration height + IME bottom insets in dp.
 */
internal fun resolveBaseWindowDimensions(
    activity: Activity?,
    configurationWidthDp: Int,
    configurationHeightDp: Int,
    density: Float,
    imeBottomDp: Int = 0
): Pair<Int, Int> {
    if (activity != null) {
        try {
            val bounds = WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(activity).bounds
            if (bounds.width() > 0 && bounds.height() > 0 && density > 0f) {
                val w = (bounds.width() / density).toInt()
                val h = (bounds.height() / density).toInt()
                if (w > 0 && h > 0) {
                    return Pair(w, h)
                }
            }
        } catch (_: Throwable) {
            // Fall through to insets-adjusted configuration
        }
    }
    val uninsettedHeight = configurationHeightDp + imeBottomDp
    return Pair(configurationWidthDp, uninsettedHeight)
}

private fun Activity.currentDisplayIdOrDefault(): Int {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.displayId ?: Display.DEFAULT_DISPLAY
        }
    } catch (e: UnsupportedOperationException) {
        // Context.display throws for non-visual/application contexts on some OEM builds.
        Display.DEFAULT_DISPLAY
    }
}

@Composable
fun rememberCoverScreenWindowState(): CoverScreenWindowState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeBottomDp = with(density) { imeBottom.toDp().value.toInt() }

    // Bumped by the DisplayManager.DisplayListener below whenever a display attaches, detaches,
    // or changes. A true secondary display (Motorola-style) appearing/disappearing at runtime
    // does not necessarily change this window's own Configuration, so without this tick the
    // state would only refresh on an unrelated recomposition.
    var displayChangeTick by remember { mutableStateOf(0) }

    DisposableEffect(activity) {
        val displayManager = activity?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val listener = if (displayManager != null) {
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {
                    displayChangeTick++
                }

                override fun onDisplayRemoved(displayId: Int) {
                    displayChangeTick++
                }

                override fun onDisplayChanged(displayId: Int) {
                    displayChangeTick++
                }
            }
        } else {
            null
        }

        if (displayManager != null) {
            try {
                displayManager.registerDisplayListener(listener, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register DisplayManager.DisplayListener", e)
            }
        }

        onDispose {
            if (displayManager != null) {
                try {
                    displayManager.unregisterDisplayListener(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unregister DisplayManager.DisplayListener", e)
                }
            }
        }
    }

    val windowLayoutInfo by produceState<WindowLayoutInfo?>(initialValue = null, activity) {
        if (activity == null) {
            value = null
            return@produceState
        }
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .collect { value = it }
    }

    val hasSeparatingHinge = windowLayoutInfo
        ?.displayFeatures
        ?.any { it is FoldingFeature && it.isSeparating } == true

    val displayId = remember(activity, displayChangeTick) {
        activity?.currentDisplayIdOrDefault() ?: Display.DEFAULT_DISPLAY
    }
    val isSecondaryDisplay = isSecondaryDisplayIdentity(displayId)
    val isInMultiWindowMode = activity?.isInMultiWindowMode == true

    val (baseWidthDp, baseHeightDp) = remember(
        activity,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        density.density,
        imeBottomDp,
        displayChangeTick
    ) {
        resolveBaseWindowDimensions(
            activity = activity,
            configurationWidthDp = configuration.screenWidthDp,
            configurationHeightDp = configuration.screenHeightDp,
            density = density.density,
            imeBottomDp = imeBottomDp
        )
    }

    val isCover = resolveIsCoverScreen(
        widthDp = baseWidthDp,
        heightDp = baseHeightDp,
        hasSeparatingHinge = hasSeparatingHinge,
        isSecondaryDisplay = isSecondaryDisplay,
        isInMultiWindowMode = isInMultiWindowMode
    )

    return remember(
        baseWidthDp,
        baseHeightDp,
        isCover,
        hasSeparatingHinge,
        displayId,
        isSecondaryDisplay,
        isInMultiWindowMode
    ) {
        CoverScreenWindowState(
            widthDp = baseWidthDp,
            heightDp = baseHeightDp,
            isCoverScreen = isCover,
            hasSeparatingHinge = hasSeparatingHinge,
            displayId = displayId,
            isSecondaryDisplay = isSecondaryDisplay,
            isInMultiWindowMode = isInMultiWindowMode
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverScreenMiniHub(
    playerViewModel: PlayerViewModel,
    dashboardViewModel: DashboardViewModel,
    prismViewModel: PrismViewModel,
    windowState: CoverScreenWindowState,
    modifier: Modifier = Modifier
) {
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val coverStats by playerViewModel.coverScreenStats.collectAsStateWithLifecycle()
    val daylistTracks by dashboardViewModel.daylistTracks.collectAsStateWithLifecycle()
    val discoveryWeeklyTracks by dashboardViewModel.discoveryWeeklyTracks.collectAsStateWithLifecycle()
    val daylistMeta by dashboardViewModel.daylistMeta.collectAsStateWithLifecycle()
    val discoveryMeta by dashboardViewModel.discoveryMeta.collectAsStateWithLifecycle()
    val track = uiState.currentTrack
    val view = LocalView.current
    val density = LocalDensity.current

    // FIX: Interactive Sheet State (Enhanced for Cover Screen Stability)
    var queueVisible by remember { mutableStateOf(false) }
    var libraryVisible by remember { mutableStateOf(false) }
    var playbackSettingsVisible by remember { mutableStateOf(false) }
    var searchOnLibraryOpen by remember { mutableStateOf(false) }
    var prismVisible by remember { mutableStateOf(false) }
    var coverScreenCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var discoveryTouchBounds by remember { mutableStateOf<Rect?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleActive by remember {
        mutableStateOf(
            CoverScreenPerformanceManager.isLifecycleStateActive(lifecycleOwner.lifecycle.currentState)
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isLifecycleActive = CoverScreenPerformanceManager.resolveLifecycleRenderingState(
                event = event,
                currentState = isLifecycleActive
            )
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hasModalOverlay = CoverScreenPerformanceManager.isModalOverlayActive(
        queueVisible = queueVisible,
        libraryVisible = libraryVisible,
        playbackSettingsVisible = playbackSettingsVisible,
        prismVisible = prismVisible
    )
    val isRenderingActive = isLifecycleActive && !hasModalOverlay

    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    val tapSlopPx = with(density) { 10.dp.toPx() }
    val unknownTitle = stringResource(R.string.coverscreen_unknown_title)
    val unknownArtist = stringResource(R.string.coverscreen_unknown_artist)
    val title = track?.title?.ifBlank { unknownTitle } ?: unknownTitle
    val artist = track?.artist?.ifBlank { unknownArtist } ?: unknownArtist
    val artworkDescription = stringResource(R.string.coverscreen_artwork_description, title, artist)

    val gestureInstructions = stringResource(R.string.coverscreen_gesture_instructions)
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
            .background(Color.Black)
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
                    CustomAccessibilityAction(tapPlayPauseLabel) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        playerViewModel.togglePlayPause()
                        true
                    },
                    CustomAccessibilityAction(swipeNextLabel) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        playerViewModel.skipToNext()
                        true
                    },
                    CustomAccessibilityAction(swipePrevLabel) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        playerViewModel.skipToPrevious()
                        true
                    },
                    CustomAccessibilityAction(swipeUpQueueLabel) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        queueVisible = true
                        true
                    },
                    CustomAccessibilityAction(swipeDownCloseLabel) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        if (queueVisible) {
                            queueVisible = false
                        } else if (libraryVisible) {
                            libraryVisible = false
                            searchOnLibraryOpen = false
                        } else if (playbackSettingsVisible) {
                            playbackSettingsVisible = false
                        } else {
                            queueVisible = false
                            searchOnLibraryOpen = true
                            libraryVisible = true
                        }
                        true
                    },
                    CustomAccessibilityAction(longPressSettingsLabel) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        queueVisible = false
                        libraryVisible = false
                        playbackSettingsVisible = true
                        true
                    }
                )
            }
            .coverScreenGestures(
                swipeThresholdPx = swipeThresholdPx,
                tapSlopPx = tapSlopPx,
                shouldIgnoreGestureAt = { position ->
                    val positionInRoot = coverScreenCoordinates?.localToRoot(position) ?: position
                    discoveryTouchBounds?.contains(positionInRoot) == true
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
                }
            )
    ) {
        // BACKGROUND BLUR
        if (track != null) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            TigerSurfaceCharcoal.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        if (windowState.hasSeparatingHinge) {
            // FLEX MODE: Hinge-Aware Layout
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Half: Artwork & Visuals (Viewing Zone)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = track?.artworkUri,
                        contentDescription = artworkDescription,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize(0.85f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                // Keep a safe spacer around hinge split displays.
                Spacer(modifier = Modifier.height((windowState.heightDp * 0.04f).coerceIn(16f, 28f).dp))

                // Bottom Half: Info & Safe Controls (Touch Zone)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TrackInfoText(
                        track = track,
                        subtitle = stringResource(R.string.coverscreen_status_flex_mode_active),
                        lyrics = uiState.currentLyrics,
                        currentPosition = uiState.currentPosition,
                        isLarge = true,
                        statsVisible = true,
                        screenHeightDp = windowState.heightDp.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    CoverAccessibleControls(
                        isPlaying = uiState.isPlaying,
                        onLongPressPlayPause = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            queueVisible = false
                            libraryVisible = false
                            playbackSettingsVisible = true
                        },
                        onTogglePlayPause = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            playerViewModel.togglePlayPause()
                        },
                        onPrevious = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            playerViewModel.skipToPrevious()
                        },
                        onNext = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            playerViewModel.skipToNext()
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
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CoverScreenStatsWidget(
                        stats = coverStats,
                        onOpenFullStats = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            playerViewModel.requestSonicFootprintOpen()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CoverScreenDiscoveryView(
                        daylistTracks = daylistTracks,
                        discoveryWeeklyTracks = discoveryWeeklyTracks,
                        daylistMeta = daylistMeta,
                        discoveryMeta = discoveryMeta,
                        onPlayTrack = playerViewModel::playTrack,
                        onQueueTrack = playerViewModel::addToQueue,
                        onRefresh = dashboardViewModel::refreshForYou,
                        modifier = Modifier.onGloballyPositioned {
                            discoveryTouchBounds = it.boundsInRoot()
                        }
                    )
                }
            }
        } else {
            // COVER SCREEN MODE: Centered & Glanceable
            val isTightScreen = windowState.heightDp in 1..360
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 16.dp,
                        vertical = if (isTightScreen) 6.dp else 12.dp
                    ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TrackInfoText(
                    track = track,
                    subtitle = if (uiState.isPlaying) stringResource(R.string.coverscreen_status_now_playing) else stringResource(R.string.coverscreen_status_paused),
                    lyrics = uiState.currentLyrics,
                    currentPosition = uiState.currentPosition,
                    isLarge = false,
                    statsVisible = true,
                    screenHeightDp = windowState.heightDp.dp
                )
                Spacer(modifier = Modifier.height(if (isTightScreen) 6.dp else 10.dp))
                CoverAccessibleControls(
                    isPlaying = uiState.isPlaying,
                    onLongPressPlayPause = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        queueVisible = false
                        libraryVisible = false
                        playbackSettingsVisible = true
                    },
                    onTogglePlayPause = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        playerViewModel.togglePlayPause()
                    },
                    onPrevious = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        playerViewModel.skipToPrevious()
                    },
                    onNext = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        playerViewModel.skipToNext()
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
                    }
                )
                Spacer(modifier = Modifier.height(if (isTightScreen) 4.dp else 8.dp))
                CoverScreenStatsWidget(
                    stats = coverStats,
                    onOpenFullStats = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        playerViewModel.requestSonicFootprintOpen()
                    }
                )
                Spacer(modifier = Modifier.height(if (isTightScreen) 4.dp else 8.dp))
                CoverScreenDiscoveryView(
                    daylistTracks = daylistTracks,
                    discoveryWeeklyTracks = discoveryWeeklyTracks,
                    daylistMeta = daylistMeta,
                    discoveryMeta = discoveryMeta,
                    onPlayTrack = playerViewModel::playTrack,
                    onQueueTrack = playerViewModel::addToQueue,
                    onRefresh = dashboardViewModel::refreshForYou,
                    modifier = Modifier.onGloballyPositioned {
                        discoveryTouchBounds = it.boundsInRoot()
                    }
                )
            }
        }

        // WAVEFORM (Visual Anchor)
        if (!hasModalOverlay) {
            CoverMicroWaveform(
                amplitudes = uiState.currentWaveform,
                bass = uiState.audioReactiveFrame.bass,
                energy = uiState.audioReactiveFrame.energy,
                isPlaying = uiState.isPlaying,
                isRenderingActive = isRenderingActive,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(if (windowState.hasSeparatingHinge) 16.dp else 12.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .testTag(CoverScreenTestTags.MICRO_WAVEFORM)
            )
        }

        // OPTIMIZED COVER QUEUE (Direct Overlay)
        AnimatedVisibility(
            visible = queueVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { queueVisible = false })
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
                            onClick = { queueVisible = false },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .minimumInteractiveComponentSize()
                                .size(48.dp)
                                .semantics {
                                    role = Role.Button
                                    onClick(label = closeQueueLabel) {
                                        queueVisible = false
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
                            queueVisible = false
                        },
                        onRemoveFromQueue = playerViewModel::removeFromQueue,
                        onMoveQueueItem = playerViewModel::moveQueueItem,
                        onClearQueue = playerViewModel::clearQueue
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = libraryVisible,
            enter = fadeIn() + slideInVertically { it / 8 },
            exit = fadeOut() + slideOutVertically { it / 8 },
            modifier = Modifier.fillMaxSize()
        ) {
            CoverScreenLibraryMenu(
                playerViewModel = playerViewModel,
                onDismiss = {
                    libraryVisible = false
                    searchOnLibraryOpen = false
                },
                showSearchInitially = searchOnLibraryOpen
            )
        }

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
                onOpenPrism = {
                    playbackSettingsVisible = false
                    prismVisible = true
                },
                onClose = { playbackSettingsVisible = false }
            )
        }

        AnimatedVisibility(
            visible = prismVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            CoverScreenPrismView(
                prismViewModel = prismViewModel,
                onClose = { prismVisible = false }
            )
        }
    }
}

@Composable
private fun TrackInfoText(
    track: com.tigerplayer.data.model.AudioTrack?,
    subtitle: String,
    lyrics: String?,
    currentPosition: Long,
    isLarge: Boolean,
    statsVisible: Boolean,
    screenHeightDp: Dp = LocalConfiguration.current.screenHeightDp.dp
) {
    val isTight = screenHeightDp > 0.dp && screenHeightDp <= 360.dp
    val spacerHeight = when {
        isLarge -> 12.dp
        isTight -> 4.dp
        else -> 8.dp
    }

    val fontScale = LocalDensity.current.fontScale
    val minLineHeight = calculateCoverLyricsMinLineHeight(fontScale)
    val targetLyricsHeight = calculateCoverLyricsBoxHeight(
        screenHeightDp = screenHeightDp,
        fontScale = fontScale,
        isLarge = isLarge,
        statsVisible = statsVisible
    )

    Spacer(modifier = Modifier.height(spacerHeight))

    Text(
        text = track?.title ?: stringResource(R.string.coverscreen_default_title),
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = if (isLarge) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black
    )

    Text(
        text = track?.artist?.uppercase() ?: stringResource(R.string.coverscreen_default_artist),
        color = TigerNeonOrange,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )

    Spacer(modifier = Modifier.height(spacerHeight))
    Box(
        modifier = Modifier
            .fillMaxWidth(if (isLarge) 0.82f else 0.94f)
            .wrapContentHeight()
            .heightIn(min = minLineHeight, max = targetLyricsHeight)
            .testTag(CoverScreenTestTags.LYRICS_CONTAINER),
        contentAlignment = Alignment.Center
    ) {
        LyricsDisplay(
            lyrics = lyrics,
            currentPosition = currentPosition,
            textColor = Color.White,
            activeColor = TigerNeonOrange
        )
    }
    Spacer(modifier = Modifier.height(spacerHeight))

    Text(
        text = subtitle,
        color = Color.White.copy(alpha = 0.72f),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.sp
    )
}

/**
 * Calculates the minimum vertical height required to comfortably display at least one full line
 * of synced lyrics in [LyricsDisplay] without visual or glyph clipping.
 *
 * In [LyricsDisplay], an active lyric line is styled with 24sp bold font, scaled by 1.1x via graphicsLayer,
 * and padded with 24dp vertical padding (12dp top and bottom). With viewport centering
 * (`contentPadding = maxHeight / 2`), the container must be at least as tall as the scaled text line plus
 * vertical padding and ascender/descender clearance, scaling proportionally with [fontScale].
 */
internal fun calculateCoverLyricsMinLineHeight(fontScale: Float): Dp {
    val effectiveScale = fontScale.coerceAtLeast(0.85f)
    // 40sp nominal scaled line height (36sp headline lineHeight * 1.1x active factor) * fontScale
    // + 24dp inner text vertical padding + 16dp outer clearance buffer for ascenders/descenders
    return (40.dp * effectiveScale) + 40.dp
}

/**
 * Calculates adaptive visual dimensions for the lyrics container in cover-screen and flex-mode layouts.
 *
 * Prevents hard-clipping of lyrics (issue #252) while ensuring that downstream widgets
 * ([CoverScreenStatsWidget] and [CoverScreenDiscoveryView]) remain visible and are never pushed off screen.
 *
 * Adapts dynamically across font scales (e.g. 1.0x, 1.25x, 1.5x) and screen heights (e.g. 343dp Razr 2022,
 * 400dp Galaxy Z Flip, 413dp Razr+, and flex mode split displays).
 */
internal fun calculateCoverLyricsBoxHeight(
    screenHeightDp: Dp,
    fontScale: Float = 1.0f,
    isLarge: Boolean = false,
    statsVisible: Boolean = true
): Dp {
    val minLineHeight = calculateCoverLyricsMinLineHeight(fontScale)

    if (!statsVisible) {
        return if (isLarge) {
            maxOf(180.dp, minLineHeight)
        } else {
            maxOf(130.dp, minLineHeight)
        }
    }

    // statsVisible == true
    if (isLarge) {
        // Flex mode: bottom half viewing zone has ample space (typically 380-480dp).
        // Provide comfortable multi-line space while keeping controls, stats, and discovery visible.
        val flexTarget = if (screenHeightDp > 0.dp && screenHeightDp != Dp.Infinity) {
            (screenHeightDp * 0.16f).coerceIn(120.dp, 160.dp)
        } else {
            130.dp
        }
        return maxOf(minLineHeight, flexTarget)
    }

    // Compact cover screen with stats visible:
    // On tight displays (<= 360dp, e.g. Motorola Razr 2022 @ 343dp), restrict to minimum single-line
    // height so stats and discovery are never pushed off screen.
    // On larger panels (> 360dp, e.g. Z Flip @ 390-400dp, Razr+ @ 413dp), proportionally allocate a portion
    // of the surplus height up to a safe cap.
    val effectiveHeight = if (screenHeightDp > 0.dp && screenHeightDp != Dp.Infinity) screenHeightDp else 390.dp
    val extraHeight = (effectiveHeight - 360.dp).coerceAtLeast(0.dp)
    val adaptiveHeight = minLineHeight + (extraHeight * 0.25f)
    return adaptiveHeight.coerceAtMost(120.dp).coerceAtLeast(minLineHeight)
}

/**
 * Calculates adaptive visual dimensions for playback control buttons across different cover display
 * widths (200-420 dp band).
 *
 * On narrow screens (~260dp, leaving ~228dp inside 16dp horizontal padding), 5 fixed 48dp buttons (240dp)
 * cause horizontal container overflow. Sizing the visual button circle down to 40dp (or 34dp on ~200dp
 * displays) ensures all 5 buttons and their margins fit comfortably, while each button retains at least
 * a 48x48 dp minimum interactive touch target via proportional weights.
 */
internal fun calculateCoverControlButtonSize(availableWidth: Dp): Dp {
    if (availableWidth <= 0.dp || availableWidth == Dp.Infinity) return 48.dp
    val slotWidth = availableWidth / 5
    return when {
        slotWidth >= 52.dp -> 48.dp
        slotWidth >= 46.dp -> 44.dp
        slotWidth >= 40.dp -> 40.dp
        slotWidth >= 34.dp -> 34.dp
        else -> 30.dp
    }
}

internal fun calculateCoverControlIconSize(buttonSize: Dp): Dp {
    return when {
        buttonSize >= 44.dp -> 24.dp
        buttonSize >= 38.dp -> 20.dp
        else -> 18.dp
    }
}

@Composable
internal fun CoverAccessibleControls(
    isPlaying: Boolean,
    onLongPressPlayPause: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val availableWidth = maxWidth
        val buttonSize = calculateCoverControlButtonSize(availableWidth)
        val iconSize = calculateCoverControlIconSize(buttonSize)

        val previousLabel = stringResource(R.string.coverscreen_action_previous)
        val playLabel = stringResource(R.string.coverscreen_action_play)
        val pauseLabel = stringResource(R.string.coverscreen_action_pause)
        val nextLabel = stringResource(R.string.coverscreen_action_next)
        val queueLabel = stringResource(R.string.coverscreen_action_queue)
        val libraryLabel = stringResource(R.string.coverscreen_action_library)
        val settingsLabel = stringResource(R.string.coverscreen_gesture_long_press_settings)
        val playingState = stringResource(R.string.coverscreen_status_now_playing)
        val pausedState = stringResource(R.string.coverscreen_status_paused)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverControlButton(
                icon = Icons.Filled.SkipPrevious,
                description = previousLabel,
                onClickLabel = previousLabel,
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
                buttonSize = buttonSize,
                iconSize = iconSize
            )
            CoverControlButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                description = if (isPlaying) pauseLabel else playLabel,
                stateDescription = if (isPlaying) playingState else pausedState,
                onClickLabel = if (isPlaying) pauseLabel else playLabel,
                onLongClickLabel = settingsLabel,
                onClick = onTogglePlayPause,
                onLongClick = onLongPressPlayPause,
                testTag = CoverScreenTestTags.PLAYBACK_SETTINGS_OPEN,
                modifier = Modifier.weight(1f),
                buttonSize = buttonSize,
                iconSize = iconSize
            )
            CoverControlButton(
                icon = Icons.Filled.SkipNext,
                description = nextLabel,
                onClickLabel = nextLabel,
                onClick = onNext,
                modifier = Modifier.weight(1f),
                buttonSize = buttonSize,
                iconSize = iconSize
            )
            CoverControlButton(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                description = queueLabel,
                onClickLabel = queueLabel,
                onClick = onOpenQueue,
                modifier = Modifier.weight(1f),
                buttonSize = buttonSize,
                iconSize = iconSize
            )
            CoverControlButton(
                icon = Icons.Filled.LibraryMusic,
                description = libraryLabel,
                onClickLabel = libraryLabel,
                onClick = onOpenLibrary,
                testTag = CoverScreenTestTags.LIBRARY_OPEN,
                modifier = Modifier.weight(1f),
                buttonSize = buttonSize,
                iconSize = iconSize
            )
        }
    }
}

@Composable
internal fun CoverControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    testTag: String? = null,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    stateDescription: String? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .minimumInteractiveComponentSize()
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .then(
                if (onLongClick != null) {
                    Modifier
                        .semantics {
                            role = Role.Button
                            if (stateDescription != null) {
                                this.stateDescription = stateDescription
                            }
                            onClick(label = onClickLabel ?: description) {
                                onClick()
                                true
                            }
                            onLongClick(label = onLongClickLabel ?: description) {
                                onLongClick()
                                true
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onClick() },
                                onLongPress = { onLongClick() }
                            )
                        }
                } else {
                    Modifier
                        .semantics {
                            if (stateDescription != null) {
                                this.stateDescription = stateDescription
                            }
                        }
                        .clickable(
                            role = Role.Button,
                            onClickLabel = onClickLabel ?: description,
                            onClick = onClick
                        )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .background(Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
internal fun CoverMicroWaveform(
    amplitudes: List<Float>,
    bass: Float,
    energy: Float,
    isPlaying: Boolean = true,
    isRenderingActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shouldAnimate = CoverScreenPerformanceManager.shouldAnimate(isPlaying, isRenderingActive)
    val (phase, animatedEnergy) = if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "CoverWave")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2.0).toFloat(),
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 820, easing = LinearEasing)),
            label = "CoverWavePhase"
        )
        val energyState by animateFloatAsState(
            targetValue = (0.2f + energy * 0.9f).coerceIn(0.2f, 1.2f),
            animationSpec = tween(120),
            label = "CoverWaveEnergy"
        )
        Pair(animatedPhase, energyState)
    } else {
        Pair(0f, 0.2f)
    }

    val points = remember(amplitudes) {
        CoverScreenPerformanceManager.calculateWaveformPoints(amplitudes, 32)
    }
    val reusablePath = remember { Path() }

    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val path = CoverScreenPerformanceManager.populateWaveformPath(
            path = reusablePath,
            points = points,
            width = size.width,
            height = size.height,
            phase = phase,
            bass = if (shouldAnimate) bass else 0f,
            energy = animatedEnergy
        )

        drawLine(
            color = TigerNeonOrange.copy(alpha = 0.2f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1f
        )

        drawPath(
            path = path,
            color = TigerNeonOrange.copy(alpha = 0.32f),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            color = TigerNeonOrange,
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
    }
}

private fun Modifier.coverScreenGestures(
    swipeThresholdPx: Float,
    tapSlopPx: Float,
    shouldIgnoreGestureAt: (Offset) -> Boolean,
    onTap: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit
): Modifier {
    return pointerInput(swipeThresholdPx, tapSlopPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val ignoreGesture = shouldIgnoreGestureAt(down.position)

            // Preserve system edge gestures, except top-edge down swipes used to open search.
            val edgeX = size.width * 0.12f
            val edgeY = size.height * 0.12f
            val startedAtTopEdge = down.position.y < edgeY
            if (down.position.x < edgeX || down.position.x > size.width - edgeX ||
                down.position.y > size.height - edgeY) {
                return@awaitEachGesture // Let Samsung's OS handle the edge swipe
            }

            var pointerId: PointerId = down.id
            var totalX = 0f
            var totalY = 0f

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }
                    ?: event.changes.firstOrNull()
                    ?: break

                if (!change.pressed) break

                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                pointerId = change.id
            }

            val absX = abs(totalX)
            val absY = abs(totalY)

            if (ignoreGesture) return@awaitEachGesture

            when {
                absX <= tapSlopPx && absY <= tapSlopPx -> onTap()
                !startedAtTopEdge && absX > absY && totalX <= -swipeThresholdPx -> onSwipeLeft()
                !startedAtTopEdge && absX > absY && totalX >= swipeThresholdPx -> onSwipeRight()
                !startedAtTopEdge && absY > absX && totalY <= -swipeThresholdPx -> onSwipeUp()
                absY > absX && totalY >= swipeThresholdPx -> onSwipeDown()
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
