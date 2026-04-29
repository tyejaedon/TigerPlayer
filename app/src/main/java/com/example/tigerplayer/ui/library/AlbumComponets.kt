package com.example.tigerplayer.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick

// --- VANGUARD CONSTANTS ---


// ==========================================
// --- 1. THE MAIN GRID (Adaptive Layout) ---
// ==========================================

@Composable
fun AlbumGridCard(
    title: String,
    artist: String,
    artworkUri: Any?,
    trackCount: Int,
    modifier: Modifier = Modifier,
    isActive: Boolean = false, // 🔥 NEW: highlight state
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = if (pressed) 0.96f else 1f
                scaleY = if (pressed) 0.96f else 1f
            }
            .bounceClick(
                onClick = onClick,
                onPress = { pressed = true },
                onRelease = { pressed = false }
            ),
        horizontalAlignment = Alignment.Start
    ) {

        // =========================
        // 🎨 COVER ART (UPGRADED)
        // =========================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(
                    elevation = if (isActive) 24.dp else 14.dp,
                    shape = RoundedCornerShape(26.dp),
                    spotColor = if (isActive)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else Color.Black.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(26.dp))
        ) {

            // --- ARTWORK ---
            AsyncImage(
                model = artworkUri,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                error = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier.fillMaxSize()
            )

            // --- DARK GRADIENT (READABILITY FIRST) ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    )
            )

            // --- ACTIVE GLOW ---
            if (isActive) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(26.dp)
                        )
                )
            }

            // --- TRACK COUNT BADGE ---
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$trackCount",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =========================
        // 🧾 METADATA (REFINED)
        // =========================

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


fun Modifier.bounceClick(
    onClick: () -> Unit,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null
): Modifier = composed {

    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "bounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    onPress?.invoke()

                    tryAwaitRelease()

                    isPressed = false
                    onRelease?.invoke()
                    onClick()
                }
            )
        }
}




