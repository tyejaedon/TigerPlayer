# Cover Screen Mode

This module enables TigerPlayer on flip-phone outer displays with gesture-first controls.

## Two device models - resize vs. true secondary display

Cover/outer displays are exposed to an app in two fundamentally different ways. Detection must
pick the right signal for each; relying on dp size alone (the original implementation) cannot
tell them apart. See `docs/CoverScreen-DisplayId-Review.md` for the full analysis this milestone
was based on.

| | Resize model | True secondary-display model |
|---|---|---|
| Example devices | Samsung Galaxy Z Flip/Fold | Motorola external displays, dual-screen/multi-display accessories |
| How it's exposed | Same `Activity`, same `displayId` (`Display.DEFAULT_DISPLAY`). The OS just delivers a smaller `Configuration` when the device is closed. There is no second `Display` object. | The cover panel is a distinct `Display` with its own non-zero `displayId`, discoverable via `DisplayManager` and requiring explicit targeting (`ActivityOptions.setLaunchDisplayId`, `Context.createDisplayContext`). |
| Authoritative signal | The dp-size + hinge heuristic (`isCoverScreenHeuristic` + `FoldingFeature.isSeparating`) - there is no `displayId` to check, so this is the *only* signal available. | `displayId` identity (`isSecondaryDisplayIdentity`) - a non-default `displayId` is unambiguous proof of a genuine secondary display, and is trusted over dp size. |
| Runtime changes | Configuration changes drive recomposition automatically. | Requires a `DisplayManager.DisplayListener` (attach/detach/change) since the panel can connect/disconnect independently of this window's own `Configuration`. |

`resolveIsCoverScreen()` combines both: `isSecondaryDisplay` wins outright when true (true
secondary-display model), otherwise the dp/hinge heuristic decides (resize model) - after first
rejecting ordinary user-resized multi-window states (split-screen, freeform/desktop-mode,
Samsung DeX pop-up view) that would otherwise produce false positives in the same dp band.

Motorola external-display support is advertised via manifest meta-data
(`com.motorola.android.externaldisplay.*`) but is unverified on physical hardware - see
`docs/CoverScreen-Motorola-Verification.md`.

## Components

- `CoverScreenMiniHub.kt`
  - `rememberCoverScreenWindowState()` combines a `WindowInfoTracker`-driven hinge/viewport
    heuristic with a `displayId`/`DisplayManager` identity check, and registers a
    `DisplayManager.DisplayListener` to react to displays attaching/detaching at runtime.
  - `CoverScreenMiniHub(...)` swaps the normal app shell with a compact gesture UI. It is mounted
    by `MainScreen` whenever `CoverScreenWindowState.isCoverScreen` is true.
  - The cover-screen stats and Day List / Discover Weekly suggestions reuse the `PlayerViewModel`
    and `DashboardViewModel` state consumed by Home. The suggestion row lazily loads artwork,
    supports play and queue actions, and keeps horizontal browsing separate from player gestures.
  - `isCoverScreenHeuristic(...)`, `isSecondaryDisplayIdentity(...)`, and `resolveIsCoverScreen(...)`
    keep each detection signal - and how they combine - independently testable.
- `CoverScreenLibraryMenu.kt`
  - Modal library navigation sheet presenting tabbed categories (Artists, Albums, Songs, Playlists, Spotify Playlists)
    and integrated search with voice search capabilities.
- `CoverScreenAlbumsView.kt`
  - Focused album details and track list view with one-tap play-all and queue manipulation.
- `CoverScreenPerformanceManager.kt`
  - Frame rate limiter (60 FPS cap), animation throttling, lifecycle-aware rendering state management,
    and zero-allocation path generation for canvas drawing.

## Gesture Map

- Single tap: Play / Pause
- Swipe left: Next track
- Swipe right: Previous track
- Swipe up: Open queue sheet
- Swipe down: Close active sheet (queue / library) or open search
- Long-press play/pause: Open playback settings for shuffle, repeat, swipe volume, and EQ presets
- Horizontal swipe on the discovery row: Browse suggestions without skipping the playing track

## Accessibility & TalkBack Support

The cover screen subsystem is engineered for WCAG AA accessibility compliance:

