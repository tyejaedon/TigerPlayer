# TigerPlayer Domain Model Sub-compartment (`data/model/`)

This directory contains pure Kotlin domain data classes representing business objects across TigerPlayer.

---

## 1. Plain-English Overview

Think of **Domain Models** as the "universal currency" of TigerPlayer:
- UI screens, ViewModels, engines, and repositories all use these models to talk to each other.
- Unlike database entities or network DTOs (which change depending on Retrofit or SQLite rules), domain models are clean, immutable data structures representing real musical objects — like an `AudioTrack`, `Artist`, or `Lyrics`.

---

## 2. Core Domain Models Breakdown

### `AudioTrack.kt`
* **Purpose:** Unifying data model representing a playable song regardless of source (Local MediaStore, Navidrome server, or Spotify).
* **Properties:** `id`, `title`, `artist`, `album`, `durationMs`, `path`, `albumArtUri`, `mediaSource`, `trackNumber`, `year`, `bitrate`, `sampleRate`, `isFavorite`.

### `Artist.kt`
* **Purpose:** Represents an artist entity with track count, album list, and biographical summary.

### `Lyrics.kt`
* **Purpose:** Represents synchronized (`LyricLine`) and plain-text lyrics for a track.

### `AudioOutputDevice.kt`
* **Purpose:** Represents connected audio hardware (Bluetooth headphones, wired jack, USB DAC) with hardware features and active PEQ profile references.

### `AutoEqModels.kt`
* **Purpose:** Data classes parsing AutoEQ database target profiles, frequency compensation curves, and headphone models.

### `M3uModels.kt` & `MusicFolder.kt` & `TrackFingerprint.kt`
* **Purpose:** Playlist representation, folder node items, and acoustic audio fingerprint data.

---

## 3. Design Guidelines

1. **Immutability:** Domain models are read-only `data class` declarations (`val` properties only).
2. **Decoupling:** Domain models do not depend on Android UI, View, or Retrofit framework annotations.
3. **Value Equality:** Standard `data class` auto-generated `equals()` and `hashCode()` contracts ensure smooth Compose `StateFlow` recomposition diffing.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Model Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Domain Model** | A pure Kotlin data class representing business concepts. | Serves as the standardized model across UI, engine, and repository layers. | Code coupling and difficult testing if UI relies directly on API payloads. |
| **MediaSource** | Enum indicating track origin (`LOCAL`, `NAVIDROME`, `SPOTIFY`). | Route playback commands to appropriate engine and streamer. | Incorrect transport routing or invalid streaming URIs. |
| **Immutability** | Data that cannot be modified after creation. | Prevents accidental state mutation across coroutine boundaries. | Threading bugs and race conditions. |
