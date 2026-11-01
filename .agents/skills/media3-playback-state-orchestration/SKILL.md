---
name: media3-playback-state-orchestration
description: State management, dual-transport routing, gapless pre-buffering, and mutex-guarded offload volume transitions using AndroidX Media3 and MediaSessionService.
---

# Media3 Playback State & Orchestration Skill

This skill provides architecture guidelines, lifecycle protocols, and state synchronization runbooks for TigerPlayer's media playback subsystems across `AudioPlayerService.kt`, `MediaControllerManager.kt`, and `PlaybackEngine.kt`.

---

## 1. Plain-English Architecture Overview

Think of the **Media Orchestration Layer** as an air-traffic controller for music. It coordinates playback commands coming from buttons on the screen, lockscreen notifications, smart watches, and Bluetooth headsets:
1. **Remote vs. Local Director (Dual Transport):** Automatically knows whether a song lives on the device, on a personal Navidrome server, or on Spotify, and directs the commands to the right audio player without cluttering the screen code.
2. **Smooth Volume Transition (Offload Fade):** When changing audio modes or routing, it eases the volume down like a theater dimmer before swapping audio engines, then raises it back up, preventing nasty speaker pops.
3. **Continuous Play (Gapless Playback):** Silently loads and prepares the next track a few seconds before the current song ends, so that live concerts and concept albums play seamlessly without abrupt pauses between tracks.

---

## 2. Core Orchestration Mechanisms

### Compartment A: Foreground Service & MediaSession Lifecycle
* **Host Service:** `AudioPlayerService` inherits from AndroidX Media3 `MediaSessionService`.
* **Foreground Contract:**
  - Manages `MediaNotification.Builder` using `MediaStyle` notification.
  - Automatically updates metadata (`MediaMetadataCompat`), album art, playback state flags, and custom action commands (`ACTION_SHUFFLE`, `ACTION_REPEAT`, `ACTION_TOGGLE_DSP`, `ACTION_LOAD_PEQ`).
  - Releases system resources and cancels background coroutine scopes safely on `onDestroy()`.

### Compartment B: Hardware Offload Volume Ramp (`offloadTransitionMutex`)
* **Problem:** Switching between software DSP processing and hardware offload decoding causes Android Audio HAL re-initialization pops.
* **Resolution Protocol:**
  ```
  [User Toggles DSP/Offload]
             │
             ▼
  Acquire offloadTransitionMutex
             │
             ▼
  8-Step Ramp Down (Volume 1.0 -> 0.0, 20ms delay/step)
             │
             ▼
  Drain Delay (350ms for A2DP / HAL buffer drainage)
             │
             ▼
  Reconfigure ExoPlayer TrackSelection & AudioSink
             │
             ▼
  8-Step Ramp Up (Volume 0.0 -> 1.0, 40ms delay/step)
             │
             ▼
  Release offloadTransitionMutex
  ```
* **Cancellation Safety:** If a user rapidly toggles the button mid-fade, previous transition jobs are cancelled, but the terminal restoration logic always runs inside a `finally` block to prevent volume getting stuck at 0.

### Compartment C: Dual Transport State Machine
* **Local vs Spotify Routing in `PlaybackEngine.kt`:**
  - If track ID starts with `spotify:`, `PlaybackEngine` calls `mediaControllerManager.pause()` followed by `spotifyRepository.playUri()`.
  - If track is local or Navidrome, `PlaybackEngine` pauses Spotify remote and commands `mediaControllerManager.setPlaylistAndPlay()`.
* **State Flow Propagation:**
  - ViewModels listen to unified reactive StateFlows (`isPlaying`, `currentPosition`, `currentMediaId`, `sleepTimerState`) without caring which transport engine is currently active.

### Compartment D: Dynamic ResolvingDataSource
* **Location:** `ResolvingDataSource` in `AudioPlayerService.kt`.
* **Subsonic / Navidrome Token Security:**
  - Auth tokens are not hardcoded into permanent track URIs (which prevents token leakage in databases or cached metadata).
  - When ExoPlayer requests the next audio chunk, `ResolvingDataSource` intercepts the request and injects fresh MD5/salt signatures via `NavidromeUrlSigner` in real time.

### Compartment E: Audio Focus & Interruption Handling
* **Audio Attributes Configuration:**
  - `USAGE_MEDIA` and `AUDIO_CONTENT_TYPE_MUSIC`.
* **Audio Becoming Noisy:**
  - `player.setHandleAudioBecomingNoisy(true)` automatically pauses playback when headphones are disconnected.
* **Transient Ducking:**
  - Handles incoming navigation prompts or phone calls by automatically reducing volume or pausing, and restoring playback upon focus regain.

---

## 3. Tabulated Media3 Jargon Glossary

| Jargon Term | Plain-English Definition | Role in TigerPlayer Pipeline | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **MediaSession** | Android's standardized bridge between audio playback and the OS. | Enables lock screen controls, Android Auto, notification actions, and Bluetooth headsets. | Media buttons and lockscreen notifications stop working. |
| **Hardware Offload** | Directing audio decoding straight to low-power audio DSP chips. | Drastically reduces battery consumption during long listening sessions. | High battery drain if inactive; audio popping if toggled without fading. |
| **Gapless Playback** | Continuous audio streaming between tracks without gaps or latency. | Pre-buffers next track's audio headers and frames before current track ends. | 1-2 second awkward silence between tracks in live albums. |
| **ResolvingDataSource** | A dynamic data stream adapter that generates URLs on demand. | Authenticates and signs Navidrome streaming URLs at the exact moment of playback. | Expired token errors, streaming failure, or leaking credentials in cache. |
| **Audio Becoming Noisy** | Android broadcast triggered when audio output switches to speakers. | Automatically pauses music when wired or Bluetooth headphones are disconnected. | Music unexpectedly blares through phone speakers in quiet environments. |
| **Audio Ducking** | Temporarily lowering music volume for other sounds. | Allows GPS navigation instructions or notifications to be heard clearly over music. | Navigation directions are drowned out by loud music. |
