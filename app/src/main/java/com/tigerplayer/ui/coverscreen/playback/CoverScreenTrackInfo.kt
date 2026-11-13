package com.tigerplayer.ui.coverscreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import com.tigerplayer.ui.theme.TigerNeonRed
import com.tigerplayer.ui.theme.glassEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.tigerplayer.R
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.engine.SleepTimerState
import com.tigerplayer.ui.player.LyricsDisplay
import com.tigerplayer.ui.player.PlaybackRateDialog
import com.tigerplayer.ui.player.SleepTimerSheet
import com.tigerplayer.ui.theme.TigerNeonOrange

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
 * remain visible and are never pushed off screen.
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

    if (isLarge) {
        val flexTarget = if (screenHeightDp > 0.dp && screenHeightDp != Dp.Infinity) {
            (screenHeightDp * 0.16f).coerceIn(120.dp, 160.dp)
        } else {
            130.dp
        }
        return maxOf(minLineHeight, flexTarget)
    }

    val effectiveHeight = if (screenHeightDp > 0.dp && screenHeightDp != Dp.Infinity) screenHeightDp else 390.dp
    val extraHeight = (effectiveHeight - 360.dp).coerceAtLeast(0.dp)
    val adaptiveHeight = minLineHeight + (extraHeight * 0.25f)
    return adaptiveHeight.coerceAtMost(120.dp).coerceAtLeast(minLineHeight)
}

/**
 * Calculates adaptive visual dimensions for album artwork in the cover-screen viewport.
 *
 * Expands cover art prominence on compact outer displays (e.g. Motorola Razr, Samsung Galaxy Z Flip)
 * and flex-mode split views, providing an immersive, high-visibility square artwork presentation
 * while maintaining proper clearance for controls and waveforms.
 */
internal fun calculateCoverArtSize(
    screenHeightDp: Dp,
    isLarge: Boolean = false
): Dp {
    if (isLarge) {
        val flexTarget = if (screenHeightDp > 0.dp && screenHeightDp != Dp.Infinity) {
            (screenHeightDp * 0.45f).coerceIn(160.dp, 220.dp)
        } else {
            190.dp
        }
        return flexTarget
    }

    val effectiveHeight = if (screenHeightDp > 0.dp && screenHeightDp != Dp.Infinity) screenHeightDp else 380.dp
    return (effectiveHeight * 0.48f).coerceIn(150.dp, 220.dp)
}

internal const val COVER_MEDIA_CYCLE_PAGE_COUNT = 2

/**
 * View cycle targets for the swipable media area in [CoverScreenTrackDashboard]:
 * Dot 1 (index 0) = Album artwork
 * Dot 2 (index 1) = Synced lyrics (LyricsDisplay)
 */
