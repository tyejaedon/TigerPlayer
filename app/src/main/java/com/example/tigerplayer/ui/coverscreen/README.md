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
  - `isCoverScreenHeuristic(...)`, `isSecondaryDisplayIdentity(...)`, and `resolveIsCoverScreen(...)`
    keep each detection signal - and how they combine - independently testable.

## Gesture map

- Single tap: play/pause
- Swipe left: next track
- Swipe right: previous track
- Swipe up: open queue sheet
- Swipe down: close queue sheet

## Visuals

- Blurred artwork background + `TigerSurfaceCharcoal` overlay
- Center title/artist with neon glow
- 10dp bottom micro-waveform driven by `audioReactiveFrame`

## Verify

```zsh
cd "/Users/tyejaedon/StudioProjects/TigerPlayer"
./gradlew :app:testDebugUnitTest
```


