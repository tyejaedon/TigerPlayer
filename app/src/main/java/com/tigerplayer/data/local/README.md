# Local Persistence Sub-compartment (`data/local/`)

This directory houses the on-device persistence layer of TigerPlayer, providing disk storage for library metadata, playlist management, listening history, audio waveform cache, and encrypted user credentials.

---

## 1. Plain-English Overview

Think of the **Local Persistence Sub-compartment** as the "safe and personal storage locker" of the app:
- Every time you scan your music, create a playlist, or adjust an equalizer profile, this module saves that information directly onto your phone's storage.
- It ensures you can access your music instantly without re-scanning thousands of files every time the app opens.
- It acts like a vault for your privacy: server passwords and login tokens are encrypted with military-grade security so other apps on your phone cannot steal them.

---

## 2. Sub-compartment Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│ data/local/ Architecture                                               │
│                                                                        │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ Room SQLite Database (`TigerDatabase`)                             │ │
│ │                                                                    │ │
│ │ DAOs (`dao/`):                                                     │ │
│ │  ├── TigerDao          (Tracks, Artists, Lyrics, History, Waves)   │ │
│ │  ├── PlaylistDao       (User Playlists & Track Associations)       │ │
│ │  └── MusicFolderDao    (Discovered Folder Hierarchy)               │ │
│ │                                                                    │ │
│ │ Entities (`entity/`):                                              │ │
│ │  ├── CachedTrackEntity      ├── PlaylistEntity                     │ │
│ │  ├── ArtistCacheEntity      ├── MusicFolderEntity                  │ │
│ │  ├── LyricsCacheEntity      ├── PlaybackHistoryEntity              │ │
│ │  └── WaveformCacheEntity                                           │ │
│ └──────────────────────────────────┬─────────────────────────────────┘ │
│                                    │                                   │
│ ┌──────────────────────────────────▼─────────────────────────────────┐ │
│ │ DataStore & Encrypted Preferences                                  │ │
│ │  ├── SettingsDataStore   (App Theme, Offload Audio Routing)        │ │
│ │  ├── PlaybackPrefs       (Acoustic Environment, Crossfade, PEQ)    │ │
│ │  ├── NavidromePrefs      (Server URL, Username, Salt)              │ │
│ │  ├── SpotifyPrefs        (User Scopes, Connected Account)          │ │
│ │  └── SecurePrefsCipher   (AES-GCM Encrypted Token Storage)         │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Component & File Catalog

### Database Core (`TigerDatabase.kt`)
* **Room Database Container**: Manages database lifecycle, DAOs, and type converters (`Converters.kt` for JSON lists, Dates, and enums).
* **DAOs (`data/local/dao/`)**:
  - `TigerDao`: Primary data gateway. Provides reactive `Flow<List<CachedTrackEntity>>` queries, batch insert/upsert methods for library scans, history logging, and LRCLIB lyrics lookup.
  - `PlaylistDao`: Creates, renames, deletes, and reorders playlists and associated tracks.
  - `MusicFolderDao`: Tracks scanned folder paths and excluded directory rules.

### Entity Schema (`data/local/entity/`)
* **`CachedTrackEntity`**: Primary track metadata record (ID, title, artist, album, duration, bitrate, file path, media source type, ReplayGain tags).
* **`PlaybackHistoryEntity`**: Records track completion events, timestamp epochs, and actual listened durations for analytics.
* **`LyricsCacheEntity`**: Offline store for plain and synchronized `.lrc` lyrics.
* **`WaveformCacheEntity`**: Persists downsampled RMS waveform peaks to eliminate re-computation on repeated song plays.

### Preferences & Security
* **`SettingsDataStore`**: Reactive key-value store using Jetpack DataStore Preferences for application preferences (dark mode, offload routing, haptic profiles).
* **`PlaybackPrefs`**: Preferences for DSP acoustic environment modes, equalizer presets, and volume defaults.
* **`SecurePrefsCipher`**: AES-256-GCM hardware-backed cryptography wrapper for sensitive tokens, preventing plaintext credential storage on disk.

---

## 4. Scope, Migration Guardrails & Known Debt

### Critical Room Migration Guardrail (Rule 4 from AGENTS.md)
* **Invariant:** Never bump the Room `version` in `TigerDatabase` without adding an explicit `Migration(from, to)` implementation and an accompanying unit test using `MigrationTestHelper`.
* **Issue #43 Context:** TigerPlayer previously lacked schema migrations and relied on destructive migrations. Any new entity column or table must define a non-destructive SQL migration script to protect user playlists and listening history from being wiped on app updates.

---

## 5. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Local Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **DAO (Data Access Object)** | An interface containing the SQL queries used to read and write database records. | Provides type-safe Kotlin functions for accessing SQLite tables. | Database query errors, crashes, or unhandled data types. |
| **Room Migration** | Code that alters database tables without deleting existing user data. | Upgrades database structure when new features add columns. | User playlists and history are wiped if destructive fallback triggers. |
| **AES-GCM Encryption** | Advanced encryption standard providing both privacy and tamper-proofing. | Secures passwords and tokens stored in `SecurePrefsCipher`. | Passwords stored in plaintext could be extracted by unauthorized root processes. |
| **Type Converter** | Helper code teaching Room how to save complex objects like lists into a table. | Converts complex data types (e.g., list of strings) into text for storage. | App crashes on save or read if object cannot be serialized. |
