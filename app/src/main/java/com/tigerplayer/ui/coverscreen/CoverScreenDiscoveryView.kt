package com.tigerplayer.ui.coverscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.tigerplayer.R
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.dashboard.CurationFeedMeta
import kotlin.math.max

internal data class CoverScreenRecommendation(
    val source: String,
    val track: AudioTrack
)

private const val MAX_RECOMMENDATIONS = 3

internal fun buildCoverScreenRecommendations(
    daylistTracks: List<AudioTrack>,
    discoveryWeeklyTracks: List<AudioTrack>
): List<CoverScreenRecommendation> = buildList {
    daylistTracks.firstOrNull()?.let { add(CoverScreenRecommendation("DAY LIST", it)) }
    discoveryWeeklyTracks.firstOrNull()?.let {
        add(CoverScreenRecommendation("DISCOVERY WEEKLY", it))
    }
    daylistTracks.drop(1).forEach {
        if (size < MAX_RECOMMENDATIONS) add(CoverScreenRecommendation("DAY LIST", it))
    }
    discoveryWeeklyTracks.drop(1).forEach {
        if (size < MAX_RECOMMENDATIONS) add(CoverScreenRecommendation("DISCOVERY WEEKLY", it))
    }
}.take(MAX_RECOMMENDATIONS)

internal data class CoverDayPhase(
    val label: String,
    val headline: String,
    val subtitle: String,
    val accent: Color,
    val mist: Color
)

@Composable
internal fun rememberCoverDayPhase(): CoverDayPhase {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return remember(hour) {
        when (hour) {
            in 5..11 -> CoverDayPhase(
                label = "MORNING WAVE",
                headline = "Sunrise Kinetic Arc",
                subtitle = "Brighter rhythms to start momentum.",
                accent = Color(0xFFFFC857),
                mist = Color(0xFF56361B)
            )
            in 12..16 -> CoverDayPhase(
                label = "AFTERNOON FLOW",
                headline = "Midday Signal Drift",
                subtitle = "Steady grooves to keep focus locked.",
                accent = Color(0xFF4FC3F7),
                mist = Color(0xFF1C3140)
            )
            in 17..21 -> CoverDayPhase(
                label = "EVENING GLOW",
                headline = "Twilight Pulse Network",
                subtitle = "Warm textures for sunset sessions.",
                accent = Color(0xFFFF7A59),
                mist = Color(0xFF3A1F25)
            )
            else -> CoverDayPhase(
                label = "NIGHT ECHO",
                headline = "Afterdark Resonance Grid",
                subtitle = "Low-light atmospheres and deep bass lanes.",
                accent = Color(0xFFB388FF),
                mist = Color(0xFF231C37)
            )
        }
    }
}

/**
 * Dual discovery curation portal for the cover screen:
 * Provides two elongated pill buttons for Daylist (with chrono time-phase styling) and
 * Discovery Weekly (with emerald anomaly feed styling).
 */
