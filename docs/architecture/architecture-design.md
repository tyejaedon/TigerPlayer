# TigerPlayer Architectural Design Guide

This document is the authoritative technical specification and architectural reference for the TigerPlayer Android music player. It details subsystem boundaries, data synchronization models, the Media3 audio pipeline, and state flow invariants.

---

## 1. Architectural Philosophy & Layered Structure

TigerPlayer enforces a strict unidirectional, five-layer software architecture:

```
UI (Jetpack Compose)
       │
       ▼
  ViewModel (State Holders & Coordinators)
       │
       ▼
    Engine (Pure Domain Orchestration)
       │
       ▼
  Repository (Single Source of Truth & Caching)
       │
       ▼
Data Sources / DAOs / Remote APIs / Hardware Subsystems
```

### Layer Boundary Rules

1. **UI Layer (`ui/`, `navigation/`)**:
   - Built exclusively with Jetpack Compose.
   - Declarative and stateless; composables accept state objects and hoist events via lambdas.
   - Never interact directly with Engines, Repositories, or Data Sources.
   - Communicates solely with ViewModels.

2. **ViewModel Layer (`ui/player/`, `ui/library/`, `ui/home/`)**:
   - Exposes immutable reactive streams (`StateFlow<T>`, `SharedFlow<T>`).
   - Translates domain flows into presentation-ready state models.
   - Scoped to feature lifecycles (`PlaybackViewModel`, `LibraryViewModel`, `StatsViewModel`).
   - Coordinates application-wide player state via `@Singleton PlaybackStateManager`.
   - Free of Android UI widget imports and non-Android framework domain logic.

3. **Engine Layer (`engine/`)**:
   - Plain `@Inject` domain orchestrators (pure Kotlin where feasible).
   - Contains business rules, math algorithms (DSP, FFT, fluid physics, scrobble qualification), and transport coordination.
   - Orchestrates one or more repositories and low-level service managers.
   - Never imports UI components or Compose runtime elements.

4. **Repository Layer (`data/repository/`)**:
   - Annotated with `@Singleton`; acts as the canonical single source of truth for all domain entities.
   - Manages caching policies, data diffing, in-memory buffering, and reconciliation between disk (Room) and remote sources.
   - All public methods exposing I/O operations are main-safe suspend functions or expose cold/hot Flows configured with `Dispatchers.IO`.

5. **Data Source / API / DAO Layer (`data/local/`, `data/source/`, `data/remote/`)**:
   - Low-level persistence (Room DAOs, DataStore Preferences, EncryptedSharedPreferences).
   - Hardware/OS readers (`LocalAudioDataSource` querying MediaStore, `SafFolderScanner` traversing Storage Access Framework).
   - Network API clients (Retrofit Subsonic/Navidrome interfaces, Spotify App Remote SDK bridge, Last.fm, LRCLIB).

---

### Layered Architecture Diagram

