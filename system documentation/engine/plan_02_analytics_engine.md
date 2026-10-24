# Phase 2 Plan: Logic-Layer History Analytics & Mood Profiling

## Objectives

Implement deep listening insights—**Song Time-of-Day Profile**, **Weekly Skip Rates**, and **Daily Mood/Energy Profiles**—strictly at the logic layer without Room database schema migrations.

---

## Work Items & Implementation Tasks

### 1. Data Models (`AnalyticsModels.kt`)
- Create `AnalyticsModels.kt` containing `TimeOfDayBucket`, `SongTimeProfile`, `TrackSkipStats`, and `DailyMoodProfile`.

### 2. Logic Layer Implementation (`StatsEngine` & `HistoryRepository`)
- **Song Time-of-Day Profile**: Derives `Calendar.HOUR_OF_DAY` from existing [`PlaybackHistoryEntity.timestamp`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/data/local/entity/PlaybackHistoryEntity.kt) values to bucket listening into Morning ($06\text{--}12$), Afternoon ($12\text{--}18$), Evening ($18\text{--}22$), and Night ($22\text{--}06$).
- **Weekly Most Skipped Songs**: Identifies tracks where `5,000 ms <= durationListenedMs < 30,000 ms` or $<30\%$ of track duration across a 7-day rolling window.
- **Daily Mood & Energy Breakdown**: Classifies title, album, and genre metadata into mood tags (`HIGH_ENERGY`, `CHILL_DOWNTEMPO`, `ACOUSTIC_ORGANIC`, `MELANCHOLIC_NIGHT`, `UPBEAT_POP`) and average energy scores.

### 3. Critical Improvements to [`HistoryRepository.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/data/repository/HistoryRepository.kt)
- Fix stale midnight/weekly timestamp properties by converting static `val`s into dynamic observe functions (`observeListeningTimeToday()`).
- Modernize date boundary calculations using `java.time` APIs.
- Add `.distinctUntilChanged()` to public aggregate `Flow` properties.
- Add Room composite indices to [`PlaybackHistoryEntity`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/data/local/entity/PlaybackHistoryEntity.kt): `@Entity(tableName = "playback_history", indices = [Index("timestamp"), Index("trackId"), Index("artist")])`.

---

## Verification
- Detailed design document: [`system documentation/engine/analytics.md`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/system%20documentation/engine/analytics.md).
- Unit tests: `PlaybackAnalyticsTest`.
