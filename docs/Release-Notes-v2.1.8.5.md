# TigerPlayer v2.1.8.5 Release Notes 🐅🎵

Welcome to **TigerPlayer v2.1.8.5**! This release delivers major enhancements across lyrics visualization, external vehicle and wearable playback routing, interactive graph astronomy, GPU shader rendering, and real-time audio pipeline efficiency: enhanced word-level synchronized lyrics (ELRC) with Apple Music-style progressive word fill animations, dual-line translation, and romanization; a pluggable lyrics provider architecture introducing Netease Cloud Music and Kugou integration alongside LRCLIB and Subsonic; full `MediaLibraryService` migration powering Android Auto and Wear OS hierarchical browse trees; an interactive Constellation album detail view and Cover Screen atomic artist orbit layout; calibrated screen-bound fluid visualizer containment on pure OLED black backdrops; and exhaustive lifecycle-gated CPU and battery optimizations throughout the real-time DSP pipeline.

---

## 🌟 What's New at a Glance

### 🎤 1. Enhanced Word-Level Synchronized Lyrics (ELRC) & Pluggable Provider Architecture
Sing along with word-by-word vocal synchronization and multi-language comprehension:
- **Enhanced Word-Level Timing (ELRC)**: Extended the lyrics domain model (`LyricLine`, `LyricWord`) and `LrcParser` to parse word-level timestamps (`<mm:ss.xx>`), line durations, and vocal singer roles (`LEAD`, `BACKGROUND`, `DUET_SECONDARY`).
- **Apple Music-Style Progressive Word Fill**: Integrated `calculateWordProgress` into `LyricsViewportCoordinator` and `PlayerLyrics`, smoothly driving per-word fill shaders and dynamic font scaling as each syllable is sung.
- **Dual-Line Translations & Romanization**: Automatically detects and pairs translated and romanized (Pinyin/Romaji) lyric lines within tight temporal windows, displaying synced translations directly beneath active lines.
- **Pluggable Lyrics Provider Architecture**: Decoupled lyrics fetching into a priority-ordered `LyricsProvider` engine (`NeteaseProvider`, `KugouProvider`, `LrcLibProvider`, `SubsonicLyricsProvider`), seamlessly falling back to remote multi-provider searches when local or embedded lyrics are unavailable.
- **Interlude Detection & Active Line Centering**: Automatically detects instrumental breaks exceeding 6 seconds and displays animated interlude dots; refactored viewport center offset math (`itemHeightPx / 2`) to ensure pixel-perfect vertical centering in padded Compose viewports on compact cover screens.

### 🚗 2. MediaLibraryService Migration for Android Auto & Wear OS
Seamless in-car and wearable audio navigation:
- **MediaLibrarySession Upgrade**: Migrated `AudioPlayerService` from legacy `MediaSessionService` to modern AndroidX `MediaLibraryService`, establishing a bidirectional IPC media library session.
- **Structured Two-Tier Browse Tree**: Introduced `MediaBrowseTree` domain engine delivering categorized root tabs (`Albums`, `Artists`, `Playlists`, `Folders`, `Heavy Rotation`, and `Day List`) with full pagination, search, and container-to-track resolution.
- **Automotive & Wear OS Integration**: Declared `automotive_app_desc.xml` manifest descriptor and registered `MEDIA_PLAY_FROM_SEARCH` intent filters, enabling voice commands ("Play X on TigerPlayer") and standard head-unit steering wheel navigation.
- **Lossless Queue IPC Preservation**: Unified queue serialization with `MediaItemConverter` to guarantee zero playback state loss across automotive and background process boundaries.

### 🌌 3. Constellation Album Detail View & Cover Screen Atomic Artist Orbits
Next-generation astronomical music navigation:
- **Constellation Album Detail View**: Added dedicated album detail inspection directly inside the interactive Constellation graph, featuring orbiting track nodes, metadata badges, and single-tap album playback.
- **Cover Screen Atomic Artist View**: Created `CoverScreenAtomicArtistView` specifically designed for clamshell flip screens, rendering artists with orbiting electron album spheres and an interactive asteroid belt track ring.
- **Physical Orbit Dynamics**: Tuned repulsion forces, drag resistance, and boundary attraction to ensure smooth, organic celestial motion during high-speed gestures.

