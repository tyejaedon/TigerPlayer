# Audiophile Parametric EQ & Lossless Signal Pipeline Specification

## Executive Summary

This specification defines the architectural transition of TigerPlayer's equalization subsystem from a 4-band gesture visualizer ("Aural Nexus") into a studio-grade, bit-depth-preserving Parametric Equalizer (PEQ) and lossless audio pipeline. 

TigerPlayer is built for local libraries containing high-resolution audio (FLAC, ALAC, WAV) alongside remote sources (Navidrome, Spotify). Lossless playback exists to deliver master recordings without loss of dynamic range, harmonic distortion, or transient smearing. The current implementation in `app/src/main/java/com/tigerplayer/ui/equalizer` and `AdaptiveDspEngine.kt` contains severe architectural bottlenecks that degrade lossless FLAC streams.

This plan details the technical root causes, the target DSP and UI architectures, and a phased roadmap of discrete GitHub issues under Milestone **v2.3 Signature**.

---

## 1. Codebase Audit: Current State & Critical Bottlenecks

### 1.1 The 16-Bit Truncation Bottleneck (Dynamic Range Degradation)
In [`AdaptiveDspEngine.kt`](../app/src/main/java/com/tigerplayer/engine/DspEngine.kt#L433-L437):
```kotlin
this.outputAudioFormat = AudioFormat(
    inputAudioFormat.sampleRate,
    inputAudioFormat.channelCount,
    C.ENCODING_PCM_16BIT
)
```
And in [`queueInput()`](../app/src/main/java/com/tigerplayer/engine/DspEngine.kt#L728-L731):
```kotlin
outBuffer.putShort((sampleL.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
if (channels == 2) {
    outBuffer.putShort((sampleR.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
}
```
* **Failure:** Even though 24-bit PCM and 32-bit Float FLAC audio are accepted as input, the output format is unconditionally forced to 16-bit PCM.
* **Quantization Distortion:** Truncating 24-bit audio to 16-bit without **Triangular Probability Density Function (TPDF) dithering** introduces correlated harmonic distortion during low-level passages, reverbs, and acoustic decays. The 144 dB dynamic range of 24-bit FLAC is forcibly reduced to 96 dB.

### 1.2 Unwanted Dynamic Compression (`LIMITER_THRESHOLD = 0.82f`)
In [`AdaptiveDspEngine.kt`](../app/src/main/java/com/tigerplayer/engine/DspEngine.kt#L46):
```kotlin
private const val LIMITER_THRESHOLD = 0.82f
```
And in the look-ahead AGC processing loop:
```kotlin
val reduction = if (agcEnvelope > LIMITER_THRESHOLD) LIMITER_THRESHOLD / agcEnvelope else 1f
sampleL = (delayedL * reduction).coerceIn(-1.0f, 1.0f)
```
* **Failure:** $0.82$ linear corresponds to **$-1.72\text{ dBFS}$**. Modern high-fidelity and classical masters regularly peak between $-0.5\text{ dBFS}$ and $0.0\text{ dBFS}$.
* **Auditory Result:** The AGC actively compresses transient peaks and alters the dynamics of uncompressed FLAC music, introducing gain pumping even when the EQ is flat.

### 1.3 Fletcher-Munson Trap via Naive Auto-Makeup Gain
In [`AdaptiveDspEngine.updateAcousticNodes()`](../app/src/main/java/com/tigerplayer/engine/DspEngine.kt#L263-L268):
```kotlin
val maxBoostDb = nodes.filter { it.gainDb > 0f }.sumOf { it.gainDb.toDouble() }.toFloat()
autoMakeupGain = if (maxBoostDb > 0f) {
    10f.pow(-maxBoostDb / 20f)
} else {
    1.0f
}
```
* **Failure:** Positively boosted band values are summed linearly. For example, a $+5\text{ dB}$ sub-bass boost and a $+4\text{ dB}$ air boost result in a $+9\text{ dB}$ sum, causing the engine to attenuate the entire track by $-9\text{ dB}$.
* **Auditory Result:** Because sub-bass (60 Hz) and air (12 kHz) do not peak simultaneously, this massive attenuation triggers the **Fletcher-Munson equal-loudness illusion**: lower overall volume makes the audio sound thin and hollow, leading users to believe the EQ degrades audio quality.

### 1.4 Coupled 2D XY Touchpad & Implicit Q Factor
In [`NexusScreen.kt`](../app/src/main/java/com/tigerplayer/ui/equalizer/NexusScreen.kt) and [`NexusViewModel.kt`](../app/src/main/java/com/tigerplayer/ui/equalizer/NexusViewModel.kt#L57-L61):
* **Coupled Parameters:** Moving a node adjusts Frequency on the X-axis and Gain on the Y-axis simultaneously. Precise single-parameter adjustment is impossible on touch screens.
* **Uncontrollable Q:** Filter $Q$ is computed via an arbitrary radial formula (`0.9f + (1f - abs(clampedY)) * 3.1f`). Users cannot manually narrow a band to isolate a resonance or widen a shelf.
* **No Numerical Telemetry:** The UI displays only abstract neon rings and labels (*SUB*, *BODY*, *PRESENCE*, *AIR*) with no numerical values for Center Frequency (Hz), Gain (dB), or Q.
* **Band Count Limit:** 4 fixed nodes cannot correct headphone resonances, room modes, or follow standardized targets (e.g., Harman Target).

### 1.5 Disconnected `AutoEqParser` & Dead Code
In [`AudioPlayerService.kt`](../app/src/main/java/com/tigerplayer/service/AudioPlayerService.kt#L936-L941):
* An `AutoEqParser` exists inside `AudioPlayerService.kt` and responds to `ACTION_LOAD_PEQ`.
* **Failure:** `ACTION_LOAD_PEQ` is never invoked by any ViewModel, Screen, or Repository in the application. It is completely isolated dead code, while the UI only knows about 4 hardcoded spatial nodes.

---

## 2. Target Architecture: Studio-Grade Lossless Pipeline

```
  ┌─────────────────────────────────────────────────────────────┐
  │                 Lossless Input Audio Stream                 │
  │     (FLAC / ALAC / WAV: 16-bit, 24-bit, or 32-bit Float)    │
  └──────────────────────────────┬──────────────────────────────┘
                                 │
                                 ▼
  ┌─────────────────────────────────────────────────────────────┐
  │                   Bit-Perfect Bypass Check                  │
  │  Is EQ Flat/Bypassed AND No DSP Active AND Sink Offloadable?│
  └──────────────┬───────────────────────────────┬──────────────┘
                 │ YES                           │ NO
                 ▼                               ▼
  ┌─────────────────────────────┐ ┌─────────────────────────────┐
  │  Direct Bitstream Passthrough│ │ 32-Bit Float DSP Pipeline   │
  │  - Zero processing overhead │ │ - Peak-normalized preamp    │
  │  - Bit-perfect to USB/HAL   │ │ - 10-band StateVariableFilter│
  │  - Offload enabled          │ │ - Sample-rate tracking      │
  └─────────────────────────────┘ └──────────────┬──────────────┘
                                                 │
                                                 ▼
                                  ┌─────────────────────────────┐
                                  │   Sink Format Negotiation   │
                                  ├─────────────────────────────┤
                                  │ • Float32 Sink: Direct out  │
                                  │ • 24-bit Sink: 24-bit PCM   │
                                  │ • 16-bit Sink: TPDF Dither  │
                                  └─────────────────────────────┘
```

### 2.1 Core Engineering Principles

1. **Bit-Depth Preservation & Dither:**
   * Downstream Media3 processors must negotiate `ENCODING_PCM_FLOAT` or preserve input bit-depth (24-bit/32-bit).
   * When conversion to 16-bit integer is required by the hardware sink, high-pass triangular PDF (TPDF) dither must be applied to decorrelate quantization noise from the audio signal.
2. **True Composite-Peak Headroom (Preamp):**
   * Instead of summing positive gains, the DSP engine calculates the actual peak gain of the composite filter response curve across the spectrum ($20\text{ Hz} - 20\text{ kHz}$) using the transfer function.
   * Auto-headroom sets:
     $$\text{Preamp Gain (dB)} = -\max(0\text{ dB}, \text{Composite Peak Gain})$$
   * Manual Preamp slider ($-15\text{ dB}$ to $+15\text{ dB}$) with clipping/headroom meter.
3. **Dual-Mode UI Interface:**
   * **Simple Mode ("Nexus"):** Retain the fluid 2D gesture pad for quick mood shaping and cover-screen flip-phone use.
   * **Studio Mode ("Parametric"):** Full-featured audiophile workspace:
     * Logarithmic frequency response display (20 Hz – 20 kHz, $-15\text{ dB}$ to $+15\text{ dB}$).
     * Interactive draggable nodes with independent fine-tuning steppers for Frequency (1 Hz), Gain (0.1 dB), and Q (0.01).
     * 5 to 10 fully parametric bands with selectable filter topologies (Peaking, Low Shelf, High Shelf, Notch, Low Pass, High Pass).
     * AutoEQ profile import (direct paste or file selector).

---

## 3. Phased Implementation Roadmap

All issues are cultivated under Milestone **v2.3 Signature**.

### Phase 1: Engine Foundation & Bit-Depth Integrity
* **[#278](https://github.com/tyejaedon/TigerPlayer/issues/278): Audio Pipeline: True 24-bit/Float32 PCM pass-through and TPDF dithering in AdaptiveDspEngine**
  * *Area:* `area:dsp`, `differentiator`, `P1-high`
  * *Scope:* Remove hardcoded 16-bit output in `AdaptiveDspEngine.configure()`; negotiate Float32/24-bit output; implement TPDF dither generator for 16-bit fallback sinks; replace the fixed 0.82 AGC limiter with a transparent true-peak ceiling.

* **[#279](https://github.com/tyejaedon/TigerPlayer/issues/279): DSP: Peak-normalized composite headroom and auto-preamp calculation**
  * *Area:* `area:dsp`, `P1-high`
  * *Scope:* Replace naive positive gain summation with composite filter curve peak detection using `BiquadDesigner.magnitudeAt()`; provide automated negative pre-gain and manual preamp adjustment to prevent inter-sample clipping without dynamic compression.

### Phase 2: Domain Modeling & AutoEQ Decoupling
* **[#280](https://github.com/tyejaedon/TigerPlayer/issues/280): DSP: Decouple and wire AutoEqParser with dynamic multi-band ParametricEqProfile engine**
  * *Area:* `area:dsp`, `area:playback`, `differentiator`, `P1-high`
  * *Scope:* Extract `AutoEqParser` from `AudioPlayerService.kt` into `com.tigerplayer.engine.peq`; create `ParametricEqProfile` and `ParametricBand` domain models (up to 10 bands); wire dynamic SVF filter creation in `AdaptiveDspEngine`; add unit tests for AutoEQ format parsing and curve generation.

### Phase 3: Studio Parametric UI & User Experience
* **[#281](https://github.com/tyejaedon/TigerPlayer/issues/281): UI: Studio Parametric Equalizer screen with interactive frequency curve and numerical controls**
  * *Area:* `area:ui`, `feature`, `differentiator`, `P1-high`
  * *Scope:* Build `ParametricEqScreen` and `ParametricEqViewModel`; implement interactive Canvas curve with draggable nodes; build per-band parameter cards with Hz/dB/Q controls; implement AutoEQ import dialog and profile management; provide a toggle between Simple Nexus and Studio Parametric modes.

### Phase 4: Bit-Perfect Passthrough & Signal Path Transparency
* **[#282](https://github.com/tyejaedon/TigerPlayer/issues/282): Playback: Zero-cost bit-perfect bypass and signal-path telemetry for untouched FLAC streams**
  * *Area:* `area:playback`, `area:dsp`, `differentiator`, `P2-medium`
  * *Scope:* Implement direct buffer passthrough or processor detachment when EQ is bypassed/flat; coordinate with Issue #70 (Signal Path Badge) to expose live telemetry (`Source Bit-Depth/Rate` -> `DSP State` -> `Output Sink`).

### Dependency Graph

```
#278 24-bit/Float32 Pipeline ──┬─> #279 Peak-Normalized Headroom
                                └─> #280 Multi-Band PEQ Engine ──┬─> #281 Studio PEQ UI ──> #66 Per-Device AutoEQ
                                                                 └─> #282 Bit-Perfect Bypass ──> #70 Signal Path Badge
```

---

## 4. Verification & Testing Strategy

1. **DSP Math & Bit-Depth Verification:**
   * Unit test asserting that 24-bit and 32-bit Float buffers passed to `AdaptiveDspEngine.queueInput()` are emitted without bit truncation when sink supports Float.
   * Statistical test on TPDF dither output asserting triangular distribution across $\pm 1\text{ LSB}$ without DC offset.
2. **Frequency & Headroom Verification:**
   * Composite peak calculation unit test comparing simulated filter curves against calculated auto-preamp attenuation.
   * Verification that sine waves at $0.0\text{ dBFS}$ with $+6\text{ dB}$ boost do not produce clipped flat-tops when auto-headroom is active.
3. **AutoEQ Parser Verification:**
   * Unit tests parsing standard Oratory1990 and Crinacle AutoEQ profiles (e.g., Sennheiser HD650, Sony WH-1000XM4), validating Preamp, Fc, Gain, and Q values.
4. **UI Regression & Cover Screen Verification:**
   * Verify that existing `CoverScreenNexusView` continues to function using the underlying multi-band engine.
   * Compose preview and instrumented tests for the new `ParametricEqScreen`.
