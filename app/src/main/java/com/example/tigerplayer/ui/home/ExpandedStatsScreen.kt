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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.player.StatItem
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.glassEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedStatsScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val statsState by viewModel.detailedStatsState.collectAsState()
    val filters = listOf("Today", "This Week", "This Month", "Lifetime")

    // --- LIVE BACKGROUND ANIMATION (The Aard Mesh) ---
    val infiniteTransition = rememberInfiniteTransition(label = "MeshGradient")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "X"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 1. Live Animated Mesh Gradient
        val primaryColor = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxSize().blur(120.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(xOffset, size.height * 0.2f),
                    radius = 1000f
                )
            )
        }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // --- TOP BAR: THE ARCHIVE HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.glassEffect(CircleShape)
                ) {
                    Icon(WitcherIcons.Back, contentDescription = "Return", tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "CHRONICLE INSIGHTS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            // --- FILTER SELECTION: TEMPORAL AXIS ---
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(filters) { filter ->
                    val isSelected = filter == statsState.selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.updateStatsFilter(filter) },
                        label = {
                            Text(
                                text = filter.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null,
                        shape = CircleShape
                    )
                }
            }

            // --- CONTENT: THE SCROLL OF KNOWLEDGE ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                // HERO CARD: TOTAL RITUAL TIME
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(MaterialTheme.shapes.extraLarge)
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "TOTAL MANIFESTATION TIME",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = statsState.totalListeningHours.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "H ",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Text(
                                text = statsState.totalListeningMinutes.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "M",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // SECTION: TOP ARTISTS (THE VANGUARD)
                if (statsState.topArtists.isNotEmpty()) {
                    DetailedSectionTitle(main = "THE VANGUARD", sub = "Most summoned artists in this era")
                    statsState.topArtists.forEachIndexed { index, artist ->
                        StatRow(rank = index + 1, item = artist, isArtist = true)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // SECTION: TOP TRACKS (THE CHANTS)
                if (statsState.topTracks.isNotEmpty()) {
                    DetailedSectionTitle(main = "THE CHANTS", sub = "Top frequencies recorded in the archives")
                    statsState.topTracks.forEachIndexed { index, track ->
                        StatRow(rank = index + 1, item = track, isArtist = false)
                    }
                }

                Spacer(modifier = Modifier.height(120.dp)) // Padding for MiniPlayer
            }
        }
    }
}

@Composable
private fun DetailedSectionTitle(main: String, sub: String) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = main,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = sub.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatRow(rank: Int, item: StatItem, isArtist: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // RANK INDICATOR
        Text(
            text = "#$rank",
            modifier = Modifier.width(40.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )

        // IMAGE MEDALLION
        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(if (isArtist) CircleShape else MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        )

        // METADATA
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.playCount} SUMMONS • ${item.secondaryText.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}