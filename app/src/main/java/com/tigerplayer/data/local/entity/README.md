# TigerPlayer Entity Sub-compartment (`data/local/entity/`)

This directory defines the **Room Database Entities** representing database table structures in SQLite.

---

## 1. Plain-English Overview

Think of **Entities** as the "table blueprints and index cards" of TigerPlayer:
- Each Entity class corresponds to a single table in the SQLite database (`TigerDatabase`).
- They define columns, data types, primary keys, indexes, and unique constraints.
- They ensure stored data (such as track durations, album covers, listener scrobbles, or EQ curves) remains structured and validated.

---

## 2. Core Entities Breakdown

### `CachedTrackEntity.kt`
* **Table Name:** `cached_tracks`
* **Primary Key:** `id` (`String` - URI or server track ID)
* **Columns:** `title`, `artist`, `album`, `durationMs`, `path`, `albumArtUri`, `mediaSource` (LOCAL, NAVIDROME, SPOTIFY), `bitrate`, `sampleRate`, `year`, `genre`.
* **Index:** Indexed on `artist`, `album`, and `path` for high-speed search.

### `PlaylistEntity.kt`
* **Table Name:** `playlists`
* **Primary Key:** `id` (`Long`, auto-generate)
* **Columns:** `name`, `createdAt`, `updatedAt`, `trackIdsJson`, `isSmartPlaylist`.

### `PlaybackHistoryEntity.kt`
* **Table Name:** `playback_history`
* **Primary Key:** `id` (`Long`, auto-generate)
* **Columns:** `trackId`, `title`, `artist`, `timestamp`, `durationListenedMs`, `completed`.

### `AudioDeviceEntity.kt` & `DevicePresetEntity.kt`
* **Table Name:** `audio_devices` / `device_presets`
* **Primary Key:** `macAddress` / `id`
* **Columns:** `deviceName`, `connectionType` (BLUETOOTH, WIRED, USB_DAC), `autoEqProfileId`, `peqGainArrayJson`, `spatialReverbEnabled`.

### `LyricsCacheEntity.kt`
* **Table Name:** `lyrics_cache`
* **Primary Key:** `trackId`
* **Columns:** `plainText`, `syncedLrcJson`, `source` (LRCLIB, LOCAL_SIDECAR, EMBEDDED), `cachedAt`.

### `ArtistCacheEntity.kt` & `MusicFolderEntity.kt` & `WaveformCacheEntity.kt`
* **Purpose:** Auxiliary tables caching online artist biographies (`Wikipedia`/`Lastfm`), SAF folder directories, and downsampled waveform envelope buffers.

---

## 3. Room Invariants & Migration Rules

1. **Schema Changes:** Any field addition or column type change **must** increment `TigerDatabase` schema version AND include a corresponding migration test.
2. **Type Converters:** Complex objects (e.g., lists, JSON payloads) must be converted via `Converters.kt` using Gson/Moshi.
3. **Primary Key Stability:** Primary keys must be immutable and unique across local and remote sources.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Entity Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Room Entity** | A Kotlin data class annotated with `@Entity` representing a database table. | Defines columns and primary keys for SQLite tables. | Database creation or table mapping failure. |
| **Primary Key** | Unique identifier for a database row. | Uniquely identifies tracks (`id`), playlists, or device MAC addresses. | Duplicate rows or database write collisions. |
| **Index** | Database lookup table that speeds up searches. | Speeds up filtering tracks by artist or album name. | Slow database queries and UI stutter during searches. |
