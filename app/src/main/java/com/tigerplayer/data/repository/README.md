# Repository Sub-compartment (`data/repository/`)

This directory contains the `@Singleton` domain repositories of TigerPlayer. Repositories act as the canonical Single Source of Truth (SSoT) for all domain entities, arbitrating between disk caches, OS system content resolvers, and remote network services.

---

## 1. Plain-English Overview

Think of the **Repository Sub-compartment** as the "head librarian and concierge":
- When the screen asks for "all songs by Daft Punk", it doesn't care whether those songs live on an SD card, an internal folder, or a home media server in your basement. The repository gathers them from everywhere, deduplicates them, and delivers a clean, unified list.
- It is smart about caching: if you've already scanned your music, it reads from the local database instantly instead of re-reading every audio file from scratch.
- It operates safely in the background so scrolling your library remains smooth and never freezes your phone.

---

## 2. Sub-compartment Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│ data/repository/ Architecture                                          │
│                                                                        │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ AudioRepository (Canonical Library Gateway)                        │ │
│ │  ├── Coordinates: LocalAudioDataSource + NavidromeRepository       │ │
│ │  ├── Optimization: LibraryCacheDiffer (in-memory diffing)          │ │
│ │  ├── Filtering: FolderExclusionRules (user blacklist filters)      │ │
│ │  └── Emits: Flow<List<AudioTrack>>                                 │ │
│ └──────────────────────────────────┬─────────────────────────────────┘ │
│                                    │                                   │
│ ┌──────────────────────────────────┼─────────────────────────────────┐ │
│ │ Specialized Domain Repositories  │                                 │ │
│ │  ├── HistoryRepository           │ ──> Play session logs & stats   │ │
│ │  ├── LyricsRepository            │ ──> LRCLIB & local .lrc sidecars│ │
│ │  ├── NavidromeRepository         │ ──> Subsonic REST client bridge │ │
│ │  ├── SpotifyRepository           │ ──> Spotify Remote SDK & Web API│ │
│ │  ├── SpotifyAuthManager          │ ──> OAuth PKCE flow management  │ │
│ │  ├── MusicFolderRepository       │ ──> Folder tree directory nodes │ │
│ │  └── WeatherRepository           │ ──> Contextual weather telemetry│ │
│ └──────────────────────────────────┴─────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Core Repositories & Responsibilities

### `AudioRepository`
* **Canonical Role:** Single entry point for all song, artist, and album queries.
* **Key Functions:**
  - `getUnifiedTracks()`: Returns an observable `Flow<List<AudioTrack>>` combining local storage and Subsonic tracks.
  - `scanLocalMedia()`: Scans MediaStore, runs `LibraryCacheDiffer` to isolate added/modified tracks, and updates `TigerDatabase` in a single Room transaction.
  - `applyFolderExclusion()`: Filters tracks against blacklisted directories defined in `FolderExclusionRules`.

### `HistoryRepository`
* **Canonical Role:** Logs listening events and powers the analytics dashboard.
* **Key Functions:**
  - `addTrackToHistory(trackId, durationListenedMs)`: Inserts a playback log entry in `PlaybackHistoryEntity`.
  - `getTopArtists(epoch: StatsEpoch)`: Queries most played artists filtered by time window (Day, Week, Month, All-Time).
  - *Known Debt (Issue #42):* Ensure plays are recorded upon genuine listening completion rather than track initiation.

### `LyricsRepository`
* **Canonical Role:** Multi-tiered lyrics resolver.
* **Resolution Hierarchy:**
  1. Inspect local audio metadata tags (USLT / embedded lyrics).
  2. Inspect local `.lrc` sidecar files in the track's folder.
  3. Query `TigerDatabase` offline lyrics cache.
  4. Query remote `LrclibApi` for synced/plain lyrics and save to cache.

### `SpotifyRepository` & `SpotifyAuthManager`
* **Canonical Role:** Manages Spotify connectivity and playback state streams.
* **PKCE Security:** `SpotifyAuthManager` implements Authorization Code Flow with Proof Key for Code Exchange (`PkceGenerator`), eliminating the need for client secrets.

---

## 4. Concurrency & Main-Safety Contracts

1. **Main-Safety Invariant:** All public repository functions that perform disk I/O, database queries, or network requests **must** declare their dispatcher internally via `withContext(Dispatchers.IO)` or `flowOn(Dispatchers.IO)`. Calling repository functions from the UI thread is guaranteed never to drop frames.
2. **Cancellation Exception Rule:** Never catch `CancellationException` in general catch blocks. Always allow coroutine cancellation to propagate:
   ```kotlin
   try {
       // I/O work
   } catch (e: CancellationException) {
       throw e
   } catch (e: Exception) {
       Log.e(TAG, "Non-fatal I/O error", e)
   }
   ```

---

## 5. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Repository Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Single Source of Truth (SSoT)** | The one authoritative place where all screens get their data. | Repositories act as SSoT, shielding the UI from raw database and network calls. | UI displays conflicting or out-of-sync data across different tabs. |
| **Main-Safe** | Code guaranteed not to freeze the phone screen when executed. | All suspend functions in repositories run on background I/O threads. | App freezes, stutters, or shows the Android "App Not Responding" (ANR) dialog. |
| **Diffing** | Checking what changed between new and old data before saving. | `LibraryCacheDiffer` updates only altered songs in the database. | Sluggish performance and battery drain during library scans. |
| **Sidecar File** | An external file sitting alongside the song file (like `song.lrc`). | Used by `LyricsRepository` for synchronized karaoke lyrics. | Timed lyrics fail to display if sidecar matching fails. |
