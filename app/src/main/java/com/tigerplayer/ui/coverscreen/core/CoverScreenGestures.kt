package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pure classifier for discriminating single-tap vs. double-tap gestures.
 *
 * When a double-tap listener is active, single-tap actions are scheduled with a debounce window
 * ([doubleTapTimeoutMs]) so that the first tap of a double tap does not accidentally trigger
 * single-tap actions (such as pausing audio playback when double-tapping to toggle visualizers).
 */
internal class TapGestureClassifier(
    private val doubleTapTimeoutMs: Long = 300L,
    private val maxDoubleTapDistancePx: Float = 60f
) {
    private var lastTapTimeMs = 0L
    private var lastTapPosition = Offset.Zero

    sealed interface Result {
        data object SingleTapImmediate : Result
        data object DoubleTap : Result
        data object ScheduleSingleTap : Result
    }

    fun onSlopTap(position: Offset, hasDoubleTapListener: Boolean, currentTimeMs: Long): Result {
        if (!hasDoubleTapListener) {
            lastTapTimeMs = 0L
            return Result.SingleTapImmediate
        }

        val isDoubleTap = (currentTimeMs - lastTapTimeMs < doubleTapTimeoutMs) &&
                (position - lastTapPosition).getDistance() < maxDoubleTapDistancePx

        return if (isDoubleTap) {
            lastTapTimeMs = 0L
            Result.DoubleTap
        } else {
            lastTapTimeMs = currentTimeMs
            lastTapPosition = position
            Result.ScheduleSingleTap
        }
    }

    fun reset() {
        lastTapTimeMs = 0L
        lastTapPosition = Offset.Zero
    }
}

/**
 * Pure evaluator determining whether a touch position lies within the system edge exclusion zone
 * (preserving Android OS notification shade pull-down, navigation bar, and back gesture areas).
 */
internal fun isCoverScreenSystemEdge(
    position: Offset,
    sizeWidth: Float,
    sizeHeight: Float,
    edgeRatio: Float = 0.12f
): Boolean {
    if (sizeWidth <= 0f || sizeHeight <= 0f) return false
    val edgeX = sizeWidth * edgeRatio
    val edgeY = sizeHeight * edgeRatio
    return position.x < edgeX || position.x > sizeWidth - edgeX ||
        position.y < edgeY || position.y > sizeHeight - edgeY
}

/**
 * Custom pointer input modifier handling cover-screen swipe, tap, and press-and-hold gestures while
 * respecting edge exclusion zones for device shell navigation (e.g., Samsung Galaxy Z Flip edge gestures).
 */
internal fun Modifier.coverScreenGestures(
    swipeThresholdPx: Float,
    tapSlopPx: Float,
    shouldIgnoreGestureAt: (Offset) -> Boolean,
    onTap: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
): Modifier = pointerInput(swipeThresholdPx, tapSlopPx, onDoubleTap, onLongPress) {
    coroutineScope {
        val classifier = TapGestureClassifier(doubleTapTimeoutMs = 300L, maxDoubleTapDistancePx = 60f)
        var pendingTapJob: Job? = null

        this@pointerInput.awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val ignoreGesture = shouldIgnoreGestureAt(down.position)

            // Preserve system edge gestures symmetrically (Samsung Galaxy Z Flip / Motorola Razr notification shade, back, and home gestures)
            if (isCoverScreenSystemEdge(down.position, size.width.toFloat(), size.height.toFloat())) {
                return@awaitEachGesture // Let the OS handle the edge swipe
            }

            var pointerId: PointerId = down.id
            var totalX = 0f
            var totalY = 0f
            var isReleased = false
            var longPressTriggered = false
            var gestureAborted = false

            if (onLongPress != null && !ignoreGesture) {
                try {
                    withTimeout(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()
                                ?: break

                            if (change.isConsumed) {
                                gestureAborted = true
                                break
                            }

                            val delta = change.positionChange()
                            totalX += delta.x
                            totalY += delta.y
                            pointerId = change.id

                            if (!change.pressed) {
                                isReleased = true
                                break
                            }

                            if (abs(totalX) > tapSlopPx || abs(totalY) > tapSlopPx) {
                                break
                            }
                        }
                    }
                } catch (_: PointerEventTimeoutCancellationException) {
                    if (!gestureAborted) {
                        longPressTriggered = true
                        pendingTapJob?.cancel()
                        pendingTapJob = null
                        classifier.reset()
                        onLongPress.invoke()
                    }
                }
            }

            if (gestureAborted) return@awaitEachGesture

            if (longPressTriggered) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull()
                        ?: break
                    change.consume()
                    if (!change.pressed) break
                }
                return@awaitEachGesture
            }

            if (!isReleased) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull()
                        ?: break

                    if (change.isConsumed) {
                        gestureAborted = true
                        break
                    }

                    val delta = change.positionChange()
                    totalX += delta.x
                    totalY += delta.y
                    pointerId = change.id

                    if (!change.pressed) break
                }
            }

            if (gestureAborted) return@awaitEachGesture

            val absX = abs(totalX)
            val absY = abs(totalY)

            if (ignoreGesture) return@awaitEachGesture

            when {
                absX <= tapSlopPx && absY <= tapSlopPx -> {
                    when (classifier.onSlopTap(down.position, onDoubleTap != null, System.currentTimeMillis())) {
                        TapGestureClassifier.Result.SingleTapImmediate -> {
                            pendingTapJob?.cancel()
                            pendingTapJob = null
                            onTap()
                        }
                        TapGestureClassifier.Result.DoubleTap -> {
                            pendingTapJob?.cancel()
                            pendingTapJob = null
                            onDoubleTap?.invoke()
                        }
                        TapGestureClassifier.Result.ScheduleSingleTap -> {
                            pendingTapJob?.cancel()
                            pendingTapJob = launch {
                                delay(300L)
                                onTap()
                            }
                        }
                    }
                }
                absX > absY && totalX <= -swipeThresholdPx -> {
                    classifier.reset()
                    pendingTapJob?.cancel()
                    pendingTapJob = null
                    onSwipeLeft()
                }
                absX > absY && totalX >= swipeThresholdPx -> {
                    classifier.reset()
                    pendingTapJob?.cancel()
                    pendingTapJob = null
                    onSwipeRight()
                }
                absY > absX && totalY <= -swipeThresholdPx -> {
                    classifier.reset()
                    pendingTapJob?.cancel()
                    pendingTapJob = null
                    onSwipeUp()
                }
                absY > absX && totalY >= swipeThresholdPx -> {
                    classifier.reset()
                    pendingTapJob?.cancel()
                    pendingTapJob = null
                    onSwipeDown()
                }
            }
        }
    }
}
