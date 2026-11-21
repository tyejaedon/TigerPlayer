# TigerPlayer Analytics & Stats Sub-compartment (`engine/stats/`)

This directory houses the listening history logging, scrobble threshold validation, and listening telemetry calculations.

---

## 1. Plain-English Overview

Think of the **Stats Sub-compartment** as the "statistician and listening tracker" of TigerPlayer:
- It tracks how long you listen to each song, validating whether a listen counts as a completed play (scrobble).
- It computes top artists, most played tracks, total listening hours, and analytics data displayed on the dashboard and cover screen.

---

## 2. Component Breakdown

### `StatsEngine.kt`
* **Role:** Evaluates playback events against scrobble criteria (e.g. at least 50% played or 240 seconds listened) and logs validated sessions to `HistoryRepository`.
* **Telemetry Data:** Emits `CoverScreenStatsSnapshot` and dashboard analytics for listening history.

---

## 3. Qualification Rules & Debt Warnings

1. **Scrobble Threshold:** Playback is only recorded as a completed scrobble if listened duration passes 50% or 240 seconds.
2. **Issue #42 Safeguard:** Ensure play events are recorded after duration verification, not at track commencement.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Stats Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Scrobble** | Verified record of listening to a track. | Validates listening history in `StatsEngine`. | Skewed or inflated listening statistics. |
