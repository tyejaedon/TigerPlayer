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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.dashboard.DashboardViewModel
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverWeeklyDetailScreen(
    viewModel: PlayerViewModel,
    originTag: String,
    onBackClick: () -> Unit,
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val tracks by dashboardViewModel.discoveryWeeklyTracks.collectAsStateWithLifecycle()
    val spotlight = remember(tracks) { tracks.take(3) }
    val artistCluster = remember(tracks) {
        tracks.map { it.artist }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
    }

    var entered by remember(originTag) { mutableStateOf(false) }
    LaunchedEffect(originTag) {
        entered = true
    }
    val heroScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.86f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "DiscoverHeroScale"
    )
    val heroAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "DiscoverHeroAlpha"
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DISCOVER WEEKLY",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "ANOMALY FEED",
                            color = Color(0xFF67F7B1),
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
                .background(Color(0xFF061111))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1B3C2E), Color.Transparent),
                        radius = 900f
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    DiscoveryDeck(
                        spotlight = spotlight,
                        modifier = Modifier.graphicsLayer {
                            scaleX = heroScale
                            scaleY = heroScale
                            alpha = heroAlpha
                            translationY = (1f - heroAlpha) * 90f
                        }
                    )
                }

                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = heroAlpha
                                translationY = (1f - heroAlpha) * 56f
                            },
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.05f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "UNKNOWN SIGNALS",
                                color = Color(0xFF67F7B1),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (artistCluster.isEmpty()) {
                                Text(
                                    text = "No new discovery signals yet. Spin more tracks to ignite this feed.",
                                    color = Color.White.copy(alpha = 0.74f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    artistCluster.forEach { artist ->
                                        AssistChip(onClick = {}, label = { Text(artist, maxLines = 1) })
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = heroAlpha
                                translationY = (1f - heroAlpha) * 42f
                            },
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setPlaylistAndPlay(tracks, 0) },
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF67F7B1),
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play Feed", fontWeight = FontWeight.Black)
                        }

                        OutlinedButton(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    viewModel.setPlaylistAndPlay(tracks.shuffled(), 0)
                                }
                            },
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF67F7B1).copy(alpha = 0.75f))
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = Color(0xFF67F7B1))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Random Scan", color = Color.White)
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
                            Text(
                                text = "Discovery Weekly is empty right now.",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "FRESH DROP INDEX",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF67F7B1),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    }

                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        DiscoverTrackCard(
                            rank = index + 1,
                            track = track,
                            onClick = { viewModel.setPlaylistAndPlay(tracks, index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryDeck(
    spotlight: List<AudioTrack>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.06f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "WEEKLY NEON DECK",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "A handpicked drift into tracks you probably have not heard from your own archive.",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                contentAlignment = Alignment.Center
            ) {
                if (spotlight.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = WitcherIcons.Radio,
                            contentDescription = null,
                            tint = Color(0xFF67F7B1)
                        )
                    }
                } else {
                    spotlight.forEachIndexed { index, track ->
                        val rotate = if (index == 0) 0f else if (index == 1) -8f else 8f
                        val xOffset = if (index == 0) 0.dp else if (index == 1) (-62).dp else 62.dp
                        val yOffset = if (index == 0) 0.dp else 12.dp

                        AsyncImage(
                            model = track.artworkUri,
                            contentDescription = "Spotlight artwork ${track.title}",
                            contentScale = ContentScale.Crop,
                            fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_tiger_logo),
                            modifier = Modifier
                                .size(136.dp)
                                .padding(start = xOffset, top = yOffset)
                                .rotate(rotate)
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverTrackCard(
    rank: Int,
    track: AudioTrack,
    onClick: () -> Unit
) {
    val rankTint = when {
        rank <= 3 -> Color(0xFF67F7B1)
        rank <= 10 -> Color(0xFF4FC3F7)
        else -> Color.White.copy(alpha = 0.8f)
    }

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
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(rankTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    color = rankTint,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black
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
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = rankTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

