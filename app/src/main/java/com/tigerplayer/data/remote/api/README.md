# TigerPlayer Remote API Sub-compartment (`data/remote/api/`)

This directory houses the **Retrofit API interfaces** defining remote HTTP network contracts for cloud streaming services, metadata resolution, lyrics providers, and weather services.

---

## 1. Plain-English Overview

Think of **Remote API interfaces** as the "phone lines and web callers" of TigerPlayer:
- They define the exact web addresses (URLs), parameters, and HTTP methods (GET, POST) used to communicate with external servers.
- Services accessed include Navidrome (Subsonic API), Spotify Web API, LRCLIB (synced lyrics), Last.fm (artist info), Wikipedia (biographies), AutoEQ database, and YouTube metadata.

---

## 2. Core API Interfaces Breakdown

### Subsonic & Navidrome (`SubsonicApi.kt`, `NavidromeApiService.kt`)
* **Purpose:** Queries Subsonic/Navidrome servers for ping status, music folders, artists, albums, playlists, and track stream URLs.
* **Security:** Employs MD5 salt/token authentication parameters on request queries.

### Spotify (`SpotifyApiService.kt`)
* **Purpose:** Interfaces with Spotify Web API endpoints for user playlists, saved albums, and track metadata using OAuth 2.0 PKCE tokens.

### Lyrics & Metadata (`LrclibApi.kt`, `LastfmApi.kt`, `WikipediaApi.kt`, `ITunesApi.kt`, `KugouApi.kt`)
* **Purpose:** Fetches synchronized LRC lyrics (`LrclibApi`), artist bios (`WikipediaApi`, `LastfmApi`), cover artwork (`ITunesApi`), and alternative lyric sources (`KugouApi`).

### Audio & System APIs (`AutoEqApi.kt`, `YouTubeApi.kt`, `YoutubeApiLayer.kt`, `WeatherApiService.kt`)
* **Purpose:** Downloads headphone AutoEQ frequency response profiles (`AutoEqApi`), resolves YouTube metadata (`YouTubeApi`), and fetches weather info for mood-based music recommendations (`WeatherApiService`).

---

## 3. Network & Safety Rules

1. **Retrofit Contracts:** All methods are `suspend` functions returning typed response objects or Retrofit `Response<T>`.
2. **Timeout & Failure Safety:** Calls execute with configured OkHttpClient timeouts (default 15s) and catch HTTP exceptions gracefully.
3. **No Plaintext Secrets:** Passwords and secrets are never passed in cleartext.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Remote API Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Retrofit** | Type-safe HTTP client library for Android. | Turns Kotlin interface methods into HTTP web requests. | Inability to talk to cloud music servers. |
| **Subsonic API** | Open media streaming protocol supported by Navidrome/Airsonic. | Fetches cloud library tracks and streams audio. | Cloud library fails to load or stream. |
| **OAuth 2.0 PKCE** | Secure token authorization flow without embedded secrets. | Authenticates Spotify account access. | Authorization failure or credential leak. |