@Composable
internal fun CoverScreenMediaCycleViewport(
    currentViewIndex: Int,
    onViewIndexChanged: (Int) -> Unit,
    track: AudioTrack?,
    lyrics: Lyrics,
    currentPosition: Long,
    isLarge: Boolean,
    minLineHeight: Dp,
    targetHeight: Dp,
    targetArtHeight: Dp = targetHeight,
    onToggleVortexImmersive: () -> Unit = {},
    onDoubleTapAlbumArt: () -> Unit = {},
    onDoubleTapLyrics: () -> Unit = {},
    onSeek: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var totalDragX by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 36.dp.toPx() }

    val effectiveTargetHeight = if (currentViewIndex == 0) targetArtHeight else targetHeight
    val animatedViewportHeight by animateDpAsState(
        targetValue = effectiveTargetHeight,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "CoverViewportHeight"
    )

    Box(
        modifier = modifier
            .fillMaxWidth(if (isLarge) 0.85f else 0.94f)
            .height(animatedViewportHeight)
            .testTag(CoverScreenTestTags.LYRICS_CONTAINER)
            .pointerInput(currentViewIndex) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount
                    },
                    onDragEnd = {
                        if (totalDragX < -swipeThresholdPx) {
                            // Swiped Left: advance view (0 -> 1)
                            onViewIndexChanged((currentViewIndex + 1).coerceAtMost(COVER_MEDIA_CYCLE_PAGE_COUNT - 1))
                        } else if (totalDragX > swipeThresholdPx) {
                            // Swiped Right: previous view (1 -> 0)
                            onViewIndexChanged((currentViewIndex - 1).coerceAtLeast(0))
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = currentViewIndex,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                ) { it * direction } + fadeIn(
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ) + scaleIn(
                    initialScale = 0.94f,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                )) togetherWith (slideOutHorizontally(
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                ) { -it * direction } + fadeOut(
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ) + scaleOut(
                    targetScale = 1.06f,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                ))
            },
            label = "CoverMediaCycleAnimation",
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                // Dot 1: Album Artwork
                0 -> {
                    var showLikedHeart by remember { mutableStateOf(false) }
                    val heartScale = remember { androidx.compose.animation.core.Animatable(0.4f) }
                    val heartAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
                    val coroutineScope = rememberCoroutineScope()
                    var heartAnimJob by remember { mutableStateOf<Job?>(null) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(CoverScreenTestTags.ALBUM_ART_CONTAINER)
                            .pointerInput(track?.id) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        onDoubleTapAlbumArt()
                                        heartAnimJob?.cancel()
                                        heartAnimJob = coroutineScope.launch {
                                            showLikedHeart = true
                                            heartScale.snapTo(0.4f)
                                            heartAlpha.snapTo(1f)
                                            launch {
                                                heartScale.animateTo(
                                                    1.3f,
                                                    androidx.compose.animation.core.spring(dampingRatio = 0.5f)
                                                )
                                            }
                                            heartAlpha.animateTo(
                                                0f,
                                                androidx.compose.animation.core.tween(
                                                    durationMillis = 750,
                                                    delayMillis = 150
                                                )
                                            )
                                            showLikedHeart = false
                                        }
                                    },
                                    onLongPress = {
                                        onToggleVortexImmersive()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = track?.artworkUri,
                            contentDescription = track?.title,
                            contentScale = ContentScale.Crop,
                            fallback = painterResource(R.drawable.ic_tiger_logo),
                            modifier = Modifier
                                .fillMaxHeight(0.98f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .glassEffect(RoundedCornerShape(20.dp))
                        )

                        if (showLikedHeart) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.98f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.2f))
                                    .testTag(CoverScreenTestTags.LIKE_HEART_OVERLAY),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = stringResource(R.string.coverscreen_action_like),
                                    tint = TigerNeonRed,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .scale(heartScale.value)
                                        .alpha(heartAlpha.value)
                                )
                            }
                        }
                    }
                }
                // Dot 2: Lyrics View
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .glassEffect(RoundedCornerShape(20.dp))
                            .padding(8.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { onDoubleTapLyrics() },
                                    onLongPress = { onToggleVortexImmersive() }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        LyricsDisplay(
                            lyrics = lyrics,
                            currentPosition = currentPosition,
                            textColor = Color.White,
                            activeFontSize = if (isLarge) 22.sp else 20.sp,
                            inactiveFontSize = if (isLarge) 18.sp else 16.sp,
                            plainLyricsFontSize = if (isLarge) 20.sp else 18.sp,
                            lineVerticalPadding = if (isLarge) 10.dp else 8.dp,
                            onLineClick = onSeek
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoverScreenTrackDashboard(
    track: AudioTrack?,
    subtitle: String,
    lyrics: Lyrics,
    currentPosition: Long,
    isLarge: Boolean,
    statsVisible: Boolean,
    isVortexImmersive: Boolean = false,
    onToggleVortexImmersive: () -> Unit = {},
    onDoubleTapAlbumArt: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenPlaybackSettings: () -> Unit = {},
    playlists: List<Playlist> = emptyList(),
    onAddToPlaylist: ((Long) -> Unit)? = null,
    playbackSpeed: Float = 1f,
    pitchRatio: Float = 1f,
    onSpeedChangeFinished: (Float) -> Unit = {},
    onPitchChangeFinished: (Float) -> Unit = {},
    sleepTimerState: SleepTimerState? = null,
    onSetSleepTimerDuration: ((Int) -> Unit)? = null,
    onSetEndOfTrackSleepTimer: (() -> Unit)? = null,
    onSetEndOfQueueSleepTimer: (() -> Unit)? = null,
    onCancelSleepTimer: (() -> Unit)? = null,
    onMediaCyclePositioned: (Rect) -> Unit = {},
    onSeek: ((Long) -> Unit)? = null,
    screenHeightDp: Dp = LocalConfiguration.current.screenHeightDp.dp
) {
    val isTight = screenHeightDp > 0.dp && screenHeightDp <= 360.dp
    val spacerHeight = when {
        isLarge -> 10.dp
        isTight -> 4.dp
        else -> 6.dp
    }

    val fontScale = LocalDensity.current.fontScale
    val minLineHeight = calculateCoverLyricsMinLineHeight(fontScale)
    val targetLyricsHeight = calculateCoverLyricsBoxHeight(
        screenHeightDp = screenHeightDp,
        fontScale = fontScale,
        isLarge = isLarge,
        statsVisible = statsVisible
    )
    val targetArtHeight = calculateCoverArtSize(
        screenHeightDp = screenHeightDp,
        isLarge = isLarge
    )
    val targetHeight = maxOf(targetLyricsHeight, targetArtHeight)

    // View index: 0 = Album (dot 1), 1 = Lyrics (dot 2)
    var currentViewIndex by remember { mutableIntStateOf(0) }

    var showPlaybackRateDialog by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showPlaylistSelector by remember { mutableStateOf(false) }

    CoverScreenTrackActionDialogs(
        showPlaybackRateDialog = showPlaybackRateDialog,
        showSleepTimerSheet = showSleepTimerSheet,
        showPlaylistSelector = showPlaylistSelector,
        playbackSpeed = playbackSpeed,
        pitchRatio = pitchRatio,
        sleepTimerState = sleepTimerState,
        playlists = playlists,
        onDismissPlaybackRateDialog = { showPlaybackRateDialog = false },
        onDismissSleepTimerSheet = { showSleepTimerSheet = false },
        onDismissPlaylistSelector = { showPlaylistSelector = false },
        onSpeedChangeFinished = onSpeedChangeFinished,
        onPitchChangeFinished = onPitchChangeFinished,
        onSetSleepTimerDuration = onSetSleepTimerDuration,
        onSetEndOfTrackSleepTimer = onSetEndOfTrackSleepTimer,
        onSetEndOfQueueSleepTimer = onSetEndOfQueueSleepTimer,
        onCancelSleepTimer = onCancelSleepTimer,
        onAddToPlaylist = onAddToPlaylist
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(spacerHeight))

        CoverScreenTrackHeader(
            track = track,
            isLarge = isLarge,
            isVortexImmersive = isVortexImmersive,
            currentViewIndex = currentViewIndex,
            onViewIndexChanged = { currentViewIndex = it },
            onOpenQueue = onOpenQueue,
            onOpenLibrary = onOpenLibrary,
            onOpenPlaybackSettings = onOpenPlaybackSettings,
            onShowPlaylistSelector = { showPlaylistSelector = true },
            onShowPlaybackRateDialog = { showPlaybackRateDialog = true },
            onShowSleepTimerSheet = { showSleepTimerSheet = true }
        )

        if (!isVortexImmersive) {
            Spacer(modifier = Modifier.height(spacerHeight))

            CoverScreenMediaCycleViewport(
                currentViewIndex = currentViewIndex,
                onViewIndexChanged = { currentViewIndex = it },
                track = track,
                lyrics = lyrics,
                currentPosition = currentPosition,
                isLarge = isLarge,
                minLineHeight = minLineHeight,
                targetHeight = targetHeight,
                targetArtHeight = targetHeight,
                onToggleVortexImmersive = onToggleVortexImmersive,
                onDoubleTapAlbumArt = onDoubleTapAlbumArt,
                onDoubleTapLyrics = onDoubleTapAlbumArt,
                onSeek = onSeek,
                modifier = Modifier.onGloballyPositioned {
                    onMediaCyclePositioned(it.boundsInRoot())
                }
            )

            Spacer(modifier = Modifier.height(if (isTight) 2.dp else 4.dp))

            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
internal fun CoverScreenTrackHeader(
    track: AudioTrack?,
    isLarge: Boolean,
    isVortexImmersive: Boolean,
    currentViewIndex: Int,
    onViewIndexChanged: (Int) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlaybackSettings: () -> Unit,
    onShowPlaylistSelector: () -> Unit,
    onShowPlaybackRateDialog: () -> Unit,
    onShowSleepTimerSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (!isVortexImmersive) {
            CoverScreenTrackOverflowMenu(
                onOpenQueue = onOpenQueue,
                onOpenPlaybackSettings = onOpenPlaybackSettings,
                onShowPlaylistSelector = onShowPlaylistSelector,
                onShowPlaybackRateDialog = onShowPlaybackRateDialog,
                onShowSleepTimerSheet = onShowSleepTimerSheet
            )
        } else {
            Spacer(modifier = Modifier.size(32.dp))
        }

        CoverScreenTrackMetadataArea(
            track = track,
            isLarge = isLarge,
            isVortexImmersive = isVortexImmersive,
            currentViewIndex = currentViewIndex,
            onViewIndexChanged = onViewIndexChanged,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        )

        if (!isVortexImmersive) {
            IconButton(
                onClick = onOpenLibrary,
                modifier = Modifier
                    .size(32.dp)
                    .testTag(CoverScreenTestTags.LIBRARY_OPEN)
            ) {
                Icon(
                    imageVector = Icons.Filled.LibraryMusic,
                    contentDescription = stringResource(R.string.coverscreen_action_library),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
internal fun CoverScreenTrackOverflowMenu(
    onOpenQueue: () -> Unit,
    onOpenPlaybackSettings: () -> Unit,
    onShowPlaylistSelector: () -> Unit,
    onShowPlaybackRateDialog: () -> Unit,
    onShowSleepTimerSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { showOverflowMenu = true },
            modifier = Modifier
                .size(32.dp)
                .testTag(CoverScreenTestTags.queueTrack("open"))
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Track options",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = showOverflowMenu,
            onDismissRequest = { showOverflowMenu = false },
            modifier = Modifier.glassEffect(RoundedCornerShape(18.dp), isOpaque = true),
            containerColor = Color.Transparent
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Add to playlist",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null,
                        tint = TigerNeonOrange,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showOverflowMenu = false
                    onShowPlaylistSelector()
                },
                modifier = Modifier.testTag("cover_track_menu_add_playlist")
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Open queue",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showOverflowMenu = false
                    onOpenQueue()
                },
                modifier = Modifier.testTag("cover_track_menu_open_queue")
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Playback speed",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showOverflowMenu = false
                    onShowPlaybackRateDialog()
                },
                modifier = Modifier.testTag("cover_track_menu_playback_speed")
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Settings",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showOverflowMenu = false
                    onOpenPlaybackSettings()
                },
                modifier = Modifier.testTag("cover_track_menu_settings")
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Sleep timer",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showOverflowMenu = false
                    onShowSleepTimerSheet()
                },
                modifier = Modifier.testTag("cover_track_menu_sleep_timer")
            )
        }
    }
}

