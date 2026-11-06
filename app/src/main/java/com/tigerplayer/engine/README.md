# TigerPlayer Domain Engine Compartment (`engine/`)

This directory contains pure domain orchestration classes and digital signal processing (DSP) math models. The engine layer operates between the **Presentation Layer** (`ui/`, `viewmodel/`) and the **Data / Service Layers** (`data/`, `service/`).

---

## 1. Plain-English Overview

Think of the **Engine Layer** as the "brains and studio engineers" of TigerPlayer:
- It decides **what** to do with music, but does not draw buttons on the screen or directly talk to the hardware drivers.
- When you turn on an equalizer or concert hall reverb, the DSP sub-compartment calculates the exact mathematical changes needed to every sound wave sample.
- When you search for an artist, the library sub-compartment sorts and organizes your albums and folder hierarchies in milliseconds.
- When you listen to songs, the stats sub-compartment measures how long you listened to compute your top artists and track history.

---

## 2. Compartment Architecture & Subfolder Structure

To maintain modularity and maintainability, the engine layer is compartmentalized into functional sub-packages:

```
┌────────────────────────────────────────────────────────────────────────┐
│ Engine Layer (`engine/`)                                               │
│                                                                        │
│ ┌──────────────────────┐  ┌───────────────────┐  ┌───────────────────┐ │
│ │ 📁 dsp/              │  │ 📁 graphics/      │  │ 📁 playback/      │ │
│ │ - AdaptiveDspEngine  │  │ - FluidRenderEngine│  │ - PlaybackEngine  │ │
│ │ - StateVarFilter     │  │ - VizualizerEngine│  │ - SleepTimerCtrl  │ │
│ │ - SchroederReverb    │  │ - FftSpectral     │  │                   │ │
│ └──────────────────────┘  └───────────────────┘  └───────────────────┘ │
│ ┌──────────────────────┐  ┌───────────────────┐  ┌───────────────────┐ │
│ │ 📁 library/          │  │ 📁 stats/         │  │ 📁 network/       │ │
│ │ - LibraryEngine      │  │ - StatsEngine     │  │ - NetworkEngine   │ │
│ │ - MetadataEngine     │  │ - Scrobble filter │  │ - Connectivity    │ │
│ │ - FolderTreeBuilder  │  │                   │  │                   │ │
│ └──────────────────────┘  └───────────────────┘  └───────────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                         ┌──────────┴──────────┐
                         ▼                     ▼
                [MediaControllerManager] [AudioRepository]
```

---

## 3. Sub-compartment Index

Detailed technical documentation for each sub-compartment can be found in its respective directory:

- 📁 **[DSP Sub-compartment (`engine/dsp/`)](./dsp/README.md)**: Real-time AudioProcessors, State Variable Filters, Schroeder Reverb, Look-Ahead Limiter, ReplayGain, and Offload Volume schedules.
- 📁 **[Graphics & Telemetry Sub-compartment (`engine/graphics/`)](./graphics/README.md)**: Fluid simulation shaders, EGL contexts, Fast Fourier Transform analysis, waveform downsampling, and Prism 3-stem frequency isolation.
- 📁 **[Playback Sub-compartment (`engine/playback/`)](./playback/README.md)**: Dual transport facade (ExoPlayer + Spotify App Remote) and sleep timer countdown management.
- 📁 **[Library Sub-compartment (`engine/library/`)](./library/README.md)**: Audio track indexing, directory tree parsing, and online metadata enrichment (LRCLIB lyrics, Wikipedia bios).
- 📁 **[Stats Sub-compartment (`engine/stats/`)](./stats/README.md)**: Playback history logging, scrobble threshold validation, and analytics telemetry.
- 📁 **[Network Sub-compartment (`engine/network/`)](./network/README.md)**: Connectivity state monitoring and callback handling.

---

## 4. Scope, Thread Safety & Design Invariants

### Threading Invariants
* **Audio Thread:** Real-time methods in `AdaptiveDspEngine` (`queueInput`, `processAudio`) execute directly on ExoPlayer's high-priority audio rendering thread. **Strict Rule:** Zero object allocations, zero memory allocations (`new`, `ArrayList`, lambdas), and zero disk/network access inside these loops.
* **Calculation Thread (`Dispatchers.Default`):** Complex sorting, folder tree parsing (`FolderTreeBuilder`), and scrobble aggregations run on `Dispatchers.Default` to prevent UI frame drops.
* **StateFlow Boundary:** All visualizer telemetry emitted from `AdaptiveDspEngine` (`audioReactiveFrame`) transitions from the audio thread to UI coroutines via `MutableStateFlow` with confluent conflation.

---

## 5. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Engine Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **State Variable Filter (SVF)** | Analog-modeling digital filter allowing smooth parameter adjustments. | Implements interactive PEQ bands in `AdaptiveDspEngine`. | Audio zipper noise or pops during real-time slider manipulation. |
| **Linkwitz-Riley (LR4)** | 4th-order audio crossover filter with steep 24 dB/octave attenuation. | Splits tracks into Vocals, Beats, and Instruments in `PrismIsolator`. | Phase cancellation or hollow acoustic volume dips. |
| **Schroeder Reverb** | Algorithmic room simulator combining comb and all-pass filters. | Simulates concert hall spatial acoustics in `SchroederHallProcessor`. | Metallic ringing or harsh echo if feedback gains are uncalibrated. |
| **Look-Ahead Limiter** | Anticipatory volume limiter buffering sound for ~0.8ms. | Clamps high-amplitude sound peaks before they hit the speakers. | Digital clipping, distortion, and harsh speaker crackle. |
| **Spectral Flux** | Rate of change of sound energy across frequencies over time. | Measures musical transients and drum hits for visualizers and haptics. | Visualizers and haptics pulse randomly instead of syncing to rhythmic beats. |
| **Scrobble** | Verified log of listening to a song (usually >50% completed). | Evaluated by `StatsEngine` before recording playback to user history. | Inaccurate analytics and skewed listening statistics. |
