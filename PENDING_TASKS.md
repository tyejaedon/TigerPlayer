# TigerPlayer Milestone Task Board

Last updated: 2026-06-27

This board is grouped by release milestones and issue-sized tasks.

Execution rule: after every implementation run, this file must be updated immediately (issue checkboxes, stage, completed items, and files touched). See `agent.md` and `prompt.md`.

## Completed So Far

### M1 - Neon + Visual Core
- [x] Dynamic neon extraction/quantization pipeline integrated and used in core player surfaces.
- [x] Reactive fluid motion bridge added (`ReactiveMotionFrame`) with smoothed uniforms (`uExpansion`, `uFlowSpeed`, `uTurbulence`).
- [x] Advection/divergence/pressure shaders updated for breathing flow + turbulence response.
- [x] Legacy vortex renderer path removed; Compose now uses the active `FluidRenderer` pipeline.
- [x] Vorticity confinement pass integrated (`curl` + `vorticity` passes).
- [x] Lightweight half-float bloom integrated (prefilter + separable blur + composite).
- [x] Kick-reactive advection dissipation pulse behavior integrated.
- [x] Mobile optimization pass executed (branchless turbulence normalization, cached texel uniforms, mediump bloom shaders).
- [x] Six-band chromatic fluid mapping integrated (bass red/violet blooms, treble cyan/blue/white splashes).
- [x] Art-driven screen audit completed; neon extraction fallback/reset behavior hardened across detail/player/cloud surfaces.
- [x] Ambient gradients now enforce snapped-neon accent + OLED fade pattern at the theme helper level.
- [x] Color snapping edge-case tests added for low-saturation/muddy/near-neutral inputs.
- [x] Fluid display chroma-preservation hotfix applied (reduced center white clipping, improved gradient rolloff, restrained bloom washout).
- [x] Secondary fluid aesthetic pass applied (ring-layered injections + center limiter + lower bloom whitening) for stronger neon gradients.
- [x] Final fluid polish pass applied (stronger low/mid/high chroma separation, darker bass core, outer neon halo shaping).
- [x] Global neon UI polish applied (revamped color tokens, stronger contrast, RGB neon border accents, and higher-visibility glass/shadow rendering).
- [x] RGB border hierarchy tuned: only premium containers keep RGB border accents; standard glass cards stay clean.
- [x] Added visual mode toggle for `Balanced` vs `High` Neon Contrast in Settings (persisted via DataStore and applied app-wide).
- [x] Expanded theme customization with multi-level Neon Intensity (`Soft` / `Balanced` / `High`) and propagated via CompositionLocals.
- [x] High neon now boosts ambient gradient strength and fluid overlay tinting in player/art-driven surfaces.
- [x] Formal `PremiumGlassCard` wrapper added and adopted for premium shells to lock RGB border hierarchy semantics.
- [x] Expanded `PremiumGlassCard` adoption to additional library hero containers (Artist Hero, Vanguard stats, Album hero metadata).
- [x] Added visual QA screenshot/regression checklist for Home/Player/Library.
- [x] Repaired accidental syntax corruption in `DominantColorExtractorTest` imports to restore unit-test compilation.

### M2 - Queue + Daylist
- [x] Queue semantics implemented (`playNext`, `addToQueue`, remove, reorder).
- [x] Infinite tail auto-append logic implemented in `MediaControllerManager`.
- [x] Dedicated `QueueScreen` added with pinned now playing + drag reorder.
- [x] Daylist/Vault Room queries and dashboard state wiring added.
- [x] Daylist/Vault home containers redesigned into packed Material cards that open playlist-style sheets.
- [x] Queue UX hardening pass applied (drag-handle long-press reorder with haptic feedback + row long-press Song Options sheet).
- [x] Song Options sheet now supports both `Play Next` and `Add to Queue End` actions.
- [x] Queue reorder fluidity pass applied (animated item placement + step-threshold drag smoothing to reduce jitter).
- [x] QueueScreen interaction pass: removed queue-row Song Options long-press and switched queue item play to index seek (`playQueueItem`) to avoid full playlist resets/flicker.
- [x] Daylist/Vault recommendation pipeline replaced with a two-phase Discovery Engine (Cold Start trendsetters -> Personalized weighted scoring + unseen-genre injection).
- [x] Daily discovery regeneration + stale/empty container messaging added (midnight refresh and >24h launch refresh guard).
- [x] Full Player queue upgraded with drag-handle long-press reorder + index-based queue seek, and artist-image backdrop rendering restored from metadata pipeline.
- [x] Queue drag visual feedback polish pass applied (lifted dragged row, subtle shadow/scale, and active drag-handle tinting in Queue + Full Player queue views).
- [x] Queue reorder affordance pass applied (between-row drop target indicator, neighbor offset animation, and throttled haptic ticks on long drags).

