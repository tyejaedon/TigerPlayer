# TigerPlayer Domain Engine Compartment (`engine/`)

This directory contains pure domain orchestration classes and digital signal processing (DSP) math models. The engine layer operates between the **Presentation Layer** (`ui/`, `viewmodel/`) and the **Data / Service Layers** (`data/`, `service/`).

---

## 1. Plain-English Overview

Think of the **Engine Layer** as the "brains and studio engineers" of TigerPlayer:
- It decides **what** to do with music, but does not draw buttons on the screen or directly talk to the hardware drivers.
- When you turn on an equalizer or concert hall reverb, the DSP engine calculates the exact mathematical changes needed to every sound wave sample.
- When you search for an artist, the library engine sorts and organizes your albums and folder hierarchies in milliseconds.
- When you listen to songs, the stats engine measures how long you listened to compute your top artists and track history.

---

## 2. Compartment Architecture & Data Flow

```
[UI / ViewModels]
       │
       ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Engine Layer (`engine/`)                                               │
│                                                                        │
│ ┌──────────────────────┐  ┌───────────────────┐  ┌───────────────────┐ │
│ │ PlaybackEngine       │  │ LibraryEngine     │  │ StatsEngine       │ │
│ │ - Dual transport     │  │ - Folder tree     │  │ - Scrobble filter │ │
│ │ - Queue coordination │  │ - Diffing & search│  │ - History metrics │ │
│ └──────────┬───────────┘  └─────────┬─────────┘  └─────────┬─────────┘ │
│            │                        │                      │           │
│ ┌──────────▼────────────────────────▼──────────────────────▼─────────┐ │
│ │ Real-Time DSP Sub-compartment                                      │ │
│ │ ┌───────────────────┐  ┌───────────────────┐  ┌──────────────────┐ │ │
│ │ │ AdaptiveDspEngine │  │ PrismIsolator     │  │ SchroederHall    │ │ │
│ │ │ - AudioProcessor  │  │ - Linkwitz-Riley  │  │ - 4 comb filters │ │ │
│ │ │ - StateVarFilter  │  │ - 3-stem split    │  │ - 2 all-pass     │ │ │
│ │ └─────────┬─────────┘  └───────────────────┘  └──────────────────┘ │ │
│ │           ▼                                                        │ │
│ │ ┌───────────────────┐  ┌───────────────────┐  ┌──────────────────┐ │ │
│ │ │ LookAheadLimiter  │  │ ReplayGainCalc    │  │ FftSpectral      │ │ │
│ │ │ - 1024 spl buffer │  │ - Headroom clamp  │  │ - 6 spectral bins│ │ │
│ │ └───────────────────┘  └───────────────────┘  └──────────────────┘ │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                         ┌──────────┴──────────┐
                         ▼                     ▼
                [MediaControllerManager] [AudioRepository]
```

---

## 3. Class & Component Breakdown

### Playback & Transport Coordination
* **`PlaybackEngine`**: Dual-transport facade that unifies playback commands across local/Subsonic audio (via `MediaControllerManager`) and Spotify (via `SpotifyRepository`).
  - *Key Flows:* `isPlaying: Flow<Boolean>`, `currentPosition: Flow<Long>`, `currentMediaId: Flow<String>`, `sleepTimerState: StateFlow<SleepTimerState>`.
  - *Methods:* `playTrack(track, libraryTracks)`, `pauseActiveTransport()`, `setSleepTimerDuration(minutes, fadeOutEnabled)`.
* **`SleepTimerController`**: Tracks countdown intervals and end-of-track triggers, initiating smooth volume attenuation when sleep fires.
* **`OffloadVolumePolicy`**: Calculates step attenuation and duration schedules for hardware offload switching (8 steps down, 350ms drain, 8 steps up).

### Real-Time DSP Audio Processing
* **`AdaptiveDspEngine`**: High-performance Media3 `AudioProcessor` executing 6-stage audio transformations:
  1. Mid/Side (M/S) stereo channel widening.
  2. Parametric Equalization via Cytotoxic State Variable Filters.
  3. Acoustic Environment modeling (Studio Bypass, Vinyl flutter with LFO, Concert Hall).
  4. Prism 3-stem frequency isolation (Vocals, Beats, Instruments).
  5. 1024-sample look-ahead peak limiter and Automatic Gain Control (AGC).
  6. 6-band FFT spectral dynamics emitting `AudioReactiveFrame` via `StateFlow`.
* **`StateVariableFilter`**: Cytotoxic Topology-Preserving Transform (TPT) digital filter. Unlike traditional direct-form biquads, SVF retains numerical stability and zero parameter zipper noise during rapid frequency/Q modulation.
* **`PrismIsolator`**: 4th-order Linkwitz-Riley (LR4) crossover networks separating audio into Low (<250Hz), Mid (250Hz - 3.4kHz), and High (>3.4kHz) bands that sum to an exact flat unity amplitude response.
* **`SchroederHallProcessor`**: Spatial reverberation engine employing 4 mutually prime feedback comb filters (`1116`, `1188`, `1277`, `1356` samples) and 2 series all-pass diffusion filters (`225`, `556` samples).
* **`ReplayGainCalculator`**: Parses album/track loudness tags and dynamically scales linear gain while preventing digital clipping ($G \cdot \text{Peak} \le 1.0$).
* **`MonoBiquad`**: Direct Form II transposed biquad filter used for auxiliary tone shaping.

