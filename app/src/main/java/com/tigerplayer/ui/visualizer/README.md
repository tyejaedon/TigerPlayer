# TigerPlayer Visualizer Sub-compartment (`ui/visualizer/`)

This directory houses full-screen immersive audio visualizers and GPU-accelerated graphic render surfaces.

---

## 1. Plain-English Overview

Think of the **Visualizer Sub-compartment** as the "full-screen music show" of TigerPlayer:
- It provides a dedicated full-screen mode for fluid simulations, spectrum bars, and audio-reactive visuals.

---

## 2. Component Breakdown

### `ImmersiveVisualizerScreen.kt`
* **Role:** Full-screen composable hosting fluid shaders, AGSL RenderEffects, and audio-reactive telemetry animations.

---

## 3. UI Guidelines

1. **System Bar Hiding:** Enforces immersive edge-to-edge rendering with hidden system status and navigation bars.
2. **Gesture Support:** Supports tap-to-dismiss gestures to return to playback controls.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Visualizer Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Immersive Mode** | Full-screen view hiding system bars. | Enabled in `ImmersiveVisualizerScreen.kt`. | System status bar covers full-screen visualizer graphics. |
