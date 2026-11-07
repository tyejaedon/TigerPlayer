# TigerPlayer v2.1.8 Release Notes 🐅🎵

Welcome to **TigerPlayer v2.1.8**! This release brings major visual, acoustic, and performance upgrades across TigerPlayer: an in-place Immersive Fluid Visualizer mode and spring-physics gesture polish in the Main Player, anticipatory sub-frame rolling lyrics with 3D cyclic carousel projection, an application storage breakdown and granular cache management matrix, an algorithmic overhaul of the Vinyl Warmth DSP engine, dynamic album artwork palette binding for fluid GPU simulation, an all-new side-scrollable Frequency Bands tuning overlay for the Studio Parametric EQ, restored sweeping curved waveforms, and comprehensive Cover Screen gesture isolation and detail view parity.

---

## 🌟 What's New at a Glance

### 🌊 1. In-Place Immersive Fluid Visualizer & Main Player Gesture Polish
Experience seamless transitions and fluid visual immersion directly within the player:
- **In-Place Immersive Vortex Mode**: Replaced the legacy header EQ button with an in-place Immersive Fluid Visualizer toggle (`Icons.Rounded.Waves`). Toggling effortlessly animates blur radius, scrim, and player controls to zero alpha while maintaining a full-bleed tap-anywhere dismiss layer, a floating hint chip, and back-press restoration.
- **Drag-Down-to-Collapse**: Added an intuitive, natural drag-down gesture on the player header to collapse the full player sheet smoothly.
- **Synchronized Spring Physics**: Player sheet dismiss transitions are perfectly synchronized with background library scaling using matching spring physics (`stiffness = 250f`, `dampingRatio = 0.85f`).
- **Directional Slide Transitions**: Replaced jarring zoom/inflation scaling during artwork, lyrics, and queue switches with crisp horizontal directional slide animations.
- **Zero-Latency MiniPlayer**: Eliminated a 300ms tap latency penalty on the MiniPlayer by scoping the double-tap-to-like gesture exclusively to the artwork thumbnail.

### 🎤 2. Anticipatory Rolling Lyrics Viewport & 3D Cylindrical Carousel
Sing along with studio-grade lyric motion and fluid sub-frame precision:
- **Anticipatory Viewport Coordinator**: Introduced `LyricsViewportCoordinator` with an anticipatory ~750ms lead-in window and continuous per-frame position extrapolation (`withFrameMillis`), eliminating the 250ms polling quantization lag of discrete snap scrolling.
- **Dynamic Multi-Line Height Caching**: Dynamically caches variable line heights to guarantee exact, continuous vertical centering regardless of lyric length.
- **Calmed Spring Damping**: Calmed active line scale springs with non-bouncy damping (`Spring.DampingRatioNoBouncy`) for buttery smooth emphasis transitions.
- **3D Cylindrical Cyclic Carousel**: Added 3D perspective camera projection (`rotationX`, depth scaling, and dynamic alpha/color transitions) that curves upcoming and past lines naturally around a virtual cylinder.

### 💾 3. Application Storage Breakdown & Cache Management
Take granular control over application disk usage and temporary caches:
- **Comprehensive Storage Breakdown Matrix**: Integrated real-time storage footprint calculation into `SettingScreen` (`StorageRepository`, `StorageBreakdown`), detailing disk consumption across artwork caches, synchronized lyrics, artist biographies, waveform analysis data, and core Room/DataStore persistence.
- **Arcane Glass Segmented Progress Bar**: Visualizes the proportional disk share of each cache component with sleek, color-coded segmented progress indicators.
- **Targeted Subsystem Purging**: Added individual 1-tap "Clear" actions per purgeable cache subsystem alongside a master "Purge All Temporary Archives" action, backed by safety confirmation dialogs ensuring user playlists and listening history are never deleted.

### 💽 4. Vinyl Warmth DSP Engine Overhaul
Authentic analog warmth with zero digital artifacts or distortion:
- **Artifact-Free Circular Delay & Fractional Interpolation**: Corrected circular delay buffer indexing and backward linear interpolation math, eliminating periodic comb-filtering glitch spikes caused by wrapping into 23ms-old history when delay modulation dropped below 1 sample.
- **Authentic Turntable Wow Modulation**: Recalibrated the 0.55 Hz wow LFO depth to an authentic ~2–3 cents pitch flutter (~0.15% excursion).
- **$C^1$-Continuous Cubic Soft Clipper**: Replaced folding polynomial non-linearity with a smooth, continuous cubic soft saturation curve ($f(x) = x - x^3 / 6.75$ for $|x| \le 1.5$) with zero first derivative at saturation boundaries, eliminating foldback distortion and boundary step jumps while introducing warm 2nd and 3rd harmonics.
- **Stereo Decorrelated Noise & Silence Gating**: Replaced harsh mono white hiss with stereo decorrelated noise generators, low-pass filtered surface warmth, ~28 Hz platter rumble, and dynamic envelope-tracked silence gating to silence the noise floor during pauses.
- **Auto-Headroom Gain Staging**: Added -2.5 dB pre-attenuation auto-headroom before the RIAA low-shelf filter (+2.5 dB at 350 Hz) to guarantee unity gain and prevent digital clipping on hot signals.
- **Audio Thread Safety**: Pre-allocated `vinylShelf` biquad filter and reconfigured parameters on sample rate changes rather than allocating on the real-time audio loop.