```plantuml
@startuml
!theme plain
skinparam componentStyle uml2
skinparam packageStyle rectangle
skinparam shadowing false
skinparam defaultFontName sans-serif

package "UI Layer (Jetpack Compose)" as UILayer {
  [FullPlayerScreen] as FullPlayer
  [MiniPlayer] as MiniPlayer
  [LibraryScreen] as LibraryScreen
  [HomeScreen] as HomeScreen
  [CoverScreen] as CoverScreen
  [TigerPlayerNavGraph] as NavGraph
}

package "ViewModel Layer" as VMLayer {
  [PlaybackViewModel] as PlaybackVM
  [LibraryViewModel] as LibraryVM
  [StatsViewModel] as StatsVM
  [PlayerViewModel\n(Coordinator Facade)] as PlayerVM
  [PlaybackStateManager\n(@Singleton State Holder)] as StateMgr
}

package "Engine Layer" as EngineLayer {
  [PlaybackEngine] as PlaybackEng
  [LibraryEngine] as LibraryEng
  [StatsEngine] as StatsEng
  [MetadataEngine] as MetadataEng
  [AdaptiveDspEngine] as DspEng
  [WaveformEngine] as WaveformEng
  [FluidRenderEngine] as FluidEng
  [NetworkEngine] as NetEng
}

package "Repository Layer (@Singleton)" as RepoLayer {
  [AudioRepository] as AudioRepo
  [HistoryRepository] as HistoryRepo
  [MediaDataRepository] as MediaDataRepo
  [SpotifyRepository] as SpotifyRepo
  [NavidromeRepository] as NavidromeRepo
  [LyricsRepository] as LyricsRepo
  [MusicFolderRepository] as FolderRepo
}

package "Data Sources & Subsystems" as DataLayer {
  database "Room Database\n(TigerDatabase)" as RoomDB {
    [TigerDao]
    [PlaylistDao]
    [MusicFolderDao]
  }
  [DataStore Preferences] as DataStore
  [LocalAudioDataSource\n(MediaStore)] as MediaStoreSource
  [SafFolderScanner\n(SAF Traversal)] as SafScanner
  [Subsonic REST API] as NavidromeApi
  [Spotify App Remote SDK] as SpotifySdk
  [AudioPlayerService\n(MediaSessionService)] as AudioService
}

' UI to ViewModel
FullPlayer --> PlaybackVM
MiniPlayer --> PlaybackVM
CoverScreen --> PlaybackVM
LibraryScreen --> LibraryVM
HomeScreen --> StatsVM
NavGraph ..> PlayerVM : Injects

PlaybackVM --> StateMgr
LibraryVM --> LibraryEng
StatsVM --> StatsEng
PlayerVM --> StateMgr
PlayerVM --> PlaybackVM
PlayerVM --> LibraryVM
PlayerVM --> StatsVM

' ViewModel to Engine
StateMgr --> PlaybackEng
StateMgr --> MetadataEng
StateMgr --> StatsEng
StateMgr --> WaveformEng
StateMgr --> DspEng

' Engine to Repository & Service
PlaybackEng --> SpotifyRepo
PlaybackEng --> AudioRepo
LibraryEng --> AudioRepo
LibraryEng --> HistoryRepo
LibraryEng --> FolderRepo
StatsEng --> HistoryRepo
MetadataEng --> MediaDataRepo
MetadataEng --> LyricsRepo
NetEng --> NavidromeRepo
NetEng --> AudioRepo

' Repository to Data Sources
AudioRepo --> TigerDao
AudioRepo --> PlaylistDao
AudioRepo --> MediaStoreSource
AudioRepo --> SafScanner
AudioRepo --> NavidromeRepo
HistoryRepo --> TigerDao
SpotifyRepo --> SpotifySdk
NavidromeRepo --> NavidromeApi
FolderRepo --> MusicFolderDao

' Playback to AudioPlayerService
StateMgr ..> AudioService : Controls via MediaController
@enduml
```

---

## 2. Media3 & Audio Playback Pipeline

TigerPlayer builds upon AndroidX Media3 (`MediaSessionService` + `ExoPlayer`), providing foreground audio playback, background survival, hardware offload management, and a real-time DSP/visualizer pipeline.

### Playback Architecture Components

1. **`AudioPlayerService` (`MediaSessionService`)**:
   - Manages the lifecycle of `ExoPlayer` and the underlying `MediaSession`.
   - Maintains the ongoing media playback foreground service notification with `MediaStyle`.
   - Handles audio focus transitions and transient focus loss (`AUDIOFOCUS_LOSS_TRANSIENT`).
   - Registers audio noisy receivers to pause when headphones or Bluetooth disconnect.
   - Manages audio offload transitions (`routeToSystemDecoderDsp`) with smooth volume fades (`VOLUME_RAMP_STEPS`) and hardware sink drain delays (`SINK_DRAIN_DELAY`).

2. **`MediaControllerManager`**:
   - Application-scoped bridge between the UI and `AudioPlayerService`.
   - Connects asynchronously via `SessionToken` to `MediaController`.
   - Serializes track queues into `MediaItem` extras using 18 metadata fields, ensuring queue state restoration without re-querying the library.
   - Drives periodic playback position ticks (250 ms) and background persistence (15 s intervals).

