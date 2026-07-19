package com.example.tigerplayer.ui.player

import android.graphics.fonts.FontStyle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.theme.TigerCyberCyan
import com.example.tigerplayer.ui.theme.igniRed
import com.example.tigerplayer.utils.BluetoothDeviceInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackInfoCard(
    track: AudioTrack,
    textColor: Color,
    secondaryTextColor: Color,
    showTechnicalInfo: Boolean,
    bluetoothDevice: BluetoothDeviceInfo = BluetoothDeviceInfo(),
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TechRow("Path", track.path ?: "Unknown", textColor)
                    TechRow("Sample Rate", "${track.sampleRate} Hz", textColor)
                    TechRow("Bitrate", "${track.bitrate / 1000} kbps", textColor)
                    TechRow("Format", track.mimeType, textColor)
                    
                    if (bluetoothDevice.isConnected) {
                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            color = textColor.copy(alpha = 0.1f)
                        )
                        TechRow("Output Device", bluetoothDevice.name, TigerCyberCyan)
                        TechRow("Audio Codec", bluetoothDevice.codec, TigerCyberCyan)
                        TechRow("Battery", if (bluetoothDevice.batteryLevel >= 0) "${bluetoothDevice.batteryLevel}%" else "Unknown", TigerCyberCyan)
                        val totalSecs = bluetoothDevice.listeningTimeMs / 1000
                        val hours = totalSecs / 3600
                        val minutes = (totalSecs % 3600) / 60
                        val seconds = totalSecs % 60
                        TechRow("Total Listening Time", String.format("%02d:%02d:%02d", hours, minutes, seconds), TigerCyberCyan)
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {



                Column(modifier = Modifier.weight(1f,true)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
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
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    if (track.album.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.album,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }

                IconButton(
                    onClick = { onToggleTechInfo(true) },
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Audio details",
                        tint = Color.White
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

                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (track.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (track.isLiked) "Unlike song" else "Like song",
                        tint = if(track.isLiked) Color.Red else Color.White,
                        modifier = Modifier.scale(scale)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val format = track.mimeType.substringAfter("/").uppercase()

                MetadataBadge(
                    text = if (track.mimeType.contains("flac")) "HI-RES" else format,
                    isHighlight = track.mimeType.contains("flac"),
                    textColor = Color.White,
                    onLongClick = { onToggleTechInfo(true) }
                )

                if (track.bitrate > 0) {
                    MetadataBadge(
                        text = "${track.bitrate / 1000} KBPS",
                        textColor = Color.White,
                        onLongClick = { onToggleTechInfo(true) }
                    )
                }

                track.year?.let {
                    MetadataBadge(
                        text = it,
                        textColor = Color.White,
                        onLongClick = { onToggleTechInfo(true) }
                    )
                }

                if (bluetoothDevice.isConnected) {
                    MetadataBadge(
                        text = bluetoothDevice.name.uppercase(),
                        isHighlight = true,
                        highlightColor = TigerCyberCyan,
                        textColor = Color.Black,
                        onLongClick = { onToggleTechInfo(true) }
                    )
                }
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
    highlightColor: Color = MaterialTheme.igniRed,
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
                        if (isHighlight) highlightColor.copy(0.25f) else Color.White.copy(0.15f),
                        if (isHighlight) highlightColor.copy(0.1f) else Color.White.copy(0.05f)
                    )
                ),
                CircleShape
            )
            .border(
                0.5.dp,
                Brush.linearGradient(
                    listOf(
                        if (isHighlight) highlightColor.copy(0.5f) else Color.White.copy(0.3f),
                        Color.Transparent
                    )
                ),
                CircleShape
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isHighlight) highlightColor else textColor,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
