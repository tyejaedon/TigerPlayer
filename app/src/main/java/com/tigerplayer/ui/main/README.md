# TigerPlayer Main Shell Sub-compartment (`ui/main/`)

This directory houses the root main screen container shell, navigation host wrapper, mini-player overlay, and bottom navigation bar.

---

## 1. Plain-English Overview

Think of the **Main Sub-compartment** as the "outer shell and house frame" of TigerPlayer:
- It holds the top-level application layout: the bottom navigation bar, main content view host, and persistent mini-player anchored at the bottom of the screen.

---

## 2. Component Breakdown

### `MainScreen.kt`
* **Role:** Root Scaffold composable wrapping `NavHost`, `MiniPlayer`, bottom navigation bar, and cover screen mini-hub mounting logic.

### `MainScreenVisibility.kt` & `MainScreenTestTags.kt`
* **Role:** Manages mini-player and bottom bar visibility rules (hiding them on full-player screens) and defines UI test tags.

---

## 3. UI Guidelines

1. **Scaffold Hoisting:** Coordinates navigation state and mini-player positioning across all feature screens.
2. **Edge-to-Edge:** Respects system window insets (status bar, navigation bar, keyboard IME).

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Main Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Root Scaffold** | Primary Jetpack Compose container holding top/bottom bars. | Implemented in `MainScreen.kt`. | Layout overlap or inconsistent navigation bars. |
