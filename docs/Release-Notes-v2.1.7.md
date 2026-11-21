# TigerPlayer v2.1.7 Release Notes 🐅🎵

Welcome to **TigerPlayer v2.1.7**! This release delivers dynamic orbital motion physics to the Constellation Engine, a dedicated full-screen GPU Fluid Visualizer destination, proactive Home screen audio intelligence (AutoEq Smart Card & Acoustic Mood Carousel), a refined Studio Parametric Equalizer with real-time spectrum analysis, harmonized data-driven audio waveforms, optimized hardware image rendering, and rock-solid Cover Screen gesture continuity.

---

## 🌟 What's New at a Glance

### 🪐 1. Constellation Engine Motion Physics & Cover Screen Parity
Step into a living, dynamic audio universe with real-time orbital dynamics:
- **60/120 FPS Orbital Motion Physics**: Re-implemented `ConstellationPhysics` with elliptical orbits, orbital eccentricity, phase offsets, rotational speed variations, audio energy pulse modulation, and recursive parent-child position hierarchy.
- **Reeled-In Dense Galaxy View**: Artist planet orbital radii in `OrbitalLayoutEngine` have been reeled in by ~16x, creating a cohesive, densely packed galaxy view. The camera default zoom is updated to `0.45f` for instant clarity.
- **Cover Screen Constellation Parity**: Outer flip displays now feature live 60 FPS orbital motion rendering, interactive touch node selection, and artist reading cards (`CoverScreenConstellationView`).

### 🎨 2. Full-Screen Immersive Visualizer Screen
Unleash the full power of real-time GPU fluid visualizers:
- **Dedicated Destination**: Extracted `FluidRenderEngine` into a full-screen `ImmersiveVisualizerScreen` with spatial audio nodes and interactive fluid vortex simulation.
- **Header Launcher**: Easily jump into full-screen visualizer mode via a dedicated header button on the `FullPlayerScreen`.
- **Clean Engine Lifecycle**: Retains full lifecycle safety, automatically restoring Parametric EQ audio processing upon exiting the visualizer.

### 🎚️ 3. Studio Parametric Equalizer & Real-Time Spectrum Analyzer
Engineered for technical precision and audiophile sound shaping:
- **Pure Studio Console**: Refined `EqualizerHubView` into a streamlined studio PEQ console, expanding the frequency response curve height from 180dp to 240dp.
- **Real-Time Spectrum Analyzer**: Embedded a 6-band audio-reactive spectrum analyzer directly into `EqualizerFrequencyCurve`, driven by live telemetry from `AudioReactiveFrame`.
- **Decoupled Navigation & DSP Signal Chain**: Removed state-mutating side effects from tab selection so EQ tabs remain strictly navigational. Added explicit `EngineSourceToggle` controls and a persistent `DspSignalChainPill` badge visualizing active DSP pipeline blocks.

### 🎧 4. Home Screen Audio Intelligence & Acoustic Mood Carousel
Proactive sound management right on your Home dashboard:
- **Proactive AutoEq Smart Card**: Automatically detects connected headphones and surfaces matching acoustically calibrated profile recommendations on `HomeScreen` with 1-tap profile application.
- **Acoustic Mood Carousel**: Extracted Acoustic Space environments out of the EQ tab and into a dedicated `AcousticMoodCarousel` on `HomeScreen`, providing 1-tap toggling for global DSP spatial environments (Studio, Concert Hall, Lounge, Outdoor).

### 🌊 5. Harmonized Data-Driven Waveforms
Visual audio parity across every player interface:
- **Harmonized Amplitude Ratios**: Unified wave tier ratios (1x, 2x, 3x) across both `FieryWavySeeker` (Main Player) and `CoverMicroWaveform` (Cover Screen), reducing amplitude variance between displays from 12x down to a balanced 3x.
- **Data-Driven Wave Motion**: Modulates wave phase warping and energy dynamically using real-time audio telemetry from `DspEngine` and `WaveformEngine`.
- **Frozen Phase on Pause**: When playback is paused, waveform height and audio energy profiles remain fully intact with a frozen phase (`0f`) instead of collapsing, preserving visual parity between play and pause states.

