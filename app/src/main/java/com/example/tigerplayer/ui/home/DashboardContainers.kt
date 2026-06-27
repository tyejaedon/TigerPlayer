package com.example.tigerplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.theme.TigerCyberCyan
import com.example.tigerplayer.ui.theme.TigerHotPink
import com.example.tigerplayer.ui.theme.glassEffect

private enum class PlaylistContainerType {
    DAYLIST,
    VAULT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContainersSection(
    state: DashboardUiState,
    onTrackClick: (List<AudioTrack>, Int) -> Unit,
    onRefresh: () -> Unit
) {
    var selectedContainer by remember { mutableStateOf<PlaylistContainerType?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FOR YOU HUB",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
            TextButton(onClick = onRefresh) {
                Text(text = "Refresh")
            }
        }

        SmartContainerCard(
            title = "NEON DAYLIST (${state.segment.name})",
            subtitle = "Curated by your current listening window",
            tracks = state.neonDaylist,
            statusMessage = buildContainerMessage(
                stale = state.daylistIsStale,
                fallbackMessage = state.daylistMessage
            ),
            accent = TigerCyberCyan,
            icon = Icons.Outlined.QueueMusic,
            onClick = { selectedContainer = PlaylistContainerType.DAYLIST }
        )
        Spacer(modifier = Modifier.height(16.dp))

        SmartContainerCard(
            title = "THE VAULT",
            subtitle = "Rediscover deep cuts and hidden repeats",
            tracks = state.vaultTracks,
            statusMessage = buildContainerMessage(
                stale = state.vaultIsStale,
                fallbackMessage = state.vaultMessage
            ),
            accent = TigerHotPink,
            icon = Icons.Outlined.QueueMusic,
            onClick = { selectedContainer = PlaylistContainerType.VAULT }
        )
    }

    val selectedTracks = when (selectedContainer) {
        PlaylistContainerType.DAYLIST -> state.neonDaylist
        PlaylistContainerType.VAULT -> state.vaultTracks
        null -> emptyList()
    }
    val selectedTitle = when (selectedContainer) {
        PlaylistContainerType.DAYLIST -> "Neon Daylist"
        PlaylistContainerType.VAULT -> "The Vault"
        null -> ""
    }

    if (selectedContainer != null) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { selectedContainer = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PlaylistSheetContent(
                title = selectedTitle,
                tracks = selectedTracks,
                onTrackClick = { index, _ ->
                    onTrackClick(selectedTracks, index)
                    selectedContainer = null
                }
            )
        }
    }
}

private fun buildContainerMessage(stale: Boolean, fallbackMessage: String?): String? {
    return when {
        stale -> "Needs refresh. Pull a fresh generation."
        !fallbackMessage.isNullOrBlank() -> fallbackMessage
        else -> null
    }
}

@Composable
private fun SmartContainerCard(
    title: String,
    subtitle: String,
    tracks: List<AudioTrack>,
    statusMessage: String?,
    accent: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .glassEffect(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${tracks.size} songs",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (tracks.isEmpty()) {
            Text(
                text = statusMessage ?: "Building your container...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            return
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tracks.take(4).forEach { track ->
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }

        Text(
            text = statusMessage ?: "Tap to open playlist",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun PlaylistSheetContent(
    title: String,
    tracks: List<AudioTrack>,
    onTrackClick: (Int, AudioTrack) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tracks.size, key = { index -> "${tracks[index].id}_$index" }) { index ->
            val track = tracks[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .clickable { onTrackClick(index, track) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
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
                Button(
                    onClick = { onTrackClick(index, track) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Play")
                }
            }
        }
    }
}