### M3 - Apex DSP + Crossfade
- [x] Acoustic Environments DSP mode added (`OFF`, `VINYL_WARMTH`, `CONCERT_HALL`).
- [x] Service custom command bridge added for acoustic environment switching.
- [x] `AcousticEnvironmentScreen` and settings wiring implemented.
- [x] Flow State crossfade engine implemented (7s tail fade + transition fade-in).
- [x] Runtime crossfade toggle added and persisted via DataStore.
- [x] Tuned Vinyl Warmth and Concert Hall coefficients by output route profile (speaker/headphones/bluetooth).
- [x] Acoustic environment mode now restores across service lifecycle transitions via settings persistence.
- [x] Flow State crossfade duration control added (0-12s) and bound to engine behavior.
- [x] Added DSP output-safety unit tests and crossfade regression tests (seek/skip/manual conflict guards).

### M4 - Sonic Footprint + PiP
- [x] Sonic Footprint DAO aggregation + repository flow implemented.
- [x] `SonicFootprintViewModel` and `SonicFootprintScreen` with custom Canvas radar chart added.
- [x] Expanded filter windows added (`Today`, `Week`, `Last 7`, `Month`, `Last 30`, `Last 90`, `Year`, `Lifetime`).
- [x] Global listening share metric added (selected minutes as % of lifetime).
- [x] Listening analytics rewired to track true listened milliseconds from playback position deltas (instead of full track duration inserts).
- [x] Artist analytics normalized and re-ranked by total listened duration with unknown/blank artist filtering.

---

## M1 - Neon + Visual Core

**Description:** Finalize neon identity and fluid rendering quality so the visual system is production-stable and consistently reactive.

**Stage:** Completed (all M1 issues closed and validated in compile + targeted unit tests).

**Files edited so far:**
- `app/src/main/java/com/example/tigerplayer/engine/FluidRenderEngine.kt`
- `app/src/main/java/com/example/tigerplayer/utils/Shaders.kt`
- `app/src/main/java/com/example/tigerplayer/ui/player/TigerVortexRender.kt`
- `app/src/main/java/com/example/tigerplayer/ui/player/FullPlayerScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/library/AlbumDetailScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/library/ArtistDetailScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/library/AritistComponents.kt`
- `app/src/main/java/com/example/tigerplayer/ui/library/PlaylistDetailScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/cloud/SpotifyAlbumDetailScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/cloud/SpotifyPlaylistScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/theme/Color.kt`
- `app/src/main/java/com/example/tigerplayer/ui/theme/Theme.kt`
- `app/src/main/java/com/example/tigerplayer/ui/theme/Modifiers.kt`
- `app/src/main/java/com/example/tigerplayer/ui/theme/DynamicNeonLocals.kt`
- `app/src/main/java/com/example/tigerplayer/ui/theme/ThemeTuning.kt`
- `app/src/main/java/com/example/tigerplayer/ui/theme/PremiumGlassCard.kt`
- `app/src/main/java/com/example/tigerplayer/MainActivity.kt`
- `app/src/main/java/com/example/tigerplayer/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/com/example/tigerplayer/ui/settings/SettingScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/main/MainScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/player/MiniPlayer.kt`
- `app/src/main/java/com/example/tigerplayer/ui/home/DashboardContainers.kt`
- `VISUAL_QA_CHECKLIST.md`
- `app/src/test/java/com/example/tigerplayer/ui/theme/DominantColorExtractorTest.kt`
- `app/src/main/java/com/example/tigerplayer/utils/Shaders.kt` (post-M1 display tonemapping hotfix)
- `app/src/main/java/com/example/tigerplayer/engine/FluidRenderEngine.kt` (post-M1 injection/bloom balancing hotfix)

