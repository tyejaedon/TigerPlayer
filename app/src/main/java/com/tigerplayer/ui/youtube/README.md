# TigerPlayer YouTube UI Sub-compartment (`ui/youtube/`)

This directory houses YouTube metadata search, video preview components, and YouTube player surfaces.

---

## 1. Plain-English Overview

Think of the **YouTube Sub-compartment** as the "video search and streaming view" of TigerPlayer:
- It lets you search for music videos and live performances on YouTube.
- It displays search result cards and embeds YouTube player surfaces.

---

## 2. Component Breakdown

### `YouTubeSearchScreen.kt` & `YouTubeSearchViewModel.kt`
* **Role:** Search interface allowing users to enter queries, view video result cards, and trigger playback.

### `YouTubePlayerView.kt`
* **Role:** Web/Android View wrapper embedding the YouTube player interface.

---

## 3. Design Guidelines

1. **Fallback Safety:** Handles network connectivity loss gracefully during video loading.
2. **Lifecycle Management:** Pauses video playback when the screen is navigated away or backgrounded.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in YouTube Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **YouTube API** | Web service for searching YouTube music content. | Drives search queries in `YouTubeSearchViewModel.kt`. | Search fails to return video results. |
