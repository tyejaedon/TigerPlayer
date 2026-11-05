# TigerPlayer v2.1.6 Release Notes 🐅🎵

Welcome to **TigerPlayer v2.1.6**! This release brings a massive leap forward in audiophile sound quality, seamless foldable outer-screen experiences, real-time synchronized lyrics, smart headphone integration, and rock-solid stability hardening.

Whether you're jamming on a Samsung Galaxy Z Flip or Motorola Razr, tuning your high-end headphones with AutoEq, or enjoying lossless FLAC playback, TigerPlayer v2.1.6 is crafted to give you the ultimate Android listening experience.

---

## 🌟 What's New at a Glance

### 📱 1. Next-Gen Foldable Cover Screen Experience
Your clamshell outer screen is now a first-class music command center:
- **Big, Bold Album Art**: Artwork now scales dynamically to fill your outer display (~165dp on compact screens, ~180dp on standard flip displays, and ~200dp on spacious screens).
- **Press-and-Hold Immersive Mode**: Simply long-press anywhere on the cover screen mini-hub to transition into a pure, full-screen audio-reactive visualizer. A gentle haptic pulse confirms the switch, and an unobtrusive hint chip guides you on how to bring controls back.
- **Full Settings on the Cover Screen**: You no longer need to unfold your phone to adjust preferences! Access theme toggles (pure AMOLED black, neon accents), Spotify & Navidrome account controls, backup/restore, custom music folder pickers, and playback tweaks directly from the outer display.
- **3D Playlist Wheel & On-Screen Management**: Browse playlists on a smooth 3D cylindrical carousel, create new playlists on the fly, reorder tracks with simple move controls, edit playlist cover photos, and rename playlists without unfolding.
- **Double-Tap to Like**: Love what's playing? A quick double-tap on the cover screen album art saves it to your favorites with a playful popping neon heart animation.

### 🎚️ 2. Studio Parametric Equalizer & AutoEq Headphone Nexus
Experience true studio-grade sound tailored specifically to your ears and headphones:
- **10-Band ISO Parametric Console**: Fine-tune your audio across ISO octave center frequencies (31 Hz – 16 kHz) with smooth touch faders and +/-0.5 dB micro-steppers.
- **AutoEq Database Integration (3,000+ Headphone Models)**: Search for your exact headphone model (Sony, Sennheiser, Audio-Technica, Bose, Apple, Beyerdynamic, and hundreds more) and apply acoustically calibrated target curves with a single tap.
- **Smart Composite Peak Headroom**: Say goodbye to sudden volume drops! Our new biquad filter designer computes true cumulative peak gain across all frequency bands, preventing digital clipping without unnecessary Fletcher-Munson volume attenuation.
- **Lossless True-Peak Limiter**: Enjoy uncompressed dynamic range on FLAC, WAV, and high-res PCM audio with transparent 1.0f (0.0 dBFS) true-peak protection that eliminates pumping artifacts.

### 🎤 3. Live Synchronized Lyrics
Sing along with precision:
- **Karaoke-Style Smooth Scrolling**: Lyrics scroll smoothly and stay centered on the current line, with an intelligent intro guard so early verses aren't missed.
- **Tap-a-Line to Seek**: Hear a line you love? Tap any lyric line to jump directly to that exact moment in the song.
- **Embedded & Sidecar Support**: Automatically extracts embedded lyrics from ID3 tags (`USLT`/`SYLT`) and FLAC Vorbis comments, reads local companion `.lrc` files, and seamlessly queries online lyric providers as a fallback.
- **Manual Timing Offset**: Fine-tune lyric timing by +/-0.5s directly from the lyrics view for perfect synchronization.

### 🎧 4. Smart Audio Devices Hub
Seamless intelligence for your listening hardware:
- **Device Profiles**: TigerPlayer now remembers your preferred EQ presets and settings separately for Phone Speakers, Wired Headphones, and each of your Bluetooth devices.
- **Audio Specs & Telemetry**: See active Bluetooth codecs (LDAC, aptX HD, AAC, SBC), battery levels, and cumulative listening time at a glance.
- **Race-Free Bluetooth Switching**: Hardened state synchronization ensures audio routing shifts cleanly when headphones connect or disconnect.

### 🌊 5. Harmonized Waveforms & Background Visualizers
- **Baseline-Anchored Reactive Waveforms**: Both the main player seekbar and cover screen scrubber feature 3-tier sinusoidal harmonic waves that anchor with mathematical precision to the baseline, reacting dynamically to kicks, bass, and audio energy.
- **Cinematic Fluid Vortex**: Tap the album card in the main player to switch between high-definition artist imagery and a hypnotic, audio-reactive fluid vortex visualizer.

### 📁 6. Universal M3U / M3U8 Playlist Support
- Import and export standard RFC 8216 and extended M3U/M3U8 playlists with support for relative/absolute paths, UTF-8 character encoding, and Windows/Mac/Linux line endings.
- Snapshot dynamic curation feeds (**Daylist** and **Discover Weekly**) into standalone playlists with a single tap.

### 🛠️ 7. Spotify Connection Diagnostics
- Frustrating OAuth errors are gone! The new diagnostics dialog explains exactly what needs attention (account permissions, developer app configuration, or offline mode) with clear, numbered steps and 1-tap retry actions.

---

## 🔒 Release Hardening & Reliability

This release underwent rigorous stress testing and quality validation:
- **Algorithmic Numerical Fuzzing**: Parametric biquad filter topologies were stress-tested across 1,000+ extreme frequency, gain, and Q combinations to verify zero mathematical instabilities (NaN/Infinity).
- **High-Volume Parser Verification**: The LRC lyrics parser and M3U playlist engine were verified against 5,000-line stress loads and malformed inputs.
- **Zero-Allocation Rendering**: Render loops are verified to eliminate per-frame allocations, maintaining 60/120 FPS fluid pacing without micro-stutters or thermal throttling.
- **100% Quality Gates Passed**: Static analysis (Lint), full unit test suites, and release packaging verified for both Full and FOSS flavors.

---

## 📦 Download & Installation

TigerPlayer is distributed in two flavors:

| Distribution Flavor | APK Asset | Description |
| :--- | :--- | :--- |
| **Full Release** | `app-full-release.apk` | Complete experience with Spotify App Remote integration, cloud lyrics, and streaming features. |
| **FOSS Release** | `app-foss-release.apk` | 100% free and open-source build without proprietary SDKs, compatible with F-Droid reproducible builds. |
| **Play Store Bundle** | `app-fullRelease.aab` | Android App Bundle for Google Play distribution. |

### Verifying Integrity
All release binaries include SHA-256 checksums (`SHA256SUMS.txt`), R8 ProGuard de-obfuscation mapping files, and a complete SPDX software bill of materials (`tigerplayer-sbom.spdx.json`).

---

## 🔗 Technical Changelog
For the complete technical breakdown of commits, pull requests, issue linkages, and code modifications, please see the [Complete Technical Changelog](https://github.com/tyejaedon/TigerPlayer/blob/master/CHANGELOG.md).

---

*Thank you to all our testers and open-source contributors for making TigerPlayer the most powerful, beautiful music player on Android!*
