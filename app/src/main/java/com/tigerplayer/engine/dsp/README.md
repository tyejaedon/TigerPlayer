# TigerPlayer DSP Sub-compartment (`engine/dsp/`)

This directory houses the **Digital Signal Processing (DSP)** algorithms, real-time audio processors, biquad filter mathematics, reverb processors, and offload volume management components of TigerPlayer.

---

## 1. Plain-English Overview

Think of the **DSP Sub-compartment** as the "sound studio hardware rack" of TigerPlayer:
- Every time you adjust the equalizer, turn up the bass, enable concert hall reverb, balance song volumes (ReplayGain), or switch output devices, the math in this directory processes every digital sound wave sample.
- It operates directly on ExoPlayer's audio thread to deliver zero-latency sound enhancement without audio stutter.

---

## 2. Component Breakdown

### `AdaptiveDspEngine.kt`
* **Role:** Central Media3 `AudioProcessor` executing 6 real-time audio pipeline stages:
  1. Stereo channel widening (Mid/Side processing).
  2. Parametric Equalization (PEQ) via State Variable Filters.
  3. Acoustic Environment modeling (Studio Bypass, Vinyl Wow/LFO, Concert Hall Reverb).
  4. Prism 3-stem frequency isolator (Vocals, Beats, Instruments).
  5. Sample-rate aware look-ahead peak limiter and AGC.
  6. 6-band spectral analysis emitting telemetry (`AudioReactiveFrame`).

### `StateVariableFilter.kt`
* **Role:** Cytotoxic Topology-Preserving Transform (TPT) State Variable Filter (SVF). Retains numerical stability and zero parameter zipper noise during rapid frequency or Q slider adjustments.

### `SchroederHallProcessor.kt`
* **Role:** Algorithmic reverberation engine employing 4 feedback comb filters (`1116`, `1188`, `1277`, `1356` samples) and 2 series all-pass diffusion filters (`225`, `556` samples).

### `ReplayGainCalculator.kt` & `OffloadVolumePolicy.kt`
* **Role:** Dynamic track loudness normalization (`ReplayGainCalculator`) and 4-phase smooth step volume ramp schedules during audio offload transitions (`OffloadVolumePolicy`).

### `MonoBiquad.kt` & `DspModels.kt` & `AudioDeviceEngine.kt`
* **Role:** Direct Form II biquad audio filter (`MonoBiquad`), EQ preset data structures (`DspModels`), and AutoEQ headphone curve matching (`AudioDeviceEngine`).

---

## 3. Audio Thread Invariants

1. **Zero Allocation Rule:** Never allocate objects (`new`, `ArrayList`, lambdas, string concatenations) inside real-time audio processing loops (`queueInput`, `processAudio`).
2. **Main Safety:** Coefficient calculation and curve fitting run off the audio thread; update atomic filter parameters safely.
3. **True Peak Ceiling:** Signal output must be clamped to prevent digital clipping distortion ($1.0\text{f} / 0\text{ dBFS}$).

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in DSP Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **State Variable Filter (SVF)** | Analog-modeling filter topology for smooth parameter changes. | Powers interactive parametric EQ bands. | Audio pops or zipper noise when dragging sliders. |
| **Look-Ahead Limiter** | Anticipatory volume clamp buffering ~0.8ms of sound. | Prevents speaker clipping during high EQ boosts. | Digital distortion and speaker crackle. |
| **Comb Filter** | Delay line feedback filter producing acoustic reflections. | Core component of `SchroederHallProcessor` reverb. | Metallic ringing if feedback gains are wrong. |
