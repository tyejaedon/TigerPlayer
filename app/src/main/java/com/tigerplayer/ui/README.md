# TigerPlayer UI & Presentation Compartment (`ui/`)

This directory contains the user interface and presentation layer of TigerPlayer, built entirely with modern **Jetpack Compose** and structured under unidirectional reactive data flow (MVI / StateFlow).

---

## 1. Plain-English Overview

Think of the **UI Compartment** as the "dashboard, instrument panel, and stage" of TigerPlayer:
- It is everything you see and touch: the full-screen record player, the playlist scroller, the spinning neon vortex visualizers, and the cover-screen widget on foldable phones.
- It strictly listens: it never directly reads from the database or controls the audio hardware. Instead, it observes ViewModels and displays the latest state (what song is playing, album artwork, elapsed seconds).
- When you tap a button (like play, skip, or adjust an EQ slider), the UI sends an event to the ViewModel and lets the lower engines do the work.

---

## 2. Compartment Architecture & Compose Flow

```
[User Gesture / Screen Tap]
             │
             ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Jetpack Compose UI Screens (`ui/`)                                     │
│                                                                        │
│ ┌──────────────────────┐  ┌───────────────────┐  ┌───────────────────┐ │
│ │ FullPlayerScreen     │  │ LibraryScreen     │  │ HomeScreen        │ │
│ │ - Controls & Seeker  │  │ - Songs, Albums   │  │ - Quick Play      │ │
│ │ - Lyrics & Queue     │  │ - Artists, Folders│  │ - Heavy Rotation  │ │
│ └──────────┬───────────┘  └─────────┬─────────┘  └─────────┬─────────┘ │
│            │                        │                      │           │
│ ┌──────────┴───────────┐  ┌─────────┴─────────┐  ┌─────────┴─────────┐ │
│ │ SonicPrismScreen     │  │ CoverScreenHub    │  │ Constellation     │ │
│ │ - 3-Stem Isolator    │  │ - Foldable Screen │  │ - Visual Node Map │ │
│ └──────────────────────┘  └───────────────────┘  └───────────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Hoisted Events & Actions
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ ViewModel & Coordinator Layer                                          │
│                                                                        │
│  [PlaybackViewModel]    [LibraryViewModel]      [StatsViewModel]       │
│           │                     │                       │              │
│           └─────────────────────┼───────────────────────┘              │
│                                 ▼                                      │
│                      [PlaybackStateManager]                            │
│                      (@Singleton State Coordinator)                    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Emits StateFlow<UiState>
                                    ▼
                           [Domain Engines]
```

---

## 3. Core Feature Sub-compartments

### 1. Player Experience (`ui/player/`)
* **`FullPlayerScreen`**: The primary full-screen playback experience including album artwork, dynamic blur backdrop, seekbar, and playback controls.
* **`MiniPlayer`**: The persistent bottom player bar visible across navigation tabs, displaying active track title, artist, progress indicator, and play/pause controls.
* **`TigerVortexRender` & `PipVisualizerSurface`**: High-performance real-time visualizers powered by Android Graphics Shading Language (AGSL) and Compose Canvas.
* **`PlaybackStateManager`**: A `@Singleton` state holder coordinating player state across multiple ViewModels and screens to prevent desynchronization.
* **`PlaybackViewModel`**: Exposes immutable `PlaybackUiState` (`isPlaying`, `currentPosition`, `duration`, `isShuffle`, `repeatMode`).

### 2. Library Navigation (`ui/library/`)
* **`LibraryScreen`**: Multi-tab library browser with tabs for **Songs**, **Albums**, **Artists**, **Playlists**, and **Folders**.
* **`LibraryViewModel`**: Aggregates track listings, applies search query filters, sorts tracks alphabetically or by date added, and triggers library scans.

### 3. Sonic Prism (`ui/prism/`)
* **`SonicPrismScreen`**: Interactive stem-isolation interface allowing independent real-time volume manipulation of Vocals, Beats, and Instruments.
* **`PrismViewModel`**: Translates slider gestures into `PrismMixLevels` dispatched to `AdaptiveDspEngine`.

### 4. Foldable Cover Screen (`ui/coverscreen/`)
* **`CoverScreenDiscoveryView` & `CoverScreenMiniHub`**: Specialized UI rendered on external cover displays (e.g. Motorola Razr, Samsung Galaxy Z Flip).
* **Display ID Detection:** Uses display-aware metrics to mount mini-controls without launching the full main window.

### 5. Design System & Theming (`ui/theme/`)
* **Arcane Glass Design Standard:** Custom translucent glass surfaces (`PremiumGlassCard`), frosted blur backdrops, and neon accent highlights.
* **`DominantColorExtractor`**: Extracts primary and secondary vibrant accent colors from album artwork to tint background gradients dynamically.
* **`ColorSafety`**: Ensures extracted colors meet WCAG contrast guidelines against dark backgrounds for accessible legibility.

---

## 4. UI Rendering Performance Guardrails (60 / 120 FPS)

To prevent dropped frames, stutter, and battery drain:
1. **Zero Allocation in Draw Passes:** Never allocate objects (`Paint`, `Path`, `Shader`, arrays, or lambda closures) inside `Canvas`, `drawWithContent`, or `onDrawBehind`. All drawing coordinates and paints must be pre-allocated in `remember` blocks.
2. **Recomposition Decoupling:** Keep position scrubber updates decoupled from library and settings lists. The 4 Hz playback position ticks update only `PlayerSeeker` and `MiniPlayer` progress bars, preventing full-screen recompositions.
3. **Stable Parameter Types:** Ensure composable parameters use `@Immutable` or `@Stable` data models and `ImmutableList` to enable smart recomposition skipping.

---

## 5. Known Limitations & Tech Debt

1. **Issue #72 (`PlayerViewModel` God Object):** `PlayerViewModel` was historically a 603-line god object shared across multiple screens. New features must use scoped ViewModels (`PlaybackViewModel`, `QueueViewModel`, `LibraryViewModel`) coordinated by `PlaybackStateManager`.
2. **Cover Screen Layout Constraints:** Cover screens have limited screen real estate and lower touch sampling rates; avoid multi-touch gestures on external displays.

---

## 6. Tabulated UI Jargon Glossary

| Jargon Term | Plain-English Definition | Role in UI Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Jetpack Compose** | Android's modern toolkit for building user interfaces using Kotlin code. | Renders all buttons, screens, and animations in TigerPlayer. | UI cannot be rendered. |
| **Recomposition** | Redrawing only the parts of a screen whose data has changed. | Updates song titles or time clocks without redrawing the entire screen. | Screen stutter and high battery usage if unnecessary areas re-render. |
| **StateFlow** | A reactive stream that emits the latest state value to observers. | Delivers playback positions, track metadata, and queue lists to screens. | UI displays stale information or fails to update when songs switch. |
| **Hoisting** | Moving event logic out of a button up to its parent screen or ViewModel. | Keeps UI components modular, previewable, and testable. | Code becomes tightly coupled and difficult to test or reuse. |
| **AGSL (Android Graphics Shading Language)** | Code executed directly on the graphics card (GPU) for visual effects. | Animates the swirling neon vortex and fluid visualizer shaders. | Visualizers drop below 60 FPS or overload the CPU. |
| **Zero Allocation** | Writing code that reuses existing variables instead of creating new ones. | Protects visualizers and list scrolling from garbage collection stutter. | Micro-stutters and choppy animation frame drops. |
| **Dominant Color** | The most prominent color automatically detected in album artwork. | Tints the background gradient and accents to match the playing song. | Background remains generic gray or clashes with album art. |
