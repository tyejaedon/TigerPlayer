package com.example.tigerplayer.ui.player

import androidx.compose.ui.platform.testTag
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick

@Composable
fun QueueDisplay(
    queue: List<AudioTrack>,
    currentTrackId: String?,
    currentQueueIndex: Int,
    isPlaying: Boolean,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    dynamicTextColor: Color,
    accentColor: Color,
    onTrackClick: (Int) -> Unit, // Use index for playQueueItem
    onRemoveFromQueue: (Int) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit
) {
    if (queue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "The queue is empty.\nNo shadows follow.",
                color = dynamicTextColor.copy(alpha = 0.4f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleLarge
            )
        }
        return
    }

    // LOCAL STATE FOR OPTIMISTIC UI
    var localQueue by remember(queue) { mutableStateOf(queue) }
    val listState = rememberLazyListState()
    val view = LocalView.current

    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragCurrentIndex by remember { mutableIntStateOf(-1) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }

    // Reconcile optimistic queue immediately after drag settles, even if move fails upstream.
    LaunchedEffect(queue, dragStartIndex) {
        if (dragStartIndex == -1) {
            localQueue = queue
        }
    }

    val currentIndex = remember(currentQueueIndex, localQueue.size, currentTrackId) {
        if (currentQueueIndex in localQueue.indices) {
            currentQueueIndex
        } else {
            localQueue.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0)
        }
    }

    LaunchedEffect(currentTrackId) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex, scrollOffset = -200)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(dynamicTextColor.copy(alpha = 0.08f))
                .border(0.5.dp, dynamicTextColor.copy(0.15f), androidx.compose.foundation.shape.CircleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (shuffleModeEnabled) "CHAOS SEQUENCE" else "ARCHIVE ORDER",
                style = MaterialTheme.typography.labelLarge,
                color = dynamicTextColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shuffleModeEnabled) {
                    Icon(WitcherIcons.Shuffle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Icon(
                    if (repeatMode == 1) WitcherIcons.RepeatOne else WitcherIcons.Repeat,
                    null,
                    tint = if (repeatMode != 0) Color.White else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (shuffleModeEnabled) {
            Text(
                text = "Reorder is disabled while shuffle is enabled",
                style = MaterialTheme.typography.bodySmall,
                color = dynamicTextColor.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .testTag("player_queue_list")
                .semantics { contentDescription = "Player queue list" },
            contentPadding = PaddingValues(top = 8.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                items = localQueue,
                key = { _, track -> track.id }
            ) { index, track ->
                val isActive = index == currentIndex
                val isDragging = dragCurrentIndex == index && dragStartIndex != -1
                val latestIndex by rememberUpdatedState(index)
                val resolvedQueueIndex = remember(queue, track.id, index) {
                    queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: index
                }

                val dragHandleModifier = if (shuffleModeEnabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(track.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragStartIndex = latestIndex
                                dragCurrentIndex = latestIndex
                                draggedDistance = 0f
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            },
                            onDragEnd = {
                                if (dragStartIndex != -1 && dragCurrentIndex != -1 && dragStartIndex != dragCurrentIndex) {
                                    onMoveItem(dragStartIndex, dragCurrentIndex)
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
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggedDistance += dragAmount.y
                                val itemHeight = 72.dp.toPx()
                                val offsetInt = (draggedDistance / itemHeight).toInt()

                                if (offsetInt != 0) {
                                    val newIndex = (dragCurrentIndex + offsetInt).coerceIn(0, localQueue.lastIndex)
                                    if (newIndex != dragCurrentIndex) {
                                        val mutable = localQueue.toMutableList()
                                        val item = mutable.removeAt(dragCurrentIndex)
                                        mutable.add(newIndex, item)
                                        localQueue = mutable
                                        dragCurrentIndex = newIndex
                                        draggedDistance -= (offsetInt * itemHeight)
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                            }
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .testTag("queue_row_${track.id}_$index")
                        .fillMaxWidth()
                        .animateItem()
                        .bounceClick { onTrackClick(resolvedQueueIndex) },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isDragging)
                                    Brush.linearGradient(listOf(accentColor.copy(0.25f), accentColor.copy(0.1f)))
                                else if (isActive)
                                    Brush.linearGradient(listOf(accentColor.copy(0.15f), Color.Transparent))
                                else SolidColor(Color.Transparent)
                            )
                            .border(
                                width = if (isActive || isDragging) 1.dp else 0.dp,
                                brush = if (isDragging) 
                                    SolidColor(accentColor.copy(0.4f))
                                else
                                    Brush.linearGradient(listOf(dynamicTextColor.copy(0.3f), Color.Transparent)),
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(48.dp)) {
                                AsyncImage(
                                    model = track.artworkUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                )
                                if (isActive && isPlaying) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(WitcherIcons.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                                    color = dynamicTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    track.artist.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = dynamicTextColor.copy(alpha = 0.6f),
                                    letterSpacing = 1.sp,
                                    maxLines = 1
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isActive) {
                                    IconButton(onClick = { onRemoveFromQueue(resolvedQueueIndex) }) {
                                        Icon(WitcherIcons.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(dynamicTextColor.copy(alpha = if (isDragging) 0.14f else 0.08f))
                                        .then(dragHandleModifier)
                                        .testTag("drag_handle_$index"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "≡",
                                        color = dynamicTextColor.copy(alpha = if (isDragging) 0.9f else 0.65f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
