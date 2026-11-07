package com.tigerplayer.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.ui.theme.TigerNeonOrange
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.isActive

/**
 * Centralized Compose test tags for the lyrics screen, following the PrismTestTags convention
 * (see AGENTS.md / testing.instructions.md).
 */
object LyricsTestTags {
    const val NO_LYRICS_FOUND = "lyrics_no_lyrics_found"
    const val PLAIN_LYRICS_TEXT = "lyrics_plain_text"
    const val SYNCED_LYRICS_LIST = "lyrics_synced_list"
    const val OFFSET_CONTROLS = "lyrics_offset_controls"
    const val OFFSET_DECREASE = "lyrics_offset_decrease"
    const val OFFSET_INCREASE = "lyrics_offset_increase"
    const val OFFSET_RESET = "lyrics_offset_reset"
    const val OFFSET_DISPLAY = "lyrics_offset_display"
    const val LYRIC_LINE_PREFIX = "lyric_line_"
}

@Composable
fun LyricsDisplay(
    lyrics: Lyrics,
    currentPosition: Long,
    textColor: Color,
    activeFontSize: TextUnit = 24.sp,
    inactiveFontSize: TextUnit = 20.sp,
    plainLyricsFontSize: TextUnit = 24.sp,
    lineVerticalPadding: Dp = 12.dp,
    manualOffsetMs: Long = 0L,
    showOffsetControls: Boolean = false,
    cyclicProjection: Boolean = true,
    leadWindowMs: Long = LyricsViewportCoordinator.DEFAULT_LEAD_WINDOW_MS,
    onLineClick: ((Long) -> Unit)? = null,
    onOffsetAdjust: ((Long) -> Unit)? = null,
    onOffsetReset: (() -> Unit)? = null
) {
    when (lyrics) {
        is Lyrics.None -> {
            Box(Modifier.fillMaxSize().testTag(LyricsTestTags.NO_LYRICS_FOUND), Alignment.Center) {
                Text(
                    "NO LYRICS FOUND",
                    color = textColor.copy(0.4f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
            }
        }
        is Lyrics.Plain -> {
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .testTag(LyricsTestTags.PLAIN_LYRICS_TEXT),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = lyrics.text.trim(),
                    color = textColor.copy(0.8f),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = plainLyricsFontSize
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 32.dp, horizontal = 24.dp)
                )
            }
        }
        is Lyrics.Synced -> {
            val parsed = lyrics.lines
            if (parsed.isEmpty()) {
                Box(Modifier.fillMaxSize().testTag(LyricsTestTags.NO_LYRICS_FOUND), Alignment.Center) {
                    Text(
                        "NO LYRICS FOUND",
                        color = textColor.copy(0.4f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                }
            } else {
                val listState = rememberLazyListState()
                val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
                val density = LocalDensity.current

                val totalOffsetMs = lyrics.offsetMs + manualOffsetMs
                val rawAdjustedPosition = currentPosition + totalOffsetMs

                // Sub-frame monotonic time tracker to extrapolate 250ms playback ticks smoothly
                var lastAnchorPos by remember { mutableLongStateOf(rawAdjustedPosition) }
                var lastAnchorMonoTime by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }

                if (rawAdjustedPosition != lastAnchorPos) {
                    lastAnchorPos = rawAdjustedPosition
                    lastAnchorMonoTime = android.os.SystemClock.elapsedRealtime()
                }

                var frameMonotonicTime by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
                LaunchedEffect(lyrics, currentPosition) {
                    while (isActive) {
                        withFrameMillis {
                            frameMonotonicTime = android.os.SystemClock.elapsedRealtime()
                        }
                    }
                }

                val smoothPosition = remember(lastAnchorPos, lastAnchorMonoTime, frameMonotonicTime) {
                    LyricsViewportCoordinator.extrapolatePosition(
                        reportedPositionMs = lastAnchorPos,
                        lastAnchorMonotonicMs = lastAnchorMonoTime,
                        currentMonotonicMs = frameMonotonicTime,
                        isPlaying = true
                    )
                }

                val anticipatoryState = remember(parsed, smoothPosition, leadWindowMs) {
                    LyricsViewportCoordinator.calculateAnticipatoryState(
                        lines = parsed,
                        currentPositionMs = smoothPosition,
                        leadWindowMs = leadWindowMs
                    )
                }

                val activeIndex = anticipatoryState.activeIndex
                val targetScrollIndex = anticipatoryState.targetScrollIndex
                val focalIndex = anticipatoryState.focalIndex
                val itemHeights = remember { mutableStateMapOf<Int, Int>() }

                var previousTargetIndex by remember { mutableIntStateOf(targetScrollIndex) }

                LaunchedEffect(targetScrollIndex, isUserDragging) {
                    if (targetScrollIndex in parsed.indices && !isUserDragging) {
                        val isLargeJump = abs(targetScrollIndex - previousTargetIndex) > 1
                        previousTargetIndex = targetScrollIndex

                        val layoutInfo = listState.layoutInfo
                        val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetScrollIndex }

                        if (targetItem != null && !isLargeJump) {
                            val viewportCenter = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
                            val itemCenter = targetItem.offset + (targetItem.size / 2)
                            val delta = (itemCenter - viewportCenter).toFloat()

                            if (abs(delta) > 1f) {
                                listState.animateScrollBy(
                                    value = delta,
                                    animationSpec = tween(
                                        durationMillis = anticipatoryState.effectiveLeadWindowMs.toInt(),
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            }
                        } else {
                            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                            val approxItemHeightPx = itemHeights[targetScrollIndex] ?: with(density) {
                                (lineVerticalPadding * 2 + activeFontSize.toDp()).toPx()
                            }.toInt()
                            val centerOffset = LyricsViewportCoordinator.calculateCenterOffset(viewportHeight, approxItemHeightPx)
                            if (isLargeJump) {
                                listState.scrollToItem(targetScrollIndex, scrollOffset = centerOffset)
                            } else {
                                listState.animateScrollToItem(targetScrollIndex, scrollOffset = centerOffset)
                            }
                        }
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val edgePadding = maxHeight / 2

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag(LyricsTestTags.SYNCED_LYRICS_LIST),
                        contentPadding = PaddingValues(vertical = edgePadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(
                            items = parsed,
                            key = { index, line -> "lyric_${line.timeMs}_$index" }
                        ) { index, line ->
                            val isActive = index == activeIndex
                            val inactiveScale = (inactiveFontSize.value / activeFontSize.value).coerceIn(0.75f, 1f)

                            LyricRow(
                                text = line.text,
                                isActive = isActive,
                                index = index,
                                lineTimeMs = line.timeMs,
                                focalIndex = focalIndex,
                                cyclicProjection = cyclicProjection,
                                activeColor = TigerNeonOrange,
                                textColor = textColor,
                                activeFontSize = activeFontSize,
                                inactiveScale = inactiveScale,
                                lineVerticalPadding = lineVerticalPadding,
                                onHeightMeasured = { measuredHeight -> itemHeights[index] = measuredHeight },
                                onLineClick = onLineClick
                            )
                        }
                    }

                    if (showOffsetControls && onOffsetAdjust != null) {
                        LyricsOffsetControls(
                            offsetMs = manualOffsetMs,
                            onAdjust = onOffsetAdjust,
                            onReset = onOffsetReset ?: { onOffsetAdjust(-manualOffsetMs) },
                            textColor = textColor,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsOffsetControls(
    offsetMs: Long,
    onAdjust: (Long) -> Unit,
    onReset: () -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.65f),
        contentColor = textColor,
        modifier = modifier.testTag(LyricsTestTags.OFFSET_CONTROLS)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            IconButton(
                onClick = { onAdjust(-500L) },
                modifier = Modifier
                    .size(28.dp)
                    .testTag(LyricsTestTags.OFFSET_DECREASE)
            ) {
                Icon(
                    Icons.Rounded.Remove,
                    contentDescription = "Delay lyrics by 0.5 seconds",
                    tint = textColor.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp)
                )
            }

            val offsetSec = offsetMs / 1000f
            val formatted = if (offsetMs == 0L) "Sync" else String.format(Locale.US, "%+.1fs", offsetSec)
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (offsetMs != 0L) Color.Yellow else textColor.copy(alpha = 0.75f),
                modifier = Modifier
                    .clickable { onReset() }
                    .padding(horizontal = 6.dp)
                    .testTag(LyricsTestTags.OFFSET_DISPLAY)
            )

            IconButton(
                onClick = { onAdjust(500L) },
                modifier = Modifier
                    .size(28.dp)
                    .testTag(LyricsTestTags.OFFSET_INCREASE)
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Advance lyrics by 0.5 seconds",
                    tint = textColor.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
@Composable
private fun LyricRow(
    text: String,
    isActive: Boolean,
    index: Int,
    lineTimeMs: Long,
    focalIndex: Float,
    cyclicProjection: Boolean,
    activeColor: Color,
    textColor: Color,
    activeFontSize: TextUnit,
    inactiveScale: Float,
    lineVerticalPadding: Dp,
    onHeightMeasured: ((Int) -> Unit)? = null,
    onLineClick: ((Long) -> Unit)?
) {
    val projection = remember(index, focalIndex, inactiveScale, cyclicProjection) {
        LyricsViewportCoordinator.calculateProjection(
            itemIndex = index,
            focalIndex = focalIndex,
            inactiveScale = inactiveScale,
            enabled = cyclicProjection
        )
    }

    val scale by animateFloatAsState(
        targetValue = projection.scale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "LyricScale"
    )
    val alpha by animateFloatAsState(
        targetValue = projection.alpha,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LyricAlpha"
    )

    val colorFraction = (1f - abs(index - focalIndex)).coerceIn(0f, 1f)
    val color = lerp(textColor, activeColor, colorFraction)

    Text(
        text = text.ifBlank { "•••" },
        color = color,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Black,
            fontSize = activeFontSize
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${LyricsTestTags.LYRIC_LINE_PREFIX}$index")
            .onSizeChanged { onHeightMeasured?.invoke(it.height) }
            .clickable(
                enabled = onLineClick != null,
                onClick = { onLineClick?.invoke(lineTimeMs) }
            )
            .padding(vertical = lineVerticalPadding, horizontal = 24.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                if (cyclicProjection) {
                    rotationX = projection.rotationX
                    cameraDistance = 16f * density
                }
            }
    )
}
