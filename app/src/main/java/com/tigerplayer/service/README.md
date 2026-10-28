# TigerPlayer Service & Runtime Compartment (`service/`)

This directory contains the background playback runtime, AndroidX Media3 `MediaSessionService`, audio processor pipeline, and cross-process media controller communication.

---

## 1. Plain-English Overview

Think of the **Service Compartment** as the "engine room and power plant" of the music player:
- While you navigate through different screens or even lock your phone and put it in your pocket, this compartment stays running in the background to ensure your music never cuts out.
- It is the bridge between the Android operating system (the lock screen controls, Bluetooth car audio, and notifications) and the music decoding engine (ExoPlayer).
- It also acts as the audio pipeline conductor: as music streams in, it feeds it through equalizers, extracts visualizer frequency bars, and coordinates synchronized vibration beats on your phone.

---

## 2. Compartment Architecture & Audio Chain

```
[System Media Controls / UI Commands]
                  │
                  ▼
       [MediaControllerManager]  (IPC / MediaSession Bridge)
                  │
                  ▼
       [AudioPlayerService]      (MediaSessionService / Foreground)
                  │
        ┌─────────┴─────────┐
        ▼                   ▼
 [ResolvingDataSource]  [ExoPlayer Decoder]
 (Navidrome Signer)         │
                            ▼
           ┌────────────────────────────────┐
           │ DefaultAudioSink               │
           │                                │
           │ 1. AdaptiveDspEngine           │
           │    (PEQ, Reverb, Stem Split)   │
           │                                │
           │ 2. FftProcessor                │
           │    (6-band FFT spectral bins)  │
           │                                │
           │ 3. WaveformCaptureProcessor    │
           │    (Downsample waveform ring)  │
           └────────────────┬───────────────┘
                            │
               ┌────────────┴────────────┐
               ▼                         ▼
      [Android Audio HAL]     [Real-Time Telemetry]
      (PCM / Offload Sink)    ├── AudioReactiveFrame -> Compose UI
                              └── Spectral Flux -> Audio-Reactive Haptics
```

---

## 3. Core Classes & Responsibilities

### `AudioPlayerService`
* **Type:** Foreground Android Service extending AndroidX Media3 `MediaSessionService`.
* **Primary Responsibilities:**
  - Manages `MediaSession` and system notification with `MediaStyle`.
  - Configures `DefaultAudioSink` with custom `AudioProcessor` chain: `[AdaptiveDspEngine] -> [FftProcessor] -> [WaveformCaptureProcessor]`.
  - Implements the mutex-guarded **Offload Transition Volume Ramp** (`offloadTransitionMutex`) to eliminate audio pops when toggling between DSP and hardware offload.
  - Dynamically signs Navidrome streaming credentials at request time via `ResolvingDataSource` without saving permanent tokens into URIs.
  - Generates synchronized audio-reactive haptic pulses using phone vibration motors (`VibratorManager`).
  - Implements Samsung HAL compatibility by forcing 16-bit PCM output instead of 32-bit float output.

### `MediaControllerManager`
* **Type:** `@Singleton` IPC transport coordinator connecting ViewModels to `AudioPlayerService`.
* **Primary Responsibilities:**
  - Connects to the foreground `MediaSession` using an asynchronous `MediaController.Builder`.
  - Tracks playback state flows: `isPlaying`, `currentPosition` (polled at 4 Hz), `currentMediaId`, `shuffleModeEnabled`, `repeatMode`, and `sleepTimerState`.
  - Manages the active playlist queue, track switching, and queue serialization.
  - Triggers fade-out pause actions when sleep timers expire (`fadeOutAndPause()`).

### `FftProcessor`
* **Type:** Media3 `AudioProcessor`.
* **Primary Responsibilities:**
  - Intercepts audio buffers in the playback pipeline and performs Fast Fourier Transform (FFT) analysis.
  - Dispatches 6-band frequency bin energies and spectral flux measurements to `AdaptiveDspEngine` and UI visualizers without stalling audio playback.

