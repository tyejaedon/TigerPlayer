# TigerPlayer v2.1.9 Release Notes 🐅🎵

Welcome to **TigerPlayer v2.1.9**! This release brings comprehensive AutoEQ profile lifecycle management, complete feature and layout parity across clamshell Cover Screen and Main App views, pixel-perfect sequential active lyrics viewport centering in padded viewports, and notification media controls with a quick-like action.

---

## 🌟 What's New at a Glance

### 🎛️ 1. AutoEQ Configuration Lifecycle: Disable & Remove Profiles
Take total control over applied AutoEQ headphone profiles with zero acoustic residue:
- **Clean Profile Removal**: Introduced `removeAutoEqProfile()` in `EqualizerHubViewModel` and `removeAutoEqFromDevice()` across `AudioDeviceRepository`, `AudioDeviceEngine`, and `AudioDevicesViewModel`. Removing an AutoEQ configuration cleanly unbinds device-associated AutoEQ metadata, deletes the bound AutoEQ preset, resets parametric equalizer bands to flat (0.0 dB), and restores digital preamp headroom to 0.0 dB.
- **Ubiquitous Main & Cover Screen Entry Points**:
  - **Equalizer Hub**: Added prominent AutoEQ disable and remove buttons (`MainEqRemoveAutoEqButton`, `MainEqDisableAutoEqChip`, `EqRemoveAutoEqButton`, `EqDisableAutoEqButton`) on active profile status pills and quick preset bars in both Main App and Cover Screen views.
  - **AutoEQ Selector Sheet**: Added an `ACTIVE AUTOEQ` banner featuring a direct `"DISABLE"` button (`AutoEqSheetRemoveButton`).
  - **Cover Screen Home Hub**: Added a quick-clear button (`CoverHomeClearAutoEqButton`) on the Cover Screen Home Equalizer card badge.
  - **Audio Devices Hub & Headphone Selector**: Added `"REMOVE"` actions on active device route hero cards, historical device items, and the AutoEQ headphone selector dialog.

### 📱 2. Clamshell Cover Screen & Main App Complete Feature Parity
Zero compromises when using compact outer flip displays:
- **Studio Parametric EQ Parity (`CoverScreenEqualizerLayout`)**:
  - Interactive 2D frequency curve canvas with real-time audio-reactive node dragging.
  - Direct `DspSignalChainPill` live telemetry monitor.
  - `5-Band` and `10-Band` ISO layout switcher.
  - Granular `PREAMP` digital headroom slider (-15 dB to +15 dB).
  - Per-band `ON` / `BYP` bypass toggles.
  - Glowing `TUNE FREQUENCY BANDS` action launcher opening the full `FrequencyBandsTuningOverlay`.
  - Clickable target device header opening the comprehensive `AudioDevicesHubSheet`.
- **Spatial Nexus Parity (`CoverScreenNexusView`)**:
  - Unified `CoverScreenEqPreset` alignments, including the `Transparency (SpaceBuds Z)` mood preset.
  - Integrated `DspSignalChainPill` live telemetry monitor into the Cover Screen view.
- **Sonic Prism Parity (`CoverScreenPrismView`)**:
  - Added horizontally scrollable `PrismPreset` chips (`Balanced`, `Vocal Focus`, `Beat Punch`, `Instrumental`).
  - Added a dedicated 1-tap `"Reset"` action to restore stem faders to balanced defaults.
- **Settings Parity (`CoverScreenSettingsView`)**:
  - **Visualizer & Waveform Matrix**: Configurable `VisualizerPaletteMode` (Album Art vs. Default Neon), fluid sensitivity slider (0.5x–2.0x), and waveform sensitivity slider (0.5x–2.0x).
  - **Storage & Cache Management Matrix**: Total disk usage display, live storage breakdown for artwork, lyrics, artist metadata, and waveform peaks, individual subsystem cache clear actions, and a master "Purge All Temporary Archives" confirmation dialog.

### 🎤 3. Sequential Active Lyric Line Viewport Centering
Sing along with smooth, jitter-free lyric line tracking:
- **True Viewport Midpoint Math**: Fixed a coordinate-space mismatch in `PlayerLyrics` where sequential $n + 1$ lyric line transitions computed the viewport center using span span dimensions rather than true padded `LazyListLayoutInfo` coordinates (`(viewportStartOffset + viewportEndOffset) / 2`).
- **Smooth Incremental Anchoring**: Extracted `LyricsViewportCoordinator.calculateIncrementalScrollDelta` with unit test validation, guaranteeing active lyric lines remain vertically centered in padded Compose viewports on compact cover screens and full displays alike.

### 🔔 4. Media Notification Controls & SpaceBuds Alignment
- **Quick-Like Action**: Added a direct favorite/heart toggle action directly within the system playback notification controls.
- **SpaceBuds Z Preset Alignment**: Integrated custom transparency and acoustic tuning presets for SpaceBuds Z across playback and equalization pipelines.

---

## 🔒 Quality & Hardening Status

- **Automated Stress & Unit Test Pass**: 100% unit tests passing across all engine subfolders, AutoEQ lifecycle coordinators, device repositories, and lyrics viewport coordinators (596 tests, 0 failures).
- **Zero-Allocation Rendering**: Render loops and frequency curve calculations run allocation-free on active frames for smooth 60/120 FPS performance.
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
