package com.example.tigerplayer.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import coil.compose.AsyncImage
import com.example.tigerplayer.data.model.AudioTrack

@Composable
fun QueueScreen(
    onBackClick: () -> Unit,
    viewModel: QueueViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    var localUpcoming by remember(uiState.upcomingTracks) { mutableStateOf(uiState.upcomingTracks) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragCurrentIndex by remember { mutableIntStateOf(-1) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06080C))
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "QUEUE",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }

        uiState.currentTrack?.let { current ->
            Text(
                text = "NOW PLAYING",
                color = Color(0xFF00E5FF),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            QueueTrackRow(
                track = current,
                isPinned = true,
                onClick = { viewModel.playTrackAt(uiState.currentIndex) }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        Text(
            text = if (uiState.isShuffleEnabled) "QUEUE (SHUFFLED)" else "UPCOMING",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        if (uiState.isShuffleEnabled) {
            Text(
                text = "Reorder is disabled while shuffle is enabled",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(localUpcoming, key = { index, track -> "${track.id}#$index" }) { index, track ->
                val isDragging = dragCurrentIndex == index && dragStartIndex != -1
                val latestIndex by rememberUpdatedState(index)

                val dragHandleModifier = if (uiState.isShuffleEnabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(track.id, index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragStartIndex = latestIndex
                                dragCurrentIndex = latestIndex
                                draggedDistance = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                if (dragStartIndex >= 0 && dragCurrentIndex >= 0 && dragStartIndex != dragCurrentIndex) {
                                    viewModel.moveUpcomingItem(dragStartIndex, dragCurrentIndex)
                                }
                                dragStartIndex = -1
                                dragCurrentIndex = -1
                                draggedDistance = 0f
                            },
                            onDragCancel = {
                                localUpcoming = uiState.upcomingTracks
                                dragStartIndex = -1
                                dragCurrentIndex = -1
                                draggedDistance = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggedDistance += dragAmount.y
                                val itemHeight = 84.dp.toPx()
                                val offsetInt = (draggedDistance / itemHeight).toInt()

                                if (offsetInt != 0) {
                                    val newIndex = (dragCurrentIndex + offsetInt).coerceIn(0, localUpcoming.lastIndex)
                                    if (newIndex != dragCurrentIndex) {
                                        val mutable = localUpcoming.toMutableList()
                                        val item = mutable.removeAt(dragCurrentIndex)
                                        mutable.add(newIndex, item)
                                        localUpcoming = mutable
                                        dragCurrentIndex = newIndex
                                        draggedDistance -= offsetInt * itemHeight
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                        )
                    }
                }

                QueueTrackRow(
                    track = track,
                    isPinned = false,
                    isDragging = isDragging,
                    dragHandleModifier = dragHandleModifier,
                    onClick = {
                        val absoluteIndex = if (uiState.currentIndex >= 0) {
                            uiState.currentIndex + index + 1
                        } else {
                            index
                        }
                        viewModel.playTrackAt(absoluteIndex)
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: AudioTrack,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val glowColor = if (isPinned) Color(0xFF39FF14) else Color(0xFF00E5FF)

    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            glowColor.copy(alpha = if (isDragging) 0.18f else 0.12f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                )
                .border(
                    width = if (isDragging) 1.2.dp else 0.7.dp,
                    color = Color.White.copy(alpha = if (isDragging) 0.28f else 0.16f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isPinned) FontWeight.Black else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.uppercase(),
                    color = Color.White.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isPinned) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .then(dragHandleModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text("≡", color = Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

