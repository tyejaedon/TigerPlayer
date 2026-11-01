package com.tigerplayer.ui.coverscreen

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.tigerplayer.ui.theme.glassEffect
import com.tigerplayer.ui.theme.TigerNeonOrange
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tigerplayer.data.model.AudioTrack

@Composable
fun CoverScreenQueueView(
    queue: List<AudioTrack>,
    currentTrackId: String?,
    currentIndex: Int,
    shuffleModeEnabled: Boolean,
    onTrackTapped: (AudioTrack) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var localQueue by remember(queue) { mutableStateOf(queue) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragCurrentIndex by remember { mutableIntStateOf(-1) }
    var draggedDistance by remember { mutableStateOf(0f) }
    var pendingRemovalIndex by remember { mutableIntStateOf(-1) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(queue, dragStartIndex) {
        if (dragStartIndex == -1) localQueue = queue
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 260.dp)
            .glassEffect(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Queue (${queue.size})",
                style = MaterialTheme.typography.titleSmall
            )
            TextButton(
                onClick = { confirmClear = true },
                enabled = queue.isNotEmpty(),
                modifier = Modifier.testTag(CoverScreenTestTags.QUEUE_CLEAR)
            ) {
                Text("Clear queue")
            }
        }

        if (localQueue.isEmpty()) {
            Text(
                text = "Queue is empty",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .testTag(CoverScreenTestTags.QUEUE_EMPTY),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (shuffleModeEnabled) {
                Text(
                    text = "Turn off shuffle to reorder the queue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag(CoverScreenTestTags.QUEUE_LIST),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = localQueue,
                    key = { index, track -> "${track.id}#$index" }
                ) { index, track ->
                    val latestIndex by rememberUpdatedState(index)
                    val isCurrent = index == currentIndex || track.id == currentTrackId
                    val isDragging = dragStartIndex >= 0 && dragCurrentIndex == index
                    val swipeThreshold = 72.dp
                    val swipeModifier = Modifier.pointerInput(track.id, index) {
                        var horizontalDistance = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, amount ->
                                horizontalDistance += amount
                                change.consume()
                            },
                            onDragEnd = {
                                if (horizontalDistance < -swipeThreshold.toPx()) {
                                    pendingRemovalIndex = latestIndex
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                            },
                            onDragCancel = { horizontalDistance = 0f }
                        )
                    }
                    val dragModifier = if (shuffleModeEnabled || localQueue.size < 2) {
                        Modifier
                    } else {
                        Modifier.pointerInput(track.id, index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragStartIndex = latestIndex
                                    dragCurrentIndex = latestIndex
                                    draggedDistance = 0f
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                },
                                onDragEnd = {
                                    if (dragStartIndex >= 0 &&
                                        dragCurrentIndex >= 0 &&
                                        dragStartIndex != dragCurrentIndex
                                    ) {
                                        onMoveQueueItem(dragStartIndex, dragCurrentIndex)
                                    }
                                    dragStartIndex = -1
                                    dragCurrentIndex = -1
                                    draggedDistance = 0f
                                },
                                onDragCancel = {
                                    localQueue = queue
                                    dragStartIndex = -1
                                    dragCurrentIndex = -1
                                    draggedDistance = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    draggedDistance += amount.y
                                    val rowHeightPx = 60.dp.toPx()
                                    val rowShift = (draggedDistance / rowHeightPx).toInt()
                                    if (rowShift != 0) {
                                        val newIndex = (dragCurrentIndex + rowShift)
                                            .coerceIn(0, localQueue.lastIndex)
                                        if (newIndex != dragCurrentIndex) {
                                            localQueue = localQueue.toMutableList().apply {
                                                add(newIndex, removeAt(dragCurrentIndex))
                                            }
                                            dragCurrentIndex = newIndex
                                            draggedDistance -= rowShift * rowHeightPx
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (isCurrent) Modifier.glassEffect(RoundedCornerShape(10.dp)) else Modifier
                            )
                            .background(
                                if (isCurrent) com.tigerplayer.ui.theme.TigerNeonOrange.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
                            )
                            .then(swipeModifier)
                            .clickable { onTrackTapped(track) }
                            .semantics {
                                contentDescription =
                                    "Queue item ${index + 1} of ${localQueue.size}, ${track.title} by ${track.artist}. Swipe left to remove."
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag(CoverScreenTestTags.queueItem(track.id, index)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = if (shuffleModeEnabled) {
                                "Reordering unavailable while shuffle is on"
                            } else {
                                "Long press and drag to reorder"
                            },
                            modifier = Modifier
                                .then(dragModifier)
                                .testTag(CoverScreenTestTags.queueDragHandle(index)),
                            tint = if (isDragging) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }

    if (pendingRemovalIndex >= 0) {
        val pendingTrack = queue.getOrNull(pendingRemovalIndex)
        if (pendingTrack != null) {
            AlertDialog(
                onDismissRequest = { pendingRemovalIndex = -1 },
                title = { Text("Remove from queue?") },
                text = { Text("Remove ${pendingTrack.title} from the queue?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val currentPosition = queue.indexOfFirst { it.id == pendingTrack.id }
                            if (currentPosition >= 0) onRemoveFromQueue(currentPosition)
                            pendingRemovalIndex = -1
                        },
                        modifier = Modifier.testTag(CoverScreenTestTags.QUEUE_REMOVE_CONFIRM)
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRemovalIndex = -1 }) {
                        Text("Cancel")
                    }
                },
                modifier = Modifier.testTag(CoverScreenTestTags.QUEUE_REMOVE_DIALOG)
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear queue?") },
            text = { Text("Remove all ${queue.size} tracks from the queue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearQueue()
                        confirmClear = false
                    },
                    modifier = Modifier.testTag(CoverScreenTestTags.QUEUE_CLEAR_CONFIRM)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag(CoverScreenTestTags.QUEUE_CLEAR_DIALOG)
        )
    }
}
