# Player UI Sub-compartment (`ui/player/`)

This directory contains the primary playback interface of TigerPlayer, including the full-screen record player, persistent mini player, lyrics auto-scroller, queue sheet, and real-time GPU visualizer shaders.

---

## 1. Plain-English Overview

Think of the **Player UI Sub-compartment** as the "main stage, turntable, and soundboard":
- It is where the music comes alive: you see rotating album art, swirling neon particle vortexes dancing to the bass, and synchronized lyrics that scroll line-by-line in real time.
- It provides immediate tactile controls: sliding the seekbar to skip ahead, pressing play/pause, shuffling your queue, or setting a sleep timer so music fades out gently as you fall asleep.
- It is coordinated behind the scenes: no matter what screen you are looking at, the mini-player at the bottom stays in perfect lockstep with the music.

---

## 2. Sub-compartment Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│ ui/player/ Architecture                                                │
│                                                                        │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ Jetpack Compose Player Screens & Components                        │ │
│ │  ├── FullPlayerScreen        (Main player canvas with blur backdrop│ │
│ │  ├── MiniPlayer              (Persistent bottom navigation player) │ │
│ │  ├── PlayerControls          (Play, Pause, Skip, Shuffle, Repeat)  │ │
│ │  ├── PlayerSeeker            (4 Hz high-frequency position scrubber│ │
│ │  ├── PlayerLyrics            (Synced LRC karaoke scroller)         │ │
│ │  ├── PlayerQueue             (Drag-and-drop queue reordering sheet)│ │
│ │  ├── SleepTimerSheet         (Sleep countdown modal sheet)         │ │
│ │  └── TigerVortexRender       (Real-time AGSL GPU shader visualizer)│ │
│ └──────────────────────────────────┬─────────────────────────────────┘ │
│                                    │ Hoisted Events & State Updates    │
│ ┌──────────────────────────────────▼─────────────────────────────────┐ │
│ │ State Holders & Coordinators                                       │ │
│ │  ├── PlaybackViewModel       (Exposes PlaybackUiState)             │ │
│ │  └── PlaybackStateManager    (@Singleton Canonical State Holder)   │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Core Components & Function Catalog

### `FullPlayerScreen` & `MiniPlayer`
* **`FullPlayerScreen`**: Full-screen modal player layout featuring:
  - Dynamically tinted Arcane Glass background via `DominantColorExtractor`.
  - Swipable album art carousel for previous/next track selection.
  - Interactive tabs switching between Album Artwork, Synced Lyrics, and Neon Vortex Visualizers.
* **`MiniPlayer`**: Lightweight persistent bottom bar attached to the root scaffold. Observes `PlaybackStateManager.playbackState` to show track progress without triggering recomposition of parent screens.

### `PlaybackStateManager` & `PlaybackViewModel`
* **`PlaybackStateManager`**: `@Singleton` state coordinator. Acts as the central clearinghouse for playback state to prevent desynchronization between different ViewModels (`PlayerViewModel`, `LibraryViewModel`, `StatsViewModel`).
* **`PlaybackViewModel`**: Scoped ViewModel exposing immutable `PlaybackUiState`:
  - `isPlaying: Boolean`, `currentPosition: Long`, `duration: Long`.
  - `isShuffleEnabled: Boolean`, `repeatMode: Int`.
  - `isDspActive: Boolean`, `isBitPerfect: Boolean`.
* *Design Invariant (Issue #72):* Migrated away from the legacy 603-line `PlayerViewModel` god object into scoped, cohesive state coordinators.

### High-Frequency Position Polling (`PlayerSeeker`)
* **Scrubber Mechanics:** Observes `currentPosition` polled at 4 Hz (`250ms` intervals).
* **Smooth Animation:** Interpolates position smoothly between ticks while decoupling scrubber updates from static metadata (title, artist) to prevent full-screen recompositions.

### Real-Time Visualizers (`TigerVortexRender` & `PipVisualizerSurface`)
* **Shader Technology:** Uses Android Graphics Shading Language (AGSL) on Android 13+ (API 33+) with fallback Canvas rendering on older versions.
* **Data Ingestion:** Collects `AudioReactiveFrame` emitted from `AdaptiveDspEngine`, mapping 6-band FFT spectral energies directly to shader uniforms (`uTime`, `uBass`, `uMid`, `uTreble`).

---

## 4. UI Performance & Battery Optimization (60 / 120 FPS)

1. **Zero Allocation in Draw Passes:** Never instantiate `Paint`, `Path`, `Shader`, or lambda objects inside `Canvas` or `onDrawBehind`. All variables are pre-allocated in `remember` scopes.
2. **Recomposition Isolation:** Position scrubber animations are isolated to `PlayerSeeker` and `MiniPlayer` progress indicators, preventing unnecessary recomposition of heavy backdrop blurs and album art cards.

---

## 5. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Player Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **AGSL (Android Graphics Shading Language)** | Code that runs directly on the graphics card (GPU). | Powers real-time fluid and neon vortex visualizers. | Visualizers drop below 60 FPS or overload the CPU. |
| **Recomposition** | Redrawing only the parts of a screen whose data has changed. | Updates song titles or time clocks without redrawing the entire screen. | Screen stutter and high battery usage if unnecessary areas re-render. |
| **Dominant Color** | The primary vibrant color detected in an album cover. | Tints player backdrops and glow gradients to match the music. | Background remains generic gray or clashes with album art. |
| **God Object** | A massive class that tries to do everything by itself. | `PlayerViewModel` historically managed too many concerns (Issue #72). | Hard to maintain, slow to test, and prone to state bugs across screens. |
| **Interpolation** | Calculating smooth in-between steps between two time values. | Makes the seekbar progress smoothly between 250ms position ticks. | Seekbar moves with jerky, robotic jumps instead of gliding smoothly. |
