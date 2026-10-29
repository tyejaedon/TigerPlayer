# Data Source Sub-compartment (`data/source/`)

This directory houses the on-device audio file scanners and media indexers that interface directly with the Android operating system's filesystem and MediaStore databases.

---

## 1. Plain-English Overview

Think of the **Data Source Sub-compartment** as the "scouts and inspectors" of TigerPlayer:
- Whenever you add new songs to your phone, these classes walk through your device folders and index every music track.
- They inspect each song's identity card: extracting the title, the singer, the album name, how long the song is, and even embedded album cover pictures.
- They know how to handle modern Android security rules: whether songs are in your normal Downloads folder or tucked away on an external micro-SD card, they ask Android for permission properly so your songs show up reliably.

---

## 2. Sub-compartment Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│ data/source/ Architecture                                              │
│                                                                        │
│ ┌────────────────────────────────────┐ ┌─────────────────────────────┐ │
│ │ LocalAudioDataSource               │ │ SafFolderScanner            │ │
│ │  ├── Android MediaStore Query      │ │  ├── Android Storage Access │ │
│ │  ├── ContentResolver Cursor        │ │  │   Framework (SAF)        │ │
│ │  ├── Batch Chunking (500 items)    │ │  ├── DocumentFile Traversal │ │
│ │  └── AudioTrack Model Mapping      │ │  └── Excluded Directory Map │ │
│ └──────────────────┬─────────────────┘ └──────────────┬──────────────┘ │
│                    │                                  │                │
│                    └─────────────────┬────────────────┘                │
│                                      ▼                                 │
│                              [AudioRepository]                         │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Component Breakdown & Function Catalog

### `LocalAudioDataSource`
* **Mechanism:** Queries Android's system `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` using `ContentResolver`.
* **Projections:** Extracts columns for `_ID`, `TITLE`, `ARTIST`, `ALBUM`, `DURATION`, `DATA` (file path), `SIZE`, `DATE_MODIFIED`, `ALBUM_ID`, and audio attributes.
* **Batch Optimization:** Processes cursors in chunks of 500 items, yielding execution periodically so large music libraries (10,000+ tracks) scan smoothly without causing out-of-memory errors or freezing background threads.
* **Artwork URI Construction:** Generates canonical Android content URIs (`content://media/external/audio/albumart/<ALBUM_ID>`) for fast image loading via Coil.

### `SafFolderScanner`
* **Mechanism:** Uses the Android Storage Access Framework (`DocumentFile.fromTreeUri`) to traverse user-selected folder trees.
* **Use Case:** Crucial for reading audio files from removable micro-SD cards, USB OTG drives, or private directories where standard MediaStore indexers do not have read permissions.
* **Safety:** Filters out non-audio extensions and handles permission revocations gracefully without crashing.

---

## 4. Permissions & OS Compatibility Guardrails

1. **Android Version Permissions:**
   - On Android 13+ (API 33+), requests `READ_MEDIA_AUDIO`.
   - On Android 12 and below (API 29..32), requests legacy `READ_EXTERNAL_STORAGE`.
2. **User Library Privacy (Rule 3 from AGENTS.md):**
   - Never log user library content (song titles, file paths, artist names) at `Log.d` or `Log.i` in production code. Gate diagnostics strictly behind `if (BuildConfig.DEBUG)`.

---

## 5. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Source Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **MediaStore** | Android's built-in database indexing all media files on the device. | Ingests local music files in `LocalAudioDataSource`. | Local songs do not appear in the library. |
| **ContentResolver** | Android's bridge for querying system databases like MediaStore. | Performs SQL-like queries to retrieve track metadata from the OS. | App cannot discover new songs on the device. |
| **Storage Access Framework (SAF)** | Android security system granting access to custom folders and SD cards. | Reads music files from external SD cards or custom user folders. | "Permission denied" errors when reading external storage. |
| **DocumentFile** | Android class representing a file or folder inside a SAF directory. | Traverses nested directory trees recursively in `SafFolderScanner`. | Nested folders or sub-albums are skipped during scanning. |