### `WaveformCaptureProcessor`
* **Type:** Media3 `AudioProcessor`.
* **Primary Responsibilities:**
  - Captures real-time PCM sound wave samples into a circular ring-buffer.
  - Employs downsampling algorithms to yield smooth audio waveform contours for seekbars and dynamic visualizer surfaces.

### `HapticsDebugMonitor`
* **Type:** Diagnostics telemetry collector.
* **Primary Responsibilities:**
  - Tracks haptic pulse counts, vibration trigger scores, and cooldown skips.
  - Logs telemetry for verifying rhythm synchronization and motor temperature safety.

---

## 4. Transition Lifecycle & Safety Protocols

### Hardware Offload Transition Sequence (`offloadTransitionMutex`)
When switching between software DSP effects and hardware battery-saving offload, low-level audio hardware buffers must be re-initialized. An abrupt switch causes loud speaker clicks. TigerPlayer executes a strict 4-phase transition:
1. **Fade Out:** Ramps player volume down to `0.0` over 8 steps (`20ms` delay per step).
2. **Buffer Drain:** Waits `350ms` (`SINK_DRAIN_DELAY`) to allow hardware A2DP and Audio HAL buffers to empty completely.
3. **Sink Reconfiguration:** Reconfigures ExoPlayer's audio sink and track selector preferences.
4. **Fade In:** Ramps volume smoothly back up to `1.0` over 8 steps (`40ms` delay per step).
* *Cancellation Invariant:* If the user repeatedly toggles the button mid-transition, previous jobs are cancelled, but terminal volume restoration executes inside a `finally` block so audio volume is never left muted.

### Audio-Reactive Haptics Safeguards
- **Refractory Cooldown:** Enforces a minimum `90ms` pause between haptic pulses to prevent the vibration motor from buzzing continuously.
- **Amplitude Constraints:** Vibrations are clamped between `40` (subtle tap) and `255` (firm thud).
- **Pulse Duration:** Percussive pulses are limited to `10ms - 20ms` for crisp, drum-like transients.

---

## 5. Known Limitations & Tech Debt

1. **Issue #48 (`MediaControllerManager` Release):** Ensure `MediaControllerManager.release()` is invoked when terminating playback to prevent service connection leaks.
2. **Issue #73 (Queue Serialization Fragility):** The queue parser relies on positional string parsing with 18 delimited fields. Treat this parser carefully until object-based schema serialization is implemented.
3. **Samsung HAL Float Workaround:** On Samsung devices, float output is explicitly disabled (`!isSamsungDevice`) to prevent HAL buffer underruns in proprietary audio post-processors.

---

## 6. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Service Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **MediaSessionService** | Android OS class that hosts background audio playback. | Keeps playback alive when the app is backgrounded and handles lock screen actions. | Music stops when the user switches apps or locks the screen. |
| **MediaController** | A client interface used by screens to send commands to the background service. | Communicates between UI ViewModels and `AudioPlayerService`. | UI buttons (play, pause, skip) fail to control playback. |
| **AudioProcessor** | A plug-in component inside ExoPlayer that manipulates audio bytes in real time. | Implemented by `AdaptiveDspEngine`, `FftProcessor`, and `WaveformCaptureProcessor`. | Sound effects, equalizers, and visualizers stop working. |
| **Audio Offload** | Playing music using dedicated low-power audio chips. | Saves significant battery life during extended playback. | Increased battery drain if disabled; speaker pops if switched abruptly. |
| **Sink Drain Delay** | A brief wait period allowing queued sound packets to exit the speaker hardware. | Prevents buffer collisions when reconfiguring the audio sink. | Loud clicks, electrical pops, or audio driver crashes. |
| **ResolvingDataSource** | A dynamic network adapter that computes streaming URLs at request time. | Injects fresh Navidrome MD5/salt security signatures right before streaming. | Broken streams due to expired tokens, or security leaks if saved in plaintext. |
| **Spectral Flux** | Measurement of how fast sound energy changes between audio frames. | Detects percussion hits to trigger synchronized haptic vibrations. | Phone vibrator triggers continuously or out of sync with the rhythm. |
