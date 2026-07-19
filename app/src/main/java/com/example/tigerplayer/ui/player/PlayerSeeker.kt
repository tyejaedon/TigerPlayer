package com.example.tigerplayer.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.theme.igniRed
import com.example.tigerplayer.ui.theme.aardBlue
import java.util.concurrent.TimeUnit
import kotlin.math.sin

@SuppressLint("AutoboxingStateCreation")
@Composable
fun FieryWavySeeker(
    uiState: PlayerUiState,
    track: AudioTrack,
    onSeek: (Long) -> Unit,
    textColor: Color
) {
    val accentColor = if (uiState.isPlaying) MaterialTheme.igniRed else MaterialTheme.aardBlue

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val duration = track.durationMs.coerceAtLeast(1L)
    val progressValue = uiState.currentPosition.toFloat() / duration
    val actualProgress = progressValue.coerceIn(0f, 1f).takeIf { !it.isNaN() } ?: 0f

    // OPTIMIZATION: Faster linear interpolation for progress, Snap for seeking/dragging
    val animatedProgress by animateFloatAsState(
        targetValue = if (isDragging) dragProgress else actualProgress,
        animationSpec = if (isDragging) {
            snap()
        } else {
            // Short linear animation for snappier feedback without "lag"
            tween(200, easing = LinearEasing)
        },
        label = "SeekerAnim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "Wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "Phase"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(Unit) {
                    var widthPx = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            widthPx = size.width.toFloat()
                            dragProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeek((dragProgress * duration).toLong())
                        },
                        onDragCancel = { isDragging = false }
                    ) { change, _ ->
                        change.consume()
                        if (widthPx <= 0f) widthPx = size.width.toFloat()
                        dragProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2
                val progressX = size.width * animatedProgress

                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val path = Path()
                val amp = if (uiState.isPlaying) 12f else 0f
                path.moveTo(0f, centerY)

                val endX = progressX.toInt().coerceAtMost(size.width.toInt())
                for (x in 0..endX step 5) {
                    path.lineTo(x.toFloat(), centerY + (amp * sin(x * 0.05f + phase)))
                }

                drawPath(path = path, color = accentColor, style = Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round
                ))
                drawCircle(
                    color = accentColor, 
                    radius = 6.dp.toPx(), 
                    center = Offset(progressX, centerY + (amp * sin(progressX * 0.05f + phase)))
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatDuration(if (isDragging) (dragProgress * duration).toLong() else uiState.currentPosition),
                color = textColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = formatDuration(duration),
                color = textColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@SuppressLint("DefaultLocale")
fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
