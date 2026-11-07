# TigerPlayer Dependency Injection Compartment (`di/`)

This directory houses the **Hilt / Dagger** Dependency Injection (DI) modules for TigerPlayer. It configures object lifetimes, graph dependencies, singleton scopes, and interface bindings across the application.

---

## 1. Plain-English Overview

Think of the **DI Compartment** as the "wiring system and supply manager" of TigerPlayer:
- Instead of screens or services manually creating their own database connections, network clients, or DSP engines using `new` or `object` singletons, this compartment automatically constructs and provides them when requested (`@Inject`).
- It guarantees that heavy resources (like the SQLite database connection, OkHttpClient, or ExoPlayer instance) are created **once** (`@Singleton`) and shared across the app.
- It enables clean testing: in unit tests, real modules can be swapped for mock modules without changing app code.

---

## 2. Architecture & Injection Graph

```
┌────────────────────────────────────────────────────────────────────────┐
│ Hilt Dependency Injection Graph (`di/`)                                │
│                                                                        │
│ ┌──────────────────────┐  ┌───────────────────┐  ┌───────────────────┐ │
│ │ DatabaseModule       │  │ DataStoreModule   │  │ NetworkModule     │ │
│ │ - TigerDatabase      │  │ - SettingsStore   │  │ - Retrofit APIs   │ │
│ │ - DAOs (TigerDao)    │  │ - PlaybackPrefs   │  │ - OkHttpClient    │ │
│ └──────────┬───────────┘  └─────────┬─────────┘  └─────────┬─────────┘ │
│            │                        │                      │           │
│ ┌──────────▼────────────────────────▼──────────────────────▼─────────┐ │
│ │ Domain & Application Modules                                       │ │
│ │ ┌───────────────────┐  ┌───────────────────┐  ┌──────────────────┐ │ │
│ │ │ AudioModule       │  │ MediaModule       │  │ SpotifyModule    │ │ │
│ │ │ - AudioRepository │  │ - AudioPlayer     │  │ - AuthManager    │ │ │
│ │ └───────────────────┘  └───────────────────┘  └──────────────────┘ │ │
│ └───────────────────────────────────┬────────────────────────────────┘ │
└─────────────────────────────────────┬──────────────────────────────────┘
                                      │
                         ┌────────────┴────────────┐
                         ▼                         ▼
             [Service & Domain Engines]     [UI ViewModels]
```

---

## 3. Core Modules & Component Roles

### `DatabaseModule`
* **Scope:** `@InstallIn(SingletonComponent::class)`
* **Provides:**
  - `TigerDatabase`: The central Room database instance.
  - `TigerDao`, `PlaylistDao`, `AudioDeviceDao`, `MusicFolderDao`: DAO instances extracted from `TigerDatabase`.
* **Role:** Establishes SQLite persistence binding for local tracks, user playlists, and audio device profiles.

### `DataStoreModule`
* **Scope:** `@InstallIn(SingletonComponent::class)`
* **Provides:**
  - `SettingsDataStore`: App preferences (themes, offload routing, haptics).
  - `PlaybackPrefs`: Dedicated preference store for DSP and playback settings.
* **Role:** Supplies key-value configuration stores backed by Jetpack DataStore.

### `NetworkModule`
* **Scope:** `@InstallIn(SingletonComponent::class)`
* **Provides:**
  - `OkHttpClient`: Configured with timeouts, logging, and caching.
  - `Retrofit`: Configured with Gson/Moshi converters.
  - `NavidromeApiService`, `SubsonicApi`: Subsonic media server APIs.
  - `SpotifyApiService`: Spotify REST API endpoints.
  - `LrclibApi`: Timed lyrics API client.
  - `LastfmApi`, `KugouApi`, `ITunesApi`, `WikipediaApi`, `WeatherApiService`.
* **Role:** Orchestrates network connectivity, HTTP client interceptors, and Retrofit service creation.

### `AudioModule`
* **Scope:** `@InstallIn(SingletonComponent::class)`
* **Provides:**
  - `AudioRepository`: Binds `AudioRepositoryImpl` to `AudioRepository` interface.
  - `AudioDeviceRepository`: Binds `AudioDeviceRepositoryImpl`.
* **Role:** Binds concrete repository implementations to domain interfaces.

### `MediaModule`
* **Scope:** `@InstallIn(SingletonComponent::class)`
* **Provides:**
  - `MediaControllerManager`: Bridge between UI ViewModels and `AudioPlayerService`.
* **Role:** Manages cross-process media controller IPC communication.

### `SpotifyAppRemoteModule`
* **Scope:** `@InstallIn(SingletonComponent::class)`
* **Provides:**
  - `SpotifyAppRemoteClient`, `SpotifyAuthManager`.
* **Role:** Manages PKCE authentication and Spotify App Remote SDK binding.

### `YoutubeModule`
* **Scope:** `@InstallIn(SingletonComponent::class)`
* **Provides:**
  - `YouTubeApi`, `YoutubeApiLayer`.
* **Role:** Supplies YouTube metadata and search integration.

---

## 4. Design Guidelines & Rules

1. **Singleton Binding Invariants:** All modules supplying database handles, repositories, or API clients must be annotated with `@Singleton` to prevent duplicated memory allocations.
2. **Interface Hoisting:** Always bind interfaces rather than concrete classes where possible (e.g. `@Binds abstract fun bindAudioRepository(...)`).
3. **No Direct Instantiation:** Never use `getInstance()` patterns or manual constructors in ViewModels or Services; inject dependencies via `@Inject constructor(...)`.

---

## 5. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in DI Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Hilt** | Google's official dependency injection framework for Android built on Dagger. | Automates creation and injection of objects across the app. | Compilation errors or `NullPointerException` during runtime initialization. |
| **SingletonComponent** | Hilt component scope whose lifecycle matches the entire Application runtime. | Keeps database, network, and repositories alive for the whole app lifetime. | Objects destroyed prematurely, causing state loss or multiple DB connection leaks. |
| **`@Provides`** | Annotation telling Hilt how to construct an instance of a third-party class. | Used in `NetworkModule` and `DatabaseModule` to create Retrofit and Room instances. | Hilt does not know how to build the required object, causing a build failure. |
| **`@Binds`** | Efficient annotation binding an interface to its concrete implementation. | Maps `AudioRepository` interface to `AudioRepositoryImpl`. | Reduces generated code size compared to `@Provides`. |