@Composable
fun CoverScreenDiscoveryView(
    daylistTracks: List<AudioTrack> = emptyList(),
    discoveryWeeklyTracks: List<AudioTrack> = emptyList(),
    daylistMeta: CurationFeedMeta = CurationFeedMeta(),
    discoveryMeta: CurationFeedMeta = CurationFeedMeta(),
    onPlayTrack: (AudioTrack) -> Unit,
    onQueueTrack: (AudioTrack) -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenDiscoveryDetail: (() -> Unit)? = null,
    onOpenDaylistDetail: (() -> Unit)? = null,
    // Backwards-compatible parameter
    onOpenDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var internalDiscoveryOpen by remember { mutableStateOf(false) }
    var internalDaylistOpen by remember { mutableStateOf(false) }
    val dayPhase = rememberCoverDayPhase()

    val handleOpenDiscovery = {
        when {
            onOpenDiscoveryDetail != null -> onOpenDiscoveryDetail()
            onOpenDetail != null -> onOpenDetail()
            else -> internalDiscoveryOpen = true
        }
    }

    val handleOpenDaylist = {
        if (onOpenDaylistDetail != null) {
            onOpenDaylistDetail()
        } else {
            internalDaylistOpen = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(CoverScreenTestTags.DISCOVERY_VIEW),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // DAYLIST BUTTON (Time-Phase Themed)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clickable(onClick = handleOpenDaylist),
            shape = RoundedCornerShape(19.dp),
            color = Color(0xFF090B12).copy(alpha = 0.85f),
            border = BorderStroke(1.dp, dayPhase.accent.copy(alpha = 0.45f)),
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(dayPhase.accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = dayPhase.accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "DAYLIST • ${dayPhase.label}",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (daylistTracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(dayPhase.accent.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${daylistTracks.size}",
                            color = dayPhase.accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "CURATE",
                        color = dayPhase.accent.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        // DISCOVERY WEEKLY BUTTON (Emerald Neon Themed)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clickable(onClick = handleOpenDiscovery),
            shape = RoundedCornerShape(19.dp),
            color = Color(0xFF061111).copy(alpha = 0.85f),
            border = BorderStroke(1.dp, Color(0xFF67F7B1).copy(alpha = 0.45f)),
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF67F7B1).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF67F7B1),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "DISCOVERY WEEKLY",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp
                    )
                }

                if (discoveryWeeklyTracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF67F7B1).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${discoveryWeeklyTracks.size}",
                            color = Color(0xFF67F7B1),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "EXPLORE",
                        color = Color(0xFF67F7B1).copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }

    if (internalDiscoveryOpen) {
        Dialog(
            onDismissRequest = { internalDiscoveryOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CoverScreenDiscoverWeeklyDetailView(
                tracks = discoveryWeeklyTracks,
                meta = discoveryMeta,
                onPlayTrack = { track ->
                    onPlayTrack(track)
                    internalDiscoveryOpen = false
                },
                onPlayFeed = {
                    if (discoveryWeeklyTracks.isNotEmpty()) {
                        onPlayTrack(discoveryWeeklyTracks.first())
                        internalDiscoveryOpen = false
                    }
                },
                onShuffleFeed = {
                    if (discoveryWeeklyTracks.isNotEmpty()) {
                        onPlayTrack(discoveryWeeklyTracks.shuffled().first())
                        internalDiscoveryOpen = false
                    }
                },
                onRefresh = onRefresh,
                onClose = { internalDiscoveryOpen = false }
            )
        }
    }

    if (internalDaylistOpen) {
        Dialog(
            onDismissRequest = { internalDaylistOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CoverScreenDaylistDetailView(
                tracks = daylistTracks,
                meta = daylistMeta,
                onPlayTrack = { track ->
                    onPlayTrack(track)
                    internalDaylistOpen = false
                },
                onPlayFeed = {
                    if (daylistTracks.isNotEmpty()) {
                        onPlayTrack(daylistTracks.first())
                        internalDaylistOpen = false
                    }
                },
                onShuffleFeed = {
                    if (daylistTracks.isNotEmpty()) {
                        onPlayTrack(daylistTracks.shuffled().first())
                        internalDaylistOpen = false
                    }
                },
                onRefresh = onRefresh,
                onClose = { internalDaylistOpen = false }
            )
        }
    }
}

/**
 * Cover-screen optimized version of DaylistDetailScreen, matching the chrono/time-phase
 * portal aesthetic, hero card, play arc/shuffle tide controls, and sequence list.
 */
@Composable
fun CoverScreenDaylistDetailView(
    tracks: List<AudioTrack>,
    meta: CurationFeedMeta = CurationFeedMeta(),
    onPlayTrack: (AudioTrack) -> Unit,
    onPlayFeed: () -> Unit = {},
    onShuffleFeed: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayPhase = rememberCoverDayPhase()
    val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }
    val leadArtist = remember(tracks) {
        tracks.groupingBy { it.artist }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "Unknown"
    }

    val chronoGradient = remember(dayPhase) {
        Brush.linearGradient(
            listOf(dayPhase.accent.copy(alpha = 0.45f), dayPhase.mist, Color.Transparent)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090B12))
            .background(chronoGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DAYLIST PORTAL",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = dayPhase.label,
                        color = dayPhase.accent,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Chrono Hero Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.06f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(dayPhase.accent.copy(alpha = 0.25f))
                                        .border(1.dp, dayPhase.accent.copy(alpha = 0.7f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Timer,
                                        contentDescription = null,
                                        tint = dayPhase.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = dayPhase.headline,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = dayPhase.subtitle,
                                        color = Color.White.copy(alpha = 0.75f),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("${tracks.size} tracks", fontSize = 11.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = Color.White
                                    )
                                )
                                AssistChip(
                                    onClick = {},
                                    label = { Text(formatMinutes(totalDurationMs), fontSize = 11.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        labelColor = Color.White
                                    )
                                )
                                if (tracks.isNotEmpty()) {
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                text = "Lead: $leadArtist",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 11.sp
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = Color.White.copy(alpha = 0.08f),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Play / Shuffle Buttons
                if (tracks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onPlayFeed,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = dayPhase.accent,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play Arc", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = onShuffleFeed,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, dayPhase.accent.copy(alpha = 0.7f)),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    tint = dayPhase.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Shuffle Tide", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    item {
                        Text(
                            text = "TIME-SYNCED SEQUENCE",
                            style = MaterialTheme.typography.labelSmall,
                            color = dayPhase.accent,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    }

                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        CoverDaylistTrackRow(
                            index = index,
                            track = track,
                            accent = dayPhase.accent,
                            onClick = { onPlayTrack(track) }
                        )
                    }
                } else {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.05f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (meta.isLoading) "Loading daylist portal..." else "No daylist generated yet.",
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Play a few tracks in this time bucket to self-curate.",
                                    color = Color.White.copy(alpha = 0.65f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp
                                )
                                if (!meta.isLoading) {
                                    OutlinedButton(
                                        onClick = onRefresh,
                                        border = BorderStroke(1.dp, dayPhase.accent.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Refresh Portal", color = dayPhase.accent, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverDaylistTrackRow(
    index: Int,
    track: AudioTrack,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.04f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = (index + 1).toString(),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
                Spacer(
                    modifier = Modifier
                        .height(14.dp)
                        .width(1.dp)
                        .background(accent.copy(alpha = 0.35f))
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatDuration(track.durationMs),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp
            )
        }
    }

    HorizontalDivider(
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.05f),
        modifier = Modifier.padding(horizontal = 6.dp)
    )
}

/**
 * Cover-screen optimized version of DiscoverWeeklyDetailScreen, preserving the dark emerald/neon
 * aesthetic, spotlight deck, action buttons, and ranked fresh drop index in a compact, glanceable format.
 */
@Composable
fun CoverScreenDiscoverWeeklyDetailView(
    tracks: List<AudioTrack>,
    meta: CurationFeedMeta = CurationFeedMeta(),
    onPlayTrack: (AudioTrack) -> Unit,
    onPlayFeed: () -> Unit = {},
    onShuffleFeed: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spotlight = remember(tracks) { tracks.take(3) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF061111))
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1B3C2E), Color.Transparent),
                    radius = 500f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DISCOVER WEEKLY",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "ANOMALY FEED",
                        color = Color(0xFF67F7B1),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Spotlight Hero Deck
                if (spotlight.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.05f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                spotlight.forEachIndexed { index, track ->
                                    val rotate = if (index == 0) 0f else if (index == 1) -8f else 8f
                                    val xOffset = if (index == 0) 0.dp else if (index == 1) (-42).dp else 42.dp
                                    val yOffset = if (index == 0) 0.dp else 6.dp

                                    AsyncImage(
                                        model = track.artworkUri,
                                        contentDescription = "Spotlight ${track.title}",
                                        contentScale = ContentScale.Crop,
                                        fallback = painterResource(R.drawable.ic_tiger_logo),
                                        modifier = Modifier
                                            .size(76.dp)
                                            .offset(x = xOffset, y = yOffset)
                                            .rotate(rotate)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Play / Shuffle Buttons
                if (tracks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onPlayFeed,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF67F7B1),
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play Feed", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = onShuffleFeed,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF67F7B1).copy(alpha = 0.75f)),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    tint = Color(0xFF67F7B1),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Random", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    item {
                        Text(
                            text = "FRESH DROP INDEX",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF67F7B1),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    }

                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        CoverDiscoverTrackCard(
                            rank = index + 1,
                            track = track,
                            onClick = { onPlayTrack(track) }
                        )
                    }
                } else {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.05f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (meta.isLoading) "Loading recommendations..." else "Discovery Weekly is empty right now.",
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (!meta.isLoading) {
                                    OutlinedButton(
                                        onClick = onRefresh,
                                        border = BorderStroke(1.dp, Color(0xFF67F7B1).copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Refresh Feed", color = Color(0xFF67F7B1), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverDiscoverTrackCard(
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
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.04f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(rankTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    color = rankTint,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = rankTint,
                modifier = Modifier.size(16.dp)
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
