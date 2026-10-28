# TigerPlayer Data & Persistence Compartment (`data/`)

This directory houses the data access, persistence, caching, and network synchronization layer of TigerPlayer. It acts as the application's Single Source of Truth (SSoT) for library tracks, user playlists, listening history, and cloud service integrations.

---

## 1. Plain-English Overview

Think of the **Data Compartment** as the "library, vault, and filing cabinet" of the player:
- It knows where every single song comes from: whether it is stored on your device's internal storage, an external SD card, a personal cloud server (Navidrome), or Spotify.
- It remembers your playlists, your favorite songs, your listening history, and even offline lyric text files.
- It protects your credentials: password salts and API tokens are encrypted and never stored in plain text.
- If you lose internet access, the local database ensures you can still view your collection and play downloaded or locally cached music without error.

---

## 2. Compartment Architecture & Data Flow

```
┌────────────────────────────────────────────────────────────────────────┐
│ External Data Sources & Network APIs                                   │
│ [Android MediaStore]   [Navidrome Server]   [Spotify Client]  [LRCLIB] │
└──────────┬─────────────────────┬───────────────────┬─────────────┬─────┘
           │                     │                   │             │
           ▼                     ▼                   ▼             ▼
┌──────────────────────┐ ┌──────────────┐ ┌────────────────┐ ┌───────────┐
│ LocalAudioDataSource │ │ NavidromeRepo│ │SpotifyAuth(PKCE│ │LyricsRepo │
│ & SafFolderScanner   │ │ (SubsonicApi)│ │& SpotifyRepo   │ │(LrclibApi)│
└──────────┬───────────┘ └───────┬──────┘ └────────┬───────┘ └─────┬─────┘
           │                     │                 │               │
           ▼                     ▼                 ▼               ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Local Persistence Layer (Room DB & DataStore)                          │
│                                                                        │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ TigerDatabase (Room DB)                                            │ │
│ │ - CachedTrackEntity     - ArtistCacheEntity   - PlaylistEntity     │ │
│ │ - MusicFolderEntity     - PlaybackHistory     - LyricsCacheEntity  │ │
│ └────────────────────────────────────────────────────────────────────┘ │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ Preferences: SettingsDataStore, PlaybackPrefs, SecurePrefsCipher   │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Single Source of Truth Repositories (@Singleton)                       │
│                                                                        │
│  [AudioRepository]      [HistoryRepository]      [MusicFolderRepo]     │
│  (Emits Flow<List<AudioTrack>>)                                        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
                         [Engines & ViewModels]
```

---

## 3. Core Sub-compartments & Classes

### 1. Repository Layer (`data/repository/`)
* **`AudioRepository`**: The central repository unifying local MediaStore tracks, Storage Access Framework files, and remote Navidrome tracks into a cohesive `Flow<List<AudioTrack>>`.
  - *Diffing & Sync:* Uses `LibraryCacheDiffer` to perform fast in-memory diffing against Room database entities, avoiding unnecessary disk rewrites.
  - *Folder Exclusion:* Respects user-defined blacklist rules via `FolderExclusionRules`.
* **`HistoryRepository`**: Records playback events (`PlaybackHistoryEntity`), tracks listen duration, and supplies analytics queries for most-played artists, albums, and recent tracks.
* **`LyricsRepository`**: Fetches synced and plain-text lyrics from LRCLIB (`LrclibApi`) or local `.lrc` sidecar files, caching them in Room (`LyricsCacheEntity`).
* **`NavidromeRepository`**: Communicates with Subsonic/Navidrome servers (`SubsonicApi`, `NavidromeApiService`), fetching server ping status, music folders, artists, and playlists.
* **`SpotifyRepository`**: Manages communication with the Spotify App Remote SDK and Spotify Web API. Emits reactive `spotifyPlaybackState` flows.
* **`SpotifyAuthManager`**: Executes Authorization Code Flow with **PKCE** (*Proof Key for Code Exchange*), ensuring zero client secrets are stored or needed in the APK.