3. **Audio Processor Pipeline (`DefaultAudioSink`)**:
   ExoPlayer passes 32-bit floating point or 16-bit PCM audio frames through a linear chain of custom `AudioProcessor` implementations before delivery to Android HAL:

   ```
   ExoPlayer Audio Track
            │
            ▼
   ┌────────────────────────────────────────┐
   │ AdaptiveDspEngine                      │
   │ - Parametric EQ (SVF Filters)          │
   │ - ReplayGain Scaling & Peak Limiter   │
   │ - Acoustic Environment Simulation      │
   └────────────────────────────────────────┘
            │
            ▼
   ┌────────────────────────────────────────┐
   │ FftProcessor                           │
   │ - Windowing & Spectral FFT Analysis    │
   │ - 6-Band Audio-Reactive Energy Split   │
   └────────────────────────────────────────┘
            │
            ▼
   ┌────────────────────────────────────────┐
   │ WaveformCaptureProcessor               │
   │ - Real-Time Peak & RMS Audio Capture   │
   └────────────────────────────────────────┘
            │
            ▼
   Android AudioTrack / Audio HAL
   ```

4. **Samsung OEM Compatibility Guard**:
   - Samsung audio HALs can suffer buffer instability in float mode under heavy GPU visualizer loads.
   - `AudioPlayerService` detects Samsung devices at runtime (`Build.MANUFACTURER`) and forces 16-bit PCM output while allowing bit-perfect float output on other OEMs.

5. **Audio-Reactive Haptic Engine**:
   - `AudioPlayerService` observes spectral energy from `AdaptiveDspEngine` / `FftProcessor`.
   - Smooths bass and energy flux over time, triggering haptic pulses via Android `Vibrator` / `VibratorManager` when exceeding dynamic thresholds with strict cooldown guards.

---

### Media3 Playback Pipeline Diagram

```plantuml
@startuml
!theme plain
skinparam sequenceMessageAlign center
skinparam shadowing false
skinparam defaultFontName sans-serif

participant "UI (Compose)" as UI
participant "PlaybackViewModel" as VM
participant "PlaybackStateManager" as StateMgr
participant "PlaybackEngine" as PlayEng
participant "MediaControllerManager" as MCM
participant "MediaSession" as Session
participant "AudioPlayerService" as Service
participant "ExoPlayer" as Exo
participant "AudioSink Processors" as DSP
participant "Android AudioTrack / HAL" as HAL

UI -> VM : playTrack(track, queue)
VM -> StateMgr : playTrack(track, queue)
StateMgr -> PlayEng : playTrack(track, queue)

alt Local or Navidrome Track
    PlayEng -> MCM : setPlaylistAndPlay(queue, startIndex)
    MCM -> Session : setMediaItems(mediaItems, startIndex, 0L)
    Session -> Service : onSetMediaItems()
    Service -> Exo : setMediaItems(), prepare(), play()
    Exo -> DSP : queueInput(audioByteBuffer)
    
    group AudioProcessor Chain
        DSP -> DSP : 1. AdaptiveDspEngine (PEQ + ReplayGain + Reverb)
        DSP -> DSP : 2. FftProcessor (Spectral Bands + Centroid)
        DSP -> DSP : 3. WaveformCaptureProcessor (RMS + Peaks)
    end
    
    DSP -> HAL : write(processedBuffer)
    
    loop Every 250ms (Position Tick)
        MCM -> Exo : currentPosition, isPlaying
        MCM -> StateMgr : updatePosition(posMs)
        StateMgr -> VM : emit PlaybackState
        VM -> UI : Recompose Scrubber & Timers
    end

else Spotify Remote Track
    PlayEng -> MCM : pause() [Silence Local]
    PlayEng -> SpotifyRepo : playUri(spotifyTrackUri)
    SpotifyRepo -> SpotifySdk : getPlayerApi().play(uri)
end
@enduml
```

---

## 3. Data Synchronization & Multi-Source Library Architecture

