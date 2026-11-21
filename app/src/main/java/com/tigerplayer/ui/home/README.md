# TigerPlayer Home Screen Sub-compartment (`ui/home/`)

This directory houses the primary home landing hub, listening statistics dashboards, acoustic mood carousels, and Bluetooth nexus cards.

---

## 1. Plain-English Overview

Think of the **Home Sub-compartment** as the "front dashboard and command center" of TigerPlayer:
- It welcomes the user with personal listening insights, recent tracks, curated daylists, acoustic mood carousels, and quick playback controls.
- It provides deep dive screens into listening statistics ("Sonic Footprint", "Expanded Stats", "Discover Weekly") and connected Bluetooth audio device metrics.

---

## 2. Component Breakdown

### `HomeScreen.kt` & `HomeViewModel.kt`
* **Role:** Primary home screen composable displaying acoustic mood carousels, quick links to cloud/library, and listening stats overview. Driven by state in `HomeViewModel`.

### `DashboardViewModel.kt` & `DashboardContainers.kt`
* **Role:** Manages high-level dashboard cards and widgets (top artists, listening time breakdown, favorite genres).

### `SonicFootprintScreen.kt` & `ExpandedStatsScreen.kt` & `StatsViewModel.kt`
* **Role:** Detailed listening analytics visualization, graphing listening hours, top tracks, scrobble trends, and genre distributions.

### `BluetoothNexusCard.kt` & `DaylistDetailScreen.kt` & `DiscoverWeeklyDetailScreen.kt`
* **Role:** Displays active Bluetooth audio connection details (codec, battery level) and curated playlist detail screens.

---

## 3. UI & Performance Guidelines

1. **State Hoisting:** Composables receive stateless UI data holders (`HomeUiState`) and emit user interaction lambdas.
2. **Lazy Column Optimization:** Complex dashboards use `LazyColumn` with explicit `key` parameters for efficient scrolling recomposition.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Home Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Sonic Footprint** | Personal listening statistics visualizer. | Displays user listening trends and top genres in `SonicFootprintScreen`. | Stale or missing listening statistics. |
| **State Hoisting** | Pattern of moving state up to make components reusable and stateless. | Keeps Composables clean and testable. | Duplicate state bugs or recomposition loops. |
