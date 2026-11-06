# TigerPlayer Audio Devices UI Sub-compartment (`ui/devices/`)

This directory houses the user interface for audio output device management, Bluetooth profiles, AutoEQ headphone database search, and custom device preset configuration.

---

## 1. Plain-English Overview

Think of the **Devices Sub-compartment** as the "headphone and speaker tuning station" of TigerPlayer:
- It detects when you connect Bluetooth headphones, USB DACs, or wired headphones, showing battery level and connection codec details.
- It lets you search the AutoEQ database (containing 2,500+ headphone models) to automatically apply frequency correction curves tailored to your exact headphone model.

---

## 2. Component Breakdown

### `AudioDevicesHubSheet.kt` & `AudioDevicesViewModel.kt`
* **Role:** Bottom sheet modal displaying connected audio output devices, active EQ profile bindings, and codec status (LDAC, aptX HD, AAC, SBC).

### `AutoEqHeadphoneSelectorDialog.kt`
* **Role:** Search dialog querying the AutoEQ headphone profile database, allowing users to apply matched compensation curves with one tap.

### `CreateWiredProfileDialog.kt`
* **Role:** Dialog allowing creation of custom EQ profiles for wired headphones and AUX audio connections.

---

## 3. UI Guidelines

1. **Auto-Detection:** Automatically highlights active device routes when Bluetooth connections change.
2. **Preset Linking:** Saves per-device EQ preferences in SQLite (`AudioDeviceDao`) so settings apply automatically upon device reconnection.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Devices Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **AutoEQ** | Open project providing frequency correction curves for 2,500+ headphones. | Applied via `AutoEqHeadphoneSelectorDialog.kt` for neutral headphone response. | Headphone audio remains uncalibrated. |
| **Codec** | Digital format for encoding wireless Bluetooth audio (e.g. LDAC, AAC). | Displayed in `AudioDevicesHubSheet.kt` to inform user of Bluetooth stream quality. | User unaware of low-quality Bluetooth connection. |