TigerPlayer unifies local storage, network-attached personal servers (Navidrome/Subsonic), and cloud streaming (Spotify) into a consolidated, searchable catalog.

### Ingestion & Convergence Pipeline

1. **Local MediaStore Source (`LocalAudioDataSource`)**:
   - Queries `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` with projected columns.
   - Hoists column index lookups outside cursor iterations for high-throughput scanning.
   - Monitors content changes via `ContentObserver` on the external audio URI with a 1.5-second debounce window to collapse batch filesystem events into a single incremental pass.

2. **Custom Storage Directories (`SafFolderScanner`, `MusicFolderRepository`)**:
   - Scans user-specified storage trees via Android Storage Access Framework (SAF).
   - Recursively traverses directories, parsing file headers and respecting `.nomedia` exclusion flags.
   - Persists allowed and excluded directory configurations in Room (`MusicFolderDao`).

3. **Navidrome Subsonic Integration (`NavidromeRepository`, `NetworkEngine`)**:
   - Connects to Subsonic-compliant servers via Retrofit.
   - Authenticates using dynamic salt + MD5 token generation (`NavidromeUrlSigner`), avoiding caching passwords or tokens in URIs.
   - Remote stream URIs use a clean internal scheme (`navidrome://<id>`); audio requests are dynamically intercepted and authenticated at streaming time via `ResolvingDataSource.Factory`.

4. **Incremental Differential Cache (`LibraryCacheDiffer`)**:
   - Instead of clearing and repopulating Room on library scans, `LibraryCacheDiffer` computes a fast fingerprint delta (`id`, `dateModified`, `size`).
   - Room updates execute atomically within a transaction (`applyCachedTracksDelta`), performing surgical upserts and deletions. This eliminates SQLite thrashing and UI jitter.

---

### Data Synchronization Flow Diagram

```plantuml
@startuml
!theme plain
skinparam componentStyle uml2
skinparam shadowing false
skinparam defaultFontName sans-serif

cloud "Local Filesystem\n(Device Storage)" as LocalFS
cloud "Navidrome Server\n(Subsonic API)" as NavidromeServer
cloud "Spotify Web Service" as SpotifyWeb

package "Data Retrieval Subsystems" {
  [MediaStore ContentResolver] as MediaStore
  [SafFolderScanner\n(SAF Tree Traversal)] as SafScanner
  [NavidromeRepository\n(Retrofit REST)] as NavidromeRepo
  [SpotifyRepository] as SpotifyRepo
}

package "Reconciliation & Engine" {
  [LibraryCacheDiffer\n(Fingerprint Diffing)] as Differ
  [AudioRepository\n(@Singleton Aggregator)] as AudioRepo
  [LibraryEngine\n(Sorting & In-Memory Filters)] as LibraryEngine
}

package "Persistence Layer (Room DB)" {
  database "TigerDatabase" {
    [CachedTrackEntity\n(Local + Remote Tracks)] as TrackTable
    [PlaylistEntity] as PlaylistTable
    [ArtistCacheEntity] as ArtistTable
    [PlaybackHistoryEntity] as HistoryTable
  }
}

LocalFS --> MediaStore : ContentObserver Triggers
LocalFS --> SafScanner : SAF Document Tree
NavidromeServer --> NavidromeRepo : Subsonic REST Sync
SpotifyWeb --> SpotifyRepo : App Remote / Metadata

MediaStore --> AudioRepo : Scanned Tracks
SafScanner --> AudioRepo : SAF Audio Files
NavidromeRepo --> AudioRepo : Remote Tracks

AudioRepo --> Differ : Compare Scanned vs Room Fingerprints
Differ --> TrackTable : applyCachedTracksDelta()\n(Surgical Upserts & Deletions)

TrackTable --> AudioRepo : getCachedLocalTracks() Flow
NavidromeRepo --> AudioRepo : remoteCache Flow

AudioRepo --> LibraryEngine : unifiedTracksFlow\n(Local + Navidrome Combined)
LibraryEngine --> AudioRepo : getAggregatedLibraryFlow()
@enduml
```