### 📱 6. Cover Screen Gesture Contracts & Layout Continuity
Enhanced interaction design on clamshell flip displays:
- **Like Overlay Polish**: Fixed album art double-tap like animation in `CoverScreenMediaCycleViewport` with clean coroutine job cancellation (`heartAnimJob`) and explicit test tags (`CoverScreenTestTags.LIKE_HEART_OVERLAY`).
- **Layout Shift Elimination**: Matched lyrics container height to artwork dimensions (`targetArtHeight`), eliminating height jumps and layout shifts during swipe page transitions.
- **Recurring Fluid Vortex Toggle**: Standardized recurring press-and-hold (long-press) interaction on album artwork and media cycle viewports to toggle the audio-reactive Fluid Vortex visualizer mode.

### 🖼️ 7. Hardware Bitmap Caching & Dynamic 3D Header Transitions
Smoother scrolling and elevated visual depth:
- **Hardware Bitmap Caching**: Optimized `WetPaintingFadeImage` and `AsyncImage` rendering with hardware bitmap caching and single-pass blur layers.
- **Dynamic 3D Scroll Transitions**: Added dynamic 3D collapse, scale, and fade-out scroll transitions for cover art headers in `PlaylistDetailScreen`, `CoverScreenPlaylistDetailView`, and `CoverScreenAlbumsView`.
- **Header Shade Removal & Floating Art**: Removed dark overlay shading behind titles, and restored 3D floating cover artwork to `CoverScreenPlaylistDetailView` in alignment with main app design.

### 🎤 8. High-Contrast Lyrics & Precision Synchronization
Singing along made even clearer:
- **High-Contrast Active Lyrics**: Active lyric line highlighted in vibrant `TigerNeonOrange`, clearly distinguished from inactive lines.
- **High-Precision Sync**: Active lyric index tracking responds dynamically to playback position changes with pre-roll defaults for timestamped LRC lyrics.

### 🏗️ 9. Domain Engine Compartmentalization & Architecture Documentation
Clean architecture structure for high maintainability:
- **Subfolder Reorganization**: Reorganized `engine` package and unit tests into dedicated domain packages (`dsp/`, `graphics/`, `playback/`, `library/`, `stats/`, `network/`).
- **Comprehensive READMEs**: Authored modular architecture `README.md` guides across all package compartments under `com.tigerplayer`.

---

## 🔒 Quality & Hardening Status

- **Automated Stress & Unit Test Pass**: 100% unit tests passing across all engine subfolders, layout engines, waveform calculations, and cover screen gesture contracts.
- **Zero-Allocation Rendering**: Render loops are verified to run allocation-free on active frames for smooth 60/120 FPS performance.
- **Strict Lint & Compile Gate**: Verified clean build output with zero errors across both `full` and `foss` flavors.

---

## 📦 Download & Installation

TigerPlayer is available in two flavors:

| Edition | APK Asset | Description |
| :--- | :--- | :--- |
| **Full Release** | `app-full-release.apk` | Complete feature suite: Local Library + Spotify App Remote + Online Lyrics |
| **FOSS Release** | `app-foss-release.apk` | 100% Free & Open Source: zero proprietary binaries, zero tracking, F-Droid reproducible |
| **Play Store Bundle** | `app-fullRelease.aab` | Android App Bundle for Google Play distribution |

### Integrity Verification
All release assets include SHA-256 checksums (`SHA256SUMS.txt`), R8 ProGuard de-obfuscation mapping files, and a SPDX software bill of materials (`tigerplayer-sbom.spdx.json`).

---

## 🔗 Technical Changelog
For full technical details, commit logs, and issue references, see the [Complete Technical Changelog](https://github.com/tyejaedon/TigerPlayer/blob/master/CHANGELOG.md).

---

*Thank you for supporting TigerPlayer — the ultimate audiophile music experience for Android!*
