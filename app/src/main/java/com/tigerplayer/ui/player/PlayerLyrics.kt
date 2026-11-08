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
import kotlinx.coroutines.delay

const val DEFAULT_LYRICS_ACTIVE_POSITION_RATIO = 0.30f
const val DEFAULT_LYRICS_MANUAL_SCROLL_COOLDOWN_MS = 3500L

/**
 * Calculates the target vertical offset in pixels for docking the active lyric line
 * within the scrollable viewport.
 *
 * Defaults to [DEFAULT_LYRICS_ACTIVE_POSITION_RATIO] (0.30f) to elevate active lyrics
 * into the upper portion of the view, leaving ample clearance below for upcoming lyrics.
 */
internal fun calculateLyricsTargetY(
    viewportHeight: Float,
    activePositionRatio: Float = DEFAULT_LYRICS_ACTIVE_POSITION_RATIO
): Float {
    return viewportHeight * activePositionRatio.coerceIn(0.1f, 0.9f)
}

/**
 * Calculates adaptive vertical content padding for [LazyColumn] so both the first and last
 * items can align with the elevated active target position.
 */
internal fun calculateLyricsContentPadding(
    maxHeight: Dp,
    activePositionRatio: Float = DEFAULT_LYRICS_ACTIVE_POSITION_RATIO
): Pair<Dp, Dp> {
    val ratio = activePositionRatio.coerceIn(0.1f, 0.9f)
    val topPadding = maxHeight * ratio
    val bottomPadding = maxHeight * (1f - ratio)
    return Pair(topPadding, bottomPadding)
}

/**
 * Calculates the non-negative scroll offset passed to [LazyListState.animateScrollToItem]
 * to ensure that off-screen items dock accurately at the elevated target position
 * without overshooting or being thrown to the bottom of the viewport.
 */
internal fun calculateLyricsScrollOffset(
    topPaddingPx: Float,
    targetY: Float,
    itemHeightPx: Float
): Int {
    val offset = topPaddingPx - targetY + (itemHeightPx / 2f)
    return offset.toInt().coerceAtLeast(0)
}

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
    activePositionRatio: Float = DEFAULT_LYRICS_ACTIVE_POSITION_RATIO,
    manualScrollCooldownMs: Long = DEFAULT_LYRICS_MANUAL_SCROLL_COOLDOWN_MS,
    onLineClick: ((Long) -> Unit)? = null,
    onOffsetAdjust: ((Long) -> Unit)? = null,
    onOffsetReset: (() -> Unit)? = null
) {
    val lyricScrollSpec = remember {
        tween<Float>(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        )
    }

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

                var isManualScrollActive by remember { mutableStateOf(false) }

                // Reset manual scroll override when track changes
                LaunchedEffect(lyrics) {
                    isManualScrollActive = false
                }

                // Handle manual scrolling pause and inactivity cooldown
                LaunchedEffect(isUserDragging) {
                    if (isUserDragging) {
                        isManualScrollActive = true
                    } else if (isManualScrollActive) {
                        delay(manualScrollCooldownMs)
                        isManualScrollActive = false
                    }
                }

                val totalOffsetMs = lyrics.offsetMs + manualOffsetMs
                val adjustedPosition = currentPosition + totalOffsetMs
                val activeIndex = remember(lyrics, manualOffsetMs, adjustedPosition) {
                    val index = parsed.indexOfLast { it.timeMs <= adjustedPosition }
                    if (index == -1 && parsed.isNotEmpty()) 0 else index
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val (edgePaddingTop, edgePaddingBottom) = calculateLyricsContentPadding(
                        maxHeight = maxHeight,
                        activePositionRatio = activePositionRatio
                    )
                    val viewportHeightPx = with(density) { maxHeight.toPx() }

                    LaunchedEffect(activeIndex, isManualScrollActive, maxHeight) {
                        if (activeIndex in parsed.indices && !isManualScrollActive) {
                            val layoutInfo = listState.layoutInfo
                            val measuredHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
                            val effectiveViewportHeight = if (measuredHeight > 0f) measuredHeight else viewportHeightPx
                            val targetY = calculateLyricsTargetY(effectiveViewportHeight, activePositionRatio)
                            val activeItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }

                            if (activeItem != null) {
                                val itemCenter = activeItem.offset + (activeItem.size / 2)
                                val delta = itemCenter - targetY

                                if (abs(delta) > 1f) {
                                    listState.animateScrollBy(
                                        value = delta,
                                        animationSpec = lyricScrollSpec
                                    )
                                }
                            } else {
                                val approxItemHeightPx = with(density) {
                                    (lineVerticalPadding * 2 + activeFontSize.toDp()).toPx()
                                }
                                val topPaddingPx = with(density) { edgePaddingTop.toPx() }
                                val targetScrollOffset = calculateLyricsScrollOffset(
                                    topPaddingPx = topPaddingPx,
                                    targetY = targetY,
                                    itemHeightPx = approxItemHeightPx
                                )
                                listState.animateScrollToItem(activeIndex, scrollOffset = targetScrollOffset)
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag(LyricsTestTags.SYNCED_LYRICS_LIST),
                        contentPadding = PaddingValues(top = edgePaddingTop, bottom = edgePaddingBottom),
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
                                activeColor = TigerNeonOrange,
                                textColor = textColor,
                                activeFontSize = activeFontSize,
                                inactiveScale = inactiveScale,
                                lineVerticalPadding = lineVerticalPadding,
                                onLineClick = onLineClick?.let { onClick ->
                                    { timeMs ->
                                        isManualScrollActive = false
                                        onClick(timeMs)
                                    }
                                }
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
    activeColor: Color,
    textColor: Color,
    activeFontSize: TextUnit,
    inactiveScale: Float,
    lineVerticalPadding: Dp,
    onLineClick: ((Long) -> Unit)?
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.05f else inactiveScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "LyricScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LyricAlpha"
    )

    val color = if (isActive) activeColor else textColor

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
            .clickable(
                enabled = onLineClick != null,
                onClick = { onLineClick?.invoke(lineTimeMs) }
            )
            .padding(vertical = lineVerticalPadding, horizontal = 24.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    )
}
