# Phase 3 Plan: On-Device Smart Playlist Vector Engine

## Objectives

Deliver zero-cost, offline, privacy-first smart playlist generation ("AI Playlist Readiness") without external API keys, cloud LLMs, or heavy Machine Learning runtimes.

---

## Work Items & Implementation Tasks

### 1. Vector Extraction (`TrackFeatureVector`)
- Represent each track as an $8$-dimensional normalized vector $\mathbf{V}_{\text{track}} \in [0.0, 1.0]^8$:
  - Energy ($f_{\text{energy}}$), Era ($f_{\text{era}}$), Affinity ($f_{\text{affinity}}$), Skip Penalty ($f_{\text{skip\_penalty}}$), Time-of-Day Match ($f_{\text{tod}}$), Recency ($f_{\text{recency}}$), Artist Cluster ($f_{\text{artist}}$), Acoustic Score ($f_{\text{acoustic}}$).

### 2. On-Device Vector Matching Engine (`OnDeviceSmartPlaylistEngine`)
- Implement **Weighted Cosine Similarity** matching between seed/query vectors and candidate library tracks.
- Support preset smart modes: `MORNING_BOOST`, `LATE_NIGHT_CHILL`, `WORKOUT_ENERGY`, `FORGOTTEN_FAVORITES`, and `SONIC_MATCH`.

### 3. Room Database Persistence
- Wire playlist generation directly to existing [`TigerDao`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/data/local/dao/TigerDao.kt) methods for `playlists` and `playlist_track_cross_ref`.

---

## Verification
- Detailed blueprint document: [`system documentation/engine/ai_playlist_blueprint.md`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/system%20documentation/engine/ai_playlist_blueprint.md).
- Benchmark: $< 10\text{ ms}$ for 2,500 tracks on `Dispatchers.Default`.
- Unit tests: `OnDeviceSmartPlaylistEngineTest`.
