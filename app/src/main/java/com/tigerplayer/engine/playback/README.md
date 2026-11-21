# TigerPlayer Playback Engine Sub-compartment (`engine/playback/`)

This directory houses the dual-transport playback orchestration facade and sleep timer controllers.

---

## 1. Plain-English Overview

Think of the **Playback Sub-compartment** as the "conductor and playback controller" of TigerPlayer:
- It provides a single, unified interface (`PlaybackEngine`) to play, pause, seek, and skip tracks regardless of whether the music is playing from a local file, a Navidrome server, or Spotify App Remote.
- It also manages the sleep timer, counting down minutes and gently fading out volume when the timer expires.

---

## 2. Component Breakdown

### `PlaybackEngine.kt`
* **Role:** Unifying transport facade wrapping `MediaControllerManager` (local/Navidrome audio) and `SpotifyRepository` (Spotify playback).
* **Key Flows:** `isPlaying: Flow<Boolean>`, `currentPosition: Flow<Long>`, `currentMediaId: Flow<String>`, `sleepTimerState: StateFlow<SleepTimerState>`.
* **Key Methods:** `playTrack(...)`, `pauseActiveTransport()`, `seekTo(positionMs)`, `setSleepTimerDuration(...)`.

### `SleepTimerController.kt`
* **Role:** Tracks countdown intervals and end-of-track triggers, initiating smooth volume attenuation when sleep fires (`SleepTimerState.Active`, `SleepTimerState.Expired`).

---

## 3. Design Invariants

1. **Dual-Transport Routing:** Local and Navidrome streams route through Media3 ExoPlayer (`MediaControllerManager`); Spotify streams route through `SpotifyRepository`.
2. **Main Safety:** All transport commands and state flows are main-safe.
3. **Volume Attenuation:** Sleep timer volume fade uses non-blocking coroutines without abrupt audio chops.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Playback Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Dual Transport** | Supporting two separate playback systems (ExoPlayer & Spotify App Remote). | Unifies local music files and Spotify streaming behind one interface. | User must use separate screens or controls for Spotify vs local music. |
| **Sleep Timer Fade** | Smoothly lowering volume before stopping playback when sleep timer fires. | Gently turns down music volume so user isn't startled awake. | Music stops abruptly at full volume. |
