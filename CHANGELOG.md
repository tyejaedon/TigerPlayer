# Changelog

All notable changes to TigerPlayer are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versioning follows the
`versionName` in `app/build.gradle.kts` (currently pre-1.0 internal versioning; see issue #45 for the
`versionCode`/package-name cleanup tracked ahead of a real store release).

For prose-style release summaries at major milestones, see `docs/Release-Notes-*.md`. This file is
the terse, per-change log — add an entry here in the same PR as the change, under `[Unreleased]`.

## [Unreleased]

### Added

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

- **Android Gradle Plugin**: updated AGP from 9.4.1 to 9.5.0 and refreshed dependency verification metadata. (#296)

### Fixed

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
