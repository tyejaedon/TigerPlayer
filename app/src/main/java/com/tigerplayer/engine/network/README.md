# TigerPlayer Network Engine Sub-compartment (`engine/network/`)

This directory houses connectivity state observation and network capabilities monitoring.

---

## 1. Plain-English Overview

Think of the **Network Sub-compartment** as the "network monitor and signal observer" of TigerPlayer:
- It checks whether your phone has active Wi-Fi, cellular data, or no internet connection.
- It notifies cloud services (Navidrome, Spotify, LRCLIB lyrics) so TigerPlayer can automatically adapt to offline mode when connectivity is lost.

---

## 2. Component Breakdown

### `NetworkEngine.kt`
* **Role:** Registers `ConnectivityManager.NetworkCallback` to observe real-time network capability changes and emits reactive `StateFlow<Boolean>` (`isOnline`).

---

## 3. Design Guidelines

1. **State Flow Emission:** `isOnline` is exposed as a conflated `StateFlow<Boolean>` for UI and background sync observers.
2. **Resource Cleanup:** Connectivity callbacks unregister cleanly when the engine is disposed.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Network Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **ConnectivityManager** | Android OS service monitoring network states. | Observes Wi-Fi and cellular availability in `NetworkEngine`. | Silent network failures or infinite loading spinners when offline. |
