package com.example.tigerplayer.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.ui.library.AardBlue
import com.example.tigerplayer.ui.library.IgniRed
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.player.StatItem
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.igniRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedStatsScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val statsState by viewModel.detailedStatsState.collectAsState()
    val filters = listOf("Today", "This Week", "This Month", "Lifetime")
    val haptic = LocalHapticFeedback.current

    // --- LIVE BACKGROUND ANIMATION (The Aard Mesh) ---
    val infiniteTransition = rememberInfiniteTransition(label = "MeshGradient")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "X"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 1. Live Animated Mesh Gradient
        Canvas(modifier = Modifier.fillMaxSize().blur(120.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AardBlue.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(xOffset, size.height * 0.2f),
                    radius = 1200f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(IgniRed.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width - xOffset, size.height * 0.6f),
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
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClose()
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
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
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(filters) { filter ->
                    val isSelected = filter == statsState.selectedFilter

                    // Animated Selection Color
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        label = "FilterBg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "FilterText"
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(bgColor)
                            .bounceClick {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.updateStatsFilter(filter)
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = filter.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = textColor,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // --- CONTENT: THE SCROLL OF KNOWLEDGE ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                // ANIMATED COUNTERS
                val animatedHours by animateIntAsState(
                    targetValue = statsState.totalListeningHours,
                    animationSpec = tween(1500, easing = FastOutSlowInEasing), label = "Hours"
                )
                val animatedMins by animateIntAsState(
                    targetValue = statsState.totalListeningMinutes,
                    animationSpec = tween(1500, easing = FastOutSlowInEasing), label = "Mins"
                )

                // HERO CARD: TOTAL RITUAL TIME
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, MaterialTheme.shapes.extraLarge, spotColor = MaterialTheme.aardBlue.copy(alpha = 0.2f))
                        .glassEffect(MaterialTheme.shapes.extraLarge)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), MaterialTheme.shapes.extraLarge)
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "TOTAL MANIFESTATION TIME",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.aardBlue,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = animatedHours.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "H ",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Text(
                                text = animatedMins.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "M",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // SECTION: TOP ARTISTS (THE VANGUARD)
                if (statsState.topArtists.isNotEmpty()) {
                    DetailedSectionTitle(main = "THE VANGUARD", sub = "Most summoned artists")

                    // #1 Artist gets the Magazine-Style Hero Treatment
                    val topArtist = statsState.topArtists.first()
                    TopArtistHeroCard(topArtist)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Ranks 2-5
                    statsState.topArtists.drop(1).forEachIndexed { index, artist ->
                        StatRow(rank = index + 2, item = artist, isArtist = true)
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // SECTION: TOP TRACKS (THE CHANTS)
                if (statsState.topTracks.isNotEmpty()) {
                    DetailedSectionTitle(main = "THE CHANTS", sub = "Highest frequencies recorded")

                    // To build relative visual bars, we need the max play count
                    val maxTrackPlays = statsState.topTracks.maxOfOrNull { it.playCount } ?: 1

                    statsState.topTracks.forEachIndexed { index, track ->
                        RelativeTrackStatRow(
                            rank = index + 1,
                            item = track,
                            maxPlays = maxTrackPlays
                        )
                    }
                }

                Spacer(modifier = Modifier.height(120.dp)) // Padding for MiniPlayer
            }
        }
    }
}

// ==========================================
// --- PREMIUM STAT COMPONENTS ---
// ==========================================

@Composable
private fun TopArtistHeroCard(artist: StatItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // High-Resolution Image
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.5f to Color.Black.copy(alpha = 0.3f),
                        1.0f to Color.Black.copy(alpha = 0.95f)
                    )
                )
        )

        // Giant Background "#1"
        Text(
            text = "#1",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp),
            color = Color.White.copy(alpha = 0.1f),
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 16.dp, y = (-20).dp)
        )

        // Metadata
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(
                text = "TOP ARTIST",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.igniRed,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.playCount} SUMMONS",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RelativeTrackStatRow(rank: Int, item: StatItem, maxPlays: Int) {
    // Calculate the percentage of this track's plays relative to the #1 track
    val targetFraction = (item.playCount.toFloat() / maxPlays.coerceAtLeast(1).toFloat()).coerceIn(0.1f, 1f)

    // Animate the bar filling up when the screen opens
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(1500, delayMillis = rank * 100, easing = FastOutSlowInEasing),
        label = "BarFill"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
    ) {
        // THE DATA BAR (Fills from the left)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedFraction)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.aardBlue.copy(alpha = 0.2f),
                            MaterialTheme.aardBlue.copy(alpha = 0.05f)
                        )
                    )
                )
        )

        // THE CONTENT
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                modifier = Modifier.width(36.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = if (rank == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )

            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )

            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.secondaryText.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // The absolute play count aligned to the right
            Text(
                text = item.playCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
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
        Text(
            text = "#$rank",
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )

        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(if (isArtist) CircleShape else MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        )

        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.playCount} SUMMONS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
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
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = sub.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}