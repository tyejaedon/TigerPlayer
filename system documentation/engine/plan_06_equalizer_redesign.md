# Phase 6 Plan: Equalizer & DSP Hub Redesign (Settings Tab & Nexus Canvas Integration)

## Executive Overview & Architectural Vision

Currently, TigerPlayer's audio processing controls are fragmented across separate screens ([`NexusScreen.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/ui/equalizer/NexusScreen.kt), [`AcousticEnvironmentScreen.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/ui/equalizer/AcousticEnvironmentScreen.kt), and [`SettingScreen.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/ui/settings/SettingScreen.kt)).

This plan outlines a complete redesign that **consolidates all EQ and DSP features into a unified "Equalizer & DSP Hub"** accessible as a dedicated **EQ Tab inside the Settings Menu** while retaining the futuristic 2D **Aural Nexus Spatial Canvas** as an interactive advanced mode.

---

## 1. Unified Equalizer & DSP Hub Architecture

```
                       ┌──────────────────────────────────────────┐
                       │   Settings Screen (Equalizer & DSP Tab)  │
                       └────────────────────┬─────────────────────┘
                                            │
               ┌────────────────────────────┼────────────────────────────┐
               ▼                            ▼                            ▼
  ┌─────────────────────────┐  ┌─────────────────────────┐  ┌─────────────────────────┐
  │  Tab 1: Parametric EQ   │  │   Tab 2: Aural Nexus    │  │  Tab 3: Environment &   │
  │  Precision 5-Band Faders│  │ 2D Spatial Node Canvas  │  │ Hardware Compensation   │
  └────────────┬────────────┘  └────────────┬────────────┘  └────────────┬────────────┘
               │                            │                            │
               └────────────────────────────┼────────────────────────────┘
                                            │
                                            ▼
                               ┌─────────────────────────┐
                               │    EqualizerViewModel   │
                               └────────────┬────────────┘
                                            │
                                            ▼
                               ┌─────────────────────────┐
                               │    AdaptiveDspEngine    │
                               └─────────────────────────┘
```

---

## 2. Component Breakdown & Tab Specifications

### Tab 1: Parametric 5-Band Precision Faders (`ParametricEqTab`)
Provides traditional, precise frequency adjustment faders alongside a real-time frequency response curve overlay:
- **Frequency Bands**: $60\text{ Hz}$ (Sub-Bass), $230\text{ Hz}$ (Body/Warmth), $910\text{ Hz}$ (Mids), $3.6\text{ kHz}$ (Presence), $14\text{ kHz}$ (Treble Air).
- **Gain Range**: $-15\text{ dB}$ to $+15\text{ dB}$ with fine $0.5\text{ dB}$ increments.
- **Response Curve Overlay**: Real-time logarithmic magnitude curve rendered on Compose `Canvas` using [`BiquadDesigner.magnitudeAt(...)`](method://com.tigerplayer.utils.BiquadDesigner#magnitudeAt).
- **Preset Quick Selector**: `Flat`, `Bass Boost`, `Vocal Clarity`, `Treble Air`, `Night Drive`, `Studio Reference`, `Custom`.

### Tab 2: Aural Nexus 2D Spatial Canvas (`AuralNexusTab`)
Preserves the innovative 2D acoustic node spatial interface:
- **Interactive Drag Gestures**: Dragging nodes in 2D space seamlessly maps $X$-axis to frequency ($20\text{ Hz}\text{--}20\text{ kHz}$ logarithmic) and $Y$-axis to gain ($-15\text{ dB}\text{--}+15\text{ dB}$).
- **Fluid Visual Response**: Dynamic glow aura nodes matching frequency spectrum colors (Crimson for Sub-bass, Amber for Body, Neon Green for Presence, Electric Cyan for Air).

### Tab 3: Environment Modeling & Hardware Compensation (`AcousticEnvironmentTab`)
Combines acoustic environment acoustics with device hardware profile tuning:
- **Acoustic Environments**:
  - `STUDIO`: Flat, pristine reference mode.
  - `VINYL_WARMTH`: $0.5\text{ Hz}$ Wow LFO pitch modulation, soft polynomial saturation, surface hiss, RIAA low-shelf warmth.
  - `CONCERT_HALL`: Schroeder reverb tank with 4 parallel comb filters, low-pass feedback damping, and Haas early reflections.
- **Hardware Profile Auto-Compensation**:
  - Built-in speaker resonance compensation.
  - Galaxy Buds 360 Audio coordination.
  - USB DAC / Headphone Mid/Side widening.
- **ReplayGain Normalization Controls**: Mode selector (`OFF`, `TRACK`, `ALBUM`, `SMART`), manual preamp offset slider ($-15\text{ dB}$ to $+15\text{ dB}$), and peak clipping protection toggle.

---

## 3. Data Models & ViewModel Architecture (`EqualizerViewModel.kt`)

```kotlin
package com.tigerplayer.ui.equalizer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.tigerplayer.data.local.AcousticEnvironmentMode
import com.tigerplayer.data.local.ReplayGainMode
import com.tigerplayer.engine.AcousticNode
import com.tigerplayer.engine.FilterType

/**
 * Single, unified state representing all Equalizer & DSP settings.
 */
data class EqualizerState(
    val activeTab: EqualizerTab = EqualizerTab.PARAMETRIC_SLIDERS,
    val nodes: List<SpatialNode> = emptyList(),
    val selectedPreset: String = "Flat",
    val frequencyResponsePoints: List<Offset> = emptyList(),
    val environmentMode: AcousticEnvironmentMode = AcousticEnvironmentMode.STUDIO,
    val autoDeviceProfileEnabled: Boolean = true,
    val replayGainMode: ReplayGainMode = ReplayGainMode.SMART,
    val replayGainPreampDb: Float = 0f,
    val replayGainPreventClipping: Boolean = true
)

enum class EqualizerTab {
    PARAMETRIC_SLIDERS,
    AURAL_NEXUS_CANVAS,
    ACOUSTIC_ENVIRONMENT
}
```

---

## 4. Step-by-Step Implementation Roadmap

### Step 1: Create Consolidated `EqualizerViewModel`
- Unify [`NexusViewModel.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/ui/equalizer/NexusViewModel.kt) and [`AcousticEnvironmentViewModel.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/ui/equalizer/AcousticEnvironmentViewModel.kt) into a single, cohesive `@HiltViewModel class EqualizerViewModel`.
- Wire state updates directly to [`AdaptiveDspEngine`](class://com.tigerplayer.engine.AdaptiveDspEngine) and [`SettingsDataStore`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/data/local/SettingsDataStore.kt) with $150\text{ ms}$ debouncing to protect the audio server.

### Step 2: Build Parametric Slider UI (`ParametricEqTab.kt`)
- Implement vertical fader sliders for the 5 key frequency bands.
- Overlay the live Compose `Canvas` frequency response curve.

### Step 3: Refactor `NexusScreen.kt` into `AuralNexusCanvasComponent.kt`
- Extract the 2D node map into a standalone Composable component so it can be hosted inside both the Settings EQ Tab and full-screen views.

### Step 4: Add Equalizer Tab to Settings Navigation (`SettingScreen.kt`)
- Add an **"Equalizer & DSP"** tab inside [`SettingScreen.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/ui/settings/SettingScreen.kt), giving users instant access to EQ faders, Nexus canvas, and environment controls directly from Settings.

---

## Verification Plan
- **Build Gate**: `:app:assembleDebug`
- **Unit Tests**: `EqualizerViewModelTest` verifying preset transitions, dB range clamping, and debounced DSP updates.
