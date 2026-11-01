# Phase 1 Plan: Remote API Layer Cleanup & Quota Optimization

## Objectives

Clean up the remote API interfaces in `app/src/main/java/com/tigerplayer/data/remote/api`, remove dead code, and transition YouTube searches to a keyless provider to eliminate the $10,000$-unit daily quota restriction.

---

## Work Items & Implementation Tasks

### 1. Remove Dead & Redundant Interfaces
- Delete [`ITunesApi.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/data/remote/api/ITunesApi.kt) (empty interface with 0 methods).
- Delete [`WikipediaApi.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/data/remote/api/WikipediaApi.kt) (empty interface with 0 methods).
- Delete [`SubsonicApi.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/data/remote/api/SubsonicApi.kt) (stub return types; fully consolidated into [`NavidromeApiService.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/data/remote/api/NavidromeApiService.kt)).

### 2. Clean Up Dependency Injection
- Remove `@ITunesRetrofit` and `@WikipediaRetrofit` qualifier annotations and unused provider methods from [`NetworkModule.kt`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/di/NetworkModule.kt).

### 3. Migrate YouTube Search Provider
- Refactor [`YouTubeRepositoryImpl`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/app/src/main/java/com/tigerplayer/data/remote/api/YoutubeApiLayer.kt) away from official YouTube Data API v3 calls (`OfficialYouTubeDataSource`).
- Transition to a keyless Piped / InnerTube REST client wrapper (`https://pipedapi.kavin.rocks/search?q=...`), bypassing the 10,000-unit daily API key limit.

---

## Verification
- Run `:app:assembleDebug` and verify zero unresolved reference errors.
- Execute unit tests in `YouTubeRepositoryTest`.
