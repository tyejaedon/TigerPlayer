# TigerPlayer Graphics & Telemetry Sub-compartment (`engine/graphics/`)

This directory houses GPU rendering engines, FFT spectral analyzers, audio-reactive telemetry dispatchers, and waveform sample extractors.

---

## 1. Plain-English Overview

Think of the **Graphics Sub-compartment** as the "light show and telemetry room" of TigerPlayer:
- It listens to live music audio streams, measures frequencies and drum beats, and transforms that data into real-time GPU fluid simulations, waveform seekbars, and visualizer frames.
- It bridges high-speed audio signals into high-frame-rate (60/120 FPS) visual animations.

---

## 2. Component Breakdown

### `FluidRenderEngine.kt`
* **Role:** Real-time Navier-Stokes fluid simulation engine calculating fluid velocity fields, dye density, and particle advection for GPU shaders.
* **Views:** Includes `FluidVortexView` for audio-reactive full-player background animations.

### `GraphicsEngine.kt`
* **Role:** Manages AGSL shader compilation, RenderEffect blurs, EGL contexts, and dynamic color ramp extractions.

### `VizualizerEngine.kt` & `FftSpectralAnalyzer.kt`
* **Role:** Calculates 512/1024-point Fast Fourier Transforms over raw PCM buffers (`FftSpectralAnalyzer`) and dispatches downsampled frequency energy frames to UI visualizers (`VizualizerEngine`).

### `WaveformEngine.kt`
* **Role:** Downsamples audio PCM streams into visual waveform peak envelopes for seekbars and mini-visualizers.

### `PrismIsolator.kt`
* **Role:** 4th-order Linkwitz-Riley (LR4) crossover networks separating audio into Vocals, Beats, and Instruments for stem-isolated visual reactivity.

---

## 3. Threading & Rendering Rules

1. **Confluent StateFlow:** Telemetry emitted to Compose UI uses `MutableStateFlow` with confluent conflation to prevent UI rendering from blocking the audio thread.
2. **GPU Memory Lifecycle:** Shader programs, FrameBuffer Objects (FBOs), and textures must implement deterministic `release()` calls to prevent VRAM memory leaks.
3. **No Per-Frame Allocations:** Render loops (`onDrawFrame`) must use pre-allocated buffers and float arrays.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Graphics Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Navier-Stokes** | Mathematical equations describing fluid motion. | Powers interactive fluid visualizer simulations in `FluidRenderEngine`. | Stiff, unnatural, or laggy visualizer movement. |
| **Spectral Flux** | Rate of sound energy change across frequencies. | Detects percussion hits to drive visualizer pulses. | Visualizers pulse out of sync with rhythmic beats. |
| **Confluent StateFlow** | Reactive stream that drops stale frames if the receiver is busy. | Passes telemetry from audio thread to 60/120 FPS UI render loops safely. | UI frame drops or audio thread thread-lock. |
