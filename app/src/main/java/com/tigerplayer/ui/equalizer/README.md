# TigerPlayer Equalizer & DSP UI Sub-compartment (`ui/equalizer/`)

This directory houses the parametric equalizer user interface, frequency curve visualizers, acoustic environment selectors, and Nexus DSP controls.

---

## 1. Plain-English Overview

Think of the **Equalizer UI Sub-compartment** as the "sound control desk" of TigerPlayer:
- It gives you interactive control over the 10-band parametric equalizer, bass boost, stereo widening, and concert hall reverb.
- It draws real-time frequency curve graphs so you can visually see how your EQ adjustments shape the sound.

---

## 2. Component Breakdown

### `EqualizerHubView.kt` & `EqualizerHubViewModel.kt`
* **Role:** Comprehensive equalizer hub allowing users to adjust 10 EQ frequency bands, select presets, toggle AutoEQ target curves, and control gain levels.

### `EqualizerFrequencyCurve.kt`
* **Role:** Canvas composable drawing the continuous logarithmic frequency response curve ($20\text{ Hz} - 20\text{ kHz}$) computed from active biquad filter coefficients.

### `NexusScreen.kt` & `NexusViewModel.kt`
* **Role:** "Aural Nexus" master control view combining EQ, Prism 3-stem isolator sliders, and real-time audio-reactive spectrum graphs.

### `AcousticEnvironmentScreen.kt` & `AcousticEnvironmentViewModel.kt`
* **Role:** Interactive selector for acoustic environment models (Studio Bypass, Vinyl Warmth, Concert Hall Reverb).

---

## 3. Performance Invariants

1. **Logarithmic Frequency Mapping:** Frequency curve plotting uses logarithmic X-axis scaling ($\log_{10}(f)$) to accurately represent human hearing octaves.
2. **Smooth Slider Throttling:** Slider updates update ViewModel StateFlow without stalling ExoPlayer audio processing threads.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Equalizer UI Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Frequency Response Curve** | Graph showing sound volume changes across pitch frequencies. | Drawn by `EqualizerFrequencyCurve.kt` to visualize EQ shaping. | Misleading visual representation of equalizer settings. |
| **Logarithmic Scaling** | Spacing frequencies by octaves (20Hz, 200Hz, 2kHz, 20kHz). | Matches human pitch perception on the frequency curve canvas. | EQ frequency curve looks cramped and distorted. |
