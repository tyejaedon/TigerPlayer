# Motorola External-Display Verification Log (issue #124)

> Tracks whether the Motorola external-display manifest meta-data on `MainActivity` actually
> produces correct `displayId`-aware behavior, per `docs/CoverScreen-DisplayId-Review.md`
> section 2 pitfall 7 and the "Cover Screen Correctness" milestone.

## What the manifest declares

```xml
<meta-data android:name="com.motorola.android.externaldisplay.enabled" android:value="true" />
<meta-data android:name="com.motorola.android.externaldisplay.category" android:value="media" />
```

This advertises eligibility for Motorola's true-secondary-display model: the cover panel is a
distinct `Display` with its own non-zero `displayId`, not merely a resized window on the default
display.

## Code path that now backs it

Before this milestone, nothing in the codebase ever inspected `displayId`, so this meta-data was
purely decorative - the app had no way to know it had been placed on a genuine secondary display,
regardless of what the OS did. That gap is closed as of:

- **#121** - `rememberCoverScreenWindowState()` resolves the hosting `Activity`'s `displayId`
  (`Context.display?.displayId` on API 30+, `windowManager.defaultDisplay.displayId` below) and
  `isSecondaryDisplayIdentity()` treats any non-default value as an authoritative cover-screen
  signal, independent of dp size.
- **#123** - a `DisplayManager.DisplayListener` is registered so a display attaching/detaching at
  runtime (the Motorola panel connecting/disconnecting) re-resolves `displayId` rather than
  leaving a stale value cached for the lifetime of the composition.
- **#126** - `CoverScreenWindowStateInstrumentedTest` asserts the `displayId`/`isSecondaryDisplay`
  read is correct against the real `DisplayManager` state of the hosting test `Activity`.

So the meta-data is no longer disconnected from the rest of the app: **if** the OS places
`MainActivity` (or a window backed by the same Compose tree) on a `Display` with
`displayId != Display.DEFAULT_DISPLAY`, `CoverScreenWindowState.isCoverScreen` will now report
`true` and `MainScreen` will mount `CoverScreenMiniHub` there (issue #120), regardless of that
panel's physical dp size.

## What has *not* been verified

Nothing in this repository's CI or local development environment includes a physical Motorola
external-display device, and standard Android emulator images do not simulate the
`com.motorola.android.externaldisplay.*` vendor extension or a genuine second `Display` the way
that hardware does. That means the following remain **unverified**:

1. Whether the Motorola OS actually honors the meta-data and routes the activity/window to the
   external panel's `Display` in the first place (this is OS/vendor behavior outside this app's
   control).
2. Whether `Context.display?.displayId` (or the pre-API-30 fallback) reports a non-default value
   once that routing happens, on real Motorola firmware.
3. End-to-end behavior on the physical panel: correct `CoverScreenMiniHub` layout, touch input,
   and attach/detach handling (#123) when the accessory is physically connected/disconnected.

## Manual verification protocol (for whoever has the hardware)

1. Install a debug build on a Motorola device that supports an external display accessory
   (or the external-display dock/dongle itself, per Motorola's developer documentation for
   `com.motorola.android.externaldisplay`).
2. Connect the external display and launch/route TigerPlayer to it per Motorola's standard flow
   for apps declaring this meta-data.
3. Confirm via `adb shell dumpsys window displays` (or logcat around
   `rememberCoverScreenWindowState`) that the hosting window's `displayId` is non-zero.
4. Confirm `CoverScreenMiniHub` renders on the panel (not the full app shell) and responds to the
   documented gestures.
5. Disconnect/reconnect the panel and confirm the app does not crash and does not leak a
   `DisplayManager.DisplayListener` (issue #123) - check for `CoverScreenMiniHub` in `Log.e`
   output for any registration/teardown failures logged under tag `"CoverScreenMiniHub"`.
6. Record the outcome (pass/fail, device model, OS version) as a comment on issue #124 and update
   the **Status** line below.

## Status

**Unverified on physical hardware as of this milestone.** The `displayId`-aware code path exists
and is unit/instrumented-tested against the *default* display (see #126); the Motorola-specific
end-to-end path requires the manual protocol above on real hardware before it can be marked
verified. The meta-data has not been removed, since it is no longer decorative - it is backed by
a real, testable code path that will react correctly if the OS delivers a non-default `displayId`.
