# Analytics Engine Architecture Blueprint: Playback History Insights

## Executive Strategy & Architecture Overview

This blueprint outlines the end-to-end design for introducing advanced user listening analytics—**Time-of-Day Track Preferences**, **Most Skipped Songs of the Week**, and **Daily Mood/Energy Profiling**—purely at the **logic layer** ([`StatsEngine`](class://com.tigerplayer.engine.StatsEngine) and [`HistoryRepository`](class://com.tigerplayer.data.repository.HistoryRepository)) without creating new Room database entities or requiring Room schema migrations.

### Core Architectural Decisions

1. **Zero Database Schema Drift**: All required analytical metrics are dynamically derived from existing [`PlaybackHistoryEntity`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/data/local/entity/PlaybackHistoryEntity.kt) rows (`timestamp`, `durationListenedMs`, `trackId`, `title`, `artist`, `album`) coupled with local library metadata ([`AudioTrack`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/data/model/AudioTrack.kt)).
2. **Backward & Forward Compatibility**: Historical listening records written in previous app versions automatically participate in these insights without data conversion scripts.
3. **Non-Blocking Reactive Pipeline**: Analytical queries execute as Kotlin [`Flow`](class://kotlinx.coroutines.flow.Flow) transformations on `Dispatchers.Default` background threads.

---

## 1. Domain Data Models (`AnalyticsModels.kt`)

These immutable data structures encapsulate the processed analytical output for consumption by ViewModels and Compose screens.

```kotlin
package com.tigerplayer.data.model

import java.util.Calendar

/**
 * Time-of-day buckets for track affinity analysis.
 */
enum class TimeOfDayBucket(val label: String, val startHour: Int, val endHour: Int) {
    MORNING("Morning", 6, 12),       // 06:00 - 11:59
    AFTERNOON("Afternoon", 12, 18),   // 12:00 - 17:59
    EVENING("Evening", 18, 22),       // 18:00 - 21:59
    NIGHT("Late Night", 22, 6);       // 22:00 - 05:59

    companion object {
        fun fromHour(hour: Int): TimeOfDayBucket = when (hour) {
            in 6..11 -> MORNING
            in 12..17 -> AFTERNOON
            in 18..21 -> EVENING
            else -> NIGHT
        }
    }
}

/**
 * Time-of-day affinity breakdown for a specific song.
 */
data class SongTimeProfile(
    val trackId: String,
    val primaryBucket: TimeOfDayBucket,
    val morningPercentage: Float,
    val afternoonPercentage: Float,
    val eveningPercentage: Float,
    val nightPercentage: Float,
    val totalPlays: Int
)

/**
 * Skip statistics for identifying heavily skipped tracks.
 */
data class TrackSkipStats(
    val trackId: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val totalAttempts: Int,
    val skipCount: Int,
    val skipRate: Float // 0.0 to 1.0 (e.g. 0.75 = 75% skip rate)
)

/**
 * Inferred daily energy and mood classifications.
 */
enum class MoodEnergyTag(val label: String, val description: String) {
    HIGH_ENERGY("High Energy", "Fast tempo, electronic, rock, or workout beats"),
    CHILL_DOWNTEMPO("Chill & Lo-Fi", "Relaxed ambient, acoustic, or low-BPM rhythms"),
    ACOUSTIC_ORGANIC("Acoustic & Folk", "Natural instrumentation and vocal-centric tracks"),
    MELANCHOLIC_NIGHT("Late Night Melancholy", "Atmospheric, dark, or night-centric tones"),
    UPBEAT_POP("Upbeat & Vibrant", "Bright, joyful pop and dance selections")
}

/**
 * Daily mood and energy distribution breakdown.
 */
data class DailyMoodProfile(
    val dateEpochMs: Long,
    val dominantMood: MoodEnergyTag,
    val moodDistribution: Map<MoodEnergyTag, Float>,
    val averageEnergyScore: Float, // 0.0 (Calm) to 1.0 (Intense)
    val totalTracksAnalyzed: Int
)
```

---

## 2. Logic Layer Implementation

### A. Time-of-Day Affinity Analysis

Calculates when during the day a song is most frequently played using the `timestamp` column from `PlaybackHistoryEntity`.

```kotlin
// In StatsEngine.kt / HistoryRepository.kt

fun calculateSongTimeProfile(
    trackId: String,
    history: List<PlaybackHistoryEntity>
): SongTimeProfile? {
    val trackHistory = history.filter { it.trackId == trackId }
    if (trackHistory.isEmpty()) return null

    val counts = mutableMapOf(
        TimeOfDayBucket.MORNING to 0,
        TimeOfDayBucket.AFTERNOON to 0,
        TimeOfDayBucket.EVENING to 0,
        TimeOfDayBucket.NIGHT to 0
    )

    val cal = Calendar.getInstance()
    for (entry in trackHistory) {
        cal.timeInMillis = entry.timestamp
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val bucket = TimeOfDayBucket.fromHour(hour)
        counts[bucket] = (counts[bucket] ?: 0) + 1
    }

    val total = trackHistory.size.toFloat()
    val dominant = counts.maxByOrNull { it.value }?.key ?: TimeOfDayBucket.MORNING

    return SongTimeProfile(
        trackId = trackId,
        primaryBucket = dominant,
        morningPercentage = (counts[TimeOfDayBucket.MORNING] ?: 0) / total,
        afternoonPercentage = (counts[TimeOfDayBucket.AFTERNOON] ?: 0) / total,
        eveningPercentage = (counts[TimeOfDayBucket.EVENING] ?: 0) / total,
        nightPercentage = (counts[TimeOfDayBucket.NIGHT] ?: 0) / total,
        totalPlays = trackHistory.size
    )
}
```

---

### B. Skip Rate & Most Skipped Tracks of the Week

A skip is defined as a track attempt where `5,000 ms <= durationListenedMs < MIN_FULL_PLAY_MS` (e.g., played for less than 30 seconds or under 50% of nominal track length).

```kotlin
// In HistoryRepository.kt / StatsEngine.kt

fun getMostSkippedTracksOfWeek(
    history: List<PlaybackHistoryEntity>,
    allTracks: List<AudioTrack>,
    startTimeMs: Long
): List<TrackSkipStats> {
    val recentHistory = history.filter { it.timestamp >= startTimeMs }
    val trackMap = allTracks.associateBy { it.id }

    val attemptsByTrack = recentHistory.groupBy { it.trackId }

    val skipStats = mutableListOf<TrackSkipStats>()

    for ((trackId, entries) in attemptsByTrack) {
        val totalAttempts = entries.size
        if (totalAttempts < 2) continue // Exclude isolated one-off skips

        val track = trackMap[trackId]
        val nominalDurationMs = track?.durationMs ?: 180_000L // Default 3 mins

        // Skip rule: Listened between 5s and 30s OR less than 30% of nominal track length
        val skips = entries.count { entry ->
            val listened = entry.durationListenedMs
            listened in 5_000L..30_000L || (nominalDurationMs > 0 && listened < (nominalDurationMs * 0.3f))
        }

        val skipRate = skips.toFloat() / totalAttempts.toFloat()

        if (skips > 0) {
            val sample = entries.first()
            skipStats.add(
                TrackSkipStats(
                    trackId = trackId,
                    title = sample.title,
                    artist = sample.artist,
                    imageUrl = sample.imageUrl,
                    totalAttempts = totalAttempts,
                    skipCount = skips,
                    skipRate = skipRate
                )
            )
        }
    }

    return skipStats.sortedWith(
        compareByDescending<TrackSkipStats> { it.skipRate }
            .thenByDescending { it.skipCount }
    )
}
```

---

### C. Daily Mood & Energy Analysis

Infers daily listening mood based on song metadata (genre keywords, release decade, track length, and title acoustic indicators).

```kotlin
// In StatsEngine.kt

fun calculateDailyMoodProfile(
    dayEpochMs: Long,
    history: List<PlaybackHistoryEntity>,
    allTracks: List<AudioTrack>
): DailyMoodProfile {
    val startOfDay = getStartOfDay(dayEpochMs)
    val endOfDay = startOfDay + 86_400_000L

    val dayEntries = history.filter { it.timestamp in startOfDay until endOfDay }
    val trackMap = allTracks.associateBy { it.id }

    val moodCounts = mutableMapOf<MoodEnergyTag, Int>()
    var totalEnergyScore = 0f
    var count = 0

    for (entry in dayEntries) {
        val track = trackMap[entry.trackId]
        val (mood, energy) = classifyTrackMoodAndEnergy(entry.title, entry.album, track)

        moodCounts[mood] = (moodCounts[mood] ?: 0) + 1
        totalEnergyScore += energy
        count++
    }

    val total = count.coerceAtLeast(1).toFloat()
    val distribution = moodCounts.mapValues { it.value / total }
    val dominant = moodCounts.maxByOrNull { it.value }?.key ?: MoodEnergyTag.CHILL_DOWNTEMPO

    return DailyMoodProfile(
        dateEpochMs = startOfDay,
        dominantMood = dominant,
        moodDistribution = distribution,
        averageEnergyScore = if (count > 0) totalEnergyScore / count else 0.5f,
        totalTracksAnalyzed = count
    )
}

private fun classifyTrackMoodAndEnergy(
    title: String,
    album: String,
    track: AudioTrack?
): Pair<MoodEnergyTag, Float> {
    val text = "${title.lowercase()} ${album.lowercase()}"

    return when {
        text.containsAny("remix", "club", "dance", "workout", "metal", "electronic", "upbeat", "fast") ->
            MoodEnergyTag.HIGH_ENERGY to 0.9f
        text.containsAny("acoustic", "unplugged", "folk", "piano", "guitar", "vocal", "live") ->
            MoodEnergyTag.ACOUSTIC_ORGANIC to 0.4f
        text.containsAny("night", "dark", "rain", "midnight", "ambient", "slow", "sad", "lullaby") ->
            MoodEnergyTag.MELANCHOLIC_NIGHT to 0.2f
        text.containsAny("pop", "disco", "funk", "summer", "happy", "party") ->
            MoodEnergyTag.UPBEAT_POP to 0.75f
        else ->
            MoodEnergyTag.CHILL_DOWNTEMPO to 0.5f
    }
}

private fun String.containsAny(vararg keywords: String): Boolean {
    return keywords.any { this.contains(it, ignoreCase = true) }
}
```

---

## 3. Integration & Testing Strategy

1. **Service & Engine Binding**: Expose these calculations inside [`StatsEngine`](class://com.tigerplayer.engine.StatsEngine) via reactive [`StateFlow`](class://kotlinx.coroutines.flow.StateFlow) streams:
   - `val songTimeProfiles: Flow<Map<String, SongTimeProfile>>`
   - `val weeklyMostSkippedTracks: Flow<List<TrackSkipStats>>`
   - `val dailyMoodProfiles: Flow<List<DailyMoodProfile>>`
2. **Unit Tests**: Create `PlaybackAnalyticsTest` in `app/src/test/java/com/tigerplayer/engine/` verifying:
   - Accurate hour-of-day bucketing across midnight boundaries ($23:00$ vs $01:00$).
   - Correct skip rate calculations when tracks are skipped under 30 seconds.
   - Fallback mood classification for tracks with minimal tag metadata.