---

## 4. State & Event Flow (Unidirectional Data Flow)

TigerPlayer applies Unidirectional Data Flow (UDF) across all screens. State flows down through immutable `StateFlow` streams; user intents flow up via explicit function calls.

### State Management Invariants

1. **Single Source of Truth**:
   - `PlaybackStateManager` owns all playback state (`PlaybackState`).
   - Individual ViewModels (`PlaybackViewModel`, `LibraryViewModel`, `StatsViewModel`) derive specialized, immutable UI state models (`PlaybackUiState`, `LibraryUiState`, `HomeUiState`).

2. **Recomposition Decoupling**:
   - High-frequency position ticks (4 Hz) update `PlaybackState.currentPosition`.
   - UI components observing the library or analytics do not recompose during position ticks because state flows are strictly separated into discrete ViewModels.

3. **Main Safety & Concurrency**:
   - Suspend functions in Repositories and Engines use `withContext(Dispatchers.IO)` or `flowOn(Dispatchers.IO)`.
   - Room queries returning `Flow<T>` run on Room's background executor and emit downstream safely.
   - ViewModels consume flows on `viewModelScope` using `SharingStarted.WhileSubscribed(5_000)`.

---

### State and Event Flow Sequence Diagram

```plantuml
@startuml
!theme plain
skinparam sequenceMessageAlign center
skinparam shadowing false
skinparam defaultFontName sans-serif

actor User
participant "Compose Screen\n(e.g., LibraryScreen)" as Screen
participant "LibraryViewModel" as LibVM
participant "PlaybackStateManager" as StateMgr
participant "PlaybackEngine" as Engine
participant "MediaControllerManager" as MCM
participant "AudioPlayerService" as Service
participant "AudioRepository" as Repo
database "Room Database" as DB

== Queue Mutation & Playback Command ==
User -> Screen : Click song row
Screen -> LibVM : playTrack(selectedTrack, trackList)
LibVM -> StateMgr : playTrack(selectedTrack, trackList)
StateMgr -> Engine : playTrack(selectedTrack, trackList)
Engine -> MCM : setPlaylistAndPlay(trackList, startIndex)
MCM -> Service : MediaSession Command (setMediaItems)
Service -> Service : ExoPlayer.prepare() & play()

== Reactive State Feedback Loop ==
Service --> MCM : Player.Listener (onPlaybackStateChanged, onMediaItemTransition)
MCM --> StateMgr : isPlaying = true, currentMediaId = track.id
StateMgr --> StateMgr : Update _playbackState.update { ... }
StateMgr --> LibVM : Emit new PlaybackState
StateMgr --> Screen : StateFlow updates MiniPlayer & Highlights Playing Row

== Asynchronous Stats Logging ==
Service --> MCM : onMediaItemTransition (Track completed/switched)
MCM -> StateMgr : onTrackEnded()
StateMgr -> Engine : StatsEngine.onTrackFinished(track, listenedDurationMs)
Engine -> Repo : HistoryRepository.addTrackToHistory(...)
Repo -> DB : TigerDao.insertPlaybackHistory(...)
DB --> Repo : Flow<List<PlaybackHistoryEntity>> updates
Repo --> Screen : HomeScreen "Heavy Rotation" & Stats recompose automatically
@enduml
```

---

## 5. Threading, Concurrency, and Error Handling Guardrails

### Threading Architecture

| Layer | Dispatcher | Invariant |
|---|---|---|
| **UI (Compose)** | `Dispatchers.Main` | Immediate UI rendering; zero disk/network access. |
| **ViewModel** | `viewModelScope` (`Dispatchers.Main.immediate`) | State aggregation and UI event dispatching. |
| **Engine** | Configurable (`Dispatchers.Default` for math/DSP/sorting; `Dispatchers.IO` for storage coordination) | CPU-bound transforms offloaded from main thread. |
| **Repository** | `Dispatchers.IO` (`@IoDispatcher`) | Disk reads/writes, Room transactions, network I/O. Must be main-safe. |
| **Data Sources** | `Dispatchers.IO` | Room queries, MediaStore ContentResolver queries, HTTP calls. |

