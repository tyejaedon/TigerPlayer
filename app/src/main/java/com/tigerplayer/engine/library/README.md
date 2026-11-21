# TigerPlayer Library & Metadata Sub-compartment (`engine/library/`)

This directory houses the library indexing engine, directory folder tree builders, and online metadata resolution services.

---

## 1. Plain-English Overview

Think of the **Library Sub-compartment** as the "head librarian and archivist" of TigerPlayer:
- It organizes all your tracks into sorted album lists, artist catalogs, and navigable folder structures.
- It enriches your music with online lyrics (LRCLIB), artist biographies (Wikipedia, Last.fm), and high-resolution album artwork.

---

## 2. Component Breakdown

### `LibraryEngine.kt`
* **Role:** Assembles flat track lists into sorted album/artist collections, performs fast in-memory search filtering, and handles "Liked Songs" virtual playlists.

### `FolderTreeBuilder.kt`
* **Role:** Constructs navigable multi-level directory tree structures (`FolderNode`) from raw file storage paths for local and SAF storage.

### `MetadataEngine.kt`
* **Role:** Resolves track metadata enrichment (fetching synchronized/plain lyrics from LRCLIB, artist bios from Wikipedia/Last.fm, and high-resolution cover art).

---

## 3. Performance Guidelines

1. **Background Coroutines:** Sorting, tree parsing (`FolderTreeBuilder`), and diffing run on `Dispatchers.Default` to prevent UI thread stutter.
2. **Cache Isolation:** Online metadata responses are cached locally to minimize network bandwidth usage.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Library Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Folder Tree** | Hierarchical directory representation of files. | Builds interactive folder browsing in `FolderTreeBuilder`. | Inability to browse music by folder path. |
| **Metadata Enrichment** | Fetching extra info (lyrics, bios, covers) online. | Enhances track details in `MetadataEngine`. | Missing lyrics or artist descriptions. |
