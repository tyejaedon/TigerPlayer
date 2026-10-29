---
name: audio-pipeline-explainer
description: Standardized explanation framework for audio pipeline compartments, lifecycle phases, and data flows using plain English, inline jargon definitions, and tabulated technical glossaries.
---

# Audio Pipeline Explainer Skill

This skill provides a standardized framework for documenting and explaining audio architecture, signal flows, buffer transitions, and system compartments. It enforces a dual communication standard: **accessible plain English descriptions** combined with **precise engineering jargon** that is defined both **inline** (upon first mention) and **tabulated** (in structured compartment glossaries).

---

## 1. Core Explanation Methodology

When explaining any audio compartment, lifecycle phase, or data flow, always follow this 4-step structure:

### Step 1: Plain-English Functional Overview
Begin with a concrete, accessible mental model. Describe what the compartment achieves from the listener's perspective before discussing internal code structures.
* *Example:* "Think of the audio sink as the final airport terminal: it takes raw sound data packages arriving from the player, inspects them, applies acoustic adjustments (like tone controls or reverb), and hands them off to the physical headphone or speaker hardware without any stutter."

### Step 2: Architecture & Data Flow Diagram
Provide an ASCII or Mermaid diagram illustrating the data flow, highlighting thread boundaries, buffer formats, and component handoffs.

### Step 3: Deep Technical Breakdown with Inline Definitions
Explain the implementation using accurate engineering jargon. Every time a domain-specific term is introduced, define it immediately inline using parenthetical or em-dash notation.
* *Example:* "ExoPlayer feeds raw **PCM** (*Pulse-Code Modulation* — the uncompressed, digital numeric representation of analog sound waves) audio frames into `AdaptiveDspEngine`. This engine runs on the dedicated audio output thread and uses a **circular look-ahead buffer** (*a fixed-size memory ring that delays the signal by 5 milliseconds to detect upcoming volume spikes before they reach the speakers*) to prevent **clipping** (*harsh digital distortion occurring when audio signal amplitude exceeds maximum allowable digital range*)."

### Step 4: Tabulated Jargon Glossary
Conclude every architectural section with a structured Markdown table defining every technical term used, its specific role in the pipeline, and what happens if it fails.

| Jargon Term | Plain-English Definition | Role in TigerPlayer Pipeline | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **PCM (Pulse-Code Modulation)** | The raw numeric values representing sound waves. | Input and output format for all internal `AudioProcessor` stages. | Audio cannot be digitally manipulated or rendered. |
| **AudioSink** | The destination audio consumer that manages hardware output buffers. | Bridges ExoPlayer's decoding output to the Android system audio server (`AudioTrack`). | Audio stutter, buffer underruns, or complete silence. |
| **Look-Ahead Limiter** | A safety cushion that inspects upcoming audio peaks before outputting them. | Prevents digital overs/distortion when multiple EQ bands or reverbs are boosted. | Harsh acoustic clipping, cracking, or speaker distortion. |

---

## 2. TigerPlayer Audio Architecture Blueprint

Use this reference when explaining TigerPlayer's specific audio subsystems:

```
[Audio Source: Local MediaStore / Navidrome Stream / Spotify Remote]
                            │
               ┌────────────┴────────────┐
               ▼                         ▼
      [Local ExoPlayer]          [Spotify Remote SDK]
               │                         │
               ▼                         ▼
    [DefaultAudioSink]          [External Spotify App]
               │
    ┌──────────┴────────────────────────────────┐
    │ Media3 AudioProcessor Pipeline            │
    │                                           │
    │ 1. AdaptiveDspEngine                      │
    │    ├── ReplayGain Normalization           │
    │    ├── M/S Stereo Widener                 │
    │    ├── Cytotoxic Parametric EQ (SVF)      │
    │    ├── Schroeder Hall Reverb              │
    │    ├── Prism 3-Stem Isolator              │
    │    └── Look-Ahead Peak Limiter & AGC      │
    │                                           │
    │ 2. FftProcessor                           │
    │    └── 6-band FFT spectral bin analysis   │
    │                                           │
    │ 3. WaveformCaptureProcessor               │
    │    └── Downsampled PCM wave ring-buffer   │
    └──────────────────┬────────────────────────┘
                       │
         ┌─────────────┴─────────────┐
         ▼                           ▼
  [Android Audio HAL]     [Real-Time Telemetry Flow]
  (Speakers / BT / USB)   ├── AudioReactiveFrame -> Compose UI Visualizers
                          └── Bass/Flux -> Audio-Reactive Haptics
```

---

## 3. Explaining Seamlessness & Transition Phases

When explaining why playback transitions are seamless (no pops, clicks, or delays), explain these three synchronization models:

### 1. Hardware Offload Transition Ramp
* **The Problem:** Toggling between DSP effects and hardware offload (*direct playback on device DSP without CPU intervention*) abruptly re-allocates low-level hardware buffers, causing loud electrical pops or driver crashes (especially on Samsung devices).
* **The Solution (`offloadTransitionMutex`):**
  1. **Fade Out:** Gradually attenuates player volume to 0 across 8 steps (`20ms` per step).
  2. **Drain Period:** Pauses for `350ms` allowing the **HAL** (*Hardware Abstraction Layer*) and Bluetooth A2DP buffers to completely drain.
  3. **Sink Reconfiguration:** Rebuilds or resets the `AudioSink` with new routing parameters.
  4. **Fade In:** Smoothly restores original volume across 8 steps (`40ms` per step).

### 2. Gapless Playback Pre-Buffering
* **The Problem:** Latency when opening, parsing headers, and initializing decoders between consecutive tracks creates noticeable silence gaps.
* **The Solution:** ExoPlayer retains the existing `AudioSink` instance while pre-buffering the subsequent track's initial audio packets in the background. The audio stream seamlessly transitions from the end of Track A to sample zero of Track B without tearing down the audio track or reopening the device audio session.

### 3. Dual-Transport State Machine
* **The Problem:** The app supports both local ExoPlayer playback and remote Spotify playback, which could lead to overlapping audio streams.
* **The Solution (`PlaybackEngine`):** Mediates between `MediaControllerManager` and `SpotifyRepository`. Whenever one transport initiates playback, it immediately triggers an asynchronous fade-out and pause on the alternate transport before claiming audio focus.

---

## 4. Documentation Standard Checklist

When authoring documentation for TigerPlayer audio compartments:
- [ ] Begins with a listener-oriented plain-English summary.
- [ ] Visual diagram depicts component inputs, transformations, and outputs.
- [ ] Every specialized term (e.g., *FFT, SVF, Biquad, Hal, ReplayGain, Ring-Buffer, Crossover*) includes an immediate inline explanation on first appearance.
- [ ] Contains a comprehensive, tabulated glossary at the end of the section.
- [ ] Documents thread safety boundaries (e.g., Audio thread vs. Main thread vs. Coroutine scopes).
- [ ] Describes error states and fallback behaviors.
