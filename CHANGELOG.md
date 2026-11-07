# Changelog

All notable changes to TigerPlayer are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versioning follows the
`versionName` in `app/build.gradle.kts` (currently pre-1.0 internal versioning; see issue #45 for the
`versionCode`/package-name cleanup tracked ahead of a real store release).

For prose-style release summaries at major milestones, see `docs/Release-Notes-*.md`. This file is
the terse, per-change log — add an entry here in the same PR as the change, under `[Unreleased]`.

## [Unreleased]

### Added
- **Main Player In-Place Immersive Vortex Mode, Drag-to-Collapse & Transition Polish**: replaced the legacy graphic EQ header button in `FullPlayerScreen` with an in-place Immersive Fluid Visualizer toggle (`Icons.Rounded.Waves`), animating blur radius, scrim, and controls to 0 alpha while maintaining a tap-anywhere dismiss layer, floating hint pill, and back-press restore handler; added intuitive drag-down-to-collapse gesture on the player header; synchronized player sheet dismiss animation with background library scale using matching spring physics (`stiffness = 250f`, `dampingRatio = 0.85f`); replaced zoom/inflation scaling during artwork/lyrics/queue switches with directional horizontal slides; eliminated 300ms tap latency on `MiniPlayer` by scoping double-tap to like to the artwork thumbnail. (closes #400)
- **Smooth Anticipatory Lyrics Viewport & Cyclic Carousel Motion**: overhauled `PlayerLyrics` and `CoverScreenLyricsView` with `LyricsViewportCoordinator` to replace discrete, jerky snap scrolling with an anticipatory lead-in window (~750ms) and continuous sub-frame position extrapolation (`withFrameMillis`), eliminating the 250ms polling quantization lag; implemented dynamic multi-line item height caching to ensure exact vertical centering; calmed active line scale springs with non-bouncy damping (`Spring.DampingRatioNoBouncy`); introduced 3D cylindrical cyclic carousel projection (`rotationX`, perspective camera depth, and dynamic alpha/color transitions) for rolling lyrics movement; added unit test suite in `LyricsViewportCoordinatorTest`. (closes #393)
- **Application Storage Breakdown & Cache Management**: restored and enhanced the storage management matrix in `SettingScreen.kt` featuring real-time calculation of overall application storage and temporary cache footprint; added a sleek segmented Arcane Glass breakdown progress bar displaying proportions for album/artist artwork, synchronized lyrics, artist biographies, waveform peak analysis, and core database/DataStore records; provided targeted individual "Clear" actions per purgeable subsystem alongside a master "Purge All Temporary Archives" action with confirmation safety dialogs ensuring user playlists and listening history are never deleted. (closes #391)
- **Restored Curved Waveform Aesthetic & Glassmorphic Motion**: restored the fluid visual aesthetic, sweeping continuous sinusoidal curvature, and glassmorphic 2.5D specular glow to `CoverMicroWaveform` and `FieryWavySeeker`; configured unified 2.0 wave cycles and 1.0x fundamental frequency across tiers; removed artificial edge-pinching boundary tapering and discrete sample-stepped phase kinks; restored calm 820 ms hypnotic phase drift; reinstated static, unblurred stroke widths with crisp specular white cores; tightened vertical energy breathing dynamics; updated `CoverMicroWaveformTest` with natural curvature assertions. (closes #385)

### Refactored
- **Cover Screen Artist Detail Redesign & Declutter**: removed `CoverScreenArtistHeader` top bar to declutter compact cover screens and maximize vertical content area while retaining horizontal drag swipe gestures; redesigned `CoverScreenArtistHero` into a centered frosted glass card without redundant nested artwork, highlighting artist details and centered Play All / Shuffle buttons with improved spacing and breathing room. (closes #388)

### Fixed
- **Cover Screen Ambient Gesture Conflict Resolution & Child Viewport Isolation**: resolved collisions between root `coverScreenGestures` and interactive child surfaces by adding `controlsTouchBounds` and `mediaCycleTouchBounds` into `shouldIgnoreCoverScreenGesture`, eliminating accidental track skipping during lyrics scrolling and double-tap audio stutter; enabled symmetric 12% edge exclusion zones across all four display borders to preserve Android system shade pull-down; enforced pointer event consumption checks (`change.isConsumed`) to prevent gesture bleed-through; shielded root gestures during active modal overlays with back-stack sequential dismissal on downward swipe; added unit test coverage in `CoverScreenGesturesTest`. (closes #396)
- **Artist Detail Screen Crash on Glance LocalContext Import**: replaced incorrect `androidx.glance.LocalContext` import with standard Jetpack Compose `androidx.compose.ui.platform.LocalContext`, preventing `IllegalStateException: No default context` when navigating to `ArtistDetailsScreen`. (closes #379)
- **Fluid Renderer Render Loop Allocation Elimination & Idle Throttling**: refactored `applySplat` in `FluidRenderEngine.kt` to accept scalar RGB and force components directly, eliminating 3–7 per-frame heap allocations (`floatArrayOf(...)`) inside `injectForces()`; pre-allocated static fallback palette tokens (`DEFAULT_C1`, `DEFAULT_C2`, `DEFAULT_C3`) in `FluidRenderer.Companion` to prevent render-loop GC churn on missing swatches; introduced progressive idle energy decay gating that smoothly scales down ambient swirl power during prolonged silence to save battery and reduce mobile GPU thermals; added `FluidRendererTest` covering sensitivity clamping, palette mode selection, and safe teardown. (closes #394)
- **Cover Screen Album Detail Background & Hero Art Parity**: rectified `CoverScreenAlbumsView` background so the album artwork fills the entire screen with an ambient blurred layer and dark overlay matching `CoverScreenPlaylistDetailView`; added the dynamic 3D floating hero artwork card inside the scrollable list; removed blur (`blurRadius = 0f`) on the floating hero artwork in both `CoverScreenAlbumsView` and `CoverScreenPlaylistDetailView`. (closes #386)
- **Cover Screen Nexus Card Overlays & Navigation Parity**: mounted modal dialog overlays in `CoverScreenHomeView` for `CoverSonicFootprintCard` (`SonicFootprintScreen`), `CoverSpatialNexusCard` (`CoverScreenNexusView`), and `CoverAcousticSpaceCard` (`AcousticSpaceView`); added navigation callbacks (`onOpenFootprint`, `onOpenSpatialNexus`, `onOpenAcousticSpace`); centralized test tags in `CoverScreenTestTags` and added test coverage in `CoverScreenHomeViewTest`. (closes #383)
- **MetadataEngine Pre-Seed Timeout on Missing Artwork**: eliminated 5000 ms timeout stalls and repetitive `Pre-seed failed: Timed out waiting for 5000 ms` warnings in `MetadataEngine.preSeedArtistCache()` by taking the first emission from `mediaDataRepository.getArtistDetails(name)` instead of filtering on non-null `imageUrl` against an infinite Room flow; added unit test coverage in `MetadataEngineTest`. (closes #381)

## [2.1.7] - 2026-11-06

### Added
- **Constellation Engine Motion Physics & Cover Screen Parity**: re-implemented dynamic orbital motion physics math in `ConstellationPhysics` (elliptical orbits with eccentricity, phase offsets, rotational speed variations, audio energy pulse modulation, and parent-child relative orbital positions); reeled in planet (artist node) orbital distances in `OrbitalLayoutEngine` by ~16x for a cohesive, dense galaxy view; updated main `ConstellationScreen` default camera zoom to `0.45f`; brought live 60 FPS motion physics rendering, interactive touch selection, and artist reading cards to `CoverScreenConstellationView`; added unit tests in `OrbitalLayoutEngineTest`. (closes #369)
- **Cover Screen Gesture Contracts, Like Overlay & Lyrics Sizing Continuity**: fixed double-tap like animation over album artwork in `CoverScreenMediaCycleViewport` with clean job cancellation (`heartAnimJob`) and test tag assertions (`CoverScreenTestTags.LIKE_HEART_OVERLAY`); matched lyrics container dimensions to album artwork size (`targetArtHeight`), eliminating viewport height jumps and layout shifts between swipe pages; enforced recurring Fluid Vortex long-press/press-and-hold toggle behavior on album artwork and media cycle viewport; added test coverage in `CoverScreenVortexVisualizerTest` verifying deterministic recurring toggle state transitions across consecutive cycles. (closes #362)
- **Full-Screen Immersive Visualizer Screen**: extracted `FluidRenderEngine` into a dedicated full-screen destination (`ImmersiveVisualizerScreen`) hosting interactive GPU fluid simulation and spatial audio nodes; added an easy entry-point launcher button on `FullPlayerScreen` header; implemented lifecycle-aware `DisposableEffect` engine switching to cleanly restore Parametric EQ audio processing upon exit. (closes #357)
- **Proactive AutoEq Smart Card & Acoustic Mood Carousel**: added a proactive AutoEq headphone detection smart card to `HomeScreen` for 1-tap profile calibration; extracted Acoustic Space spatial audio environments out of EQ tab into a dedicated `AcousticMoodCarousel` on `HomeScreen` with global `HomeViewModel` state plumbing. (closes #355, closes #356)
- **Harmonized Waveform Amplitudes & Data-Driven Motion**: harmonized 3-tier wave ratios (1x, 2x, 3x) across `FieryWavySeeker` and `CoverMicroWaveform` to reduce amplitude variance factor from 12x down to 3x; integrated real-time telemetry phase warping and energy modulation from `DspEngine` and `WaveformEngine`; maintained frozen phase (`0f`) with full data profile fidelity when paused (`shouldAnimate = false`) for seamless play/paused parity. (closes #360)

### Refactored
- **Domain Engine Compartmentalization & Architecture READMEs**: reorganized the monolithic `engine` package and its unit tests into clean domain subfolders (`dsp/`, `graphics/`, `playback/`, `library/`, `stats/`, `network/`); authored and updated comprehensive architectural `README.md` documentation guides across all `com.tigerplayer` package directories. (closes #367)
- **Studio Parametric EQ & Spectrum Analyzer**: refined `EqualizerHubView` into a pure studio PEQ tool following externalization of spatial nexus and acoustic mood panels; expanded frequency curve height from 180dp to 240dp; integrated a real-time 6-band spectrum analyzer driven by `AudioReactiveFrame`. (closes #353)
- **Decoupled DSP Navigation & Signal Chain Pill**: decoupled state-mutating side effects from `EqualizerHubViewModel.selectStudioTab()` so EQ tabs remain strictly navigational; added explicit `EngineSourceToggle` components and a persistent `DspSignalChainPill` badge visualizing active DSP pipeline blocks. (closes #354)

### Fixed
- **Detail View AsyncImage Optimization, Dynamic Scroll Transitions, Header Shade Removal & Cover Screen Alignment**: optimized `WetPaintingFadeImage` and `AsyncImage` background rendering with hardware bitmap caching and single-pass blur layers; fixed parallax scroll calculations (`offset`) in `PlaylistDetailScreen`, `CoverScreenPlaylistDetailView`, and `CoverScreenAlbumsView` to eliminate empty black background gaps; added dynamic 3D collapse, scale, and fade out scroll transitions for cover artwork headers; removed dark background shading (`Color.Black.copy(...)` overlays and top bar container colors) behind album and playlist titles; restored 3D floating cover artwork to `CoverScreenPlaylistDetailView`; aligned cover screen album and playlist detail views with main app `AlbumDetailScreen` design. (closes #352)
- **Lyrics Color Contrast & High-Precision Synchronization**: differentiated active lyric line color (`TigerNeonOrange`) from inactive lines with balanced opacity for enhanced readability; fixed active index tracking for timestamped lyrics reacting cleanly to playback position changes and handling pre-roll graceful defaults. (closes #350)
## [2.1.6] - 2026-11-05

### Added
- **Monotonic versionCode calculation & beta downgrade protection**: standardized `app/build.gradle.kts` and `.github/workflows/release.yml` to compute monotonic version codes dynamically using the SemVer multiplier formula with a historical floor of `2020020`, ensuring `v2.1.6` cleanly supersedes all preceding beta builds (`v2.2.0-beta.1`–`v2.2.0-beta.19`, peak versionCode `2020019`) and prevents Android OS `[INSTALL_FAILED_VERSION_DOWNGRADE]` errors during release installation. (closes #348)
- **Release Hardening & Stress Testing Suite**: implemented high-intensity algorithmic stress testing and fuzzing (`ReleaseHardeningStressTest`) verifying mathematical stability across 32-band parametric biquad configurations, 5,000-line chronological LRC parsing, standard RFC 8216 / extended M3U import under malformed inputs, strict zero-shift baseline waveform anchoring, and race-free concurrent audio device routing. (closes #343)
- **Cover Screen Cover Art Prominence & Press-and-Hold Immersive Interaction**: enlarged album artwork in `CoverScreenMediaCycleViewport` / `TrackInfoText` to prominent viewport proportions adaptively scaling to cover display dimensions (~165dp on compact displays, ~180dp on standard flip displays, and ~200dp on spacious screens); replaced double-tap immersive toggle with an intuitive press-and-hold (long-press) interaction on the root `CoverScreenMiniHub` container with haptic feedback; implemented a smooth 350ms crossfade transition for media controls, track info, and waveforms; introduced a subtle, non-obscuring bottom hint chip (`coverscreen_immersive_hint`) during immersive visualizer mode instructing the user to hold anywhere to restore controls; updated TalkBack semantics and custom actions across all 5 supported locales. (closes #335)
- **Cover Screen Settings Parity & Layout Optimization**: brought `CoverScreenSettingsView` to full functional parity with `SettingScreen` for compact displays; added toggles for online artwork lookup (`fetchOnlineArtwork`) and background lyrics auto-downloading (`autoDownloadLyrics`); added Studio Equalizer & AutoEQ shortcut to launch the Equalizer Nexus; integrated custom Music Folders management with SAF directory pickers (include/exclude actions, custom folder list, and deletion); added JSON Backup & Restore section with export, strategy selection (Add to Existing / Replace All), and import; added real-time haptics telemetry debugging panel for debug builds (`BuildConfig.DEBUG`); added safety confirmation dialogs prior to logging out of Spotify or Navidrome accounts; ensured all interactive controls comply with >= 48dp touch targets and compact screen legibility. (closes #336)
- **Offline AutoEq preset JSON assets**: bundled complete precomputed parametric and graphic AutoEq measurement profiles (`autoeq_parametric_presets.json`, `autoeq_presets.json`, `autoeq_graphic_presets.json`) directly into application assets (`app/src/main/assets/autoeq/`), sanitized with clean UTF-8 encoding (no BOM) to prevent parser malformed JSON exceptions; updated `.gitignore` to track production preset assets while ignoring raw intermediate text files; enhanced `AutoEqRepositoryImpl` with robust asset resolution supporting both `autoeq_graphic_presets.json` and fallback `autoeq_presets.json`. (closes #345)
- **Cover Screen Equalizer & AutoEq Hub with Responsive Studio Parametric Console**: developed an industry-standard Equalizer & AutoEq Hub (`EqualizerHubView`, `EqualizerHubViewModel`, `CoverScreenEqHubCard`) hosted directly on `CoverScreenHomeView` and accessible across the main application (`Screen.Equalizer`, `HomeScreen` Nexus section, `SettingScreen` Neural DSP, and `CoverScreenNexusView`); features 10-band ISO octave center frequencies (31 Hz - 16 kHz), 5-band compact mode, fine-tuning steppers (+/- 0.5 dB) and touch-friendly faders, draggable real-time frequency response curve (`BiquadDesigner.magnitudeAt`), composite peak headroom calculation to prevent digital overs (`BiquadDesigner.calculateCompositePeakGainDb`), master bypass toggle, 15 curated industry presets (Flat, Bass Boost, Vocal, Treble, Electronic, Rock, Jazz, etc.), direct AutoEq headphone database search (3000+ headphone models) with one-click profile application, and Peace / ParametricEQ text importing. (closes #281)
- **Cover Screen Curved Waveform & Playback Layout**: configured `CoverMicroWaveform` so that waves remain naturally curved across their entire horizontal span without boundary edge windowing or flattening; harmonized wave amplitude progression across tiers (Wave 2 at 2x Wave 1, Wave 3 at 1.5x Wave 2); introduced 2.5D render depth projection with zero-allocation canvas shadow offsets, multi-layer glitter glow and specular core highlights on Waves 1 & 3, and optimal glassmorphic fill transparency; clustered cover playback controls closer to the album art/lyrics section via `Arrangement.Center` in `CoverScreenMiniHub` for optimal lyrics rendering clearance. (#322)
- **Cover Screen Playlist Management**: enabled comprehensive playlist management directly on the Cover Screen; added a "New Playlist" button and creation dialog in `LocalPlaylistPicker` (both empty and carousel states); introduced dedicated `CoverScreenPlaylistDetailView` supporting playlist renaming, playlist deletion with confirmation (protecting singleton Liked Songs from deletion), drag-and-drop song reordering with move up/down controls and order persistence via `PlayerViewModel.savePlaylistOrder`, track removal, Play All, Shuffle Play, and custom cover photo updates. (#320)
- **Cover Screen M3U/M3U8 playlist import**: integrated M3U/M3U8 playlist importing directly into the Cover Screen Local Playlists picker (`CoverScreenPlaylistPicker` and `CoverScreenLibraryMenu`); supported both empty-state and filled-carousel import actions with a SAF system document picker and non-intrusive status notification overlay. (#318)
- **Standard-conforming M3U/M3U8 parsing**: upgraded `M3uParser` to strictly follow industry standards (RFC 8216 / extended M3U) by adding `#PLAYLIST:<name>` header parsing, supporting arbitrary `#EXTINF` inline attributes (e.g. `tvg-id`, `group-title`), stripping optional wrapping quotes around paths/URIs, normalizing Windows/Unix/Mac line endings (`\r\n`, `\n`, `\r`), and providing transparent ISO-8859-1 charset fallback on invalid UTF-8 sequences. (#318)
- **Save Daylist & Discover Weekly as Standalone Playlists**: added an "Add as Playlist" action button in both `DaylistDetailScreen` and `DiscoverWeeklyDetailScreen` allowing users to snapshot their dynamically generated curation feeds as standalone, unique playlists; presents a customizable confirmation dialog pre-filled with the default contextual descriptor and date (e.g., `Daylist - Morning Wave (Nov 1, 2026)` or `Discover Weekly (Nov 1, 2026)`) with automatic name deduplication on repeated saves. (#316)
- **README & v2.2 Beta showcase**: updated project README with prominent v2.2 Beta candidate release details (`v2.2.0-beta.13`), direct download links for Full and FOSS signed APKs, and highlighted feature showcases for synchronized lyrics, AutoEq device integration, harmonized waveform seeker, true-peak limiting, and cover screen enhancements. (#310)
- **Harmonized waveform seeker & cinematic background fluid visualizer**: ported the 3-tier harmonic wave hierarchy and unconfined apex amplitude styling from `CoverMicroWaveform` into the main player seekbar (`FieryWavySeeker`), with dynamic neon palette extraction (`DominantColorExtractor`), zero-allocation path reuse across 60/120 FPS render loops, continuous non-destructive drag seeking, and tabular monospace timestamps; detached card-level overlays from the album art card in `FullPlayerScreen` so it cleanly displays album art; made card taps toggle between Background Fluid Visualizer mode (`FluidVortexView` + `fluidRenderer`) and clean Artist Image mode with a smooth 400ms crossfade; implemented lifecycle-aware and modal-aware GPU rendering suspension (`isRenderingActive`) when obscured or backgrounded. (#306)
- **M3U / M3U8 playlist import and export**: added lossless M3U and M3U8 import/export engine via Storage Access Framework (SAF); supports `#EXTM3U` and `#EXTINF` metadata directives, UTF-8 character encoding (with BOM handling and non-ASCII character preservation), and Windows/Unix directory separators; resolves both absolute filesystem paths and relative paths against the local audio library; provides detailed import reports for matched and unmatched entries; adds export options for relative and absolute paths while preserving explicit track order; adds UI entry points in the Library screen playlists tab and Playlist Details screen. (#58)
- **Audio Devices module, AutoEq querying, preset persistence & UI hub**: added unified Audio Devices data models and Room database persistence (schema v15 with migration and tests) for tracking Phone Speaker, Wired Headphones, and Bluetooth endpoints; streamlined Bluetooth card with listening time, codec badge, and AutoEq status; added Audio Devices Hub bottom sheet with device history, wired profile creation, and AutoEq headphone model search; integrated AutoEq headphone measurement database indexing with offline fallback and parametric curve parsing; added per-device preset persistence; integrated audio devices and EQ presets into backup manifest export/restore. (#300)
- **Synchronized lyrics engine & interactive display**: introduced typed `Lyrics` domain model (`Synced`, `Plain`, `None`), background `LrcParser` with multi-timestamp tag support, comma decimal parsing, and chronologically sorted lines; added interactive tap-a-line-to-seek, smooth centered auto-scrolling with pre-song intro guard, manual offset tuning (+/- 0.5s), local companion `.lrc` sidecar file support, and embedded ID3 `USLT`/`SYLT` and FLAC Vorbis comments lyrics extraction taking precedence over remote lookups; added secondary free lyrics provider (`KugouApi` & `LrclibApi` search fallback), silent background lyrics crawler obeying rate-limiting (1 request/sec) with negative cache suppression, and a Settings toggle for auto-downloading lyrics. (#57)
- **Cover screen 3D playlist wheel carousel & photo editor**: redesigned cover screen playlist browsing (`CoverScreenLibraryMenu.kt`) with a horizontal 3D cylindrical wheel carousel styled after `CarouselComponents.kt`, a fast-scroll slider on the side, inline photo editing directly on each playlist card, and alphabetical playlist sorting with Liked Songs pinned first. (#298)
- **Cover screen double-tap to like**: double-tapping album artwork in the cover screen viewport (`CoverScreenMiniHub` / `CoverScreenTrackInfo`) immediately likes the active song and animates a popping neon heart. (#298)
- **Singleton Liked Songs playlist convergence**: consolidated all Liked Songs references to a dedicated singleton playlist (`playlistId = -1L`) in `PlaylistEntity` and `PlaylistDao`; automatically migrates and deduplicates tracks from legacy "Liked Songs" playlists on database queries or backup restore while preventing duplicate creations. (#298)
- **Graphics & Rendering Engine framework**: added `graphics-rendering-engine` workspace skill, `graphics-rendering-specialist` subagent, and engineering reference manuals covering OpenGL ES 3.0/3.2, Vulkan compute migration, and mobile GPU microarchitectures (Qualcomm Adreno GMEM, ARM Mali TBDR, PowerVR). (#293)
- **Graphics architecture & guardrails**: updated `AGENTS.md` with strict zero-allocation render loop invariants, mandatory shader compile/link assertions, and 16-bit half-float texture bandwidth optimizations. (#293)
- **Sandbox to Beta Promotion Pipeline**: Staging and promotion workflow (`promote-to-beta.yml`) linking `sandbox/experimentation` to the `beta` branch. (#283)
- **Beta Production Release Pipeline**: Automated build, sign (v2 scheme with release keystore), audit, and export of FOSS/Full release APKs and AAB as GitHub Pre-Releases (`beta-release.yml`). (#283)
- **Dynamic SemVer Version Coding**: Dynamic `versionCode` and `versionName` injection via Gradle properties with monotonic SemVer formula for beta releases. (#283)
- **System architecture & compartment documentation**: added comprehensive per-module `README.md` specifications across `engine/`, `service/`, `data/`, and `ui/` compartments using the `audio-pipeline-explainer` framework (plain-English mental models, class breakdowns, thread safety boundaries, active limitations, and tabulated jargon glossaries). (#286)
- **PlantUML architecture specifications**: added dedicated architectural diagrams for system compartment wiring (`system-compartments.puml`), Media3 `AudioProcessor` pipeline and mutex-guarded offload transition lifecycle (`audio-pipeline-lifecycle.puml`), and multi-source data convergence and dual-transport routing (`data-convergence-architecture.puml`). (#286)

### Changed

- **Playback slider waveform baseline anchoring**: anchored 3-tier sinusoidal harmonic waveforms across both the Main Player seeker (FieryWavySeeker) and Cover Screen mini player seeker (CoverMicroWaveform) to start at shift 0 on the baseline x-axis at x = 0 and smoothly intercept the track baseline at x = width; applied a raised-cosine boundary envelope W(x) with continuous first-derivative transitions to prevent edge detachment and mid-oscillation termination while preserving full dynamic audio reactivity, multi-harmonic turbulence, and zero heap allocations. (#337)
- **Android Gradle Plugin**: pinned AGP to 9.4.0 for Android Studio compatibility and refreshed dependency verification metadata.

### Fixed

- **AudioDeviceRepository race condition**: fixed race condition during Bluetooth disconnection events where rapid reconnect/disconnect could leave an incorrect active audio device route. (#339)
- **Spotify artwork negative cache & backoff**: prevented perpetual repeated Spotify artwork network requests for unmatched local tracks by recording misses with timestamps and attempt counts in Room (`TigerDatabase` version 16 migration), implementing a 7-day base cool-off with exponential backoff on repeated failures, preferring embedded artwork over network round-trips, and adding a user-visible "ONLINE ARTWORK LOOKUP" setting to disable online artwork fetching entirely. (#64)
- **Auto-headroom composite peak calculation**: replaced naive algebraic summation of positive EQ band gains in `AdaptiveDspEngine` with true composite filter response peak detection via `BiquadDesigner.calculateCompositePeakGainDb()`; avoids drastic Fletcher-Munson loudness drops on non-overlapping frequency bands while accurately calculating cumulative compounding on overlapping filters and cancellation on opposing boost/cut nodes. (#289)
- **Lossless dynamic compression & true-peak limiting**: removed hardcoded 0.82f (-1.72 dBFS) limiter threshold in `AdaptiveDspEngine`; bypassed AGC gain reduction completely on lossless streams (`audio/flac`, `audio/wav`, `audio/alac`, `audio/aiff`, and 24-bit/Float PCM) when EQ is flat (0 dB boost or negative cuts); tuned look-ahead limiter to transparent 1.0f (0.0 dBFS) true-peak protection with 0.8ms attack and 50ms release, preventing dynamic range squashing, transient dulling, and volume pumping. (#288)

## [2.1.6-beta.1] - 2026-10-27

### Added
- **Spotify diagnostics & setup dialog**: added `SpotifyAuthError` model and `SpotifyAuthErrorDialog`, converting cryptic OAuth callbacks and configuration omissions into actionable developer and setup guidance (missing `secrets.properties` keys, redirect URI whitelisting, account permissions, and offline state) with accessible 48dp dismiss and retry actions.
- **Cover screen settings**: added full `CoverScreenSettingsView` mirroring all primary app preferences, including Spotify authentication controls, AMOLED black toggles, neon accent swatches, PiP controls, and playback parameters.
- **Cover screen navigation**: wired interactive settings launcher button in `CoverScreenLibraryMenu` with haptic feedback and back-gesture handling.
- **Context extensions**: added `Context.findActivity()` utility for safe unnesting across wrapped UI contexts.

### Changed

- **Spotify authentication workflow**: replaced fleeting transient toasts with persistent, user-friendly Arcane Glass diagnostics dialogs across both normal and cover screen modes. Added explicit CONNECT buttons when disconnected and confirmation-guarded DISCONNECT buttons when authenticated.
- **Cover screen visual overhaul**: refreshed Album and Song tabs, artist detailed grids, and playback views to Arcane Glass visual standards.

### Fixed

- **Compose observable locale**: replaced non-observable `Locale.getDefault()` invocations with `Locale.ROOT` in `CoverScreenStatsView`, resolving Android Lint compiler errors.
- **Cover screen UI layout bindings**: resolved Button and ButtonDefaults import references, and aligned `rememberTigerAmbientGradient` parameter conventions.

## [2.1.5] - 2026-10-25

### Added
- **Cover screen preview**: added `coverScreenPreviewMode` preference in settings and an exit button in cover screen settings, enabling cover screen UI preview and testing on standard phone displays. (#275)

### Fixed

- **Cover screen detection**: resolved foldable outer display detection failure caused by `computeMaximumWindowMetrics` inflating window bounds to unfolded screen dimensions (1080x2640 px / 411x1005 dp) on Samsung Galaxy Z Flip and resize-model foldables. Reconstructed true un-insetted height directly from `configurationHeightDp` and `imeBottomDp`. (#275)
- **Cover screen display identity**: hardened secondary display resolution across `LocalView.current.display`, `LocalContext.current.display`, and `Activity.display` with `Display.INVALID_DISPLAY` guarding, ensuring Motorola Razr and external displays are detected reliably before and after window attachment. (#275)
- **Cover screen heuristics**: expanded short edge (180..440 dp), long edge (<= 480 dp), and aspect ratio (<= 1.55f) bounds to support edge-to-edge clamshell outer panels. (#275)

## [2.1.4] - 2026-10-24

### Added
- **Architecture & Documentation**: Added comprehensive PlantUML architectural design guide (docs/architecture/architecture-design.md) and essential inline code comments across core modules (service/, engine/, data/, ui/), documenting threading models, domain invariants, and Media3 audio pipelines while removing stale line-number comments and decorative emoji banners. (#268)
- **Cover screen**: added an artist grid for local and Spotify libraries, with artwork thumbnails,
  album counts, artist album lists, and swipe-back navigation. (#220)
- **Cover screen**: added album track browsing with play, add-to-queue, remove-from-queue swipe,
  and long-press track actions. (#221)
- **Cover screen**: added gesture-accessible library navigation for artists, albums, local playlists,
  and authenticated Spotify playlists. (#222)
- **Cover screen**: added a 300 ms debounced library search for artists, albums, tracks, and playlists,
  with top-edge swipe activation and optional voice input. (#223)
- **Cover screen**: added categorized search results for artists, albums, and tracks, with no-results
  feedback plus immediate play and queue actions for matching tracks. (#224)
- **Cover screen**: expanded queue browsing to the full queue with long-press reordering, confirmed
  swipe removal, and a confirmed persistent clear-queue action. (#225)
- **Cover screen**: added a cached listening-stats widget with today's listening time and play
  count, top artists, a seven-day history chart, and a shortcut to Sonic Footprint. (#226)
- **Cover screen**: surfaced Day List and Discover Weekly suggestions with artwork, play/queue
  actions, horizontal browsing, and refreshable empty-feed feedback. (#227)
- **Cover screen**: added Songs tab and Play All button to the cover screen library. (#253)
- **Cover screen**: integrated Spotify playlists with a 15-minute TTL cache on the cover screen. (#230)
- **Cover screen & UI**: accessibility semantics, TalkBack announcements, and localization support. (#231)
- **UI / Input**: keyboard options, IME actions, auto-focus, and insets padding across input fields. (#261)
- **Cover screen parity**: Arcane Glass UI, spatial 3D EQ nexus, constellation library view, and cover screen settings. (#269)

### Changed

- **Architecture**: Decomposed the monolithic `PlayerViewModel` god object into scoped ViewModels (`PlaybackViewModel`, `LibraryViewModel`, `StatsViewModel`) backed by an application-scoped `PlaybackStateManager` singleton, eliminating redundant recompositions and coroutine collector overhead. (#72)
- **Performance**: Decoupled raw search query input from global `PlayerUiState` to eliminate typing latency. (#262)
- **Cover screen performance**: Optimized rendering frame pacing and battery consumption on outer displays. (#232)

### Fixed

- **Cover screen**: Prevent lyrics display clipping on small foldable displays. (#252)
- **Cover screen**: Prevent playback controls overflow on compact screens. (#251)
- **Cover screen**: Prevent unmounting and viewport clipping during IME keyboard resize events. (#260)

## [2.1.3] - 2026-10-12

### Fixed

- **Spotify**: Surface actionable UI element for Spotify App Remote reauthorization in Cloud Archives
  and Full Player screens. Added a persistent reauth banner with one-tap "Re-authorize Spotify" button
  and clear status feedback on successful reauthorization. (#201)

### Added
- **F-Droid**: Reproducible builds support with binary verification metadata and allowed signing keys
  documentation. (#246)

## [2.1.2] - 2026-09-23

### Added
- **Full player**: the Speed/Pitch dialog has a restore-to-default action that resets both to
  `1.0x` in one tap. (#167)
- **Connected Accounts**: a new Settings section to log out of Spotify and Navidrome independently,
  with a confirm-before-logout prompt. Logging out of Navidrome also triggers a library rescan so
  its tracks disappear from the unified library immediately. (#162)
- **Constellation**: the bottom "Cosmic Insight" overlay panel is now collapsible, so the starfield
  can be viewed unobstructed. Added a two-layer parallax starfield with a subtle twinkle behind the
  orbiting nodes for a stronger sense of depth. (#162)
- **Constellation**: a clickable central "Sun" node now anchors the galaxy - tapping it shows the
  "Cosmic Insight" summary as scannable label/value rows instead of a paragraph. Tapping a song node
  now shows its title, artist, and play count (including in collapsed mode), and tapping an artist
  shows their top 3 songs in place of the bio snippet.

### Changed

- **CI/CD**: the `Release candidate` workflow now also builds an unsigned release APK
  (`assembleFullRelease`), and for a `v*` tag push attaches the APK/AAB/mapping/checksums/SBOM
  directly to the GitHub Release for that tag, in addition to the existing workflow-artifact
  upload. No signing keys are read; assets remain clearly labeled as unsigned evidence.
- **CI/CD**: the `Release candidate` workflow now additionally builds and attaches an unsigned
  `foss`-flavor APK (and its R8 mapping file) to the same GitHub Release, so both distribution
  flavors are available as release assets without any manual build/upload step.
- **CI/CD**: the GitHub Release publish step now also works on a manual `workflow_dispatch` run
  targeting an existing tag (via the `ref` input), so assets can be backfilled onto a release
  whose original tag push predates a workflow change — not just on a fresh `v*` tag push.
- **Constellation**: increased minimum node sizes so low-importance artist/album/track artwork
  stays legible at any zoom level, and artist/track name plates no longer clip the label to the
  node's own bounding box, so full names are always visible.
- **Sonic Prism**: the mixer moved from a cramped expandable Home dashboard card to its own
  full-screen destination, where the faders scale to the available height (up to 360 dp) instead of
  being clamped to 180 dp. The Home feed now shows a compact entry card with a quick enable toggle
  that opens the new screen on tap. Removed the dead, commented-out mixer block from the full
  player. (#173)
- **Sonic Prism**: removed the developer-only FFT-vs-Bandpass spectral analysis toggle and its
  profiler readout from the shipped Home screen card (still available in debug builds); hid dead,
  unreachable "Sonic Prism full-screen hub" and duplicate mixer code paths that had been superseding
  each other.
- **Sonic Footprint**: the acoustic/electronic/bass-heavy/vocal/atmospheric axes are now weighted by
  actual time listened instead of raw play count, and prefer cached Last.fm genre tags over
  guessing from track title text when genre data is available for the artist. (#162)
- **Cloud screen**: the YouTube search entry point now uses a distinct video icon instead of
  reusing the same magnifying-glass icon as the search field next to it. (#162)
- **Full player**: the Speed/Pitch controls are now a compact summary row (e.g. "Speed 1.25x -
  Pitch 1.00x") that opens a dedicated glass-styled dialog on tap, instead of permanently reserving
  space for two full-width sliders inline - fixes a layout overlap on shorter screens where the
  sliders used to always be expanded. (#162, #167)
- **Queue screens**: the drag-to-reorder handle now renders as a proper icon instead of a raw text
  glyph that had been mangled into mojibake. (#163)

### Fixed

- **Spotify**: the login flow now requests Spotify's `app-remote-control` permission alongside the
  existing library scopes, so a fresh sign-in authorizes App Remote playback instead of failing
  later with "Explicit user authorization is required". Sessions known to predate that scope now
  surface an actionable reauthorization message that points to Settings > Connected Accounts. 
- **Build (F-Droid)**: removed the `org.gradle.toolchains.foojay-resolver` plugin, scoped Google
  Play Services Location and the Spotify App Remote AAR to the `full` flavor only (via a new
  `LocationProvider` abstraction with a no-op `foss` implementation), and pinned
  `kotlinx-coroutines-android` so the `foss` and `full` flavors resolve the same coroutines runtime.
  The `v2.1.1` tag F-Droid attempted to build predates this fix and never produced a working build,
  so `versionCode`/`versionName` are bumped to 3 / `2.1.2` for a fresh, buildable tag. (#71)
- **Spotify**: starting a Spotify track no longer shows a meaningless string (the raw Spotify
  track ID) stuck at `0:00`. The player now shows the real title, artist, album art, and duration
  the moment playback is requested, because the metadata already on screen is passed through to
  the interim state instead of being re-derived from the URI. (#169)
- **Spotify**: a Spotify App Remote connection that fails — the Spotify app isn't installed or
  running, the account isn't Premium, or the IPC connection is refused — now shows an explanatory
  message instead of silently leaving the player parked at `0:00` forever. Playback that is never
  confirmed by the Spotify app is also timed out and reported rather than left hanging, and builds
  without App Remote support say so. (#170)
- **Lyrics**: LRCLIB lookups now retry with cleaned title/artist metadata and without placeholder
  album labels, so lyrics can still resolve when playback surfaces generic album names like
  `"Spotify"` or tracks carry noisy suffixes such as remaster/live tags.
- **Spotify playlist screen**: the header artwork is now centred and captioned with the playlist
  name and track count, and no longer draws its glow shadow twice. Both the playlist and album
  detail screens now show an explicit empty state, surface errors that occur while they are open,
  and can no longer crash on a collection that lists the same track ID more than once. Album track
  rows gained artwork and now follow the screen's extracted accent colour. (#171)

- **Sonic Prism**: fixed a bug where the isolation mix could be silently disabled (with playback
  continuing unaffected/unannounced) purely as a side effect of the controlling screen's ViewModel
  being torn down - e.g. when the system destroys the Activity in the background while a foreground
  playback service keeps the music going. The DSP engine's enabled/disabled state is now driven only
  by the user's persisted setting, never reset by UI lifecycle.
- **Sonic Prism**: fixed a layout bug where the Home screen mixer card clipped its own vertical
  fader and controls (the card was capped at a fixed height shorter than the fader itself). The
  card now sizes to its content and uses a more compact fader suited to a dashboard card.
- **Spotify connection**: the Spotify App Remote session is now actually opened after a successful
  login. Previously `isSpotifyRemoteConnected` stayed `false` indefinitely post-auth, leaving the
  Cloud screen stuck on "reconnecting". (#162)
- **Player/Spotify source switching**: fixed a race where the player UI could get stuck showing a
  Spotify track after the user started local playback, caused by stale Spotify state re-emissions
  clobbering the just-started local track. (#162)

[Unreleased]: https://github.com/tyejaedon/TigerPlayer/compare/v2.1.2...HEAD
[2.1.2]: https://github.com/tyejaedon/TigerPlayer/compare/v2.1...v2.1.2