### Visualizer & Telemetry Processing
* **`FftSpectralAnalyzer`**: Calculates 512/1024-point Fast Fourier Transforms over raw PCM buffers to segment power into 6 dynamic frequency bins.
* **`WaveformEngine`**: Downsamples raw PCM audio into RMS envelopes and visual peak arrays for seekbars and mini-visualizers.
* **`FluidRenderEngine`**: Real-time Navier-Stokes fluid simulation calculating velocity vectors and dye density for audio-reactive GPU canvas shaders.
* **`GraphicsEngine`**: Utility for AGSL shaders, RenderEffect blurs, and Neon color ramps.
* **`VizualizerEngine`**: Broadcasts downsampled audio frames to legacy visualizer components.

### Library & Metadata Management
* **`LibraryEngine`**: Assembles flat track lists into sorted album/artist collections and builds navigable multi-level directory trees (`FolderTreeBuilder`).
* **`MetadataEngine`**: Resolves track enrichment (fetching synced/plain lyrics from LRCLIB, artist bios from Wikipedia, and high-resolution album covers).
* **`NetworkEngine`**: Observes network capabilities and transport connectivity via `ConnectivityManager.NetworkCallback`.
* **`StatsEngine`**: Logs listening history, validates scrobbles against qualifying rules (e.g. at least 50% played or 240 seconds elapsed), and computes telemetry rankings.

---

## 4. Scope, Thread Safety & Known Limitations

### Threading Invariants
* **Audio Thread:** Real-time methods in `AdaptiveDspEngine` (`queueInput`, `getOutput`, `processAudio`) execute directly on ExoPlayer's high-priority audio rendering thread. **Strict Rule:** Zero object allocations, zero memory allocations (`new`, `ArrayList`, lambdas), and zero disk/network access inside these loops.
* **Calculation Thread (`Dispatchers.Default`):** Complex sorting, folder tree parsing (`FolderTreeBuilder`), and scrobble aggregations run on `Dispatchers.Default` to prevent UI frame drops.
* **StateFlow Boundary:** All visualizer telemetry emitted from `AdaptiveDspEngine` (`audioReactiveFrame`) transitions from the audio thread to UI coroutines via `MutableStateFlow` with confluent conflation.

### Known Limitations & Tech Debt
1. **Issue #42 (StatsEngine Duration Inflation):** Historically, playback events were recorded at track commencement rather than completion. When inspecting `StatsEngine`, verify that scrobble thresholds enforce actual listened duration.
2. **Samsung HAL Float Output:** Some Samsung devices suffer from audio driver buffer crashes when receiving 32-bit float audio directly. `AdaptiveDspEngine` enforces 16-bit PCM output conversion on Samsung hardware.
3. **Prism CPU Footprint:** Running multiple 4th-order Linkwitz-Riley filters simultaneously requires optimization; avoid re-initializing filter coefficients unless frequency split points change.

---

## 5. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Engine Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **State Variable Filter (SVF)** | An analog-modeling digital filter topology that allows smooth parameter adjustments. | Implements interactive PEQ bands in `AdaptiveDspEngine`. | Audio zipper noise or pops during real-time slider manipulation if standard biquads are used. |
| **Linkwitz-Riley (LR4)** | A 4th-order audio crossover filter with a steep 24 dB/octave attenuation slope. | Splits tracks into Vocals, Beats, and Instruments in `PrismIsolator`. | Phase cancellation or hollow acoustic volume dips at crossover points. |
| **Schroeder Reverb** | An algorithmic acoustic room simulator combining comb and all-pass filters. | Simulates concert hall spatial acoustics in `SchroederHallProcessor`. | Metallic ringing or harsh echo if feedback loop gains are uncalibrated. |
| **Look-Ahead Limiter** | An anticipatory volume limiter that buffers audio for ~5ms. | Clamps high-amplitude sound peaks before they hit the speakers. | Digital clipping, distortion, and harsh speaker crackle. |
| **Spectral Flux** | The rate of change of sound energy across frequencies over time. | Measures sharp musical transients and drum hits for visualizers and haptics. | Visualizers and haptics pulse randomly instead of syncing to rhythmic beats. |
| **ReplayGain** | Stored metadata standard indicating perceived song loudness. | Balances volume levels across tracks in `ReplayGainCalculator`. | Jarring volume jumps between quiet acoustic tracks and loud modern mixes. |
| **Scrobble** | A verified log of a song being listened to (usually >50% completed). | Evaluated by `StatsEngine` before recording playback to user history. | Inaccurate analytics and skewed listening statistics. |
