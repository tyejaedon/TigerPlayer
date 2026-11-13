package com.tigerplayer.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import com.tigerplayer.data.model.LyricLine
import kotlin.math.abs

/**
 * State representing the calculated position, target index, and cyclic projection metrics
 * for smooth, anticipatory lyrics viewport scrolling.
 */
data class LyricsAnticipatoryState(
    val activeIndex: Int,
    val targetScrollIndex: Int,
    val transitionProgress: Float,
    val focalIndex: Float,
    val effectiveLeadWindowMs: Long,
    val isInterlude: Boolean
)

data class ItemProjection(
    val rotationX: Float,
    val scale: Float,
    val alpha: Float
)

object LyricsViewportCoordinator {

    const val DEFAULT_LEAD_WINDOW_MS = 750L
    const val MAX_FORWARD_EXTRAPOLATION_MS = 350L

    /**
     * Given the list of timestamped lyric lines and the current playback position,
     * calculates the active singing index, the anticipatory scroll target index,
     * and the smooth transition progress in [0f, 1f].
     *
     * If approaching the next line within [leadWindowMs], the target index anticipates
     * the next line and smoothly interpolates progress so the viewport lands dead-center
     * exactly as the vocal starts.
     */
    fun calculateAnticipatoryState(
        lines: List<LyricLine>,
        currentPositionMs: Long,
        leadWindowMs: Long = DEFAULT_LEAD_WINDOW_MS
    ): LyricsAnticipatoryState {
        if (lines.isEmpty()) {
            return LyricsAnticipatoryState(
                activeIndex = -1,
                targetScrollIndex = -1,
                transitionProgress = 0f,
                focalIndex = 0f,
                effectiveLeadWindowMs = leadWindowMs,
                isInterlude = false
            )
        }

        val currentIndex = lines.indexOfLast { it.timeMs <= currentPositionMs }
        val activeIndex = if (currentIndex == -1) 0 else currentIndex
        val nextIndex = activeIndex + 1

        val isInterlude = if (currentIndex == -1) {
            (lines[0].timeMs - currentPositionMs) > 6000L
        } else if (nextIndex in lines.indices) {
            (lines[nextIndex].timeMs - currentPositionMs) > 6000L
        } else {
            false
        }

        if (nextIndex !in lines.indices) {
            // Last line or beyond: stay anchored on the last line
            return LyricsAnticipatoryState(
                activeIndex = activeIndex,
                targetScrollIndex = activeIndex,
                transitionProgress = 0f,
                focalIndex = activeIndex.toFloat(),
                effectiveLeadWindowMs = leadWindowMs,
                isInterlude = isInterlude
            )
        }

        val currentTimeMs = lines[activeIndex].timeMs
        val nextTimeMs = lines[nextIndex].timeMs
        val gapMs = nextTimeMs - currentTimeMs

        // Clamp lead window to at most 75% of the line gap so very quick lines don't clip
        val effectiveLead = if (gapMs > 0) minOf(leadWindowMs, (gapMs * 3) / 4) else leadWindowMs
        val transitionStartTime = nextTimeMs - effectiveLead

        return if (currentPositionMs >= transitionStartTime) {
            val rawFraction = (currentPositionMs - transitionStartTime).toFloat() / effectiveLead.toFloat()
            val progress = rawFraction.coerceIn(0f, 1f)
            val easedProgress = FastOutSlowInEasing.transform(progress)
            LyricsAnticipatoryState(
                activeIndex = activeIndex,
                targetScrollIndex = nextIndex,
                transitionProgress = easedProgress,
                focalIndex = activeIndex.toFloat() + easedProgress,
                effectiveLeadWindowMs = effectiveLead,
                isInterlude = isInterlude
            )
        } else {
            LyricsAnticipatoryState(
                activeIndex = activeIndex,
                targetScrollIndex = activeIndex,
                transitionProgress = 0f,
                focalIndex = activeIndex.toFloat(),
                effectiveLeadWindowMs = effectiveLead,
                isInterlude = isInterlude
            )
        }
    }

    /**
     * Computes the 3D cylindrical cyclic projection metrics for a line at [itemIndex]
     * relative to the continuous [focalIndex].
     */
    fun calculateProjection(
        itemIndex: Int,
        focalIndex: Float,
        inactiveScale: Float = 0.85f,
        enabled: Boolean = true
    ): ItemProjection {
        val delta = itemIndex - focalIndex
        val absDelta = abs(delta)

        if (!enabled) {
            val isActive = absDelta < 0.5f
            return ItemProjection(
                rotationX = 0f,
                scale = if (isActive) 1.05f else inactiveScale,
                alpha = if (isActive) 1.0f else 0.6f
            )
        }

        // 3D cylindrical projection: lines above tilt downward, lines below tilt upward
        val rotationX = (delta * 12f).coerceIn(-28f, 28f)
        val scale = (1.05f - (absDelta * 0.10f)).coerceIn(inactiveScale.coerceAtMost(0.95f), 1.05f)
        val alpha = (1.0f - (absDelta * 0.35f)).coerceIn(0.40f, 1.0f)

        return ItemProjection(
            rotationX = rotationX,
            scale = scale,
            alpha = alpha
        )
    }

    /**
     * Calculates the scroll offset in pixels required to align an item of height [itemHeightPx]
     * to the exact center of a viewport of height [viewportHeightPx].
     *
     * In Jetpack Compose, when a LazyColumn has top `contentPadding = viewportHeightPx / 2`,
     * item index 0 naturally aligns its top edge to `y = viewportHeightPx / 2` (the center line).
     * To center the item *itself* (not just its top edge), we must shift it UP by half of its own height.
     * Shifting an item UP relative to its natural padded position requires a POSITIVE scrollOffset.
     */
    fun calculateCenterOffset(viewportHeightPx: Int, itemHeightPx: Int): Int {
        // We shift the scroll offset down (positive) by half the item's height
        // to move the item's physical rendering position UP to the true vertical center.
        return itemHeightPx / 2
    }

    /**
     * Smoothly extrapolates playback position based on monotonic frame clock
     * between discrete position updates.
     */
    fun extrapolatePosition(
        reportedPositionMs: Long,
        lastAnchorMonotonicMs: Long,
        currentMonotonicMs: Long,
        isPlaying: Boolean
    ): Long {
        if (!isPlaying || lastAnchorMonotonicMs <= 0L || currentMonotonicMs < lastAnchorMonotonicMs) {
            return reportedPositionMs
        }
        val elapsed = (currentMonotonicMs - lastAnchorMonotonicMs).coerceIn(0L, MAX_FORWARD_EXTRAPOLATION_MS)
        return reportedPositionMs + elapsed
    }

    /**
     * Given the current playback position and the lyric line, calculates the fill progress
     * for word-by-word highlighting (Apple Music style).
     *
     * Returns a list of floats (one for each word) representing the fill percentage [0f, 1f].
     */
    fun calculateWordProgress(currentPositionMs: Long, line: LyricLine): List<Float> {
        if (line.words.isEmpty()) return emptyList()

        return line.words.map { word ->
            if (currentPositionMs < word.startTimeMs) {
                0f
            } else if (currentPositionMs >= word.endTimeMs) {
                1f
            } else {
                val duration = word.endTimeMs - word.startTimeMs
                if (duration <= 0L) 1f else (currentPositionMs - word.startTimeMs).toFloat() / duration.toFloat()
            }
        }
    }
}
