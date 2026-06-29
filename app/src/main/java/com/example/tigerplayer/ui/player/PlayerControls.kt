package com.example.tigerplayer.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.igniRed

@Composable
fun PlaybackControls(
    uiState: PlayerUiState,
    onShuffleToggle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onRepeatToggle: () -> Unit,
    textColor: Color
) {
    val isPlaying = uiState.isPlaying
    val aardBlue = MaterialTheme.aardBlue
    val igniRed = MaterialTheme.igniRed

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShuffleToggle) {
                Icon(
                    imageVector = WitcherIcons.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (uiState.isShuffleEnabled) aardBlue else textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(WitcherIcons.Previous, "Previous", modifier = Modifier.size(36.dp), tint = textColor)
            }
        }

        val infiniteTransition = rememberInfiniteTransition(label = "PulseCore")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = if (isPlaying) 1.12f else 1.0f,
            animationSpec = infiniteRepeatable(tween(if (isPlaying) 800 else 2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "Pulse"
        )
        val coreColor by animateColorAsState(targetValue = if (isPlaying) igniRed else aardBlue, animationSpec = tween(600, easing = FastOutSlowInEasing), label = "CoreColor")

        Box(
            modifier = Modifier
                .size(84.dp)
                .shadow(elevation = (16.dp * pulseScale), shape = CircleShape, spotColor = coreColor.copy(alpha = 0.8f), ambientColor = Color.Transparent)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            coreColor.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(0.6f), Color.White.copy(0.1f))),
                    CircleShape
                )
                .bounceClick { onPlayPauseToggle() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(targetState = isPlaying, transitionSpec = { (scaleIn(tween(400)) + fadeIn()).togetherWith(scaleOut(tween(400)) + fadeOut()) }, label = "PlayPauseAnim") { playing ->
                Icon(
                    imageVector = if (playing) WitcherIcons.Pause else WitcherIcons.Play,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(WitcherIcons.Next, "Next", modifier = Modifier.size(36.dp), tint = textColor)
            }
            IconButton(onClick = onRepeatToggle) {
                Icon(
                    imageVector = when (uiState.repeatMode) { Player.REPEAT_MODE_ONE -> WitcherIcons.RepeatOne else -> WitcherIcons.Repeat },
                    contentDescription = "Repeat",
                    tint = if (uiState.repeatMode != Player.REPEAT_MODE_OFF) aardBlue else textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
