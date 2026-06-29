package com.example.tigerplayer.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.theme.igniRed

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackInfoCard(
    track: AudioTrack,
    textColor: Color,
    secondaryTextColor: Color,
    showTechnicalInfo: Boolean,
    onToggleTechInfo: (Boolean) -> Unit,
    onToggleLike: () -> Unit
) {
    if (showTechnicalInfo) {
        AlertDialog(
            onDismissRequest = { onToggleTechInfo(false) },
            confirmButton = {
                TextButton(onClick = { onToggleTechInfo(false) }) {
                    Text("CLOSE", color = MaterialTheme.igniRed, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text(
                    "TECHNICAL SPECIFICATIONS",
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TechRow("Path", track.path ?: "Unknown", textColor)
                    TechRow("Sample Rate", "${track.sampleRate} Hz", textColor)
                    TechRow("Bitrate", "${track.bitrate / 1000} kbps", textColor)
                    TechRow("Format", track.mimeType, textColor)
                }
            },
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(24.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = textColor,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 40.dp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = secondaryTextColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }

            val scale by animateFloatAsState(
                targetValue = if (track.isLiked) 1.15f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = 400f
                ),
                label = "likeScale"
            )

            val tint by animateColorAsState(
                targetValue = if (track.isLiked) MaterialTheme.igniRed else textColor.copy(alpha = 0.7f),
                label = "likeColor"
            )

            IconButton(
                onClick = onToggleLike,
                modifier = Modifier.padding(start = 16.dp).size(48.dp)
            ) {
                Icon(
                    imageVector = if (track.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (track.isLiked) "Unlike song" else "Like song",
                    tint = tint,
                    modifier = Modifier.scale(scale)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val format = track.mimeType.substringAfter("/").uppercase()

            MetadataBadge(
                text = if (track.mimeType.contains("flac")) "HI-RES" else format,
                isHighlight = track.mimeType.contains("flac"),
                textColor = textColor,
                onLongClick = { onToggleTechInfo(true) }
            )

            if (track.bitrate > 0) {
                MetadataBadge(
                    text = "${track.bitrate / 1000} KBPS",
                    textColor = textColor,
                    onLongClick = { onToggleTechInfo(true) }
                )
            }

            track.year?.let {
                MetadataBadge(
                    text = it,
                    textColor = textColor,
                    onLongClick = { onToggleTechInfo(true) }
                )
            }
        }
    }
}

@Composable
private fun TechRow(label: String, value: String, textColor: Color) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = textColor, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MetadataBadge(
    text: String,
    isHighlight: Boolean = false,
    textColor: Color,
    onLongClick: () -> Unit = {}
) {
    Surface(
        color = Color.Transparent,
        shape = CircleShape,
        modifier = Modifier
            .combinedClickable(onClick = { }, onLongClick = onLongClick)
            .background(
                Brush.linearGradient(
                    listOf(
                        if (isHighlight) MaterialTheme.igniRed.copy(0.25f) else Color.White.copy(0.15f),
                        if (isHighlight) MaterialTheme.igniRed.copy(0.1f) else Color.White.copy(0.05f)
                    )
                ),
                CircleShape
            )
            .border(
                0.5.dp,
                Brush.linearGradient(
                    listOf(
                        if (isHighlight) MaterialTheme.igniRed.copy(0.5f) else Color.White.copy(0.3f),
                        Color.Transparent
                    )
                ),
                CircleShape
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isHighlight) MaterialTheme.igniRed else textColor,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
