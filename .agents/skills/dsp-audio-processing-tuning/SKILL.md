---
name: dsp-audio-processing-tuning
description: Algorithmic guide and tuning reference for real-time PCM audio processing in AdaptiveDspEngine, including State Variable Filters, Linkwitz-Riley crossovers, Schroeder reverb, look-ahead limiting, and ReplayGain.
---

# DSP Audio Processing & Tuning Skill

This skill provides algorithmic details, tuning parameters, and verification procedures for TigerPlayer's digital signal processing (DSP) pipeline implemented in `AdaptiveDspEngine.kt` and its satellite processing classes.

---

## 1. Plain-English Architecture Overview

Think of the **DSP Engine** (*Digital Signal Processing Engine*) as a master recording studio inside the app. Before audio bytes are sent to the phone's physical speakers, they pass through a series of specialized virtual audio processors:
1. **Tone Adjuster (PEQ):** Boosts or attenuates precise frequencies (bass, vocal presence, treble sparkle) using mathematical state filters that never introduce audio glitching even when adjusted in real time.
2. **Stem Separator (Prism):** Splits music into three distinct sound channels—Vocals, Beats, and Instruments—allowing individual volume control or isolation.
3. **Room Acoustic Simulator (Acoustic Environment):** Adds the warmth and natural spatial echoes of a concert hall or vinyl record player.
4. **Safety Limiter & Leveler (Limiter & ReplayGain):** Normalizes song volume differences and prevents harsh crackling distortion when high sound peaks occur.

---

## 2. Core Processing Compartments & Algorithms

### Compartment A: Parametric Equalizer (Cytotoxic State Variable Filters)
* **Location:** `StateVariableFilter.kt` and `AdaptiveDspEngine.kt`
* **Algorithm:** Topology-Preserving Transform (TPT) State Variable Filter (SVF).
* **Why SVF over Biquad Direct Form:** Standard Direct Form I/II biquad filters can become numerically unstable or pop when coefficient parameters (frequency, Q, gain) are modulated in real time. Cytotoxic SVF guarantees stability and maintains constant phase and energy during real-time slider manipulation.
* **Equations (Sample Processing):**
  $$g = \tan\left(\frac{\pi \cdot f_c}{f_s}\right)$$
  $$R = \frac{1}{2 \cdot Q}$$
  $$v_1 = \frac{s_1 + g \cdot (x - s_2)}{1 + 2 \cdot R \cdot g + g^2}$$
  $$v_2 = s_2 + g \cdot v_1$$
  $$s_1 = 2 \cdot v_1 - s_1, \quad s_2 = 2 \cdot v_2 - s_2$$

### Compartment B: Prism 3-Stem Isolator
* **Location:** `PrismIsolator.kt`
* **Algorithm:** Cascaded 4th-order Linkwitz-Riley (LR4) crossovers composed of two cascaded 2nd-order Butterworth filters.
* **Band Separation:**
  - **Beats (Low Band):** Low-pass filtered below `250 Hz`.
  - **Vocals (Mid Band):** Band-pass filtered between `250 Hz` and `3,400 Hz` (the human vocal formant spectrum).
  - **Instruments (High/Remainder):** High-pass filtered above `3,400 Hz` combined with side-band mid frequencies.
* **Phase Invariant:** Linkwitz-Riley crossovers sum to an exact flat unity amplitude response ($0\text{ dB}$) across the entire crossover frequency region without phase cancellation.

### Compartment C: Schroeder Hall Reverberation
* **Location:** `SchroederHallProcessor.kt`
* **Algorithm:** Classic Schroeder Reverb structure:
  - 4 parallel Feedback Comb Filters (delay lines tuned to mutually prime sample lengths: `1116`, `1188`, `1277`, `1356` samples) to create natural modal density without ringing.
  - 2 series All-Pass Filters (`delay = 225` and `556` samples) to diffuse sound reflections smoothly across stereo channels.
  - Stereophonic decorrelation via small right-channel delay offsets.

