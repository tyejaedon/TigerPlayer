package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.dashboard.CurationFeedMeta

internal data class CoverScreenRecommendation(
    val source: String,
    val track: AudioTrack
)

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

@Composable
fun CoverScreenDiscoveryView(
    daylistTracks: List<AudioTrack>,
    discoveryWeeklyTracks: List<AudioTrack>,
    daylistMeta: CurationFeedMeta,
    discoveryMeta: CurationFeedMeta,
    onPlayTrack: (AudioTrack) -> Unit,
    onQueueTrack: (AudioTrack) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recommendations = remember(daylistTracks, discoveryWeeklyTracks) {
        buildCoverScreenRecommendations(daylistTracks, discoveryWeeklyTracks)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(CoverScreenTestTags.DISCOVERY_VIEW),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "DAILY DISCOVERY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )

        if (recommendations.isEmpty()) {
            val isLoading = daylistMeta.isLoading || discoveryMeta.isLoading
            val hasError = daylistMeta.lastErrorMessage != null ||
                discoveryMeta.lastErrorMessage != null
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        isLoading -> "Loading recommendations..."
                        hasError -> "Recommendations couldn't load."
                        else -> "No recommendations are ready yet."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(CoverScreenTestTags.DISCOVERY_EMPTY_STATE)
                )
                if (!isLoading) {
                    TextButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag(CoverScreenTestTags.DISCOVERY_REFRESH)
                    ) {
                        Text("Refresh")
                    }
                }
            }
        } else {
            val unavailableFeeds = buildList {
                if (daylistTracks.isEmpty() && !daylistMeta.isLoading) {
                    add("Day List isn't ready today.")
                }
                if (discoveryWeeklyTracks.isEmpty() && !discoveryMeta.isLoading) {
                    add("Discover Weekly isn't ready yet.")
                }
            }
            if (unavailableFeeds.isNotEmpty()) {
                Text(
                    text = unavailableFeeds.joinToString(separator = " "),
                    modifier = Modifier.testTag(CoverScreenTestTags.DISCOVERY_MISSING_FEED),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .testTag(CoverScreenTestTags.DISCOVERY_LIST),
                contentPadding = PaddingValues(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = recommendations,
                    key = { "${it.source}:${it.track.id}" }
                ) { recommendation ->
                    RecommendationCard(
                        recommendation = recommendation,
                        onPlayTrack = onPlayTrack,
                        onQueueTrack = onQueueTrack
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: CoverScreenRecommendation,
    onPlayTrack: (AudioTrack) -> Unit,
    onQueueTrack: (AudioTrack) -> Unit
) {
    val track = recommendation.track
    Card(
        modifier = Modifier
            .width(292.dp)
            .height(66.dp)
            .clickable { onPlayTrack(track) }
            .testTag(CoverScreenTestTags.discoveryRecommendation(track.id))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = "Artwork for ${track.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text(
                    text = recommendation.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { onPlayTrack(track) },
                modifier = Modifier
                    .size(40.dp)
                    .testTag(CoverScreenTestTags.discoveryPlay(track.id))
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play ${track.title}"
                )
            }
            IconButton(
                onClick = { onQueueTrack(track) },
                modifier = Modifier
                    .size(40.dp)
                    .testTag(CoverScreenTestTags.discoveryQueue(track.id))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Queue ${track.title}"
                )
            }
        }
    }
}

private const val MAX_RECOMMENDATIONS = 3