- **Full TalkBack Support**:
  - The root `CoverScreenMiniHub` node exposes custom accessibility actions (`customActions`) for all primary
    gestures:
    1. Tap to play or pause (`coverscreen_gesture_tap_play_pause`)
    2. Swipe next (`coverscreen_gesture_swipe_next`)
    3. Swipe previous (`coverscreen_gesture_swipe_prev`)
    4. Swipe up for queue (`coverscreen_gesture_swipe_up_queue`)
    5. Swipe down to close (`coverscreen_gesture_swipe_down_close`)
    6. Long press for playback settings (`coverscreen_gesture_long_press_settings`)
  - Screen reader announcements summarize available gestures (`coverscreen_gesture_instructions`) and report dynamic
    playback state via `stateDescription` ("NOW PLAYING" vs "PAUSED").
- **Touch Target Dimensions**:
  - All interactive buttons and list rows meet or exceed the 48x48 dp minimum touch target guideline
    (`minimumInteractiveComponentSize()`, `defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`).
  - Adaptive visual sizing on narrow cover displays (200-260 dp) scales visual icons down to 34-40 dp while preserving
    full 48 dp interactive touch targets through proportional weight distribution.
- **Visual Contrast & Hierarchy**:
  - Text contrast ratios exceed WCAG AA requirements (>4.5:1), including status subtitles, metadata text, and empty states.
- **Semantics & Actions**:
  - Explicit roles (`Role.Button`), `onClickLabel`, and `onLongClickLabel` semantics are declared across all controls,
    tiles, and list rows.

## Localization

Cover screen UI strings are fully localized across 5 languages with verified 1:1 key parity:

1. English (`app/src/main/res/values/strings.xml`)
2. Spanish (`app/src/main/res/values-es/strings.xml`)
3. French (`app/src/main/res/values-fr/strings.xml`)
4. German (`app/src/main/res/values-de/strings.xml`)
5. Japanese (`app/src/main/res/values-ja/strings.xml`)

Localized string resources encompass:
- Gesture hints and composite instructions
- Playback controls (previous, play, pause, next, queue, library, play all, remove)
- Status subtitles (now playing, paused, flex mode active, no lyrics found)
- Tab titles and empty state messages (artists, albums, songs, playlists, spotify playlists, auth required)
- Source selectors (local, Spotify) and voice search prompts

## Visuals

- Blurred artwork background + `TigerSurfaceCharcoal` overlay
- Center title/artist with neon glow
- 10dp bottom micro-waveform driven by `audioReactiveFrame`

## Performance & Battery Optimization (Phase 4.4 / Issue #232)

See `docs/CoverScreen-Performance-Battery-Audit.md` for full benchmark metrics and recomposition analysis.

- **60 FPS Animation Cap & Throttling**:
  - `CoverScreenPerformanceManager` enforces a 60 FPS frame rate limit (16.6ms intervals) on both Motorola Razr and Samsung Galaxy Z Flip panels.
  - Infinite transitions and energy animations in `CoverMicroWaveform` are conditionally composed: when audio is paused (`!isPlaying`) or when the screen is inactive, animations are frozen (`phase = 0f`, `animatedEnergy = 0.2f`), eliminating Choreographer frame ticks.
- **Zero-Allocation Canvas Drawing**:
  - `populateWaveformPath` reuses a persistent `Path` object across frames (`path.reset()`), eliminating 60 heap allocations per second.
  - Waveform sampling downsamples inputs to primitive `FloatArray` (32 points), avoiding boxed `Float` iterators.
  - Input sanitization protects canvas routines from `NaN`, `Float.POSITIVE_INFINITY`, and `Float.NEGATIVE_INFINITY`.
  - In `CoverScreenStatsWidget`, daily play counts are cached via `remember` and `CornerRadius` instances are pre-calculated outside draw loops.
- **Lifecycle & Modal Overlay Gating**:
  - Observes `LocalLifecycleOwner` via `LifecycleEventObserver`. On `ON_PAUSE`, `ON_STOP`, or `ON_DESTROY`, rendering is paused.
  - When modal sheets (`queueVisible`, `libraryVisible`, `playbackSettingsVisible`) are open, background waveforms are unmounted to conserve GPU cycles.
- **Battery Impact**:
  - Active playback battery consumption is reduced to **<5% vs main display** (~3.2% measured delta).
  - CPU utilization when paused drops from 18.4% to 2.3%.

## Verify

```zsh
cd "/Users/tyejaedon/StudioProjects/TigerPlayer"
./gradlew :app:lintFullDebug
./gradlew :app:testFullDebugUnitTest
./gradlew :app:assembleFullDebug
```
