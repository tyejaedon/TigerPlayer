# Adaptive DSP Engine Documentation (`DspEngine.kt`)

## Overview

The `AdaptiveDspEngine` (located in [`app/src/main/java/com/tigerplayer/engine/DspEngine.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/engine/DspEngine.kt)) is a high-performance, real-time, studio-grade audio processing pipeline implemented as an AndroidX Media3 [`AudioProcessor`](class://androidx.media3.common.audio.AudioProcessor).

It integrates directly into the Media3 / ExoPlayer audio rendering pipeline to deliver:
- Parametric equalization via Cytomic State Variable Filters (SVF)
- Real-time hardware/device profile compensation
- Acoustic environment modeling (Studio, Vinyl Warmth, Concert Hall)
- Real-time 3-stem audio separation (Prism Isolator)
- Look-ahead peak limiting & Automatic Gain Control (AGC)
- ReplayGain volume normalization integration
- Real-time spectral analysis (Bandpass filterbank / FFT) emitting [`AudioReactiveFrame`](class://com.tigerplayer.engine.AudioReactiveFrame) data via [`StateFlow`](class://kotlinx.coroutines.flow.StateFlow) for UI visualizers

---

### Key Terms & Technical Vocabulary

- **DSP (Digital Signal Processing)**: Real-time mathematical manipulation of digital audio signals (represented as raw PCM sample arrays) to adjust tone, dynamic range, spatial characteristics, or extract frequency metrics.
- **PCM (Pulse-Code Modulation)**: Digital uncompressed representation of an analog audio waveform sampled at fixed time intervals (e.g., `48,000 Hz`). Each sample amplitude is encoded using a specific bit depth: 16-bit signed integer, 24-bit packed integer, or 32-bit float (`[-1.0, 1.0]` range).
- **SVF (State Variable Filter)**: A continuous-time 2nd-order active filter topology digitized using trapezoidal integration (specifically Andrew Simper's Cytomic SVF). SVFs permit continuous, smooth parameter adjustments (frequency, gain, $Q$) without producing digital clicking, pop noise, or filter instability.
- **Quality Factor ($Q$)**: The ratio of filter center frequency to bandwidth:
  ```
  Q = f₀ / Δf
  ```
  *Concept*: Measures filter selectivity. Higher $Q$ values create sharp, narrow frequency peaks or cuts, while lower $Q$ values yield broad, gentle tonal curves.
- **Mid/Side (M/S) Processing**: An audio encoding technique that decomposes a stereo signal into mono (center) and difference (side) channels:
  ```
  Mid  = (Left + Right) / 2
  Side = (Left - Right) / 2
  ```
  *Concept*: Allows independent manipulation of centered elements (vocals, snare, bass) versus spatial stereo width (guitars, wide reverb, background effects).
- **LR4 (Linkwitz-Riley 4th Order Crossover)**: An audio crossover created by cascading two 2nd-order Butterworth filters in series:
  ```
  Attenuation Slope = -24 dB / octave
  Combined Amplitude = 0 dB (Flat at cutoff frequency)
  ```
  *Concept*: Provides steep frequency isolation between sub-bands while summing to a perfectly flat response without phase cancellation or frequency bumps.
- **FFT (Fast Fourier Transform)**: An efficient algorithm converting discrete time-domain PCM samples into their frequency-domain spectrum representation, allowing real-time spectral analysis for UI visualizer animations.
- **Biquad Filter**: A 2nd-order recursive IIR (Infinite Impulse Response) digital filter containing two poles and two zeros, used for bandpass, low-pass, high-pass, and shelf filtering.
- **Look-Ahead Limiter & AGC (Automatic Gain Control)**: A dynamics processor that delays the output signal slightly (~`4.5 ms`) using a circular ring buffer. This delay gives the envelope detector time to anticipate peak transients and apply smooth gain reduction before clipping occurs (`0 dBFS`).
- **Schroeder Reverb**: A 3D artificial reverberation architecture using parallel comb filters with low-pass feedback damping to simulate room reflection decay, followed by series all-pass filters for spatial diffusion and Haas effect early reflections.
- **Polynomial Soft Saturation**: Non-linear saturation curve used in vinyl modeling and stem isolation:
  ```
  y = x - (x³ / 4.5)
  ```
  *Concept*: Smoothly flattens peak amplitudes above `1.0` to introduce warm analog-style harmonic saturation instead of harsh digital hard-clipping.
- **Prism Power Normalization**: Stem mix scaling formula:
  ```
  Scale Factor = 1 / √(Vocals² + Beats² + Instruments²)
  ```
  *Concept*: Ensures equal loudness distribution when adjusting stem sliders by scaling output by the inverse root-sum-square of stem gains.
- **Spectral Centroid**: The "center of mass" or weighted average frequency of the audio spectrum, serving as a measure of perceived sound brightness (`[0.0, 1.0]`).
- **Spectral Flux**: A measure of frame-to-frame transient energy variation, quantifying percussive onsets or sudden musical changes.
- **ReplayGain**: An open standard measuring perceived track/album loudness (in LUFS) to calculate a target linear gain multiplier:
  ```
  Total Gain (dB) = ReplayGain Tag (dB) + Preamp (dB)
  Linear Gain     = 10 ^ (Total Gain / 20)
  Safe Linear Gain = min(Linear Gain, 1 / Peak Amplitude)
  ```
  *Concept*: Normalizes volume across tracks so different recordings play at equal loudness without altering dynamic range.
- **RIAA Equalization**: Standardized recording/playback frequency response curves for vinyl records; used in `VINYL_WARMTH` mode via low-shelf EQ to simulate analog turntable warmth.
- **Wow / Pitch Modulation**: Low-frequency (`0.5 Hz`) cyclical pitch variations caused by physical record warping or turntable speed instability.

---

## Architecture & Integration

### Injectability & Lifecycle
- Annotated with `@Singleton` and `@Inject` for Hilt dependency injection into [`AudioPlayerService`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/service/AudioPlayerService.kt).
- Implements AndroidX Media3 [`AudioProcessor`](class://androidx.media3.common.audio.AudioProcessor).
- Thread safety: Key DSP parameter state variables use `@Volatile` or thread-safe atomic updates to ensure lock-free parameter adjustments during real-time sample rendering.

### Audio Format Negotiation (`configure`)
- **Supported Encodings**: `C.ENCODING_PCM_16BIT`, `C.ENCODING_PCM_24BIT` (packed 3-byte), and `C.ENCODING_PCM_FLOAT` (32-bit floating point).
- **Internal Processing Format**: Unpacked 32-bit floating point PCM (`[-1.0, 1.0]` amplitude range).
- **Output Encoding**: Standardized to `C.ENCODING_PCM_16BIT` to ensure 100% compatibility with downstream Media3 processors (e.g., silence trimming, speed/pitch adjusters).

---

## Processing Pipeline Steps (`queueInput`)

For each audio block received in `queueInput(ByteBuffer)`, PCM samples are decoded to internal 32-bit floats and passed sequentially through seven processing stages:

```
          ┌─────────────────────────────────────────────────────────┐
          │                    Raw PCM Input                        │
          │         (PCM_16BIT / PCM_24BIT / PCM_FLOAT)             │
          └────────────────────────────┬────────────────────────────┘
                                       │
                                       ▼
          ┌─────────────────────────────────────────────────────────┐
          │  Stage 1: Headphone Mid/Side Stereo Widening            │
          └────────────────────────────┬────────────────────────────┘
                                       │
                                       ▼
          ┌─────────────────────────────────────────────────────────┐
          │  Stage 2: Device Compensation & SVF Parametric EQ       │
          │            (Auto-Makeup Gain & ReplayGain)              │
          └────────────────────────────┬────────────────────────────┘
                                       │
                                       ▼
          ┌─────────────────────────────────────────────────────────┐
          │  Stage 3: Acoustic Environment Processing               │
          │      (STUDIO / VINYL_WARMTH / CONCERT_HALL)             │
          └────────────────────────────┬────────────────────────────┘
                                       │
                                       ▼
          ┌─────────────────────────────────────────────────────────┐
          │  Stage 4: Prism 3-Stem Separation (LR4 Crossovers)      │
          │            (Vocals / Beats / Instruments)               │
          └────────────────────────────┬────────────────────────────┘
                                       │
                                       ▼
          ┌─────────────────────────────────────────────────────────┐
          │  Stage 5: Look-Ahead Peak Limiter & AGC                 │
          └────────────────────────────┬────────────────────────────┘
                                       │
                                       ▼
          ┌─────────────────────────────────────────────────────────┐
          │  Stage 6: Real-Time Spectral Analysis (Bandpass / FFT)  │
          │               ──► Emits AudioReactiveFrame StateFlow     │
          └────────────────────────────┬────────────────────────────┘
                                       │
                                       ▼
          ┌─────────────────────────────────────────────────────────┐
          │  Stage 7: Output Stage (Float ──► PCM_16BIT Conversion) │
          └────────────────────────────┬────────────────────────────┘
```

---

## Detailed Stage Functionality

### Stage 1: Headphone Mid/Side Stereo Widening
- Automatically active when output audio routes are detected as headphones, headsets, or USB DACs.
- Decomposes stereo into Mid (`(L + R) / 2`) and Side (`(L - R) / 2`) signals.
- Broadens the soundstage by applying a subtle coefficient scaling (`side * 1.02f`) to the side signal without phase degradation.

### Stage 2: Device Profile Compensation & Parametric EQ
- **Hardware Output Detection (`detectAndApplyDeviceProfile`)**: Queries `AudioManager` output devices:
  - **Built-in Speaker**: Applies low-shelf attenuation (`-6 dB @ 150 Hz`) and peaking boost (`+3 dB @ 2.5 kHz`) to compensate for small speaker resonance.
  - **Bluetooth Audio**: Applies high-shelf air boost (`+2.5 dB @ 12 kHz`, adjusted to `+1.2 dB` for Samsung Galaxy Buds to coordinate with native 360 Audio tuning).
- **State Variable Filters (SVF)**: Implements Cytomic SVF topology (Andrew Simper) via [`StateVariableFilter`](class://com.tigerplayer.engine.StateVariableFilter):
  - Supported filter types: `LOW_SHELF`, `PEAKING`, `HIGH_SHELF`.
  - Zero zipper noise / clicks during parameter updates through parameter interpolation.
- **Auto-Makeup Headroom & ReplayGain Integration**:
  - Automatically calculates true maximum peak composite gain across the audible spectrum (20 Hz - 20 kHz) via `BiquadDesigner.calculateCompositePeakGainDb(nodes, sampleRate)` rather than naively summing band gains algebraically.
  - Applies input headroom attenuation strictly equal to the true composite peak boost (`autoMakeupGain = 10^(-compositePeakDb / 20)` when `compositePeakDb > 0 dB`, otherwise `1.0f`), preventing Fletcher-Munson loudness drops on non-overlapping bands while accurately accounting for compounding on overlapping bands.
  - Multiplies input by linear ReplayGain factor calculated by [`ReplayGainCalculator`](class://com.tigerplayer.engine.ReplayGainCalculator).

### Stage 3: Acoustic Environments (`AcousticEnvironmentMode`)
1. **`STUDIO`**: Pristine, bypass mode with flat frequency response.
2. **`VINYL_WARMTH`**: Analog record simulation comprising:
   - **Wow LFO**: Low-frequency `0.5 Hz` delay line modulation introducing realistic pitch flutter.
   - **Polynomial Saturation**: Non-linear soft-clipping polynomial curve:
     ```
     y = x - (x³ / 4.5)
     ```
   - **Vinyl Hiss & DC offset**: LCG pseudo-random low-level vinyl surface noise.
   - **RIAA Warmth Shelf**: Low-shelf filter (`+2.5 dB @ 350 Hz`).
3. **`CONCERT_HALL`**: Schroeder Hall Reverb Tank ([`SchroederHallProcessor`](class://com.tigerplayer.engine.SchroederHallProcessor)):
   - 4 parallel comb filters with 1-pole low-pass damping & `tanh` soft-clipping feedback loops.
   - Series all-pass diffusion filters.
   - Early reflections modeled with Haas delay lines.

### Stage 4: Prism 3-Stem Isolator (`PrismIsolator`)
- Real-time Mid/Side 3-way stem separation using 4th-order Linkwitz-Riley crossover networks (LR4 biquads):
  - **Vocals**: Mid channel mid-band frequencies (`160 Hz` – `3.5 kHz`).
  - **Beats**: Low-frequency bass content below `160 Hz` (Mid + Side sub-bass).
  - **Instruments**: Stereo side components and high frequencies above `3.5 kHz`.
- Includes dynamic power normalization:
  ```
  Scale Factor = 1 / √(Vocals² + Beats² + Instruments²)
  ```
  and soft polynomial saturation to maintain balanced output gain when isolating individual stems.

### Stage 5: Look-Ahead True-Peak Limiter & AGC Protection
- **Look-Ahead Buffer**: 1024-sample ring buffer holding delayed Left/Right signals (delay `~4.5 ms` sample-rate aware).
- **Peak Sensing**: Inspects incoming signal peaks ahead of output generation.
- **Envelope Follower**: Fast transient attack (`~0.8 ms`) and transparent release (`~50 ms`) envelope detection preventing audible pumping.
- **Dynamic Bypass & True-Peak Protection**:
  - Automatically bypasses AGC gain reduction (`0 dB` reduction) on lossless streams (`audio/flac`, `audio/wav`, `audio/alac`, `audio/aiff`, and 24-bit/Float PCM) when EQ bands have zero or negative boost.
  - Engages true-peak limiting at `TRUE_PEAK_THRESHOLD` (`1.0f` / `0.0 dBFS`) when digital overs are imminent (positive EQ boosts or reverb summing), preventing hard digital clipping while preserving dynamic range.

### Stage 6: Real-Time Spectral Analysis & StateFlow Emission
- Operates in two modes (`SpectralAnalysisMode`):
  1. **`BANDPASS`**: 6-band biquad filterbank centered at `60 Hz`, `250 Hz`, `1 kHz`, `2.5 kHz`, `6 kHz`, and `12 kHz`.
  2. **`FFT`**: 1024-point Hann-windowed FFT via [`FftSpectralAnalyzer`](class://com.tigerplayer.engine.FftSpectralAnalyzer) using JTransforms.
- Aggregate metrics calculated per 1024 frames (`~21 ms @ 48 kHz`):
  - Sub-bass, bass, mid, treble normalized band energy levels.
  - Overall RMS signal energy level.
  - **Spectral Centroid**: Normalized center-of-mass frequency brightness measure (`[0.0, 1.0]`).
  - **Spectral Flux**: Frame-to-frame transient energy delta.
  - Execution benchmarking (`analysisCostMicros`).
- Emits structured [`AudioReactiveFrame`](class://com.tigerplayer.engine.AudioReactiveFrame) snapshots on `audioReactiveFrame` StateFlow.

### Stage 7: Output Stage
- Clamps floating-point samples to `[-1.0, 1.0]`.
- Converts Float PCM to signed 16-bit PCM shorts (`sample × 32767`).
- Writes to direct NIO output [`ByteBuffer`](class://java.nio.ByteBuffer).

---

## Core Data Structures & Models

| Data Structure / Enum | Purpose / Description |
| :--- | :--- |
| [`AudioReactiveFrame`](class://com.tigerplayer.engine.AudioReactiveFrame) | Data class emitting real-time spectral dynamics (`bass`, `mid`, `treble`, `energy`, `centroid`, `flux`, `spectralBands`, `analysisMode`, `analysisCostMicros`). |
| [`AcousticNode`](class://com.tigerplayer.engine.AcousticNode) | Parametric EQ node specification containing ID, label, filter type, frequency, gain in dB, and Q factor. |
| [`PrismMixLevels`](class://com.tigerplayer.engine.PrismMixLevels) | Target mix multipliers (`[0.0, 1.0]`) for Vocal, Beat, and Instrument stems. |
| [`AcousticEnvironmentMode`](class://com.tigerplayer.engine.AcousticEnvironmentMode) | Enum: `STUDIO`, `VINYL_WARMTH`, `CONCERT_HALL`. |
| [`PrismMode`](class://com.tigerplayer.engine.PrismMode) | Enum: `BYPASS`, `ISOLATION`. |
| [`SpectralAnalysisMode`](class://com.tigerplayer.engine.SpectralAnalysisMode) | Enum: `BANDPASS`, `FFT`. |
| [`FilterType`](class://com.tigerplayer.engine.FilterType) | Enum for SVF topology: `LOW_SHELF`, `PEAKING`, `HIGH_SHELF`. |

---

## Public API Summary

```kotlin
// Device Output Compensation
fun detectAndApplyDeviceProfile()

// Equalizer & Acoustic Nodes
fun updateAcousticNodes(nodes: List<AcousticNode>)

// Acoustic Environment Modes
fun setAcousticEnvironmentMode(mode: AcousticEnvironmentMode)

// Prism Stem Isolation
fun setPrismMode(mode: PrismMode)
fun updatePrismMix(vocals: Float, beats: Float, instruments: Float)

// ReplayGain Normalization
fun setReplayGainConfig(mode: ReplayGainMode, preampDb: Float, preventClipping: Boolean, isBitPerfect: Boolean)
fun setReplayGainTrackData(trackGainDb: Double?, albumGainDb: Double?, trackPeak: Double?, albumPeak: Double?, isSequentialAlbumPlay: Boolean)

// Spectral Visualizer Analysis
fun setSpectralAnalysisMode(mode: SpectralAnalysisMode)
val audioReactiveFrame: StateFlow<AudioReactiveFrame>

// Audio Processor State
fun getSampleRate(): Int
```

---

## Consumers & Related Components

1. **[`AudioPlayerService`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/service/AudioPlayerService.kt)**: Registers `AdaptiveDspEngine` into Media3 ExoPlayer's `RenderersFactory` audio processor chain. Configures ReplayGain tags, EQ parameters, and environment modes.
2. **[`NexusViewModel`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/ui/equalizer/NexusViewModel.kt)**: Drives parametric EQ updates and reads spectral frame flow for the equalizer visualizer.
3. **[`AcousticEnvironmentViewModel`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/ui/equalizer/AcousticEnvironmentViewModel.kt)**: Manages acoustic environment selection (`STUDIO`, `VINYL_WARMTH`, `CONCERT_HALL`).
4. **[`PrismViewModel`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/ui/prism/PrismViewModel.kt)**: Controls Prism 3-stem isolation levels and switches spectral analysis modes.
5. **[`PlayerViewModel`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/ui/player/PlayerViewModel.kt)**: Consumes real-time audio reactive frames for main player visualizers and fluid background animations.
6. **[`ReplayGainCalculator`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/engine/ReplayGainCalculator.kt)**: Computes effective linear gain factors based on Track/Album ReplayGain tags, user pre-amp dB settings, and clipping protection rules.

---

## Upgrade & Modular Architecture Refactoring Documentation

### Overview of Recent Architecture Upgrade
The DSP pipeline in [`app/src/main/java/com/tigerplayer/engine/DspEngine.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/engine/DspEngine.kt) has been refactored and modularized. Core signal processing algorithms have been extracted into dedicated, self-contained engine classes to separate signal math from Media3 `AudioProcessor` stream orchestration.

### Modular DSP Components

1. **[`DspModels.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/engine/DspModels.kt)**
   - Centralizes data models and enums used across the DSP engine and UI layer.
   - Includes [`AudioReactiveFrame`](class://com.tigerplayer.engine.AudioReactiveFrame), [`AcousticEnvironmentMode`](class://com.tigerplayer.engine.AcousticEnvironmentMode), [`PrismMode`](class://com.tigerplayer.engine.PrismMode), [`SpectralAnalysisMode`](class://com.tigerplayer.engine.SpectralAnalysisMode), [`PrismMixLevels`](class://com.tigerplayer.engine.PrismMixLevels), [`FilterType`](class://com.tigerplayer.engine.FilterType), [`AcousticNode`](class://com.tigerplayer.engine.AcousticNode), and [`StereoFrame`](class://com.tigerplayer.engine.StereoFrame).

2. **[`MonoBiquad.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/engine/MonoBiquad.kt)**
   - Direct Form I 2nd-order IIR Biquad filter implementation.
   - Provides methods for configuring Linkwitz-Riley 4th order (LR4) crossovers (`configureLowPass`, `configureHighPass`) and bandpass analysis filters (`configureBandPass`).
   - Manages z-plane delay memory ($z_1, z_2$) with zero-allocation `process()` operations.

3. **[`StateVariableFilter.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/engine/StateVariableFilter.kt)**
   - Cytomic State Variable Filter (SVF) topology by Andrew Simper replacing standard Biquads for parametric EQ.
   - Features coefficient interpolation (`smooth = 0.003f`) to eliminate zipper noise and pops during frequency/gain adjustments.
   - Supports `LOW_SHELF`, `PEAKING`, and `HIGH_SHELF` filter types.

4. **[`PrismIsolator.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/engine/PrismIsolator.kt)**
   - Dedicated 3-stem Mid/Side isolator using dual-cascaded 2nd-order biquads forming LR4 crossovers at $160\text{ Hz}$ and $3500\text{ Hz}$.
   - Features soft polynomial saturation to prevent foldback distortion without hard digital clipping:
     $$\text{softSaturate}(x) = x - 0.22 x^3 \quad (|x| \le 1.2)$$
   - Includes dynamic power normalization ($1 / \sqrt{\text{Vocals}^2 + \text{Beats}^2 + \text{Instruments}^2}$) and envelope peak limiting.

5. **[`SchroederHallProcessor.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/engine/SchroederHallProcessor.kt)**
   - 3D concert hall reverberator tank with 4 parallel comb filters ($31.1\text{ ms}$, $37.1\text{ ms}$, $41.1\text{ ms}$, $43.7\text{ ms}$) and series all-pass diffusion filters ($5.0\text{ ms}$, $1.7\text{ ms}$).
   - Integrates low-pass damping in comb feedback loops and `tanh()` soft-clipping to stabilize feedback loops.
   - Includes Haas effect early reflection delay lines ($13\text{ ms}$ / $19\text{ ms}$).

6. **[`FftSpectralAnalyzer.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/engine/FftSpectralAnalyzer.kt)**
   - 1024-point FFT spectral analysis engine utilizing `FloatFFT_1D` with Hann windowing.
   - Bins frequency magnitudes into 6 logarithmic bands ($60\text{ Hz}$, $250\text{ Hz}$, $1\text{ kHz}$, $2.5\text{ kHz}$, $6\text{ kHz}$, $12\text{ kHz}$).
   - Emits real-time logarithmic band energy profiles to drive visualizers.

### Verification & Performance Summary
- **Zero-Allocation Inner Loops**: Audio sample loops run with zero heap allocations during buffer processing.
- **CPU Optimization**: Dynamic parameter interpolation bypasses math calculations when filter state is steady.
- **VCS Status**: Maintained in `system documentation/engine/dspengine.md` (untracked in VCS).
