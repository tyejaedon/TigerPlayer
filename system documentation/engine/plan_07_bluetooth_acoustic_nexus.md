# Phase 7 Plan: Acoustic-Bluetooth Nexus Integration & Interactive 3D Card Redesign

## Executive Strategy & Product Vision

Currently, [`BluetoothNexusCard.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/ui/home/BluetoothNexusCard.kt) and [`BluetoothManager.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/utils/BluetoothManager.kt) display generic, static hardware specs (e.g. masked MAC address, static `"A2DP"` string) that provide little value to music listeners. Furthermore, the earbud dock animation relies on static linear interpolation (`lerp`) without audio reactivity.

This plan details the complete redesign to **blend Bluetooth hardware telemetries with real-time acoustic DSP character**, paired with **spring-physics 3D earbud animations and audio-reactive aura glows**.

---

## 1. Acoustic-Bluetooth Data Architecture (`BluetoothManager.kt`)

### A. Enhanced `AcousticBluetoothInfo` Data Model

Replaces generic strings with meaningful audio resolution, active DSP profiles, and connection health:

```kotlin
package com.tigerplayer.utils

import com.tigerplayer.engine.AcousticEnvironmentMode

/**
 * Unified data model blending Bluetooth hardware state with active audio acoustic parameters.
 */
data class AcousticBluetoothInfo(
    val name: String = "Internal Speaker",
    val address: String = "",
    val batteryLevel: Int = -1,             // [0..100%]
    val isConnected: Boolean = false,
    val audioCodec: String = "SBC Standard",  // e.g. "LDAC 990 kbps", "aptX HD", "AAC 256 kbps"
    val sampleRateAndDepth: String = "16-bit / 44.1 kHz", // e.g. "24-bit / 96 kHz High-Res"
    val activeDspProfile: String = "Flat Direct", // Profile applied by AdaptiveDspEngine
    val acousticVenue: AcousticEnvironmentMode = AcousticEnvironmentMode.STUDIO,
    val signalStability: Int = 100,          // Connection health percentage [0..100%]
    val listeningTimeMs: Long = 0L,
    val estimatedBatteryHours: Float = -1f    // Estimated playback hours remaining
)
```

---

## 2. Redesigned UI & Audio-Reactive Animations (`BluetoothNexusCard.kt`)

```
  ┌─────────────────────────────────────────────────────────────┐
  │  [3D Earbud Dock + Audio-Reactive Bass Pulse Aura]           │
  │  Device Name & Connection Health (e.g. "GALAXY BUDS 3 PRO") │
  │  Battery Level (85%) • High-Res Indicator                   │
  └──────────────────────────────┬──────────────────────────────┘
                                 │
  ┌──────────────────────────────┴──────────────────────────────┐
  │  Tile 1: AUDIO RESOLUTION (LDAC 990kbps • 24-bit/96kHz)      │
  │  Tile 2: ACTIVE DSP TUNING (Aural Nexus Air Boost)          │
  │  Tile 3: ACOUSTIC VENUE (Schroeder Concert Hall)            │
  │  Tile 4: SIGNAL STABILITY (98% Pristine • Bluetooth 5.3)    │
  └──────────────────────────────┬──────────────────────────────┘
                                 │
  ┌──────────────────────────────┴──────────────────────────────┐
  │  [Embedded Mini Audio Reactive Spectrum Visualizer]         │
  └─────────────────────────────────────────────────────────────┘
```

### Key UI & Animation Features

1. **Audio-Reactive Hologram Glow Canvas**:
   - The 3D earbud dock background contains pulsing radial gradient aura rings driven by real-time `AudioReactiveFrame.bass` energy.
   - Bass drum impacts trigger a subtle scale bounce and glow intensity flash.

2. **Spring Physics Card Expansion**:
   - Replaces linear transitions with Compose Spring Physics (`spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)`).

3. **Embedded Mini Audio-Reactive Spectrum Bar Array**:
   - Integrates a 6-band real-time audio reactive visualizer inside the card footer, visually demonstrating how the active Bluetooth codec delivers audio energy.

---

## 3. Step-by-Step Implementation Roadmap

### Step 1: Update `BluetoothDeviceManager` (`BluetoothManager.kt`)
- Wire [`BluetoothDeviceManager`](class://com.tigerplayer.utils.BluetoothDeviceManager) to observe [`AdaptiveDspEngine`](class://com.tigerplayer.engine.AdaptiveDspEngine) state.
- Populate active DSP profiles (`"Bluetooth High-Air Boost"`, `"Samsung 360 Coordinated"`, `"Speaker Compensation"`), real-time sample rates, and estimated playback time.

### Step 2: Redesign 3D Earbud Canvas (`BluetoothNexusCard.kt`)
- Update `AnimatedEarbudsDock3D` to receive `AudioReactiveFrame` and animate bass-reactive aura glow rings using `graphicsLayer { scaleX = ... }`.

### Step 3: Replace Hardware Tiles with Acoustic Metric Tiles
- Replace MAC address and "TRANSPORT" tiles with **AUDIO RESOLUTION**, **ACTIVE DSP TUNING**, **ACOUSTIC VENUE**, and **SIGNAL STABILITY**.

---

## Verification Plan
- **Build Gate**: `:app:assembleDebug`
- **Unit Tests**: `BluetoothDeviceManagerTest` verifying codec resolution, battery parsing, and DSP profile mapping.