### 🌊 4. Fluid Visualizer Screen-Bound Containment & High-Contrast Backdrop
Razor-sharp audio-reactive particle dynamics:
- **Screen-Bound Particle Containment**: Recalibrated `FluidConfig` orbital radii and splat expansion scales to ensure fluid vortices and velocity fields stay strictly contained within screen boundaries with a safe ~8–10% margin.
- **Pure OLED High-Contrast Backdrop**: Implemented aspect-aware containment falloff in `displayFrag` and `bloomCompositeFrag`, rendering boundary regions as true high-contrast OLED black (`#000000`).
- **Dynamic Spectral Vibrance**: Enhanced audio-reactive chromatic vibrance, saturating warm amber bass swells and electric cyan transients driven by dynamic album artwork palette swatches ($C_1, C_2, C_3$).
- **Compose Blur Removal**: Removed unnecessary Compose-layer blur filters on standard player modes to preserve razor-sharp fluid eddy definition.

### ⚡ 5. Audio Pipeline & Real-Time DSP Battery Optimization
Uncompromising audiophile sound with minimal power drain:
- **Visibility & Visualizer Gating**: Gated FFT analysis and spectral extraction in `AdaptiveDspEngine` to sleep when visualizers are inactive or the display is turned off, reducing background CPU consumption.
- **ExoPlayer AudioSink Streamlining**: Decommissioned redundant `FftProcessor` instances from the primary audio sink chain in `AudioPlayerService`.
- **Waveform Capture Optimization**: Introduced lifecycle-aware capture gating in `WaveformCaptureProcessor`, bypassing unnecessary buffer duplication when waveform UI components are hidden.
- **Lifecycle-Bound StateFlow Streams**: Transitioned audio telemetry streams in `PlaybackStateManager`, `EqualizerHubViewModel`, and `PrismViewModel` to lifecycle-aware flows that cleanly pause when the app is backgrounded.
- **Hardware Offload Battery Indicator**: Added a "(saves battery)" indicator for System Decoder / Hardware Offload DSP toggles in Settings.

### 🎚️ 6. Modularized Waveform Engine & Cover Screen UI Polish
- **Waveform Domain Modularization**: Extracted core harmonic path mathematics into `WaveformComputation` in `engine/graphics/`, decoupling rendering from calculations and eliminating heap allocations during per-frame wave rendering.
- **Responsive Waveform Animation Speed**: Tuned fluid phase drift to 400ms for punchy, reactive wave dynamics.
- **Curated Feeds Save Parity**: Added quick-action playlist save buttons (`Icons.AutoMirrored.Rounded.PlaylistAdd`) in `CoverScreenDaylistDetailView` and `CoverScreenDiscoverWeeklyDetailView`.
- **Cover Screen Track Dashboard**: Modularized the cover screen track dashboard with streamlined playback telemetry and clear tactile controls.
- **Seek While Paused Stability**: Fixed playback slider snapback when seeking while paused via immediate optimistic position dispatch.

---

## 🔒 Quality & Hardening Status

- **Automated Stress & Unit Test Pass**: 100% unit tests passing across all engine subfolders, DSP math validation, ELRC parsers, provider architecture, and viewport coordinators (594 tests, 0 failures).
- **Zero-Allocation Rendering**: Render loops are verified to run allocation-free on active frames for smooth 60/120 FPS performance.
- **Strict Lint & Compile Gate**: Verified clean build output with zero errors across both `full` and `foss` flavors.

---

## 📦 Download & Installation

TigerPlayer is available in two flavors:

| Edition | APK Asset | Description |
| :--- | :--- | :--- |
| **Full Release** | `app-full-release.apk` | Complete feature suite: Local Library + Spotify App Remote + Online Lyrics (ELRC/Netease/Kugou) |
| **FOSS Release** | `app-foss-release.apk` | 100% Free & Open Source: zero proprietary binaries, zero tracking, F-Droid reproducible |
| **Play Store Bundle** | `app-fullRelease.aab` | Android App Bundle for Google Play distribution |

### Integrity Verification
All release assets include SHA-256 checksums (`SHA256SUMS.txt`), R8 ProGuard de-obfuscation mapping files, and a SPDX software bill of materials (`tigerplayer-sbom.spdx.json`).

---

## 🔗 Technical Changelog
For full technical details, commit logs, and issue references, see the [Complete Technical Changelog](https://github.com/tyejaedon/TigerPlayer/blob/master/CHANGELOG.md).

---

*Thank you for supporting TigerPlayer — the ultimate audiophile music experience for Android!*