### Concurrency Rules

1. **Cancellation Integrity**:
   - Never swallow `CancellationException` in general `catch (e: Exception)` blocks.
   - Always rethrow `CancellationException` to allow coroutine cooperative cancellation.

2. **Mutex Scoping**:
   - Shared mutable state in `@Singleton` repositories (e.g., scan synchronization in `AudioRepository`) is protected via `kotlinx.coroutines.sync.Mutex`.
   - Never use blocking Java `synchronized` blocks inside coroutines unless guarding pure memory counters without suspend points.

3. **Audio Hardware Offload Transitions**:
   - Offload toggling (`routeToSystemDecoderDsp`) performs an asynchronous volume fade-down, pauses the hardware sink, drains audio buffers (`SINK_DRAIN_DELAY`), switches format, and ramps volume back up.
   - Guarded by `offloadTransitionMutex` to ensure mid-fade cancellations never leave the player in a muted state.


---

## 6. Compartment Documentation & PlantUML Specifications

TigerPlayer enforces the **`audio-pipeline-explainer`** documentation methodology across all core source packages:
- **Plain-English Conceptual Introductions:** Every module begins with an accessible explanation of its purpose and mental model for both newcomers and seasoned developers.
- **Inline Jargon Definitions:** Specialized terms (e.g. *PCM, SVF, HAL, AudioSink, AGSL*) are defined in place upon first appearance.
- **Tabulated Jargon Glossaries:** Each module concludes with a structured terminology table covering plain-English definition, pipeline role, and failure consequences.
- **Scope & Limitations Transparency:** Explicit documentation of thread boundaries, hardware quirk handling (e.g., Samsung HAL float bypass), and active technical debt.

### Dedicated PlantUML Architecture Models

Detailed, standalone PlantUML specifications are maintained in this directory:

| Diagram File | Description & Focus |
|---|---|
| [`system-compartments.puml`](./system-compartments.puml) | **Full System Compartments:** End-to-end component wiring across UI, ViewModel, Engine, Service, Repository, and Persistence layers. |
| [`audio-pipeline-lifecycle.puml`](./audio-pipeline-lifecycle.puml) | **AudioProcessor Pipeline & Offload Lifecycle:** Sequence flow from ExoPlayer decoders through `AdaptiveDspEngine`, `FftProcessor`, `WaveformCaptureProcessor`, to the Audio HAL, including the 4-phase mutex-guarded offload volume ramp. |
| [`data-convergence-architecture.puml`](./data-convergence-architecture.puml) | **Multi-Source Data Ingestion & Dual Transport:** Ingestion flow from Local MediaStore, Navidrome Subsonic, and Spotify Remote into Room DB, surfaced via `AudioRepository` and routed by `PlaybackEngine`. |

### Per-Module Compartment Guides

Each primary codebase folder provides a detailed compartment README:

- **[Domain Engine Compartment](../../app/src/main/java/com/tigerplayer/engine/README.md)**: Math algorithms, Cytotoxic SVF equalizers, Prism 3-stem Linkwitz-Riley crossovers, Schroeder hall reverberation, and look-ahead peak limiting.
- **[Service & Playback Runtime Compartment](../../app/src/main/java/com/tigerplayer/service/README.md)**: MediaSessionService lifecycle, ExoPlayer audio processor chain, volume ramp fading, dynamic Navidrome URL signing, and audio-reactive haptics.
- **[Data & Persistence Compartment](../../app/src/main/java/com/tigerplayer/data/README.md)**: Room database schema, Single Source of Truth repositories, DataStore preferences, Subsonic/Spotify remote APIs, and PKCE security.
- **[UI & Presentation Compartment](../../app/src/main/java/com/tigerplayer/ui/README.md)**: Jetpack Compose screens, MVI/StateFlow architecture, Arcane Glass design system, 60/120 FPS zero-allocation performance rules, and cover screen mini hubs.