### 2. Local Database & Persistence (`data/local/`)
* **`TigerDatabase`**: Room database containing:
  - `TigerDao`: Queries for cached tracks, artists, playback history, and lyrics.
  - `PlaylistDao`: CRUD operations for user and smart playlists.
  - `MusicFolderDao`: Storage of folder paths and directory metadata.
* **`SettingsDataStore`**: Jetpack DataStore Preferences for application preferences (dark mode, offload routing, haptic profiles).
* **`PlaybackPrefs`**: Dedicated DataStore for audio environment modes, crossfade parameters, and volume levels.
* **`SecurePrefsCipher`**: AES-GCM encrypted storage for server passwords and sensitive API tokens.

### 3. Data Sources (`data/source/`)
* **`LocalAudioDataSource`**: High-performance Android MediaStore query engine that scans device audio files, extracting titles, album art URIs, durations, bitrates, and album IDs.
* **`SafFolderScanner`**: Traverses storage using Android's Storage Access Framework (`DocumentFile`) for user-selected external folders.

### 4. Remote API Clients (`data/remote/api/`)
* **`SubsonicApi` / `NavidromeApiService`**: Retrofit client for the Subsonic API standard.
* **`LrclibApi`**: High-accuracy timed lyrics API.
* **`LastfmApi`**: Artist metadata, tags, and album summaries.
* **`WikipediaApi`**: Summary biographies for artists.

---

## 4. Concurrency, Main Safety & Data Integrity

### Strict Threading Rules
* **Main Safety:** All public suspend methods in repositories are **main-safe**; they execute internally on `Dispatchers.IO` using `withContext(Dispatchers.IO)` or `flowOn(Dispatchers.IO)`. Calling repository functions from the UI thread will never block the UI.
* **Cancellation Invariant:** Never swallow `CancellationException` in repository catch blocks. If a coroutine is cancelled, `CancellationException` must be re-thrown to allow structured concurrency cleanup.

### Known Limitations & Tech Debt
1. **Issue #43 (Room Database Migrations):** `TigerDatabase` historically lacked formal version migrations (`fallbackToDestructiveMigration` was used). Any schema modification to entities **must** include an explicit migration script and a migration unit test (`MigrationTestHelper`).
2. **Issue #44 (Navidrome URL Signing):** Authentication tokens must never be embedded permanently into saved track URIs. Streaming URLs are dynamically signed at request time in `AudioPlayerService` via `ResolvingDataSource`.
3. **Issue #46 (Spotify Secret Removal):** Ensure client secrets are never reintroduced; Spotify auth must strictly rely on PKCE.

---

## 5. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Data Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Room Database** | Android's official SQLite database library. | Persists songs, playlists, and history across phone reboots. | App forgets user playlists or re-scans the entire library on every launch. |
| **DataStore** | Modern key-value storage replacing old SharedPreferences. | Stores user settings, EQ profiles, and theme choices safely. | Settings fail to save or crash on concurrent background writes. |
| **PKCE (Proof Key for Code Exchange)** | A secure authorization method that eliminates secret passwords in apps. | Authenticates Spotify accounts without storing private keys in the app. | Security vulnerability or account authorization failures. |
| **MediaStore** | Android system catalog that tracks media files on the device. | Ingests local music files in `LocalAudioDataSource`. | Local songs do not appear in the library. |
| **SAF (Storage Access Framework)** | Android security system granting access to custom folders and SD cards. | Reads music files from external SD cards or custom user folders. | "Permission denied" errors when reading external storage. |
| **Diffing** | Comparing new data against old data to only save actual changes. | `LibraryCacheDiffer` updates only modified tracks in Room. | High CPU usage, database lockups, and battery drain during library scans. |
| **Sidecar File** | An external companion file placed next to an audio file (e.g. `.lrc`). | Provides synchronized lyrics without altering the original audio file. | Lyrics fail to display if sidecar matching fails. |
