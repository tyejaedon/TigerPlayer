# TigerPlayer 🐅🐺

> **[v2.0 "NEON VANGUARD"]**
> *A high-fidelity, dual-engine Android music player that unifies local archives, remote servers, and cloud signals with neon visuals and listening intelligence.*

TigerPlayer 2.0 is a major architectural overhaul focused on three pillars:

1. **Playback fidelity and control** (local Media3 + Spotify remote routing)
2. **Discovery intelligence** (Day List + Discovery Weekly + listening analytics)
3. **Immersive presentation** (GPU fluid visualizer + reactive neon UI)

This README is intentionally implementation-aware so contributors can move from setup to code quickly.

---

## Table of Contents

- [What TigerPlayer Is](#what-tigerplayer-is)
- [Core Features](#core-features)
- [Architecture Overview](#architecture-overview)
- [Discovery Logic (Day List + Discovery Weekly)](#discovery-logic-day-list--discovery-weekly)
- [Project Layout](#project-layout)
- [Requirements](#requirements)
- [Setup](#setup)
- [Build and Run](#build-and-run)
- [Testing and Validation](#testing-and-validation)
- [Troubleshooting](#troubleshooting)
- [Release Notes](#release-notes)
- [Contributing](#contributing)

---

## What TigerPlayer Is

TigerPlayer is an Android-first player built with Jetpack Compose and Media3, designed to serve as a **single control surface** for:

- Local on-device music libraries
- Spotify App Remote sessions
- Remote metadata/enrichment services (for analytics and profile quality)

Instead of treating local and cloud as separate apps, TigerPlayer routes playback commands through a unified engine and adapts UI behavior by source.

---

## Core Features

### 1) Dual-engine playback routing

- Unified playback control for local Media3 and Spotify App Remote.
- Command routing includes play, pause, seek, skip, shuffle/repeat, and queue actions.
- Queue state persistence and restoration for better process-death resilience.

### 2) Discovery + analytics surfaces

- **Day List**: time-bucketed discovery by listening window (Morning, Afternoon, Evening, Night).
- **Discovery Weekly**: stale/new track resurfacing with genre affinity weighting.
- **Heavy Rotation** and high-density listening stats backed by Room queries.
- **Sonic Footprint**: maps listening behavior into dimensions such as acoustic/electronic/bass/instrumental.

### 3) DSP and listening environments

- Acoustic environments include **Neutral**, **Vinyl Warmth**, and **Concert Hall** profiles.
- Crossfade flow controls with user-tunable duration and conflict handling around transport actions.
- Sonic Prism real-time mix controls with spectral analysis modes.

### 4) Visual system and UI

- GPU-driven fluid visualizer (Navier-Stokes-inspired shader pipeline).
- ACES-style tone mapping, bloom/vignette, and audio-reactive splat behavior.
- Neon token snapping and ambient art-derived color treatment.
- Full player, mini player, and foldable cover-screen mini hub surfaces.

### 5) Reliability and release hardening

- Release minification and resource shrinking are enabled.
- Proguard rules are wired for media/network/database stack.
- Debug health support includes StrictMode and LeakCanary integration.

---

## Architecture Overview

TigerPlayer follows MVVM with repository/data-source boundaries and reactive state via Kotlin Flows.

### UI layer

- Compose screens for Home, Library, Cloud, Full Player, Queue, Settings, and detail routes.
- Navigation graph in `app/src/main/java/com/example/tigerplayer/navigation/NavGraph.kt`.
- Bottom-tab shell and player sheet orchestration in `app/src/main/java/com/example/tigerplayer/ui/main/MainScreen.kt`.

### Domain/engine layer

- Playback routing and state coordination through engine/service classes.
- DSP processing and spectral analysis in engine package.
- Metadata and enrichment orchestration for artist and lyrics experiences.

### Data layer

- Room database + DAOs for track cache, playback history, artist cache, and analytics reads.
- DataStore-backed settings and persisted user controls.
- Retrofit-based integrations for remote metadata APIs.

---

## Discovery Logic (Day List + Discovery Weekly)

### Day List

- Data source: `DashboardViewModel.daylistTracks`.
- Repository bridge: `AudioRepository.getDaylistTracks(...)`.
- DAO query: `TigerDao.getDaylistTracks(...)`.
- Strategy: chooses tracks based on recent listening buckets plus artist/genre affinity.

### Discovery Weekly

- Data source: `DashboardViewModel.discoveryWeeklyTracks`.
- Repository bridge: `AudioRepository.getDiscoveryWeeklyTracks(...)`.
- DAO query: `TigerDao.getDiscoveryWeeklyTracks(...)`.
- Strategy:
  - Include never-played tracks and stale tracks.
  - Boost candidates matching top-genre signals from listening history.
  - Randomize final ordering and cap list size.

### UI flow for Discovery Weekly

1. Home screen shows curation row when `discoveryWeeklyTracks` is non-empty.
2. Navigation route opens `discover_weekly_detail?origin=...`.
3. Detail screen supports Play Feed, Random Scan, and per-track play actions.

---

## Project Layout

```text
TigerPlayer/
  app/
	src/main/java/com/example/tigerplayer/
	  data/           # Room entities/dao, repositories, remote api
	  engine/         # Playback, DSP, render/analysis engines
	  navigation/     # Screen routes + nav graph
	  service/        # Media service/controller integration
	  ui/             # Compose screens and components
  docs/               # Validation, issue review, implementation notes
  gradle/             # Version catalog + wrapper config
```

---

## Requirements

- **Android Studio**: Ladybug or newer recommended.
- **JDK**: 17 (project compile target).
- **Android SDK**:
  - `minSdk = 29`
  - `targetSdk = 36`
  - `compileSdk = 37`
- **Device recommendation**: Android 13+ for best UI/visualizer behavior.

Reference: `app/build.gradle.kts`.

---

## Setup

TigerPlayer reads cloud/API secrets from a root-level `secrets.properties`.

### 1) Create `secrets.properties`

```properties
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
LASTFM_API_KEY=your_lastfm_key
YOUTUBE_API_KEY=your_youtube_key
```

If this file is missing, the build still compiles with placeholder values, but cloud integrations will not work correctly.

### 2) Spotify dashboard setup

1. Register an app at the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Whitelist package `com.example.tigerplayer` and your signing SHA-1.
3. Ensure redirect placeholders match app config (`tigerplayer://callback`).

### 3) Open and sync

1. Open project root in Android Studio.
2. Let Gradle sync complete.
3. Confirm SDK/platform packages requested by AGP are installed.

---

## Build and Run

```zsh
cd /Users/tyejaedon/StudioProjects/TigerPlayer
./gradlew :app:assembleDebug
```

Install from Android Studio or use your usual deploy flow to a connected device.

For release packaging:

```zsh
cd /Users/tyejaedon/StudioProjects/TigerPlayer
./gradlew :app:assembleRelease
```

---

## Testing and Validation

Recommended baseline checks:

```zsh
cd /Users/tyejaedon/StudioProjects/TigerPlayer
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Pre-flight matrix for RC validation lives in `docs/PreFlightValidationMatrix.md`.

---

## Troubleshooting

### Cloud features are not working

- Verify `secrets.properties` keys are set and non-placeholder.
- Rebuild after changing secrets so `BuildConfig` updates.

### Spotify auth callback fails

- Recheck package name and SHA-1 in Spotify dashboard.
- Confirm callback scheme/host path placeholders match app manifest placeholders.

### Discovery rows are empty

- Confirm local library cache exists (initial scan completed).
- Discovery queries depend on cached tracks and playback history signals.

### Performance issues in visualizer surfaces

- Test with lower device thermal load and battery saver off.
- Compare behavior with PiP/fullscreen transitions to isolate lifecycle regressions.

---

## Release Notes

- Detailed 2.0 notes: `docs/Release-Notes-2.0.md`
- Issue/evidence review: `docs/Issues-Review-2026-07-10.md`
- Status rundown: `docs/Issue-Status-Rundown-2026-07-18.md`

---

## Contributing

Pull requests are welcome. For larger changes:

1. Open or reference an issue first.
2. Keep changes scoped by feature area.
3. Include validation commands/results in PR notes.
4. Update docs when behavior or setup changes.

---

## Credits

Forged in Nairobi by **Jaedon**.

If you find a bug in the archives or want to add a new ritual, contributions are appreciated.
