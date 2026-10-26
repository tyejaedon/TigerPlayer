package com.tigerplayer.ui.coverscreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.tigerplayer.R
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.engine.FluidRenderer
import com.tigerplayer.engine.FluidVortexView
import com.tigerplayer.ui.player.LyricsDisplay
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
 * View cycle targets for the swipable media area in [TrackInfoText]:
 * Dot 1 (index 0) = Album artwork
 * Dot 2 (index 1) = Synced lyrics (CoverScreenLyricsView/LyricsDisplay)
 * Dot 3 (index 2) = Fluid vortex audio-reactive canvas
 */
@Composable
internal fun CoverScreenMediaCycleViewport(
    currentViewIndex: Int,
    onViewIndexChanged: (Int) -> Unit,
    track: AudioTrack?,
    lyrics: String?,
    currentPosition: Long,
    fluidRenderer: FluidRenderer?,
    isLarge: Boolean,
    minLineHeight: Dp,
    targetHeight: Dp,
    onDoubleTapLyrics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var totalDragX by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 36.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth(if (isLarge) 0.85f else 0.94f)
            .heightIn(min = minLineHeight, max = targetHeight)
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
                            // Swiped Left: advance view (0 -> 1 -> 2)
                            onViewIndexChanged((currentViewIndex + 1).coerceAtMost(2))
                        } else if (totalDragX > swipeThresholdPx) {
                            // Swiped Right: previous view (2 -> 1 -> 0)
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
                if (targetState > initialState) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "CoverMediaCycleAnimation",
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                // Dot 1: Album Artwork
                0 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTapLyrics() }) },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = track?.artworkUri,
                            contentDescription = track?.title,
                            contentScale = ContentScale.Fit,
                            fallback = painterResource(R.drawable.ic_tiger_logo),
                            modifier = Modifier
                                .fillMaxHeight(0.92f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        )
                    }
                }
                // Dot 2: Lyrics View
                1 -> {
                    LyricsDisplay(
                        lyrics = lyrics,
                        currentPosition = currentPosition,
                        textColor = Color.White,
                        activeColor = TigerNeonOrange,
                        activeFontSize = if (isLarge) 22.sp else 20.sp,
                        inactiveFontSize = if (isLarge) 18.sp else 16.sp,
                        plainLyricsFontSize = if (isLarge) 20.sp else 18.sp,
                        lineVerticalPadding = if (isLarge) 10.dp else 8.dp
                    )
                }
                // Dot 3: Fluid Vortex
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                FluidVortexView(ctx).apply {
                                    fluidRenderer?.let { setFluidRenderer(it) }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TrackInfoText(
    track: AudioTrack?,
    subtitle: String,
    lyrics: String?,
    currentPosition: Long,
    isLarge: Boolean,
    statsVisible: Boolean,
    fluidRenderer: FluidRenderer? = null,
    onOpenQueue: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onMediaCyclePositioned: (Rect) -> Unit = {},
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

    // View index: 0 = Album (dot 1), 1 = Lyrics (dot 2), 2 = Vortex (dot 3)
    var currentViewIndex by remember { mutableIntStateOf(0) }
    var columnDragX by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 36.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(spacerHeight))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier
                    .size(32.dp)
                    .testTag(CoverScreenTestTags.queueTrack("open"))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = stringResource(R.string.coverscreen_action_queue),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
                    .pointerInput(currentViewIndex) {
                        detectHorizontalDragGestures(
                            onDragStart = { columnDragX = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                columnDragX += dragAmount
                            },
                            onDragEnd = {
                                if (columnDragX < -swipeThresholdPx) {
                                    currentViewIndex = (currentViewIndex + 1).coerceAtMost(2)
                                } else if (columnDragX > swipeThresholdPx) {
                                    currentViewIndex = (currentViewIndex - 1).coerceAtLeast(0)
                                }
                            }
                        )
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

                // Ellipsis dot scroll indicator syncing with the 3 views:
                // Dot 1 (index 0) = Album, Dot 2 (index 1) = Lyrics, Dot 3 (index 2) = Vortex
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 2.dp)
                        .testTag("cover_media_cycle_dots"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (0..2).forEach { index ->
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
                                .clickable { currentViewIndex = index }
                        )
                    }
                }
            }

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
        }

        Spacer(modifier = Modifier.height(spacerHeight))

        CoverScreenMediaCycleViewport(
            currentViewIndex = currentViewIndex,
            onViewIndexChanged = { currentViewIndex = it },
            track = track,
            lyrics = lyrics,
            currentPosition = currentPosition,
            fluidRenderer = fluidRenderer,
            isLarge = isLarge,
            minLineHeight = minLineHeight,
            targetHeight = targetLyricsHeight,
            onDoubleTapLyrics = onDoubleTap,
            modifier = Modifier.onGloballyPositioned {
                onMediaCyclePositioned(it.boundsInRoot())
            }
        )

        Spacer(modifier = Modifier.height(spacerHeight))

        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp
        )
    }
}
