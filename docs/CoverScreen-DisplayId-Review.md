# Cover Screen Optimization Review — displayId Gap Analysis

> Generated from a September 2026 targeted audit of the flip-phone / outer-display ("cover screen")
> feature ahead of a proper multi-display implementation. Companion to `Roadmap-v2.1.1-v2.3.md`.

## 1. What exists today

| File | Role |
|---|---|
| `ui/coverscreen/CoverScreenMiniHub.kt` | Heuristic detection (`isCoverScreenHeuristic`), `rememberCoverScreenWindowState()`, and a full gesture-driven mini-hub composable |
| `ui/coverscreen/README.md` | Feature doc |
| `ui/main/MainScreen.kt` | Consumes `rememberCoverScreenWindowState()` to swap nav presets and pass `isCoverOptimized` downstream |
| `ui/player/FullPlayerScreen.kt` | Reads `windowState.isCoverScreen` independently to trim motion/waveform width |
| `ui/player/PlayerViewModel.kt` | Forces `WAVEFORM` visual mode instead of `VORTEX` when cover-optimized |
| `AndroidManifest.xml` (`MainActivity`) | `resizeableActivity="true"`, `configChanges` includes `screenLayout|screenSize|smallestScreenSize|density`, plus Samsung/Motorola vendor `<meta-data>` hints |
| `test/.../CoverScreenHeuristicTest.kt` | Unit tests for the pure dp-heuristic function only |

Detection is **entirely** based on:
1. `Configuration.screenWidthDp` / `screenHeightDp` compared against a hardcoded dp window (`220..399` short edge, `≤450` long edge, aspect ratio `≤1.35`).
2. `androidx.window.layout.FoldingFeature.isSeparating`, to distinguish "cover screen" from "unfolded with a hinge."

**There is zero use of `displayId`, `DisplayManager`, `Display`, `Context.display`, or `ActivityOptions.setLaunchDisplayId` anywhere in the codebase.**

## 2. Why that matters — the displayId gap

Android foldables/flip phones expose the cover screen to an app in **two fundamentally different ways**, and the current implementation only accounts for one of them, informally:

- **Resize model (Samsung Galaxy Z Flip/Fold, most OEMs):** the *same* `Activity`, on the *same logical display* (`DEFAULT_DISPLAY`, `displayId == 0`), is simply given a smaller `Configuration` when the device is closed. There is no second `Display` object. Detecting this by dp-size heuristic is a plausible (if fragile) proxy, because there genuinely is no `displayId` to check.
- **True secondary-display model (Motorola external displays, some dual-screen/multi-display accessories, Android Desktop/DeX external monitors, Android Auto, cast targets):** the cover panel is a **distinct `Display`** with its own non-zero `displayId`, discoverable via `DisplayManager.getDisplays()` and reachable via `ActivityOptions.setLaunchDisplayId(id)` / `Context.createDisplayContext(display)`. Content does not "flow" there automatically — it must be explicitly targeted, and the receiving side needs its own `Activity`/`Presentation`/window that queries `Context.display?.displayId` (or `Display.DEFAULT_DISPLAY` comparison) to know it *is* the secondary panel.

The manifest already declares `com.motorola.android.externaldisplay.enabled` / `.category` meta-data — i.e., the project is already advertising Motorola external-display eligibility — **but nothing in the code ever inspects `displayId` to confirm which physical panel it is actually running on.** That meta-data is presently decorative: it does not, by itself, cause TigerPlayer's UI to behave correctly if the OS does place the activity/window on a genuine secondary `Display`.

### Concrete pitfalls this produces

