---
name: tigerplayer-ops
description: Operational runbooks, quality toolchain gates, flavor builds, and audio verification workflows for the TigerPlayer project.
---

# TigerPlayer Operations Skill

This skill provides fast, token-efficient procedures for building, testing, and verifying TigerPlayer.

## 1. Quality Gate Toolchain

Always run these sequentially on Windows PowerShell from `C:\TigerPlayer`:

```powershell
# 1. JVM Unit tests (JVM fast path)
.\gradlew.bat :app:testDebugUnitTest

# 2. Lint check
.\gradlew.bat :app:lintDebug

# 3. Assemble check
.\gradlew.bat :app:assembleDebug
```

## 2. Targeted Single-Test Execution

To preserve tokens and minimize output noise, never run full test suites when testing a single component:

```powershell
# Run a specific unit test class
.\gradlew.bat :app:testDebugUnitTest --tests "com.tigerplayer.data.repository.SpotifyPlaybackStateTest"

# Run a single test method
.\gradlew.bat :app:testDebugUnitTest --tests "com.tigerplayer.data.repository.SpotifyPlaybackStateTest.testStateUpdate"
```

## 3. Product Flavor Verification

TigerPlayer maintains two product flavors:
- **`foss`**: Zero proprietary binaries. Validates that F-Droid build will not fail.
- **`full`**: Integrates Spotify App Remote SDK.

Verify each flavor independently:

```powershell
# Verify FOSS flavor compiles without proprietary dependencies
.\gradlew.bat :app:assembleFossDebug

# Verify Full flavor with Spotify SDK integration
.\gradlew.bat :app:assembleFullDebug
```

## 4. Audio Engine & Playback Verification
- Inspect `PlaybackEngine.kt` and `PlaybackService.kt` for MediaSession callbacks.
- Ensure all remote calls to Spotify App Remote are safely isolated behind `SpotifyAppRemoteClient` interface.
- Never import `com.spotify.android.appremote.*` in common `src/main` code.
