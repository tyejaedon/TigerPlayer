package com.example.tigerplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.theme.PremiumGlassCard
import com.example.tigerplayer.ui.theme.TigerCyberCyan
import com.example.tigerplayer.ui.theme.TigerHotPink
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.tigerGlow

@Composable
fun DashboardContainersSection(
    state: DashboardUiState,
    onTrackClick: (AudioTrack) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "FOR YOU HUB",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
        )
        SmartContainerRow(
            title = "NEON DAYLIST (${state.segment.name})",
            tracks = state.neonDaylist,
            accent = TigerCyberCyan,
            onTrackClick = onTrackClick,
            onHeaderClick = onRefresh
        )
        Spacer(modifier = Modifier.height(16.dp))
        SmartContainerRow(
            title = "THE VAULT",
            tracks = state.vaultTracks,
            accent = TigerHotPink,
            onTrackClick = onTrackClick,
            onHeaderClick = onRefresh
        )
    }
}

@Composable
private fun SmartContainerRow(
    title: String,
    tracks: List<AudioTrack>,
    accent: Color,
    onTrackClick: (AudioTrack) -> Unit,
    onHeaderClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = accent,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 6.dp)
                .bounceClick(onHeaderClick)
        )

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .glassEffect(RoundedCornerShape(16.dp))
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Building your container...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        } else {
            val displayTracks = tracks.take(15)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    count = displayTracks.size,
                    key = { index -> displayTracks[index].id }
                ) { index ->
                    val track = displayTracks[index]
                    SmartTrackCard(track = track, accent = accent, onClick = { onTrackClick(track) })
                }
            }
        }
    }
}

@Composable
private fun SmartTrackCard(
    track: AudioTrack,
    accent: Color,
    onClick: () -> Unit
) {
    PremiumGlassCard(
        modifier = Modifier
            .size(width = 148.dp, height = 208.dp)
            .shadow(14.dp, RoundedCornerShape(18.dp), spotColor = accent.copy(alpha = 0.35f))
            .bounceClick(onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.24f))
                .padding(10.dp)
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .tigerGlow(color = accent)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

