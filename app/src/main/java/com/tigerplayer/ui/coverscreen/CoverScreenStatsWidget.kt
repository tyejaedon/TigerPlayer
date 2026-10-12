package com.tigerplayer.ui.coverscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tigerplayer.engine.CoverScreenStatsSnapshot

@Composable
fun CoverScreenStatsWidget(
    stats: CoverScreenStatsSnapshot,
    onOpenFullStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topArtists = stats.topArtists
        .take(3)
        .joinToString(separator = ", ") { it.artistName }
        .ifBlank { "No top artists yet" }
    val summary = if (stats.hasHistory) {
        "${formatListeningTime(stats.listeningTimeTodayMs)} · ${stats.tracksPlayedToday} tracks today"
    } else {
        "Listening stats appear after you play music"
    }
    val sessionArtists = stats.sessionTopArtists
        .take(3)
        .joinToString(separator = ", ") { it.artistName }
        .ifBlank { "no session artists yet" }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenFullStats)
            .testTag(CoverScreenTestTags.STATS_WIDGET)
            .semantics {
                contentDescription = "Listening statistics. $summary. Top artists: $topArtists. " +
                    "Session favorites: $sessionArtists. Open full stats."
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Listening today",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AnimatedVisibility(
                visible = stats.hasHistory,
                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Top artists: $topArtists",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (stats.sessionTrackPlayCount > 0) {
                        Text(
                            text = "Session favorites: $sessionArtists · ${stats.sessionTrackPlayCount} plays",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ListeningHistoryBars(
                        dailyPlayCounts = stats.dailyPlayCounts,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .testTag(CoverScreenTestTags.STATS_CHART)
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningHistoryBars(
    dailyPlayCounts: List<Int>,
    modifier: Modifier = Modifier
) {
    val values = dailyPlayCounts.takeLast(CoverScreenStatsSnapshot.STATS_DAYS)
        .map { it.coerceAtLeast(0).toFloat() }
    val maximum = values.maxOrNull()?.takeIf { it.isFinite() && it > 0f } ?: 1f
    val fractions = values.mapIndexed { index, value ->
        val target = (value / maximum).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        animateFloatAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 320),
            label = "listening-history-bar-$index"
        ).value
    }
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(0f)
        values.forEachIndexed { index, _ ->
            val fraction = fractions[index].takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            val barHeight = (size.height * fraction).coerceAtLeast(2.dp.toPx())
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = trackColor,
                topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            )
            drawRoundRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            )
        }
    }
}

private fun formatListeningTime(milliseconds: Long): String {
    val totalMinutes = milliseconds.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}