### Compartment D: Look-Ahead Peak Limiter & Automatic Gain Control (AGC)
* **Location:** `AdaptiveDspEngine.kt`
* **Configuration:**
  - `LOOK_AHEAD_BUFFER_SIZE = 1024` samples (fixed allocation to prevent GC churn).
  - `lookAheadDelaySamples = 240` samples (approx. `5.4 ms` at `44.1 kHz`).
  - `LIMITER_THRESHOLD = 0.82f` (allows headroom for inter-sample overs).
* **Operation:** Audio frames enter a circular delay line. The detector inspects upcoming signal peaks $5\text{ ms}$ ahead. If an upcoming peak exceeds the threshold, the gain reduction curve begins attenuating *before* the peak hits the speaker output, completely eliminating harsh clipping with zero transient smearing.

### Compartment E: ReplayGain Dynamic Scaling
* **Location:** `ReplayGainCalculator.kt` & `ReplayGainTagParser.kt`
* **Modes:** `OFF`, `TRACK`, `ALBUM`, `SMART` (Album gain for sequential album playback, Track gain for shuffle).
* **Gain Calculation:**
  $$\text{Gain}_{\text{linear}} = 10^{\frac{\text{TargetGain}_{\text{dB}} + \text{Preamp}_{\text{dB}}}{20}}$$
  If `preventClipping` is true, gain is clamped so $\text{Gain}_{\text{linear}} \cdot \text{Peak} \le 1.0$.

---

## 3. Hardware Quirk Handling (Samsung Compatibility)

* **Float vs Integer PCM:** Many Samsung audio drivers crash or drop frames in the hardware HAL when feeding 32-bit floating point audio directly to certain Bluetooth decoders or SoundAlive post-processors.
* **Guardrail:** In `AudioPlayerService.kt`:
  ```kotlin
  val enableFloatOutput = !isSamsungDevice
  ```
  On Samsung hardware, the sink processes internally as float but down-samples cleanly to 16-bit integer PCM before passing buffers to Android `AudioTrack`.

---

## 4. Tabulated DSP Jargon Glossary

| Jargon Term | Plain-English Definition | Role in TigerPlayer Pipeline | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Biquad Filter** | A 2nd-order recursive digital audio filter. | Used in baseline tone adjustments and PEQ bands. | Ringing artifacts if coefficients clip. |
| **State Variable Filter (SVF)** | An advanced digital filter topology modeling analog circuits. | Implements interactive EQ bands without zipper noise. | Stability loss under extreme frequencies if equations are unnormalized. |
| **Crossover Network** | Filters that divide a single audio signal into separate frequency ranges. | Splits song into Vocals, Beats, and Instruments in Prism isolator. | Phase cancellations or hollow-sounding frequency dips. |
| **Linkwitz-Riley (LR4)** | A 4th-order crossover filter with a 24 dB/octave slope. | Ensures adjacent frequency stems sum to a perfectly flat response. | Volume dips or phase distortion at the crossover point. |
| **Comb Filter** | A delayed version of a signal added to itself. | Simulates the multiple acoustic room reflections in the Schroeder reverb. | Metallic ringing if loop gain exceeds stability limits. |
| **All-Pass Filter** | A filter that modifies phase without altering frequency amplitudes. | Diffuses echo reflections in the reverb stage to sound like a spacious room. | Flat, mechanical-sounding artificial reverb. |
| **Look-Ahead Limiter** | An anticipatory volume limiter that buffers sound briefly. | Detects upcoming peaks and clamps gain before digital clipping occurs. | Audible clipping crackles or distorted transient kicks. |
| **ReplayGain** | Metadata specifying volume adjustments to match perceived loudness. | Normalizes volume differences between tracks from different albums. | Sudden volume jumps when switching songs in shuffle mode. |
