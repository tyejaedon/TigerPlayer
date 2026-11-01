# Navigation Compartment (`navigation/`)

This directory houses the application's routing, screen transitions, backstack management, and deep-link dispatching built with **Jetpack Navigation Compose**.

---

## 1. Plain-English Overview

Think of the **Navigation Compartment** as the "transit system and roadmap" of TigerPlayer:
- Whenever you tap a song row to open the full-screen player, click an artist to see their albums, or navigate into settings, this module switches screens smoothly with sleek animations.
- It remembers where you came from: pressing the phone's back button steps back through your history logically without accidentally exiting the app.
- It anchors the persistent bottom mini-player so that music playback continues uninterrupted while you move through different menus.

---

## 2. Sub-compartment Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│ navigation/ Architecture                                               │
│                                                                        │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ Screen.kt (Type-Safe Route Registry)                               │ │
│ │  ├── Screen.Home              ├── Screen.Library                   │ │
│ │  ├── Screen.SonicPrism        ├── Screen.AlbumDetail(albumId)      │ │
│ │  ├── Screen.ArtistDetail      ├── Screen.Settings                  │ │
│ │  └── Screen.Queue             └── Screen.YouTubeSearch             │ │
│ └──────────────────────────────────┬─────────────────────────────────┘ │
│                                    │ Route Definitions                 │
│ ┌──────────────────────────────────▼─────────────────────────────────┐ │
│ │ NavGraph.kt (Central Routing Host)                                 │ │
│ │  ├── NavHost & NavController configuration                         │ │
│ │  ├── Animated Transitions (slideInHorizontally, fadeIn, fadeOut)   │ │
│ │  ├── Persistent MiniPlayer Scaffold Binding                        │ │
│ │  └── MainNavigationPreset.kt (Bottom bar tab customization)        │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Core Components & Route Conventions

### `Screen` Sealed Class
* **Purpose:** Sealed hierarchy defining all navigation destinations.
* **Argument Passing:** Defines parameterized routes (e.g. `Screen.AlbumDetail.createRoute(albumId)`) with URL-safe argument encoding to prevent crashes on titles containing special characters (`&`, `/`, `?`).

### `NavGraph.kt`
* **Root Host:** Coordinates the primary `NavHost` backed by `rememberNavController()`.
* **Animated Content Transitions:** Implements smooth directional transitions:
  - Entering detail screens: `slideInHorizontally(initialOffsetX = { it }) + fadeIn()`.
  - Exiting detail screens: `slideOutHorizontally(targetOffsetX = { it }) + fadeOut()`.
* **Scaffold Coordination:** Hosts the persistent `MiniPlayer` above the bottom navigation bar, dynamically hiding the bar when entering full-screen visualizer modes or deep detail views.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Navigation Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **NavHost** | The container in Compose that swaps screens when you move between tabs. | Manages active screen composables in `NavGraph.kt`. | Screen fails to change when clicking navigation tabs. |
| **Backstack** | The history list of screens you have visited in the app. | Allows the phone's back gesture to return to previous screens. | Pressing back unexpectedly closes the app instead of returning. |
| **Route** | A unique text address identifying a specific screen (e.g. `album/{albumId}`). | Directs the navigator to the correct screen with parameters. | Broken navigation or 404 screen errors if route strings mismatch. |
| **Deep Link** | A web or system link that opens the app straight to a specific song or screen. | Allows external widgets or notifications to jump directly into the player. | App opens to generic home screen instead of the requested song. |
