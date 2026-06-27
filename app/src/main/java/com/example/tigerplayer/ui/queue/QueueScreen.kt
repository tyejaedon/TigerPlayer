package com.example.tigerplayer.ui.queue

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.TigerNeonOrange
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.tigerGlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun QueueScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val queue by viewModel.queueState.collectAsStateWithLifecycle()
    val currentTrackId by viewModel.currentQueueTrackId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.queueIsPlaying.collectAsStateWithLifecycle()

    QueueScreenContent(
        queue = queue,
        currentTrackId = currentTrackId,
        isPlaying = isPlaying,
        onPlayQueueItem = viewModel::playQueueItem,
        onRemoveFromQueue = viewModel::removeFromQueue,
        onMoveQueueItem = viewModel::moveQueueItem,
        modifier = modifier
    )
}

@Composable
internal fun QueueScreenContent(
    queue: List<AudioTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveFromQueue: (AudioTrack) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val vanguardVibration = remember(context) { VanguardQueueVibration(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val itemHeightPx = with(LocalDensity.current) { 76.dp.toPx() }
    val reorderStepThresholdPx = itemHeightPx * 0.55f
    var lastAutoScrollTs by remember { mutableLongStateOf(0L) }
    var lastHapticTickTs by remember { mutableLongStateOf(0L) }

    if (queue.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Queue is empty",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val currentIndex by remember(queue, currentTrackId) {
        derivedStateOf {
            queue.indexOfFirst { it.id == currentTrackId }
                .takeIf { it >= 0 }
                ?: 0
        }
    }
    val nowPlaying by remember(queue, currentIndex) {
        derivedStateOf { queue.getOrNull(currentIndex) }
    }
    val upcoming by remember(queue, currentIndex) {
        derivedStateOf { queue.drop((currentIndex + 1).coerceAtMost(queue.size)) }
    }

    var localUpcoming by remember(upcoming) { mutableStateOf(upcoming) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragCurrentIndex by remember { mutableIntStateOf(-1) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }
    val dropTargetIndex by remember(
        dragStartIndex,
        dragCurrentIndex,
        draggedDistance,
        reorderStepThresholdPx,
        localUpcoming.size
    ) {
        derivedStateOf {
            if (dragStartIndex == -1 || dragCurrentIndex == -1) {
                -1
            } else {
                when {
                    draggedDistance > reorderStepThresholdPx * 0.28f -> {
                        (dragCurrentIndex + 1).coerceAtMost(localUpcoming.size)
                    }

                    draggedDistance < -reorderStepThresholdPx * 0.28f -> {
                        dragCurrentIndex.coerceAtLeast(0)
                    }

                    else -> {
                        dragCurrentIndex.coerceAtLeast(0)
                    }
                }
            }
        }
    }

    LaunchedEffect(upcoming) {
        if (dragStartIndex == -1) {
            localUpcoming = upcoming
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(QueueTestTags.SCREEN)
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        Text(
            text = "Queue",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(12.dp))

        nowPlaying?.let {
            NowPlayingHeader(
                track = it,
                isPlaying = isPlaying,
                onClick = {
                    onPlayQueueItem(currentIndex)
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(8.dp))

        if (localUpcoming.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No upcoming tracks",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                itemsIndexed(localUpcoming, key = { index, track -> "${track.id}_${currentIndex + 1 + index}" }) { index, track ->
                    val isDragging = dragCurrentIndex == index && dragStartIndex != -1
                    val neighborDisplacement = if (dragStartIndex != -1 && !isDragging) {
                        when (index) {
                            dragCurrentIndex - 1 -> -1
                            dragCurrentIndex + 1 -> 1
                            else -> 0
                        }
                    } else {
                        0
                    }

                    Column {
                        if (dropTargetIndex == index) {
                            QueueDropTargetIndicator()
                        }
                        QueueRow(
                            modifier = Modifier.animateItem(),
                            rowTestTag = QueueTestTags.row(index),
                            dragHandleTestTag = QueueTestTags.dragHandle(index),
                            track = track,
                            isDragging = isDragging,
                            neighborDisplacement = neighborDisplacement,
                            onClick = {
                                val absoluteIndex = currentIndex + 1 + index
                                onPlayQueueItem(absoluteIndex)
                                localUpcoming = localUpcoming.drop((index + 1).coerceAtMost(localUpcoming.size))
                            },
                            onRemove = {
                                if (index in localUpcoming.indices) {
                                    val mutable = localUpcoming.toMutableList()
                                    mutable.removeAt(index)
                                    localUpcoming = mutable
                                }
                                onRemoveFromQueue(track)
                            },
                            dragHandleModifier = Modifier.pointerInput(index, localUpcoming.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragStartIndex = index
                                        dragCurrentIndex = index
                                        draggedDistance = 0f
                                        lastHapticTickTs = 0L
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vanguardVibration.onDragStart()
                                    },
                                    onDragEnd = {
                                        if (dragStartIndex != -1 && dragCurrentIndex != -1 && dragStartIndex != dragCurrentIndex) {
                                            val from = currentIndex + 1 + dragStartIndex
                                            val to = currentIndex + 1 + dragCurrentIndex
                                            onMoveQueueItem(from, to)
                                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        }
                                        vanguardVibration.onDragEnd()
                                        dragStartIndex = -1
                                        dragCurrentIndex = -1
                                        draggedDistance = 0f
                                    },
                                    onDragCancel = {
                                        localUpcoming = upcoming
                                        dragStartIndex = -1
                                        dragCurrentIndex = -1
                                        draggedDistance = 0f
                                        vanguardVibration.onDragCancel()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        draggedDistance += dragAmount.y
                                        val now = System.currentTimeMillis()

                                        val direction = when {
                                            draggedDistance > reorderStepThresholdPx -> 1
                                            draggedDistance < -reorderStepThresholdPx -> -1
                                            else -> 0
                                        }

                                        if (direction != 0) {
                                            val targetIndex = (dragCurrentIndex + direction)
                                                .coerceIn(0, localUpcoming.lastIndex)
                                            if (targetIndex != dragCurrentIndex) {
                                                val mutable = localUpcoming.toMutableList()
                                                val movedItem = mutable.removeAt(dragCurrentIndex)
                                                mutable.add(targetIndex, movedItem)
                                                localUpcoming = mutable
                                                dragCurrentIndex = targetIndex
                                                draggedDistance -= direction * reorderStepThresholdPx
                                                if (now - lastHapticTickTs >= 70L) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    vanguardVibration.onIndexCrossed()
                                                    lastHapticTickTs = now
                                                }
                                            } else {
                                                draggedDistance = 0f
                                            }
                                        }

                                        if (now - lastAutoScrollTs >= 42L) {
                                            val visible = listState.layoutInfo.visibleItemsInfo
                                            val firstVisible = visible.firstOrNull()?.index ?: 0
                                            val lastVisible = visible.lastOrNull()?.index ?: 0
                                            val nearTop = dragCurrentIndex <= (firstVisible + 1)
                                            val nearBottom = dragCurrentIndex >= (lastVisible - 1)
                                            val dragMagnitude = dragAmount.y.absoluteValue

                                            when {
                                                nearTop && dragAmount.y < 0f -> {
                                                    lastAutoScrollTs = now
                                                    scope.launch {
                                                        listState.scrollBy((-24f - dragMagnitude).coerceAtLeast(-56f))
                                                    }
                                                }

                                                nearBottom && dragAmount.y > 0f -> {
                                                    lastAutoScrollTs = now
                                                    scope.launch {
                                                        listState.scrollBy((24f + dragMagnitude).coerceAtMost(56f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            },
                        )
                    }
                }

                if (dropTargetIndex == localUpcoming.size && localUpcoming.isNotEmpty()) {
                    item(key = "queue_drop_target_end") {
                        QueueDropTargetIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueDropTargetIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .height(2.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                shape = RoundedCornerShape(999.dp)
            )
    )
}

@Composable
private fun NowPlayingHeader(
    track: AudioTrack,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (isPlaying) WitcherIcons.VolumeUp else WitcherIcons.Play,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun QueueRow(
    modifier: Modifier = Modifier,
    rowTestTag: String,
    dragHandleTestTag: String,
    track: AudioTrack,
    isDragging: Boolean,
    neighborDisplacement: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier
) {
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "QueueRowDragScale"
    )
    val dragElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "QueueRowDragElevation"
    )
    val neighborOffsetY by animateDpAsState(
        targetValue = when (neighborDisplacement) {
            -1 -> (-5).dp
            1 -> 5.dp
            else -> 0.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "QueueRowNeighborOffset"
    )
    val handleTint by animateColorAsState(
        targetValue = if (isDragging) {
            TigerNeonOrange
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "QueueRowHandleTint"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(rowTestTag)
            .vanguardLift(isDragging = isDragging, liftScale = dragScale, liftElevation = dragElevation)
            .vanguardDragGlow(isDragging = isDragging)
            .offset { IntOffset(x = 0, y = neighborOffsetY.roundToPx()) }
            .clickable(enabled = !isDragging, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isDragging) {
            TigerNeonOrange.copy(alpha = 0.20f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = WitcherIcons.Menu,
                contentDescription = "Reorder queue",
                tint = handleTint,
                modifier = dragHandleModifier
                    .testTag(dragHandleTestTag)
                    .background(
                        color = if (isDragging) TigerNeonOrange.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = WitcherIcons.Close,
                    contentDescription = "Remove from queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Modifier.vanguardLift(
    isDragging: Boolean,
    liftScale: Float,
    liftElevation: androidx.compose.ui.unit.Dp
): Modifier {
    return this
        .shadow(liftElevation, RoundedCornerShape(16.dp), clip = false)
        .graphicsLayer {
            scaleX = liftScale
            scaleY = liftScale
            alpha = if (isDragging) 1f else 0.98f
        }
}

@Composable
private fun Modifier.vanguardDragGlow(isDragging: Boolean): Modifier {
    return if (isDragging) {
        this.tigerGlow(TigerNeonOrange.copy(alpha = 0.36f))
    } else {
        this
    }
}

private class VanguardQueueVibration(context: Context) {
    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .build()

    private val vibratorManager: VibratorManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)
    } else {
        null
    }

    @Suppress("DEPRECATION")
    private val legacyVibrator: Vibrator? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } else {
        null
    }

    fun onDragStart() = vibrate(strongEffect())

    fun onDragEnd() = vibrate(strongEffect())

    fun onDragCancel() = vibrate(softEffect())

    fun onIndexCrossed() = vibrate(tickEffect())

    private fun tickEffect(): VibrationEffect {
        return VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    }

    private fun strongEffect(): VibrationEffect {
        return VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    }

    private fun softEffect(): VibrationEffect {
        return VibrationEffect.createOneShot(12L, 120)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
        } else {
            legacyVibrator?.vibrate(effect, audioAttributes)
        }
    }
}

