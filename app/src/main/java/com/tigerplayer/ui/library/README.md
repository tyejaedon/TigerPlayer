# Library UI Sub-compartment (`ui/library/`)

This directory contains the music library exploration interface of TigerPlayer, providing virtualized scrolling lists and grids across Songs, Albums, Artists, Playlists, and Folders.

---

## 1. Plain-English Overview

Think of the **Library UI Sub-compartment** as the "digital vinyl record collection":
- It lets you browse your entire music collection effortlessly, whether you have 50 songs or 50,000 songs.
- You can browse your music however you like: by song title, by artist discography, by album art, by customized playlist, or by navigating your actual storage folders.
- It includes instant search and fast-scrolling: type a single letter or drag the scrollbar handle to jump straight to artists starting with "M" without lag.

---

## 2. Sub-compartment Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│ ui/library/ Architecture                                               │
│                                                                        │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ LibraryScreen (Tabbed Navigation Container)                         │ │
│ │  ├── AllSongsTab       (Virtualized track list with alphabetic fast)│ │
│ │  ├── AlbumsTab         (Adaptive 2/3-column grid of album cards)   │ │
│ │  ├── ArtistsTab        (Artist grid with circular avatar portraits)│ │
│ │  ├── PlaylistsTab      (Smart playlists & custom user playlists)   │ │
│ │  └── FoldersTab        (Hierarchical directory tree browser)       │ │
│ └──────────────────────────────────┬─────────────────────────────────┘ │
│                                    │ Hoisted Events                    │
│ ┌──────────────────────────────────▼─────────────────────────────────┐ │
│ │ LibraryViewModel                                                   │ │
│ │  ├── Exposes: LibraryUiState (tracks, search results, sort orders) │ │
│ │  ├── Search: Decoupled query debounce to prevent typing latency    │ │
│ │  └── Filtering: SortBy (Date Added, Alphabetical, Duration, Artist)│ │
│ └──────────────────────────────────┬─────────────────────────────────┘ │
│                                    ▼                                   │
│                        [AudioRepository & Engines]                     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Core Components & Performance Optimizations

### Virtualized Lists (`AllSongsTab`)
* **LazyColumn Implementation:** Uses Jetpack Compose `LazyColumn` with explicit `key = { it.id }` contracts to ensure smart item reuse.
* **Fast-Scroll Indexer:** Renders an interactive vertical alphabet strip enabling instantaneous jumping to letter headers without stutter.

### Album & Artist Grids (`AlbumsTab`, `ArtistsTab`)
* **LazyVerticalGrid:** Adaptive grid layout (`GridCells.Adaptive(minSize = 150.dp)`).
* **Image Memory Caching:** Leverages Coil image loaders with disk and memory LRU caches to prevent memory spikes when flinging through thousands of high-resolution album covers.

### Folder Directory Browser (`FoldersTab`)
* **Hierarchical Navigation:** Visualizes directory trees built by `FolderTreeBuilder` in the engine layer, allowing breadcrumb navigation and folder playback.

### Search Query Decoupling (`LibraryViewModel`)
* **Issue #262 Optimization:** Decouples raw typing input from global playback state updates. Text entry updates an immediate local StateFlow, while search filtering is debounced to eliminate typing lag during active music playback.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Library Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **LazyColumn / LazyGrid** | A list that only draws the items currently visible on the screen. | Renders massive song libraries with thousands of items smoothly. | App runs out of memory or crashes if thousands of items are drawn at once. |
| **Item Keys** | Unique identifier tags given to each song row in a list. | Helps Compose remember which items moved when sorting or filtering. | Stuttering animations and incorrect album covers displayed on song rows. |
| **Debounce** | Waiting for typing to pause briefly before running an expensive search. | Prevents running 10 database queries when the user types a 10-letter word. | High CPU usage and stuttering keyboard input while searching. |
| **LRU Cache (Least Recently Used)** | Memory storage that deletes the oldest unused pictures first. | Manages album artwork memory usage in Coil. | Out-of-memory crashes when flinging through long album lists. |