### Issues
- [x] M1-01 Audit all art-driven screens for neon quantization consistency.
- [x] M1-02 Verify all ambient gradients use snapped neon + OLED fade pattern.
- [x] M1-03 Add color snapping edge-case tests (low-sat/muddy art).
- [x] M1-04 Add true vorticity confinement pass in FBO pipeline.
- [x] M1-05 Map 6 FFT bands to multi-band chromatic fluid injection.
- [x] M1-06 Add lightweight half-float bloom pass.
- [x] M1-07 Add kick-drum advection dissipation pulse behavior.
- [x] M1-08 Run mobile GPU optimization pass for shader branching/precision.
- [x] M1-09 Post-QA fluid color clipping pass (reduce white-core burn, preserve chroma gradient).
- [x] M1-10 Post-QA fluid gradient layering pass (reduce white dominance, improve chroma distribution).
- [x] M1-11 Final neon fluid aesthetic polish (peripheral halo + deeper center contrast + stronger band separation).
- [x] M1-12 Global neon visual identity pass (RGB border glow + high-visibility glass + stronger theme contrast).
- [x] M1-13 Premium RGB border hierarchy + neon contrast mode toggle + visual QA checklist.
- [x] M1-14 Theme interconnection hardening (neon intensity customization + high-neon ambient/fluid boost + formal PremiumGlassCard).
- [x] M1-15 Premium container standardization pass (wrapper adoption across additional library hero containers).

## M2 - Queue + Daylist

**Description:** Harden queue UX and recommendation containers for predictable, resilient playback and discovery.

**Stage:** In progress (queue/daylist UX + personalization generation are wired, including Full Player drag reorder, artist backdrop restore, drag-feedback polish, and stronger reorder affordances; shuffle/repeat validation, tail-ranking hardening, and automated tests remain).

**Files edited so far:**
- `app/src/main/java/com/example/tigerplayer/service/MediaControllerManager.kt`
- `app/src/main/java/com/example/tigerplayer/ui/queue/QueueScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/main/MainScreen.kt`
- `app/src/main/java/com/example/tigerplayer/navigation/Screen.kt`
- `app/src/main/java/com/example/tigerplayer/data/local/dao/TigerDao.kt`
- `app/src/main/java/com/example/tigerplayer/data/repository/AudioRepository.kt`
- `app/src/main/java/com/example/tigerplayer/ui/home/DashboardViewModel.kt`
- `app/src/main/java/com/example/tigerplayer/ui/home/DashboardContainers.kt`
- `app/src/main/java/com/example/tigerplayer/ui/library/SongOptionsSheet.kt`
- `app/src/main/java/com/example/tigerplayer/ui/home/HomeScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/library/LibraryScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/library/AlbumDetailScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/library/ArtistDetailScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/player/FullPlayerScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/player/PlayerViewModel.kt`
- `app/src/main/java/com/example/tigerplayer/engine/PlaybackEngine.kt`
- `app/src/main/java/com/example/tigerplayer/engine/DiscoveryEngine.kt`
- `app/src/main/java/com/example/tigerplayer/engine/ListeningDensityTracker.kt`
- `app/src/main/java/com/example/tigerplayer/data/repository/HistoryRepository.kt`
- `app/src/main/java/com/example/tigerplayer/data/repository/MediaDataRepository.kt`
- `app/src/main/java/com/example/tigerplayer/di/DiscoveryModule.kt`
- `app/src/main/res/raw/global_trending_tracks.json`

### Issues
- [ ] M2-01 Add QueueScreen instrumentation tests for drag/drop persistence.
- [ ] M2-02 Validate queue semantics under shuffle/repeat edge cases.
- [ ] M2-03 Improve infinite tail candidate ranking and fallback behavior.
- [ ] M2-04 Add Last.fm timeout/backoff handling for auto-append.
- [x] M2-05 Add Daylist/Vault empty and stale data states.
- [ ] M2-06 Add tests for day segment boundaries and SQL expectations.

## M3 - Apex DSP + Crossfade

**Description:** Tune and validate audio-engine enhancements for artifact-free real-world playback.

**Stage:** Completed (all M3 issues closed with route-aware tuning, persisted environment state, duration controls, and targeted unit regression coverage).

**Files edited so far:**
- `app/src/main/java/com/example/tigerplayer/engine/DspEngine.kt`
- `app/src/main/java/com/example/tigerplayer/service/AudioPlayerService.kt`
- `app/src/main/java/com/example/tigerplayer/service/MediaControllerManager.kt`
- `app/src/main/java/com/example/tigerplayer/ui/settings/AcousticEnvironmentScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/com/example/tigerplayer/ui/settings/SettingScreen.kt`
- `app/src/test/java/com/example/tigerplayer/engine/AdaptiveDspEngineSafetyTest.kt`
- `app/src/test/java/com/example/tigerplayer/service/FlowStateCrossfadeMathTest.kt`