1. **False positives from any small/resized window, not just a cover screen.** `isCoverScreenHeuristic()` fires for *any* window in the `220–399 × ≤450` dp band: split-screen multi-window on a normal phone or tablet, freeform/desktop-mode floating windows, Samsung DeX pop-up view, or a developer forcing `resizeableActivity` in a small window. All of these get the cover-screen UI (reduced nav, forced WAVEFORM mode, gesture-only mini-hub) even though the user is on a full, normal display. There is no check for "is this actually the physical outer panel" (which `displayId` plus device posture/state APIs could answer).
2. **No signal for genuinely separate displays.** On a device where the cover panel *is* a distinct `Display` (the Motorola case the manifest claims to support), `Configuration.screenWidthDp/heightDp` read from the `Activity`'s primary window tells you nothing about a second `Display` that isn't hosting that window at all. The dp heuristic simply cannot detect this class of device; the feature would silently do nothing there, contradicting the manifest's advertised support.
3. **Hardcoded dp band will drift as new devices ship.** The `220..399` / `≤450` / `1.35` constants are tuned to specific known cover-screen dimensions (Z Flip family). Newer or third-party cover displays (different aspect ratios, e.g. taller/narrower panels) fall outside the band and get missed; slightly larger unfolded-but-narrow multi-window states could fall inside it and get falsely flagged. A `displayId`-based check (matching against `DisplayManager`'s reported non-default displays, or a device-posture/state API) is dimension-agnostic and would not need retuning per device generation.
4. **`CoverScreenMiniHub` composable is fully implemented but never wired into the UI tree.** `MainScreen.kt` only reads `windowState.isCoverScreen` to pick a `MainNavigationPresets` variant and a flag on `FullPlayerScreen`/`PlayerViewModel`; the dedicated gesture-first `CoverScreenMiniHub` (with its edge-exclusion gestures, hinge-aware flex layout, queue sheet, micro-waveform) is dead code — grep confirms it has exactly one reference in the whole `app/src` tree (its own definition). On a real cover screen, users still get the **full app shell** (Scaffold, bottom `NavigationBar`, tab content) squeezed into ~1.9–3.4", not the purpose-built hub. This is the most user-visible gap and should be treated as a functional regression, not a polish item.
5. **No instrumented/behavioral test for the composable path.** `CoverScreenHeuristicTest.kt` only tests the pure `isCoverScreenHeuristic(width, height)` function. There is no test exercising `rememberCoverScreenWindowState()` against a fake/mock `WindowInfoTracker`, no test asserting `CoverScreenMiniHub` actually renders, and — because it's unreferenced — no test could catch that it isn't reachable.
6. **No `DisplayManager.DisplayListener` / lifecycle handling for display attach-detach.** Real external-display devices can attach/detach the secondary panel at runtime (folding, docking, disconnecting). Nothing in `MainActivity` or the DI graph listens for `DisplayManager.ACTION_DISPLAY_ADDED/REMOVED/CHANGED` or reacts to a `Presentation` needing teardown — a requirement for the true secondary-display model per the audio-playback/lifecycle guidelines (leaked receivers/listeners are already a known class of bug in this codebase per issue #48).
7. **Manifest hints are not backed by an explicit `displayId`-aware launch path.** For Motorola-style true external displays, correct behavior typically requires either: (a) explicitly targeting the display via `ActivityOptions.setLaunchDisplayId` from a companion component, or (b) verified `Configuration`/`Display` reads inside the receiving window to branch UI. Right now the meta-data is set, but if the OS *does* route the activity to a second `Display`, the app has no way to detect `displayId != Display.DEFAULT_DISPLAY` and would misapply the same dp-only heuristic — which may or may not coincidentally match, depending on the panel's physical size.

## 3. What "proper" looks like

A correct implementation needs **two independent, composable signals**, not one:

- **Display identity** — `Context.display?.displayId` (API 30+; fall back to `windowManager.defaultDisplay.displayId` pre-30) compared against `DisplayManager.getDisplays()` to know definitively whether the current window is on the default display or a distinct physical panel, and to react to displays attaching/detaching.
- **Posture / size heuristic** — the existing `FoldingFeature` + dp-band check, kept as a *secondary* signal for the resize-model devices where no second `displayId` exists at all.

The two should be OR'd/branched explicitly, and the false-positive surface (split-screen, freeform, DeX) should be excluded by also checking `isInMultiWindowMode` / windowing mode where the small size is a user resize choice rather than a real device posture.

And the mini-hub UI has to actually be mounted for a cover-mode signal to mean anything.

---

## 4. Milestone

### Milestone: "Cover Screen Correctness" — target v2.1.2, due 2026-11-30

**Theme:** the cover-screen feature currently ships UI and tests that never execute in production and a
detection strategy that cannot distinguish a real secondary display from an ordinary resized window.
Nothing here should ship as "done" until the mini-hub is reachable and detection is displayId-aware.

**Exit criteria:**
- `CoverScreenMiniHub` is mounted and reachable on at least one verified physical cover-screen device.
- Detection combines `displayId`/`DisplayManager` identity with the existing dp/hinge heuristic, and rejects ordinary split-screen/freeform/DeX resizes.
- Display attach/detach is handled without leaking listeners (mirrors the receiver-teardown discipline already required by `audio-playback.instructions.md`).
- New tests cover the previously-untested composable/state-holder path, and a regression test locks in that split-screen does not trigger cover mode.

---

## 5. Issue list

| # | Title | Priority | Area |
|---|---|---|---|
| [#120](https://github.com/tyejaedon/TigerPlayer/issues/120) | `CoverScreenMiniHub` is never mounted — cover-screen users see the full app shell, not the mini hub | P0 | ui/coverscreen |
| [#121](https://github.com/tyejaedon/TigerPlayer/issues/121) | Add `displayId`/`DisplayManager` identity check alongside the dp-size heuristic | P0 | ui/coverscreen |
| [#122](https://github.com/tyejaedon/TigerPlayer/issues/122) | Cover-screen heuristic produces false positives in split-screen / freeform / DeX pop-up view | P1 | ui/coverscreen |
| [#123](https://github.com/tyejaedon/TigerPlayer/issues/123) | No handling for display attach/detach (`DisplayManager.DisplayListener`) — risk of stale UI or leaked listeners | P1 | ui/coverscreen, lifecycle |
| [#124](https://github.com/tyejaedon/TigerPlayer/issues/124) | Motorola external-display manifest meta-data is unverified — no `displayId`-aware code path confirms it does anything | P1 | ui/coverscreen, build |
| [#125](https://github.com/tyejaedon/TigerPlayer/issues/125) | Hardcoded cover-screen dp band (`220..399`/`≤450`/aspect `≤1.35`) will miss non-Z-Flip cover panels | P2 | ui/coverscreen |
| [#126](https://github.com/tyejaedon/TigerPlayer/issues/126) | Add instrumented test for `rememberCoverScreenWindowState()` against a fake `WindowInfoTracker`/`DisplayManager` | P1 | testing |
| [#127](https://github.com/tyejaedon/TigerPlayer/issues/127) | Add regression test asserting `CoverScreenMiniHub` is actually composed when cover state is true | P1 | testing |
| [#128](https://github.com/tyejaedon/TigerPlayer/issues/128) | Document the resize-model vs. true-secondary-display model distinction in `ui/coverscreen/README.md` | P2 | docs |

### Suggested sequencing

```
#121 displayId identity check ──┬─> #122 reject split-screen/freeform false positives
                                 └─> #124 verify Motorola external-display path
#120 mount CoverScreenMiniHub ───┬─> #127 regression test for mounting
                                 └─> #123 display attach/detach lifecycle
#121 + #120 ─────────────────────> #126 instrumented state-holder test
#121 ────────────────────────────> #125 replace/augment hardcoded dp band
#120 + #121 ──────────────────────> #128 README update
```

**Critical path:** `#121` (displayId identity) and `#120` (mount the hub) gate almost everything else —
without them, all other work is polishing a feature that either can't tell what display it's on, or
never renders regardless.

**Tracking:** [GitHub Milestone "Cover Screen Correctness (v2.1.2)"](https://github.com/tyejaedon/TigerPlayer/milestone/10)

