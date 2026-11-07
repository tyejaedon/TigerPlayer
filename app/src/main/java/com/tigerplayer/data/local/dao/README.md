# TigerPlayer DAO Sub-compartment (`data/local/dao/`)

This directory houses the **Data Access Objects (DAOs)** for Room database operations in TigerPlayer.

---

## 1. Plain-English Overview

Think of **DAOs** as the "database tellers and search clerks" of TigerPlayer:
- They contain the exact SQL commands used to create, read, update, and delete information in the app's SQLite database.
- When you like a song, search for an album, view your listening history, or save a custom EQ profile for your headphones, a DAO executes the query.
- DAOs return reactive Kotlin `Flow` streams so the user interface updates instantly whenever database content changes.

---

## 2. Core DAOs & Responsibilities

### `TigerDao.kt`
* **Purpose:** Primary DAO handling track library, artist cache, listening history, and lyrics queries.
* **Key Queries:**
  - `getAllTracks()`: Returns reactive `Flow<List<CachedTrackEntity>>` of all indexed tracks.
  - `getTracksForAlbum(albumId)`: Returns tracks matching a specific album.
  - `insertTracks(tracks)`: Batch inserts or updates tracks into the database.
  - `insertPlaybackHistory(history)`: Records listening history entries.
  - `getCachedLyrics(trackId)`: Retrieves stored lyrics text and timestamps.

### `PlaylistDao.kt`
* **Purpose:** Manages user playlist CRUD operations and track-playlist associations.
* **Key Queries:**
  - `getAllPlaylists()`: Returns `Flow<List<PlaylistEntity>>`.
  - `insertPlaylist(playlist)`: Creates a new custom or smart playlist.
  - `addTrackToPlaylist(...)`, `removeTrackFromPlaylist(...)`: Manages playlist item memberships.

### `AudioDeviceDao.kt`
* **Purpose:** Manages custom audio device profiles, AutoEQ curve presets, and wired/Bluetooth device settings.
* **Key Queries:**
  - `getDevicePreset(macAddress)`: Retrieves equalizer and DSP preferences linked to a specific audio output device.
  - `upsertDevicePreset(...)`: Saves custom device configurations.

### `MusicFolderDao.kt`
* **Purpose:** Stores user-configured music directory paths and folder indexing status.
* **Key Queries:**
  - `getScannedFolders()`: Returns user-selected local directories for MediaStore / SAF scanning.

---

## 3. Query Guidelines & Reactive Data Flow

1. **Reactive Streams:** Queries returning lists or dynamic data must return `Flow<T>` or `PagingSource` so UI observers receive automatic updates.
2. **Transaction Integrity:** Operations modifying multiple tables must be annotated with `@Transaction` to ensure atomicity.
3. **Conflict Strategies:** Write operations should use `@Insert(onConflict = OnConflictStrategy.REPLACE)` or `@Upsert` to prevent duplicate key crashes.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in DAO Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **DAO (Data Access Object)** | An interface defining SQLite database queries. | Provides typed functions for reading/writing Room database rows. | Inability to access or modify database records. |
| **`@Transaction`** | Ensures a group of SQL statements all succeed together or all fail. | Used when modifying playlists or batch-updating track lists. | Database corruption or partial updates if an error occurs mid-operation. |
| **Reactive `Flow`** | Continuous stream of database updates. | Notifies ViewModels whenever tracks or playlists change in Room. | UI displays stale data until screen is manually refreshed. |