### Issues
- [x] M3-01 Tune Vinyl Warmth harmonic/noise coefficients across device outputs.
- [x] M3-02 Tune Concert Hall reverb decay/predelay per speaker/headphone profile.
- [x] M3-03 Add DSP unit tests for Acoustic Environment output safety.
- [x] M3-04 Persist acoustic environment state across service lifecycle transitions.
- [x] M3-05 Add crossfade duration setting (0-12s) and bind to Flow State engine.
- [x] M3-06 Add crossfade regression tests for seek/skip/manual transition conflicts.

## M4 - Sonic Footprint + PiP

**Description:** Complete on-device wrapped polish and deliver playback-aware PiP visual mode.

**Stage:** In progress (Sonic Footprint done with expanded filters/percent metrics; PiP Vortex not started).

**Files edited so far:**
- `app/src/main/java/com/example/tigerplayer/data/local/dao/TigerDao.kt`
- `app/src/main/java/com/example/tigerplayer/data/repository/HistoryRepository.kt`
- `app/src/main/java/com/example/tigerplayer/ui/home/SonicFootprintViewModel.kt`
- `app/src/main/java/com/example/tigerplayer/ui/home/SonicFootprintScreen.kt`
- `app/src/main/java/com/example/tigerplayer/navigation/Screen.kt`
- `app/src/main/java/com/example/tigerplayer/navigation/NavGraph.kt`
- `app/src/main/java/com/example/tigerplayer/ui/settings/SettingScreen.kt`
- `app/src/main/java/com/example/tigerplayer/engine/StatsEngine.kt`
- `app/src/main/java/com/example/tigerplayer/ui/home/ExpandedStatsScreen.kt`
- `app/src/main/java/com/example/tigerplayer/ui/player/PlayerViewModel.kt`

### Issues
- [ ] M4-01 Add animated radar morph transitions when filters change.
- [ ] M4-02 Improve sonic axis classification heuristics from local metadata.
- [ ] M4-03 Add instrumentation tests for Sonic Footprint empty/non-empty/filter transitions.
- [ ] M4-04 Implement PiP auto-enter in `MainActivity` while playback is active.
- [ ] M4-05 Configure PiP params (aspect ratio, source rect, actions).
- [ ] M4-06 Ensure PiP renders Fluid/Waveform visualizer and stays reactive.
- [ ] M4-07 Validate PiP behavior across pause/resume/background/foreground lifecycle.
- [x] M4-08 Rewire analytics accuracy for listening minutes + top artists (position-delta telemetry + artist normalization).

## M5 - QA + Release Hardening

**Description:** Add automated safety nets and release hardening for crash-free RC/release builds.

**Stage:** Not started (planned after feature completion freeze).

**Files edited so far:**
- _None yet for dedicated M5 tasks._

### Issues
- [ ] M5-01 Add `AdaptiveDspEngine.queueInput()` unit safety test suite.
- [ ] M5-02 Add Room integrity test for playback/history persistence.
- [ ] M5-03 Add Compose UI tests for player controls and render stability.
- [ ] M5-04 Integrate LeakCanary in debug builds.
- [ ] M5-05 Enable StrictMode main-thread I/O/network detection in app startup.
- [ ] M5-06 Add Macrobenchmark for QueueScreen scrolling/frame timing.
- [ ] M5-07 Execute manual edge-case matrix (calls, alarms, BT disconnect, swipe-away).
- [ ] M5-08 Finalize `proguard-rules.pro` keep rules (Media3, Coil, Retrofit/Gson, Room).
- [ ] M5-09 Run release smoke tests (`assembleRelease`, `bundleRelease`, startup playback sanity).

---

## Post-M5 Backlog (Next Program)

### Sonic Prism (Prompt 6)
- [ ] Implement Mid/Side + band-split prism DSP mode in `AdaptiveDspEngine`.
- [ ] Build `SonicPrismHub` (3 neon faders + haptic thresholds).
- [ ] Add `PrismViewModel` real-time bridge with pop-safe interpolation.

### Control Matrix Expansion (Prompt 7)
- [ ] Consolidate settings into coherent `TigerSettingsState` and expand appearance/audio/library controls.

### Foldable Cover Screen Hub (Prompt 8)
- [ ] Add cover-screen manifest flags, runtime detection, and gesture-first mini hub UI.

