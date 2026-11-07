# TigerPlayer Cloud Sub-compartment (`ui/cloud/`)

This directory houses the user interface for cloud integrations, including Spotify playlists, album grids, and Navidrome cloud library views.

---

## 1. Plain-English Overview

Think of the **Cloud Sub-compartment** as the "cloud window" of TigerPlayer:
- It lets you browse your online music accounts — including Spotify playlists, albums, and Navidrome server music folders — directly alongside your local device music.
- It provides album grids, track list pickers, and authentication status screens.

---

## 2. Component Breakdown

### `CloudScreen.kt` & `CloudViewModel.kt`
* **Role:** Landing screen for cloud services, displaying Spotify connection status, Navidrome server status, and online music navigation tabs.

### `SpotifyPlaylistScreen.kt` & `SpotifyAlbumDetailScreen.kt` & `SpotifyAlbumGrid.kt`
* **Role:** Render Spotify playlists, album covers, track tracks, and detail views using Spotify Web API models.

---

## 3. UI Guidelines

1. **Async Image Loading:** Album artwork is loaded asynchronously via Coil with placeholder and error fallback drawables.
2. **Auth State Handling:** Displays clear login triggers or PKCE error dialogs when cloud access tokens expire.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Cloud Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Coil** | Kotlin-first image loading library for Android. | Asynchronously loads cover art images in `SpotifyAlbumGrid`. | Missing album covers or UI lag during image loading. |
