package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.tigerplayer.R
import com.tigerplayer.ui.theme.TigerNeonOrange

/**
 * Calculates adaptive visual dimensions for playback control buttons across different cover display
 * widths (200-420 dp band).
 *
 * On narrow screens (~260dp, leaving ~228dp inside 16dp horizontal padding), 5 fixed 48dp buttons (240dp)
 * cause horizontal container overflow. Sizing the visual button circle down to 40dp (or 34dp on ~200dp
 * displays) ensures all 5 buttons and their margins fit comfortably, while each button retains at least
 * a 48x48 dp minimum interactive touch target via proportional weights.
 */
internal fun calculateCoverControlButtonSize(availableWidth: Dp): Dp {
    if (availableWidth <= 0.dp || availableWidth == Dp.Infinity) return 48.dp
    val slotWidth = availableWidth / 5
    return when {
        slotWidth >= 52.dp -> 48.dp
        slotWidth >= 46.dp -> 44.dp
        slotWidth >= 40.dp -> 40.dp
        slotWidth >= 34.dp -> 34.dp
        else -> 30.dp
    }
}

internal fun calculateCoverControlIconSize(buttonSize: Dp): Dp {
    return when {
        buttonSize >= 44.dp -> 24.dp
        buttonSize >= 38.dp -> 20.dp
        else -> 18.dp
    }
}

@Composable
internal fun CoverAccessibleControls(
    isPlaying: Boolean,
    isShuffleEnabled: Boolean = false,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    onToggleShuffle: (() -> Unit)? = null,
    onCycleRepeat: (() -> Unit)? = null,
    onLongPressPlayPause: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: (() -> Unit)? = null,
    onOpenLibrary: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val availableWidth = maxWidth
        val buttonSize = calculateCoverControlButtonSize(availableWidth)
        val iconSize = calculateCoverControlIconSize(buttonSize)

        val previousLabel = stringResource(R.string.coverscreen_action_previous)
        val playLabel = stringResource(R.string.coverscreen_action_play)
        val pauseLabel = stringResource(R.string.coverscreen_action_pause)
        val nextLabel = stringResource(R.string.coverscreen_action_next)
        val queueLabel = stringResource(R.string.coverscreen_action_queue)
        val libraryLabel = stringResource(R.string.coverscreen_action_library)
        val settingsLabel = stringResource(R.string.coverscreen_gesture_long_press_settings)
        val playingState = stringResource(R.string.coverscreen_status_now_playing)
        val pausedState = stringResource(R.string.coverscreen_status_paused)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onToggleShuffle != null) {
                CoverControlButton(
                    icon = Icons.Rounded.Shuffle,
                    description = if (isShuffleEnabled) "Shuffle on" else "Shuffle off",
                    stateDescription = if (isShuffleEnabled) "On" else "Off",
                    onClick = onToggleShuffle,
                    testTag = CoverScreenTestTags.PLAYBACK_SHUFFLE,
                    modifier = Modifier.weight(1f),
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                    iconTint = if (isShuffleEnabled) TigerNeonOrange else Color.White.copy(alpha = 0.5f)
                )
            }
            CoverControlButton(
                icon = Icons.Filled.SkipPrevious,
                description = previousLabel,
                onClickLabel = previousLabel,
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
                buttonSize = buttonSize,
                iconSize = iconSize
            )
            CoverControlButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                description = if (isPlaying) pauseLabel else playLabel,
                stateDescription = if (isPlaying) playingState else pausedState,
                onClickLabel = if (isPlaying) pauseLabel else playLabel,
                onLongClickLabel = settingsLabel,
                onClick = onTogglePlayPause,
                onLongClick = onLongPressPlayPause,
                testTag = CoverScreenTestTags.PLAYBACK_SETTINGS_OPEN,
                modifier = Modifier.weight(1f),
                buttonSize = buttonSize,
                iconSize = iconSize
            )
            CoverControlButton(
                icon = Icons.Filled.SkipNext,
                description = nextLabel,
                onClickLabel = nextLabel,
                onClick = onNext,
                modifier = Modifier.weight(1f),
                buttonSize = buttonSize,
                iconSize = iconSize
            )
            if (onCycleRepeat != null) {
                val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                CoverControlButton(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    description = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        else -> "Repeat off"
                    },
                    stateDescription = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        else -> "Off"
                    },
                    onClick = onCycleRepeat,
                    testTag = CoverScreenTestTags.PLAYBACK_REPEAT,
                    modifier = Modifier.weight(1f),
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                    iconTint = if (isRepeatActive) TigerNeonOrange else Color.White.copy(alpha = 0.5f)
                )
            }
            if (onOpenQueue != null) {
                CoverControlButton(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    description = queueLabel,
                    onClickLabel = queueLabel,
                    onClick = onOpenQueue,
                    modifier = Modifier.weight(1f),
                    buttonSize = buttonSize,
                    iconSize = iconSize
                )
            }
            if (onOpenLibrary != null) {
                CoverControlButton(
                    icon = Icons.Filled.LibraryMusic,
                    description = libraryLabel,
                    onClickLabel = libraryLabel,
                    onClick = onOpenLibrary,
                    testTag = CoverScreenTestTags.LIBRARY_OPEN,
                    modifier = Modifier.weight(1f),
                    buttonSize = buttonSize,
                    iconSize = iconSize
                )
            }
        }
    }
}

@Composable
internal fun CoverControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    testTag: String? = null,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    iconTint: Color = Color.White,
    stateDescription: String? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .minimumInteractiveComponentSize()
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .then(
                if (onLongClick != null) {
                    Modifier
                        .semantics {
                            role = Role.Button
                            if (stateDescription != null) {
                                this.stateDescription = stateDescription
                            }
                            onClick(label = onClickLabel ?: description) {
                                onClick()
                                true
                            }
                            onLongClick(label = onLongClickLabel ?: description) {
                                onLongClick()
                                true
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onClick() },
                                onLongPress = { onLongClick() }
                            )
                        }
                } else {
                    Modifier
                        .semantics {
                            if (stateDescription != null) {
                                this.stateDescription = stateDescription
                            }
                        }
                        .clickable(
                            role = Role.Button,
                            onClickLabel = onClickLabel ?: description,
                            onClick = onClick
                        )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .background(Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
