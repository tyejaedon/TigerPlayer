# TigerPlayer Remote DTO Model Sub-compartment (`data/remote/model/`)

This directory contains **Data Transfer Objects (DTOs)** used for parsing JSON network payloads returned by remote APIs.

---

## 1. Plain-English Overview

Think of **Remote Models** as the "shipping packing slips" of TigerPlayer:
- When Spotify or Last.fm sends data back over the internet as JSON text, these DTO data classes map those JSON fields directly into Kotlin objects.
- Once parsed, repositories map these DTO objects into TigerPlayer's clean internal `AudioTrack` and `Artist` domain models.

---

## 2. Core DTO Models Breakdown

### `SpotifyModels.kt`
* **Contains:** `SpotifyPlaylistsResponse`, `SpotifyTrackDto`, `SpotifyAlbumDto`, `SpotifyArtistDto`, `SpotifyPagingObject`.
* **Purpose:** Maps Spotify Web API JSON payloads into typed Kotlin classes annotated with `@SerializedName`.

### `LastfmModels.kt`
* **Contains:** `LastfmArtistInfoResponse`, `LastfmBioDto`, `LastfmImageDto`, `LastfmTagDto`.
* **Purpose:** Maps Last.fm REST API JSON responses for artist biographies and tags.

---

## 3. Parsing Rules & Guidelines

1. **Null Safety:** All DTO fields are declared as nullable (`?`) with default fallback values to prevent app crashes if an external API changes its JSON schema.
2. **Field Mapping:** Uses `@SerializedName` or Moshi `@Json` annotations to match exact API key names.
3. **Decoupling:** DTO models are restricted to the `data/remote/` layer and never leaked directly into UI Composables.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Remote Model Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **DTO (Data Transfer Object)** | A data class matching a raw network payload. | Receives raw JSON fields from Retrofit network calls. | JsonDataException or app crash if payload structure mismatch occurs. |
| **Null Safety** | Making fields optional (`?`) so missing data doesn't crash the app. | Protects against missing fields in external server responses. | NullPointerException when receiving incomplete JSON payloads. |
