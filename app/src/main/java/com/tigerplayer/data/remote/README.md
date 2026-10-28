# Remote Network & API Sub-compartment (`data/remote/`)

This directory contains the remote networking clients, Retrofit REST interfaces, HTTP interceptors, and data transfer models for TigerPlayer's cloud integrations.

---

## 1. Plain-English Overview

Think of the **Remote Network Sub-compartment** as the "diplomats and messengers" of TigerPlayer:
- Whenever the player needs information from the outside world—like streaming songs from your personal Navidrome server, fetching time-synced lyrics, downloading high-resolution album artwork, or syncing with Spotify—this compartment makes the phone calls.
- It translates network responses into clean Kotlin objects that the rest of the app can easily understand.
- It includes safety measures: if you are on an airplane or have a spotty internet connection, it handles network dropouts gracefully without crashing the app.

---

## 2. Sub-compartment Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│ data/remote/ Architecture                                              │
│                                                                        │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ Retrofit REST Interfaces (`api/`)                                  │ │
│ │  ├── SubsonicApi / NavidromeApiService   (Server Music Streaming)  │ │
│ │  ├── LrclibApi                           (Synchronized Lyrics)     │ │
│ │  ├── SpotifyApiService                   (Cloud Playlists/Tracks)  │ │
│ │  ├── LastfmApi                           (Artist Tags & Metadata)  │ │
│ │  ├── WikipediaApi                        (Artist Biographies)      │ │
│ │  ├── ITunesApi                           (Hi-Res Artwork Fallback) │ │
│ │  └── WeatherApiService                   (Contextual Weather)      │ │
│ └──────────────────────────────────┬─────────────────────────────────┘ │
│                                    │                                   │
│ ┌──────────────────────────────────▼─────────────────────────────────┐ │
│ │ Interceptors & Security (`remote/`)                                │ │
│ │  ├── NavidromeArtInterceptor   (Injects Subsonic auth for images)  │ │
│ │  ├── NavidromeUri              (Dynamic request-time URI signer)   │ │
│ │  └── Network Models (`model/`) (Data Transfer Objects - DTOs)      │ │
│ └────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Core Network Services & Security Guardrails

### Subsonic / Navidrome Integration
* **`SubsonicApi` & `NavidromeApiService`**: Implements the Subsonic API standard (version 1.16+ compatible). Handles JSON responses wrapped in `<subsonic-response>` envelopes.
* **Security & Authentication (Issue #44):**
  - Authentication tokens and salt strings **must never** be hardcoded into permanent track URIs saved in the database.
  - Media streaming chunks are intercepted at request time by `ResolvingDataSource` in `AudioPlayerService`, which queries `NavidromeUri` to inject fresh MD5/salt signatures dynamically.
* **`NavidromeArtInterceptor`**: An OkHttp interceptor that securely injects Subsonic credentials into Coil image requests without exposing tokens in the UI.

### Synchronized Lyrics (`LrclibApi`)
* **Endpoint:** `GET https://lrclib.net/api/get`
* **Matching Strategy:** Queries by `track_name`, `artist_name`, `album_name`, and `duration` (in seconds) to avoid incorrect lyrics on live or acoustic versions.
* **Data Model:** Deserializes into `syncedLyrics` (containing `[mm:ss.xx]` timestamp markers) and `plainLyrics`.

### Spotify Web API (`SpotifyApiService`)
* **OAuth Model:** Uses short-lived Bearer access tokens generated via PKCE authentication (`SpotifyAuthManager`).
* **Endpoints:** Fetches user playlists, library saved tracks, and artist catalogs.

---

## 4. Error Handling & Offline Fallback Invariants

1. **Network Independence:** The player must never block local playback or UI responsiveness while waiting for remote API calls. All network requests are non-blocking and execute with timeouts (e.g. 10s connect, 15s read).
2. **Offline Grace:** When offline or when a server is unreachable, network calls return empty or cached fallback results rather than throwing unhandled exceptions to UI consumers.

---

## 5. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Remote Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Retrofit** | A type-safe Android library that turns HTTP APIs into Kotlin interfaces. | Makes all web network requests across the application. | Network requests cannot be dispatched or deserialized. |
| **Interceptor** | A security gate that inspects and adds tokens to web requests before they leave the phone. | Injects authentication into Navidrome artwork requests. | Images fail to load, showing 401 Unauthorized errors. |
| **Subsonic API** | An open standard for personal music streaming servers (Navidrome, Subsonic). | Streams music and syncs playlists from remote servers. | Remote server music cannot be indexed or played. |
| **Salt & MD5 Token** | A random string added to passwords before hashing to prevent hacking. | Secures Navidrome authentication without sending raw passwords. | Passwords sent over unencrypted channels could be intercepted. |
| **DTO (Data Transfer Object)** | A temporary data structure used only to receive JSON from the internet. | Deserializes raw server responses before converting them into domain models. | App crashes if server JSON structure doesn't match expected fields. |
