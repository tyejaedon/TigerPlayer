package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/**
 * Custom pointer input modifier handling cover-screen swipe and tap gestures while respecting
 * edge exclusion zones for device shell navigation (e.g., Samsung Galaxy Z Flip edge gestures).
 */
internal fun Modifier.coverScreenGestures(
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
                return@awaitEachGesture // Let the OS handle the edge swipe
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
