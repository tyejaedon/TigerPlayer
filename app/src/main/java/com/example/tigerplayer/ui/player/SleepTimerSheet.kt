package com.example.tigerplayer.ui.player


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tigerplayer.engine.SleepTimerMode
import com.example.tigerplayer.engine.SleepTimerState
import com.example.tigerplayer.ui.theme.elevatedSurface
import com.example.tigerplayer.ui.theme.glassEffect

private val DURATION_PRESETS_MIN = listOf(5, 15, 30, 45, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onDismiss: () -> Unit,
    onSetDuration: (Int) -> Unit,
    onSetEndOfTrack: () -> Unit,
    onSetEndOfQueue: () -> Unit,
    onCancel: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
                .glassEffect(RoundedCornerShape(28.dp)),
            color = MaterialTheme.elevatedSurface.copy(alpha = 0.72f),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Sleep timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                val activeMode = state.mode.takeIf { it != SleepTimerMode.OFF }
                if (activeMode != null) {
                    val remainingLabel = when (activeMode) {
                        SleepTimerMode.DURATION -> {
                            val totalSec = state.remainingMs / 1000
                            "Stops in %d:%02d".format(totalSec / 60, totalSec % 60)
                        }
                        SleepTimerMode.END_OF_TRACK -> "Stops at end of current track"
                        SleepTimerMode.END_OF_QUEUE -> "Stops at end of queue"
                        SleepTimerMode.OFF -> "" // unreachable via takeIf guard, kept for exhaustiveness
                    }
                    Text(remainingLabel, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onCancel(); onDismiss() }) { Text("Cancel timer") }
                    Spacer(Modifier.height(12.dp))
                }

                Text("Duration", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DURATION_PRESETS_MIN.forEach { minutes ->
                        AssistChip(
                            onClick = { onSetDuration(minutes); onDismiss() },
                            label = { Text("${minutes}m") }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Track boundary", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onSetEndOfTrack(); onDismiss() },
                        label = { Text("End of track") }
                    )
                    AssistChip(
                        onClick = { onSetEndOfQueue(); onDismiss() },
                        label = { Text("End of queue") }
                    )
                }
            }
        }
    }
}
