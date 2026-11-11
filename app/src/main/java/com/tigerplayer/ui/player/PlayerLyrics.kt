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

/**
 * Main composable for displaying track lyrics, supporting empty/missing lyrics states,
 * unsynced plain text lyrics, and timestamp-synchronized 3D auto-scrolling lyrics.
 *
 * @param lyrics The [Lyrics] model state (None, Plain, or Synced). Dictates whether an empty state,
 *   plain scrollable text box, or auto-scrolling synced 3D list is rendered.
 * @param currentPosition Track playback position in milliseconds. Primary timeline anchor for lyric synchronization.
 * @param textColor Base theme/contrast text color. Serves as baseline color for inactive lyrics and UI icons.
 * @param activeFontSize Font size applied to the currently active (sung) lyric line.
 * @param inactiveFontSize Base font size for inactive lyric lines; used to calculate relative scaling (`inactiveScale`).
 * @param plainLyricsFontSize Font size used when displaying unsynced plain text lyrics.
 * @param lineVerticalPadding Vertical padding spacing applied around each individual lyric row.
 * @param manualOffsetMs User-adjusted timing offset in milliseconds (+ delays lyrics, - advances lyrics).
 *   Added to static track offset to resynchronize misaligned lyrics.
 * @param showOffsetControls Toggles visibility of the manual timing offset adjustment UI overlay.
 * @param cyclicProjection Enables or disables 3D cylindrical perspective projection (rotation tilt, scaling,
 *   and alpha fade based on distance from focal center).
 * @param leadWindowMs Time window in milliseconds before line timestamp to begin predictive smooth scrolling.
 * @param onLineClick Optional callback invoked with line timestamp (in ms) when tapped, enabling seek-to-position.
 * @param onOffsetAdjust Optional callback invoked to shift manual timing offset by a delta in milliseconds.
 * @param onOffsetReset Optional callback invoked to reset manual timing offset back to zero.
 */
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
                // True when user is actively dragging list; suspends programmatic auto-scroll
                val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
                val density = LocalDensity.current

                // Net offset combined from track-level offset and user manual adjustment
                val totalOffsetMs = lyrics.offsetMs + manualOffsetMs
                // Adjusted position reflecting net timing offset; shifts effective line matching window
                val rawAdjustedPosition = currentPosition + totalOffsetMs

                // Monotonic time tracking anchors to extrapolate position between 250ms player updates
                var lastAnchorPos by remember { mutableLongStateOf(rawAdjustedPosition) }
                var lastAnchorMonoTime by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }

                if (rawAdjustedPosition != lastAnchorPos) {
                    lastAnchorPos = rawAdjustedPosition
                    lastAnchorMonoTime = android.os.SystemClock.elapsedRealtime()
                }

                // High-precision frame monotonic clock updated every frame via withFrameMillis
                var frameMonotonicTime by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
                LaunchedEffect(lyrics, currentPosition) {
                    while (isActive) {
                        withFrameMillis {
                            frameMonotonicTime = android.os.SystemClock.elapsedRealtime()
                        }
                    }
                }

                // Continuously extrapolated playback position driving jitter-free 60/120 FPS animations
                val smoothPosition = remember(lastAnchorPos, lastAnchorMonoTime, frameMonotonicTime) {
                    LyricsViewportCoordinator.extrapolatePosition(
                        reportedPositionMs = lastAnchorPos,
                        lastAnchorMonotonicMs = lastAnchorMonoTime,
                        currentMonotonicMs = frameMonotonicTime,
                        isPlaying = true
                    )
                }

                // Anticipatory scroll metrics (active index, scroll target, focal position, lead window)
                val anticipatoryState = remember(parsed, smoothPosition, leadWindowMs) {
                    LyricsViewportCoordinator.calculateAnticipatoryState(
                        lines = parsed,
                        currentPositionMs = smoothPosition,
                        leadWindowMs = leadWindowMs
                    )
                }

                // Zero-based index of line corresponding to smoothPosition (controls active line styling)
                val activeIndex = anticipatoryState.activeIndex
                // Viewport auto-scroll target index anticipating upcoming line transitions
                val targetScrollIndex = anticipatoryState.targetScrollIndex
                // Continuous floating-point viewport center index driving fluid 3D transformations
                val focalIndex = anticipatoryState.focalIndex
                // Dynamic line height lookup map used for exact item center-offset calculations
                val itemHeights = remember { mutableStateMapOf<Int, Int>() }

                // Tracks prior target index to distinguish small steps from large seek jumps
                var previousTargetIndex by remember { mutableIntStateOf(-1) }
                var isInitialScroll by remember { mutableStateOf(true) }

                LaunchedEffect(targetScrollIndex, isUserDragging) {
                    if (targetScrollIndex in parsed.indices && !isUserDragging) {
                        val isLargeJump = isInitialScroll || abs(targetScrollIndex - previousTargetIndex) > 1
                        previousTargetIndex = targetScrollIndex
                        isInitialScroll = false

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

/**
 * Overlay control panel allowing real-time adjustment (+/- 500ms) and resetting of manual lyric synchronization offsets.
 *
 * @param offsetMs Current manual offset in milliseconds (+ delays lyrics, - advances lyrics).
 * @param onAdjust Callback invoked when user taps +/- buttons to increment/decrement offset by delta.
 * @param onReset Callback invoked when user taps offset display to reset offset to 0ms.
 * @param textColor Tint color for text and icons.
 * @param modifier Layout modifier positioning the overlay container.
 */
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

            // Converted float value in seconds for formatted user display
            val offsetSec = offsetMs / 1000f
            // Display text formatted as "+0.5s", "-0.5s", or "Sync" when offset is zero
            val formatted = if (offsetMs == 0L) "Sync" else String.format(java.util.Locale.US, "%+.1fs", offsetSec)
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

/**
 * Individual lyric row item rendered within the synchronized lyrics list. Applies dynamic 3D projection transformations
 * (scaling, rotation, alpha fading, and color blending) based on proximity to the continuous focal center index.
 *
 * @param line Content string of the lyric line.
 * @param isActive Boolean indicating whether this row represents the active singing line.
 * @param isInterlude Boolean indicating whether to show pulsing dots instead.
 * @param currentPositionMs Current ms for drawing word fills.
 * @param hasWordLevelTiming True if words can be filled.
 * @param index Row position index within the parsed lyric line list.
 * @param focalIndex Continuous floating-point focal center index of the lyrics viewport.
 * @param cyclicProjection Enables or disables 3D cylindrical projection physics (tilting, scaling, alpha).
 * @param activeColor Highlight color applied to the active lyric line (e.g. TigerNeonOrange).
 * @param textColor Base theme text color for inactive lyric lines.
 * @param activeFontSize Target font size for active lyric lines.
 * @param inactiveScale Minimum scale factor for inactive lyric lines.
 * @param lineVerticalPadding Vertical padding spacing applied to the row.
 * @param onHeightMeasured Callback emitting measured row pixel height to update the `itemHeights` lookup map.
 * @param onLineClick Optional callback emitting `lineTimeMs` when clicked to seek playback.
 */
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun LyricsDisplayPreview() {
    val dummyLines = listOf(
        com.tigerplayer.data.model.LyricLine(
            timeMs = 1000L,
            durationMs = 2500L,
            text = "Is this the real life?",
            words = emptyList()
        ),
        com.tigerplayer.data.model.LyricLine(
            timeMs = 4000L,
            durationMs = 3000L,
            text = "Is this just fantasy?",
            words = listOf(
                com.tigerplayer.data.model.LyricWord(4000L, 4500L, "Is "),
                com.tigerplayer.data.model.LyricWord(4500L, 5000L, "this "),
                com.tigerplayer.data.model.LyricWord(5000L, 5500L, "just "),
                com.tigerplayer.data.model.LyricWord(5500L, 7000L, "fantasy?")
            )
        ),
        com.tigerplayer.data.model.LyricLine(
            timeMs = 8000L,
            durationMs = 3000L,
            text = "Caught in a landslide",
            words = emptyList()
        ),
        com.tigerplayer.data.model.LyricLine(
            timeMs = 12000L,
            durationMs = 4000L,
            text = "No escape from reality",
            words = emptyList()
        )
    )

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            LyricsDisplay(
                lyrics = com.tigerplayer.data.model.Lyrics.Synced(lines = dummyLines, hasWordLevelTiming = true),
                currentPosition = 1500L, // Matches the middle of the FIRST line to demonstrate exact centering natively in the preview pane
                textColor = Color.White
            )
        }
    }
}
