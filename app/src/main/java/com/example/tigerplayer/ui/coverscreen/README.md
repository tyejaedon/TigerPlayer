# Cover Screen Mode

This module enables TigerPlayer on flip-phone outer displays with gesture-first controls.

## Components

- `CoverScreenMiniHub.kt`
  - `rememberCoverScreenWindowState()` uses `WindowInfoTracker` + viewport heuristic.
  - `CoverScreenMiniHub(...)` swaps the normal app shell with a compact gesture UI.
  - `isCoverScreenHeuristic(...)` keeps detection testable.

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