@Composable
internal fun CoverScreenTrackMetadataArea(
    track: AudioTrack?,
    isLarge: Boolean,
    isVortexImmersive: Boolean,
    currentViewIndex: Int,
    onViewIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var columnDragX by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 36.dp.toPx() }

    Column(
        modifier = modifier
            .pointerInput(currentViewIndex, isVortexImmersive) {
                if (!isVortexImmersive) {
                    detectHorizontalDragGestures(
                        onDragStart = { columnDragX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            columnDragX += dragAmount
                        },
                        onDragEnd = {
                            if (columnDragX < -swipeThresholdPx) {
                                onViewIndexChanged((currentViewIndex + 1).coerceAtMost(COVER_MEDIA_CYCLE_PAGE_COUNT - 1))
                            } else if (columnDragX > swipeThresholdPx) {
                                onViewIndexChanged((currentViewIndex - 1).coerceAtLeast(0))
                            }
                        }
                    )
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

        // Ellipsis dot scroll indicator syncing with the 2 views:
        // Dot 1 (index 0) = Album, Dot 2 (index 1) = Lyrics
        if (!isVortexImmersive) {
            Row(
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 2.dp)
                    .testTag("cover_media_cycle_dots"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (0 until COVER_MEDIA_CYCLE_PAGE_COUNT).forEach { index ->
                    val isActive = index == currentViewIndex
                    val dotWidth by animateDpAsState(
                        targetValue = if (isActive) 14.dp else 5.dp,
                        label = "DotWidth_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = dotWidth, height = 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) TigerNeonOrange else Color.White.copy(alpha = 0.35f)
                            )
                            .clickable { onViewIndexChanged(index) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun CoverScreenTrackActionDialogs(
    showPlaybackRateDialog: Boolean,
    showSleepTimerSheet: Boolean,
    showPlaylistSelector: Boolean,
    playbackSpeed: Float,
    pitchRatio: Float,
    sleepTimerState: SleepTimerState?,
    playlists: List<Playlist>,
    onDismissPlaybackRateDialog: () -> Unit,
    onDismissSleepTimerSheet: () -> Unit,
    onDismissPlaylistSelector: () -> Unit,
    onSpeedChangeFinished: (Float) -> Unit,
    onPitchChangeFinished: (Float) -> Unit,
    onSetSleepTimerDuration: ((Int) -> Unit)?,
    onSetEndOfTrackSleepTimer: (() -> Unit)?,
    onSetEndOfQueueSleepTimer: (() -> Unit)?,
    onCancelSleepTimer: (() -> Unit)?,
    onAddToPlaylist: ((Long) -> Unit)?
) {
    if (showPlaybackRateDialog) {
        PlaybackRateDialog(
            initialSpeed = playbackSpeed,
            initialPitchRatio = pitchRatio,
            onDismissRequest = onDismissPlaybackRateDialog,
            onSpeedChangeFinished = onSpeedChangeFinished,
            onPitchChangeFinished = onPitchChangeFinished
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            state = sleepTimerState ?: SleepTimerState(),
            onDismiss = onDismissSleepTimerSheet,
            onSetDuration = { minutes ->
                onSetSleepTimerDuration?.invoke(minutes)
            },
            onSetEndOfTrack = {
                onSetEndOfTrackSleepTimer?.invoke()
            },
            onSetEndOfQueue = {
                onSetEndOfQueueSleepTimer?.invoke()
            },
            onCancel = {
                onCancelSleepTimer?.invoke()
            }
        )
    }

    if (showPlaylistSelector) {
        CoverScreenAddToPlaylistDialog(
            playlists = playlists,
            onDismiss = onDismissPlaylistSelector,
            onPlaylistSelected = { playlistId: Long ->
                onAddToPlaylist?.invoke(playlistId)
            }
        )
    }
}

@Composable
internal fun CoverScreenAddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Long) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(24.dp))
                .glassEffect(RoundedCornerShape(24.dp), isOpaque = true)
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ADD TO PLAYLIST",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No playlists found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable {
                                        onPlaylistSelected(playlist.id)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = null,
                                    tint = TigerNeonOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

