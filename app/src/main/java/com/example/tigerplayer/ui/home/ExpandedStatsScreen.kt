package com.example.tigerplayer.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.player.StatItem
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.glassEffect

@Composable
fun ExpandedStatsScreen(viewModel: PlayerViewModel, onClose: () -> Unit) {
    val statsState by viewModel.detailedStatsState.collectAsState()
    val filters = listOf("Today", "This Week", "This Month", "Lifetime")

    // --- LIVE BACKGROUND ANIMATION ---
    val infiniteTransition = rememberInfiniteTransition(label = "MeshGradient")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 400f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse), label = "X"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 1. Live Animated Mesh Gradient
        val primaryColor = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxSize().blur(100.dp)) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.2f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(xOffset, 200f),
                    radius = 800f
                )
            )
        }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // --- Top Bar ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.glassEffect(CircleShape)) {
                    Icon(WitcherIcons.Back, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Chronicle Insights", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }

            // --- Filter Selection ---
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { filter ->
                    val isSelected = filter == statsState.selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.updateStatsFilter(filter) },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        border = null,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            // --- Content ---
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

                // Hero Card: Total Time
                Box(modifier = Modifier.fillMaxWidth().glassEffect(MaterialTheme.shapes.extraLarge).padding(24.dp)) {
                    Column {
                        Text("TOTAL RITUAL TIME", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(statsState.totalListeningHours.toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                            Text(" hours", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Section: Top Artists
                if (statsState.topArtists.isNotEmpty()) {
                    SectionTitle("THE VANGUARD", "Most summoned artists")
                    statsState.topArtists.forEachIndexed { index, artist ->
                        StatRow(rank = index + 1, item = artist, isArtist = true)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Section: Top Tracks
                if (statsState.topTracks.isNotEmpty()) {
                    SectionTitle("THE CHANTS", "Top frequencies recorded")
                    statsState.topTracks.forEachIndexed { index, track ->
                        StatRow(rank = index + 1, item = track, isArtist = false)
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(main: String, sub: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(main, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatRow(rank: Int, item: StatItem, isArtist: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#$rank",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(if (isArtist) CircleShape else MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                "${item.playCount} summons • ${item.secondaryText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
