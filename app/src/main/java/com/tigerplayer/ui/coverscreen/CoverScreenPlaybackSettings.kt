package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.tigerplayer.data.local.CoverScreenEqPreset
import com.tigerplayer.ui.theme.TigerNeonOrange

@Composable
fun CoverScreenPlaybackSettings(
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    volume: Float,
    selectedEqPreset: String,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onAdjustVolume: (Float) -> Unit,
    onSelectEqPreset: (CoverScreenEqPreset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnAdjustVolume by rememberUpdatedState(onAdjustVolume)
    val volumeStepPx = with(LocalDensity.current) { 24.dp.toPx() }
    val normalizedVolume = volume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
    val selectedPreset = CoverScreenEqPreset.entries.firstOrNull {
        it.displayName == selectedEqPreset
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.76f))
            .clickable(onClick = onClose)
            .testTag(CoverScreenTestTags.PLAYBACK_SETTINGS)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .testTag(CoverScreenTestTags.PLAYBACK_SETTINGS_PANEL),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF17181D),
            contentColor = Color.White,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playback settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag(CoverScreenTestTags.PLAYBACK_SETTINGS_CLOSE)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close playback settings")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(CoverScreenTestTags.PLAYBACK_SHUFFLE)
                    ) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = if (isShuffleEnabled) TigerNeonOrange else Color.White
                        )
                        Text(if (isShuffleEnabled) "Shuffle on" else "Shuffle off")
                    }
                    FilledTonalButton(
                        onClick = onCycleRepeat,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(CoverScreenTestTags.PLAYBACK_REPEAT)
                    ) {
                        Icon(
                            imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                                Icons.Rounded.RepeatOne
                            } else {
                                Icons.Rounded.Repeat
                            },
                            contentDescription = null,
                            tint = if (repeatMode == Player.REPEAT_MODE_OFF) {
                                Color.White
                            } else {
                                TigerNeonOrange
                            }
                        )
                        Text(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> "Repeat one"
                                Player.REPEAT_MODE_ALL -> "Repeat all"
                                else -> "Repeat off"
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pointerInput(volumeStepPx) {
                                var accumulatedDrag = 0f
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dragAmount ->
                                        accumulatedDrag -= dragAmount
                                        val steps = (accumulatedDrag / volumeStepPx).toInt()
                                        if (steps != 0) {
                                            latestOnAdjustVolume(steps * 0.05f)
                                            accumulatedDrag -= steps * volumeStepPx
                                            change.consume()
                                        }
                                    }
                                )
                            }
                            .semantics {
                                contentDescription =
                                    "Volume ${normalizedVolume.times(100).toInt()} percent. Swipe up or down to adjust."
                            }
                            .testTag(CoverScreenTestTags.PLAYBACK_VOLUME),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                normalizedVolume == 0f -> Icons.AutoMirrored.Rounded.VolumeMute
                                normalizedVolume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                                else -> Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Volume ${normalizedVolume.times(100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge
                        )
                        LinearProgressIndicator(
                            progress = { normalizedVolume },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = TigerNeonOrange,
                            trackColor = Color.White.copy(alpha = 0.16f)
                        )
                        Text(
                            text = "Swipe the speaker icon up or down",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.62f)
                        )
                    }
                }

                Text(
                    text = "Equalizer preset",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CoverScreenEqPreset.entries.forEach { preset ->
                        AssistChip(
                            onClick = { onSelectEqPreset(preset) },
                            label = { Text(preset.displayName) },
                            modifier = Modifier.testTag(
                                CoverScreenTestTags.eqPreset(preset.name)
                            ),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (preset == selectedPreset) {
                                    TigerNeonOrange.copy(alpha = 0.24f)
                                } else {
                                    Color.White.copy(alpha = 0.08f)
                                },
                                labelColor = Color.White
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}
