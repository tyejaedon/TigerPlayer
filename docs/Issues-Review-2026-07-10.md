# TigerPlayer Issue Review (2026-07-10)

This file captures the application-wide issue review from repository signals (code markers, commit history, and lint/build checks).

## Scope and method

- Reviewed explicit issue markers (`FIXED`, `TODO`, technical-debt comments).
- Reviewed bug-fix commits (`5118a4a`, `1d8c7c8`) and relevant diffs.
- Ran required verification commands:
  - `./gradlew :app:assembleDebug`
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew :app:lintDebug`

## Likely fixed issues

### 1) GPU resource leak when visualizer view detaches
- **Evidence:** `app/src/main/java/com/example/tigerplayer/engine/VizualizerEngine.kt:52`
- **Comment:** "Confirmed fixed: `renderer?.release()` is restored in `onDetachedFromWindow`, preventing orphan GPU textures/FBOs."

### 2) EQ state not reapplied after sample-rate changes
- **Evidence:** `app/src/main/java/com/example/tigerplayer/engine/DspEngine.kt:242`
- **Comment:** "Confirmed fixed: current acoustic nodes are reapplied when sample rate changes, preserving EQ behavior across track transitions."

### 3) AGC attenuation persisting after seek/rebuffer
- **Evidence:** `app/src/main/java/com/example/tigerplayer/engine/DspEngine.kt:458`
- **Comment:** "Confirmed fixed: AGC envelope reset in `flush()` clears stuck volume reduction after seeks/rebuffer events."

### 4) Visual computation job leak in equalizer viewmodel
- **Evidence:** `app/src/main/java/com/example/tigerplayer/ui/equalizer/NexusViewModel.kt:79`
- **Comment:** "Confirmed fixed: visual update job is tracked/cancelled, reducing background thread/job leakage risk."

### 5) Sync/perf Gradle settings marked as rectified
- **Evidence:** `gradle.properties:11-14`
- **Comment:** "Likely fixed for current setup: sync-related constraint settings were added and labeled `RECTIFIED`."

## Pending issues

### 1) Backup/data extraction policy is still TODO
- **Evidence:** `app/src/main/res/xml/data_extraction_rules.xml:8`
- **Comment:** "Pending: define explicit backup include/exclude policy before release hardening."

### 2) Local scan path carries unresolved technical debt marker
- **Evidence:** `app/src/main/java/com/example/tigerplayer/data/source/LocalAudioDataSource.kt:60`
- **Comment:** "Pending: `SPEED HACK` indicates intentional shortcut; either document benchmark rationale or refactor for maintainability."

### 3) Pre-flight validation matrix is RC checklist without execution log
- **Evidence:** `docs/PreFlightValidationMatrix.md:1`, `docs/PreFlightValidationMatrix.md:20`, `docs/PreFlightValidationMatrix.md:31`, `docs/PreFlightValidationMatrix.md:43`, `docs/PreFlightValidationMatrix.md:75`
- **Comment:** "Pending QA closure: expected outcomes are documented, but pass/fail run results are not recorded in this file."

### 4) Platform migration debt from deprecated AGP/Gradle flags
- **Evidence:** `gradle.properties:26-35`
- **Comment:** "Pending migration: build currently works, but deprecated flags should be cleaned up before AGP 10 upgrade."

## GitHub project board issues (full list)

Status legend:
- **Implemented evidence**: concrete implementation/test artifacts exist in repo.
- **Partial evidence**: related implementation exists, but acceptance details are not fully verifiable from code alone.
- **No evidence**: no matching implementation/test artifact was found in this review pass.

Progress snapshot:

| Total issues reviewed | Implemented evidence | Partial evidence | No evidence |
| --- | --- | --- | --- |
| 35 | 16 | 14 | 5 |

| Issue | Title | Status | Evidence | Comment |
| --- | --- | --- | --- | --- |
| #4 | Audit all artwork-driven screens for neon quantization consistency | Partial evidence | `app/src/main/java/com/example/tigerplayer/ui/player/FullPlayerScreen.kt:133`<br>`app/src/main/java/com/example/tigerplayer/ui/player/PipVisualizerSurface.kt:41` | Rendering paths exist, but no formal audit record is captured. |
| #5 | Enforce snapped neon ambient gradients across player/library surfaces | Partial evidence | `app/src/main/java/com/example/tigerplayer/ui/player/FullPlayerScreen.kt:141`<br>`app/src/main/java/com/example/tigerplayer/ui/player/PipVisualizerSurface.kt:41` | Gradient behavior exists, but global snap policy proof is incomplete. |
| #6 | Implement vorticity confinement pass in fluid simulation pipeline | Implemented evidence | `app/src/main/java/com/example/tigerplayer/engine/FluidRenderEngine.kt:270`<br>`app/src/main/java/com/example/tigerplayer/utils/Shaders.kt:70` | Vorticity pass and shader are implemented. |
| #7 | Map 6 FFT perceptual bands to multi-band chromatic fluid injection | Partial evidence | `app/src/main/java/com/example/tigerplayer/engine/FluidRenderEngine.kt:60`<br>`app/src/main/java/com/example/tigerplayer/utils/Shaders.kt:215` | Multi-band mapping exists; explicit six-band contract is not fully clear. |
| #8 | Add lightweight half-float bloom pass for high-energy glow | Partial evidence | `app/src/main/java/com/example/tigerplayer/engine/FluidRenderEngine.kt:320`<br>`app/src/main/java/com/example/tigerplayer/engine/FluidRenderEngine.kt:398` | Bloom/composite path exists; half-float detail is not explicitly documented. |
| #9 | Add kick-drum advection pulse dissipation behavior | Implemented evidence | `app/src/main/java/com/example/tigerplayer/engine/FluidRenderEngine.kt:234`<br>`app/src/main/java/com/example/tigerplayer/utils/Shaders.kt:45` | Kick-pulse advection/dissipation behavior is present. |
| #10 | Profile OpenGL shader passes and reduce mobile GPU branching | Partial evidence | `app/src/main/java/com/example/tigerplayer/ui/player/TigerVortexRender.kt:351`<br>`app/src/main/java/com/example/tigerplayer/ui/player/PipVisualizerSurface.kt:58` | Optimization work appears present, but no profiling report artifact was found. |
| #11 | Harden QueueScreen reorder UX and persistence edge cases | Implemented evidence | `app/src/main/java/com/example/tigerplayer/ui/queue/QueueScreen.kt:119`<br>`app/src/main/java/com/example/tigerplayer/ui/queue/QueueViewModel.kt:127` | Reorder UX and guard rails are implemented with persistence handling. |
| #12 | Add QueueScreen instrumentation test for drag-drop | No evidence | - | No dedicated drag-drop instrumentation test found. |
| #13 | Validate playNext/addToQueue semantics under shuffle/repeat modes | Partial evidence | `app/src/main/java/com/example/tigerplayer/service/MediaControllerManager.kt:712`<br>`app/src/main/java/com/example/tigerplayer/service/MediaControllerManager.kt:896` | Queue semantics code exists; explicit validation matrix is not evident. |
| #14 | Tune infinite tail auto-append candidate ranking quality | Partial evidence | `app/src/main/java/com/example/tigerplayer/service/MediaControllerManager.kt:288`<br>`app/src/main/java/com/example/tigerplayer/data/local/dao/TigerDao.kt:338` | Ranking/auto-append logic exists; quality criteria are not documented. |
| #15 | Add Daylist/Vault empty-state and stale-data handling | Partial evidence | `app/src/main/java/com/example/tigerplayer/ui/dashboard/DashboardViewModel.kt:37`<br>`app/src/main/java/com/example/tigerplayer/data/local/dao/TigerDao.kt:411` | Stale-data logic exists; empty-state acceptance remains partially evidenced. |
| #16 | Build dashboard analytics cards for Daylist and Vault performance | Partial evidence | `app/src/main/java/com/example/tigerplayer/ui/home/HomeScreen.kt:232`<br>`app/src/main/java/com/example/tigerplayer/ui/dashboard/DashboardViewModel.kt:27` | Dashboard analytics are present; direct card-specific acceptance is partial. |
| #17 | Fine-tune Vinyl Warmth harmonic/noise-floor coefficients | Implemented evidence | `app/src/main/java/com/example/tigerplayer/engine/DspEngine.kt:135`<br>`app/src/main/java/com/example/tigerplayer/ui/equalizer/AcousticEnvironmentViewModel.kt:37` | Vinyl Warmth coefficient tuning is implemented. |
| #18 | Fine-tune Concert Hall reverb decay/predelay for mobile speakers | Implemented evidence | `app/src/main/java/com/example/tigerplayer/engine/DspEngine.kt:521`<br>`app/src/main/java/com/example/tigerplayer/engine/DspEngine.kt:574` | Concert Hall parameter tuning and processing are implemented. |
| #19 | Add DSP unit tests for AcousticEnvironment modes output safety | Partial evidence | `app/src/test/java/com/example/tigerplayer/engine/AdaptiveDspEngineTest.kt:20`<br>`app/src/main/java/com/example/tigerplayer/engine/DspEngine.kt:324` | DSP tests exist, but full mode-by-mode safety coverage is not explicit. |
| #20 | Persist Acoustic Environment mode across service restarts | Implemented evidence | `app/src/main/java/com/example/tigerplayer/service/AudioPlayerService.kt:108`<br>`app/src/main/java/com/example/tigerplayer/data/local/PlaybackPrefs.kt:49` | Restore/persist flow for acoustic mode is implemented. |
| #21 | Add Flow State user control for crossfade duration (0-12s) | Implemented evidence | `app/src/main/java/com/example/tigerplayer/data/local/SettingsDataStore.kt:67`<br>`app/src/main/java/com/example/tigerplayer/ui/settings/SettingScreen.kt:233` | 0-12s crossfade user control is implemented. |
| #22 | Improve crossfade conflict handling with manual seek/skip actions | Partial evidence | `app/src/main/java/com/example/tigerplayer/service/MediaControllerManager.kt:619`<br>`app/src/main/java/com/example/tigerplayer/service/MediaControllerManager.kt:929` | Conflict mitigation exists; broader regression validation is partial. |
| #23 | Add regression tests for volume restoration after crossfade disable | No evidence | - | No dedicated regression test found. |
| #25 | Add Sonic Footprint filter chips sorting/pinning preferences | No evidence | - | No sorting/pinning chip preference implementation found. |
| #26 | Improve Sonic axis classification heuristics from local metadata | Implemented evidence | `app/src/main/java/com/example/tigerplayer/ui/home/SonicFootprintViewModel.kt:93`<br>`app/src/main/java/com/example/tigerplayer/ui/home/SonicFootprintViewModel.kt:50` | Metadata-driven sonic-axis heuristics are implemented. |
| #27 | Implement MainActivity PiP auto-enter when leaving app during playback | Implemented evidence | `app/src/main/java/com/example/tigerplayer/MainActivity.kt:155`<br>`app/src/main/java/com/example/tigerplayer/MainActivity.kt:170` | PiP auto-enter behavior is implemented in activity lifecycle handling. |
| #28 | Add Sonic Footprint instrumentation test for empty/non-empty states | No evidence | - | No matching instrumentation test found. |
| #29 | Ensure PiP renders Fluid/Waveform visualizer without lifecycle glitches | Partial evidence | `app/src/main/java/com/example/tigerplayer/ui/player/PipVisualizerSurface.kt:52`<br>`app/src/main/java/com/example/tigerplayer/ui/player/TigerVortexRender.kt:40` | PiP rendering and cleanup exist; glitch-free lifecycle is not fully test-backed. |
| #30 | Add PiP behavior tests for pause/resume/skip/background transitions | No evidence | - | No dedicated PiP behavior test suite found. |
| #31 | Add AdaptiveDspEngine queueInput unit safety test suite | Partial evidence | `app/src/test/java/com/example/tigerplayer/engine/AdaptiveDspEngineTest.kt:20`<br>`app/src/main/java/com/example/tigerplayer/engine/DspEngine.kt:268` | Unit tests exist; complete safety suite breadth remains partial. |
| #32 | Add Room integrity test for PlaybackHistory/CachedTrack operations | Partial evidence | `app/src/androidTest/java/com/example/tigerplayer/data/local/TigerDaoIntegrityTest.kt:35`<br>`app/src/main/java/com/example/tigerplayer/data/local/entity/PlaybackHistoryEntity.kt` | Room integrity coverage exists but combined acceptance remains partial. |
| #33 | Add Compose UI test for FullPlayer play-pause state transitions | Implemented evidence | `app/src/androidTest/java/com/example/tigerplayer/ui/player/FullPlayerScreenTest.kt:26` | Compose play/pause transition test is present. |
| #34 | Add Compose UI test for FullPlayer play-pause state transitions | Implemented evidence | `app/src/androidTest/java/com/example/tigerplayer/ui/player/FullPlayerScreenTest.kt:26` | Duplicate of #33; evidence is the same test file. |
| #35 | Integrate LeakCanary for debug-only memory leak monitoring | Implemented evidence | `app/src/main/java/com/example/tigerplayer/TigerPlayerApplication.kt:13`<br>`app/src/debug/java/com/example/tigerplayer/debug/LeakCanaryInitializer.kt:6` | Debug-only LeakCanary integration is present. |
| #36 | Enable StrictMode policies for main-thread I/O/network detection | Implemented evidence | `app/src/main/java/com/example/tigerplayer/TigerPlayerApplication.kt:20`<br>`app/src/main/java/com/example/tigerplayer/TigerPlayerApplication.kt:29` | StrictMode thread/vm policies are enabled. |
| #37 | Add Macrobenchmark for QueueScreen scroll/frame timing | Implemented evidence | `app/src/androidTest/java/com/example/tigerplayer/benchmark/QueueScreenMacrobenchmark.kt:25`<br>`app/src/androidTest/java/com/example/tigerplayer/benchmark/QueueScreenMacrobenchmark.kt:49` | Queue screen macrobenchmark exists and measures frame timing. |
| #38 | Finalize proguard-rules for Media3/Coil/Retrofit-Gson/Room | Implemented evidence | `app/proguard-rules.pro:7`<br>`app/build.gradle.kts:66` | Proguard rules and release wiring are present. |
| #39 | Finalize proguard-rules for Media3/Coil/Retrofit-Gson/Room | Implemented evidence | `app/proguard-rules.pro:7`<br>`app/build.gradle.kts:66` | Duplicate of #38; same evidence applies. |

## Notes

- Commit subjects do not consistently reference issue IDs (`fixes #...`), so direct GitHub issue mapping cannot be inferred from commit messages alone.
- GitHub API issue enumeration was attempted from this environment but hit API/rate/SSL constraints, so this review is based on repository-local evidence.

