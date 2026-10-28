---
name: audioreactive-ui-visualizer
description: Architecture, rendering performance, and haptic synchronization for real-time 60/120 FPS audio-reactive visualizers and haptic feedback in TigerPlayer.
---

# Audio-Reactive UI & Visualizer Skill

This skill provides implementation patterns, performance guardrails, and rendering mechanics for TigerPlayer's audio-reactive visualization suite (such as `TigerVortexRender`, `FluidRenderEngine`, `CoverScreenDiscoveryView`, and `AudioPlayerService` haptics).

---

## 1. Plain-English Architecture Overview

Think of the **Visualizer & Haptics Engine** as the senses of the music player—its eyes and touch:
1. **Audio Eavesdropper (Non-blocking Sniffer):** While music flows through the audio pipeline, lightweight detectors take tiny snapshot samples of the sound frequencies without slowing down or delaying playback.
2. **Sound-to-Light Translator (Visualizers):** Converts bass vibrations and treble brightness into animated swirling neon graphics and pulsing colors at silky-smooth 60 or 120 frames per second.
3. **Sound-to-Touch Translator (Reactive Haptics):** Detects heavy bass beats and energetic musical drops, pulsing the phone's vibration motor in precise sync with the rhythm like a miniature subwoofer in your palm.

---

## 2. Real-Time Telemetry Pipeline

```
[ExoPlayer PCM Audio Stream]
            │
            ▼
┌───────────────────────────┐
│ Media3 AudioProcessor     │
│ 1. FftProcessor           │ ──> Performs 512/1024-point Fast Fourier Transform
│ 2. WaveformCaptureProc    │ ──> Computes RMS amplitude & downsamples waveform
└───────────┬───────────────┘
            │
            ▼
[AudioReactiveFrame (StateFlow)]  <-- Thread boundary (Audio Thread -> UI Coroutines)
            │
     ┌──────┴───────────────────────────────────┐
     ▼                                          ▼
[Jetpack Compose Renderers]           [Audio-Reactive Haptics Controller]
- AGSL Canvas Shader (Vortex)         - Spectral Flux & Bass Energy Detector
- Neon Waveform Ring (PipSurface)     - Exponential Decay Smoothing Filter
- Cover Screen Discovery Glow         - VibratorManager Cooldown Pulser
```

---

## 3. High-Performance Visualization Standards (60 / 120 FPS)

Rendering complex graphics in real time requires strict adherence to Android performance guardrails:

### Guardrail 1: Zero Allocation in Draw Passes
* **The Rule:** Never instantiate objects (`Paint`, `Path`, `Color`, `Shader`, lambdas, or arrays) inside `Canvas`, `drawWithContent`, or `onDrawBehind`.
* **The Solution:** Pre-allocate vectors, coordinate arrays, and paint objects inside `remember` blocks or ViewModel holders. Re-use existing buffers across frame updates to prevent Android Garbage Collection (GC) pauses that cause stutter.

### Guardrail 2: 6-Band Spectral Energy Partitioning
The `FftProcessor` categorizes raw spectral frequencies into 6 human-perceptible dynamic bands:
1. **Sub-Bass (< 60 Hz):** Powers foundational canvas pulse, camera zoom, and deep haptics.
2. **Bass (60 - 250 Hz):** Drives primary rhythm rings, bar visualizers, and beat pulses.
3. **Low-Mid (250 - 500 Hz):** Controls harmonic warmth and core particle movement.
4. **Mid (500 - 2,000 Hz):** Reflects vocal intensity, lyric glow, and center vortex density.
5. **High-Mid (2,000 - 4,000 Hz):** Modulates snare snap, brass flare, and edge brightness.
6. **Presence (> 4,000 Hz):** Animates fine particle shimmer, vinyl sparkle, and high-frequency wave peaks.

---

## 4. Audio-Reactive Haptics Choreography

Audio-reactive haptics must feel rhythmic and organic, not erratic or buzzing. `AudioPlayerService.kt` enforces this via:

### 1. Multi-Factor Trigger Score
The haptic excitation score $S_h$ is calculated from three real-time audio metrics:
$$S_h = (w_{\text{bass}} \cdot E_{\text{bass}}) + (w_{\text{energy}} \cdot E_{\text{total}}) + (w_{\text{flux}} \cdot \text{Flux}_{\text{spectral}})$$
Where **Spectral Flux** measures the rate of energy increase from the previous frame to detect instantaneous drum beats and transient hits.

### 2. Temporal Smoothing & Cooldown
* **Smoothing Filter:** Applies asymmetric attack/release smoothing so the score rises quickly on a drum hit and fades smoothly:
  $$S_{\text{smoothed}} = S_{\text{smoothed}} + (S_{\text{target}} - S_{\text{smoothed}}) \cdot \alpha$$
* **Refractory Cooldown:** Enforces a minimum `90 ms` cooldown (`AUDIO_REACTIVE_HAPTIC_COOLDOWN_MS`) between pulses. This prevents the phone vibrator motor from overheating or blurring individual drum hits into an unappealing continuous hum.

### 3. Vibration Profiles & Safety Bounds
* **Profiles:** `BALANCED`, `PUNCHY`, `SUBTLE`.
* **Clamping:** Amplitudes are constrained between `40` (minimum perceptible tick) and `255` (maximum motor limit). Pulse durations are bounded between `10 ms` and `20 ms` for crisp, percussive haptic clicks.

---

## 5. Tabulated Visualizer & Haptic Jargon Glossary

| Jargon Term | Plain-English Definition | Role in TigerPlayer Pipeline | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **FFT (Fast Fourier Transform)** | A math algorithm that converts raw audio waves into frequency bars. | Computes bass, mid, and treble energy levels for visualizers in real time. | Visualizers display static or random shapes instead of dancing to the music. |
| **Spectral Flux** | The speed at which sound energy changes from one millisecond to the next. | Identifies sharp drum hits and sudden musical attacks for haptic beats. | Haptic vibration triggers randomly rather than on the beat. |
| **AGSL (Android Graphics Shading Language)** | Custom code that runs directly on the phone's graphics chip (GPU). | Powers complex fluid distortion and neon vortex visuals in real time. | Sluggish frame rates (below 30 FPS) or high device battery consumption. |
| **Zero Allocation** | Coding practice of reusing memory instead of creating new variables. | Prevents Android's memory cleaner from interrupting 60/120 FPS animations. | Visual animations hiccup, drop frames, or stutter during playback. |
| **RMS (Root Mean Square)** | A mathematical measurement of average audio signal power. | Dictates overall visualizer glow intensity and wave amplitude scale. | Overly sensitive or unresponsive wave rendering. |
| **Refractory Cooldown** | A mandatory brief pause before allowing another action. | Ensures the phone vibration motor resets between beats. | Vibrator motor buzzes continuously instead of pulsing rhythmically. |