### 🎨 5. Dynamic Artwork Palette Binding & Fluid GPU Optimization
Visualizer colors that adaptively harmonize with your music:
- **Dynamic Artwork Palette Extraction**: Dynamically extracts vibrant, dominant, and muted swatches via `Palette` from album art in both `FullPlayerScreen` and `CoverScreenMiniHub`, routing palette colors into `FluidRenderEngine` to drive bass, mid, treble, and ambient splat colors.
- **Shader Tone Mapping Refinement**: Refactored fluid fragment shaders in `Shaders.kt` to preserve native hue saturation, apply smooth highlight compression, and introduce dynamic audio-reactive spectral glow.
- **Visualizer & Waveform Settings**: Added user preferences for visualizer sensitivity, waveform sensitivity, and palette mode (Album Art vs. Neon Default) in `SettingScreen`.
- **Zero-Allocation GPU Render Loop**: Refactored `FluidRenderEngine.applySplat` to accept scalar RGB components directly, eliminating 3–7 per-frame heap allocations (`floatArrayOf(...)`) during force injection.
- **Idle Simulation Throttling**: Introduced progressive idle energy decay gating that smoothly scales down ambient swirl power during prolonged silence, saving battery and reducing mobile GPU thermals.
- **EGL Thread Cleanup**: Queued GL renderer destruction on the EGL thread in `FluidVortexView` to prevent background EGL context leaks.

### 🎚️ 6. Studio Parametric EQ Frequency Bands Tuning Overlay
Precision equalization designed for finger-friendly tuning:
- **Glowing Action Launcher**: Replaced the embedded fader bank on `EqualizerHubView` with a sleek, glowing capsule action button matching preamp width.
- **Dedicated Dark Opaque Tuning Overlay**: Created `FrequencyBandsTuningOverlay` for focused, distraction-free equalizer adjustment.
- **Side-Scrollable Studio Faders**: Hosted side-scrollable `StudioFaderStrip` components with an optimal 76dp width for maximum touch accuracy, complete with interactive frequency response curve previews and reset/done actions.

### 🌊 7. Restored Sweeping Curved Waveform Aesthetics
Back to pure, continuous fluid motion:
- **Fluid Sinusoidal Curvature**: Restored the sweeping continuous curvature and glassmorphic 2.5D specular glow to `CoverMicroWaveform` and `FieryWavySeeker`.
- **Harmonized 2.0 Wave Cycles**: Standardized 2.0 wave cycles and 1.0x fundamental frequency across tiers, eliminating artificial edge-pinching boundary tapering and discrete sample-stepped phase kinks.
- **Calm Hypnotic Phase Drift**: Reinstated calm 820ms hypnotic phase drift and crisp specular white cores with tightened vertical energy breathing dynamics.

### 📱 8. Cover Screen Ambient Gesture Isolation & Detail View Parity
Rock-solid interaction and visual parity on clamshell flip displays:
- **Ambient Gesture Conflict Resolution**: Added `controlsTouchBounds` and `mediaCycleTouchBounds` into root gesture ignore checks, eliminating accidental track skipping during lyrics scrolling or double-tap interactions.
- **Symmetric Edge Exclusion Zones**: Introduced 12% symmetric edge exclusion zones across all four display borders to preserve Android system notification shade pull-down.
- **Modal Dialog Overlays**: Mounted modal dialog overlays in `CoverScreenHomeView` for `CoverSonicFootprintCard` (`SonicFootprintScreen`), `CoverSpatialNexusCard` (`CoverScreenNexusView`), and `CoverAcousticSpaceCard` (`AcousticSpaceView`).
- **Redesigned Artist Detail View**: Redesigned `CoverScreenArtistHero` into a centered frosted glass card without redundant nested artwork, maximizing vertical content area while retaining horizontal swipe navigation.
- **Album Detail Background & Floating Hero Art**: Aligned `CoverScreenAlbumsView` background with `CoverScreenPlaylistDetailView` using full-screen ambient blur and dark overlay, alongside an unblurred 3D floating hero artwork card.

### ⚡ 9. Performance, Database Indexing & Platform Stability
- **Room Database v17 Auto-Migration**: Added `@Index` annotations on `PlaybackHistoryEntity` (`trackId`, `timestamp`) and `CachedTrackEntity` (`trackId`, `albumId`, `artistId`) with verified SQLite auto-migrations.
- **Memory Footprint Optimization**: Capped Coil `ImageLoader` memory cache to 12% application heap and explicitly clear image caches in `AudioPlayerService.onDestroy()`.
- **Pre-Seed Timeout Resolution**: Eliminated 5000 ms timeout stalls and warning logs in `MetadataEngine.preSeedArtistCache()` when caching artists without artwork.
- **Constellation Orbital Spacing**: Increased base spiral arm radius from 680f to 950f and enlarged orbital chain radii for improved spatial separation and touch target clarity.
- **Platform Dependencies**: Upgraded Android Gradle Plugin to 9.5.0 and Google Gson to 2.15.0.

---

## 🔒 Quality & Hardening Status

- **Automated Stress & Unit Test Pass**: 100% unit tests passing across all engine subfolders, DSP math validation, storage repositories, fluid renderer sensitivity clamping, gesture isolation contracts, and cover screen viewports.
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
