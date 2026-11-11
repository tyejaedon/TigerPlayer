package com.tigerplayer.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import com.tigerplayer.data.model.LrcLine
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
    val effectiveLeadWindowMs: Long
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
        lines: List<LrcLine>,
        currentPositionMs: Long,
        leadWindowMs: Long = DEFAULT_LEAD_WINDOW_MS
    ): LyricsAnticipatoryState {
        if (lines.isEmpty()) {
            return LyricsAnticipatoryState(
                activeIndex = -1,
                targetScrollIndex = -1,
                transitionProgress = 0f,
                focalIndex = 0f,
                effectiveLeadWindowMs = leadWindowMs
            )
        }

        val currentIndex = lines.indexOfLast { it.timeMs <= currentPositionMs }
        val activeIndex = if (currentIndex == -1) 0 else currentIndex
        val nextIndex = activeIndex + 1

        if (nextIndex !in lines.indices) {
            // Last line or beyond: stay anchored on the last line
            return LyricsAnticipatoryState(
                activeIndex = activeIndex,
                targetScrollIndex = activeIndex,
                transitionProgress = 0f,
                focalIndex = activeIndex.toFloat(),
                effectiveLeadWindowMs = leadWindowMs
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
                effectiveLeadWindowMs = effectiveLead
            )
        } else {
            LyricsAnticipatoryState(
                activeIndex = activeIndex,
                targetScrollIndex = activeIndex,
                transitionProgress = 0f,
                focalIndex = activeIndex.toFloat(),
                effectiveLeadWindowMs = effectiveLead
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
     * Calculates the center scroll offset in pixels for an item of height [itemHeightPx]
     * within a viewport of height [viewportHeightPx].
     */
    fun calculateCenterOffset(viewportHeightPx: Int, itemHeightPx: Int): Int {
        return -((viewportHeightPx / 2) - (itemHeightPx / 2))
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
}
