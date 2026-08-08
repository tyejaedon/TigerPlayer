package com.example.tigerplayer.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.dashboard.DashboardViewModel
import com.example.tigerplayer.ui.player.PlayerViewModel
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaylistDetailScreen(
    viewModel: PlayerViewModel,
    originTag: String,
    onBackClick: () -> Unit,
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val tracks by dashboardViewModel.daylistTracks.collectAsStateWithLifecycle()
    val descriptor = rememberDayPhaseDescriptor()

    val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }
    val leadArtist = remember(tracks) {
        tracks.groupingBy { it.artist }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "Unknown"
    }

    val chronoGradient = remember(descriptor) {
        Brush.linearGradient(
            listOf(descriptor.accent.copy(alpha = 0.45f), descriptor.mist, Color.Transparent)
        )
    }

    var entered by remember(originTag) { mutableStateOf(false) }
    LaunchedEffect(originTag) {
        entered = true
    }
    val heroScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.86f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "DaylistHeroScale"
    )
    val heroAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "DaylistHeroAlpha"
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DAYLIST PORTAL",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = descriptor.label,
                            color = descriptor.accent,
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090B12))
                .background(chronoGradient)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 10.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ChronoHero(
                        descriptor = descriptor,
                        trackCount = tracks.size,
                        totalDurationMs = totalDurationMs,
                        leadArtist = leadArtist,
                        modifier = Modifier.graphicsLayer {
                            scaleX = heroScale
                            scaleY = heroScale
                            alpha = heroAlpha
                            translationY = (1f - heroAlpha) * 90f
                        }
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = heroAlpha
                                translationY = (1f - heroAlpha) * 46f
                            },
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { playCurationAt(viewModel, tracks, index = 0) },
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = descriptor.accent,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play Arc", fontWeight = FontWeight.Black)
                        }

                        OutlinedButton(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    playCurationShuffled(viewModel, tracks)
                                }
                            },
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, descriptor.accent.copy(alpha = 0.7f))
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = descriptor.accent)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle Tide", color = Color.White)
                        }
                    }
                }

                if (tracks.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White.copy(alpha = 0.06f)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "No daylist generated yet.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Play a few tracks in this time bucket and this portal will self-curate.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "TIME-SYNCED SEQUENCE",
                            style = MaterialTheme.typography.labelLarge,
                            color = descriptor.accent,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    }

                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        DaylistTrackRow(
                            index = index,
                            track = track,
                            accent = descriptor.accent,
                            onClick = { playCurationAt(viewModel, tracks, index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChronoHero(
    descriptor: DayPhaseDescriptor,
    trackCount: Int,
    totalDurationMs: Long,
    leadArtist: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.06f),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(descriptor.accent.copy(alpha = 0.25f))
                        .border(1.dp, descriptor.accent.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = descriptor.accent
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = descriptor.headline,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = descriptor.subtitle,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("$trackCount tracks") })
                AssistChip(onClick = {}, label = { Text(formatMinutes(totalDurationMs)) })
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "Lead: $leadArtist",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DaylistTrackRow(
    index: Int,
    track: AudioTrack,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.04f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = (index + 1).toString(),
                    color = accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black
                )
                Spacer(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(accent.copy(alpha = 0.35f))
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    color = Color.White.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatDuration(track.durationMs),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    HorizontalDivider(
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

private data class DayPhaseDescriptor(
    val label: String,
    val headline: String,
    val subtitle: String,
    val accent: Color,
    val mist: Color
)

@Composable
private fun rememberDayPhaseDescriptor(): DayPhaseDescriptor {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return remember(hour) {
        when (hour) {
            in 5..11 -> DayPhaseDescriptor(
                label = "MORNING WAVE",
                headline = "Sunrise Kinetic Arc",
                subtitle = "Brighter rhythms to start momentum.",
                accent = Color(0xFFFFC857),
                mist = Color(0xFF56361B)
            )
            in 12..16 -> DayPhaseDescriptor(
                label = "AFTERNOON FLOW",
                headline = "Midday Signal Drift",
                subtitle = "Steady grooves to keep focus locked.",
                accent = Color(0xFF4FC3F7),
                mist = Color(0xFF1C3140)
            )
            in 17..21 -> DayPhaseDescriptor(
                label = "EVENING GLOW",
                headline = "Twilight Pulse Network",
                subtitle = "Warm textures for sunset sessions.",
                accent = Color(0xFFFF7A59),
                mist = Color(0xFF3A1F25)
            )
            else -> DayPhaseDescriptor(
                label = "NIGHT ECHO",
                headline = "Afterdark Resonance Grid",
                subtitle = "Low-light atmospheres and deep bass lanes.",
                accent = Color(0xFFB388FF),
                mist = Color(0xFF231C37)
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = max(0L, durationMs / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun formatMinutes(durationMs: Long): String {
    val minutes = max(1L, durationMs / 60_000L)
    return "$minutes min"
}

private fun playCurationAt(viewModel: PlayerViewModel, tracks: List<AudioTrack>, index: Int) {
    if (tracks.isEmpty()) return
    val safeIndex = index.coerceIn(0, tracks.lastIndex)
    val target = tracks[safeIndex]

    if (target.id.startsWith("spotify:")) {
        // Spotify App Remote does not support loading the local Media3 queue.
        viewModel.playTrack(target)
    } else {
        viewModel.setPlaylistAndPlay(tracks, safeIndex)
    }
}

private fun playCurationShuffled(viewModel: PlayerViewModel, tracks: List<AudioTrack>) {
    if (tracks.isEmpty()) return
    if (tracks.first().id.startsWith("spotify:")) {
        viewModel.playTrack(tracks.shuffled().first())
    } else {
        viewModel.setPlaylistAndPlay(tracks.shuffled(), 0)
    }
}

