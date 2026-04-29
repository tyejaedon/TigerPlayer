package com.example.tigerplayer.ui.player

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.igniRed

@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    onExpandClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val track = uiState.currentTrack ?: return
    val isPlaying = uiState.isPlaying

    val duration = track.durationMs.coerceAtLeast(1L)
    val progressValue = (uiState.currentPosition.toFloat() / duration)
    val actualProgress = if (progressValue.isNaN()) 0f else progressValue.coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = actualProgress,
        animationSpec = tween(500, easing = LinearEasing),
        label = "MiniProgress"
    )

    val actionColor by animateColorAsState(
        targetValue = if (isPlaying) MaterialTheme.igniRed else MaterialTheme.aardBlue,
        animationSpec = tween(500),
        label = "MiniPlayerActionColor"
    )

    // THE VANGUARD SURFACE: Optimized for One UI 6 corner radii
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp) // Fixed height for a consistent "Anchor" at the bottom
            .clickable { onExpandClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp), // Floating pill effect
        shape = RoundedCornerShape(20.dp), // Matches Flip 5 outer display aesthetic
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.9f),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)) // Specular rim
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // --- 1. THE NEON THREAD (Top Alignment) ---
            // Moved to the top to act as a separator between the list and player
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter)
            ) {
                drawLine(
                    color = actionColor.copy(alpha = 0.1f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = size.height
                )
                drawLine(
                    color = actionColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width * animatedProgress, 0f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- 2. THE CHRONICLE ARTWORK ---
                Box(
                    modifier = Modifier
                        .size(48.dp) // Larger for better visibility on the narrow Flip screen
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    AsyncImage(
                        model = track.artworkUri.takeIf { it != Uri.EMPTY },
                        contentDescription = null,
                        fallback = painterResource(R.drawable.ic_tiger_logo),
                        error = painterResource(R.drawable.ic_tiger_logo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // --- 3. THE SCROLLING DESIGNATION ---
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = (-0.5).sp // Tight kerning for impact
                    )
                    Text(
                        text = track.artist.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = actionColor.copy(alpha = 0.8f), // Secondary text uses current sign color
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // --- 4. TACTICAL CONTROLS ---
                // Optimized hit targets for the narrow S901U/Flip 5 display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp) // Tight spacing for the Flip's narrow width
                ) {
                    // --- 1. THE PRIMARY SIGN (Play/Pause) ---
                    Box(
                        modifier = Modifier
                            .size(48.dp) // Slightly larger hit target for the main action
                            .bounceClick { viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        // Subtle background "Orb" to define the hit zone
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    color = (if (isPlaying) MaterialTheme.igniRed else MaterialTheme.aardBlue).copy(alpha = 0.1f),
                                    shape = CircleShape
                                )
                        )

                        Icon(
                            imageVector = if (isPlaying) WitcherIcons.Pause else WitcherIcons.Play,
                            contentDescription = "Toggle Ritual",
                            tint = if (isPlaying) MaterialTheme.igniRed else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // --- 2. THE MOMENTUM STEP (Next) ---
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .bounceClick { viewModel.skipToNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = WitcherIcons.Next,
                            contentDescription = "Next Chant",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), // Dimmed to prioritize the Play button
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }            }
        }
    }
}