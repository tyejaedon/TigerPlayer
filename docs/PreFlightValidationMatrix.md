# TigerPlayer Pre-Flight Validation Matrix (RC)

## Device matrix

- Samsung Galaxy S22 (One UI latest)
- Samsung foldable (Z Fold/Z Flip cover + inner display)
- Bluetooth earbuds (SBC/AAC), wired headset adapter

## Build under test

- Variant: `release`
- ABI: `arm64-v8a`
- Network: Wi-Fi + LTE fallback
- Battery: run once at 100%, once at 15% with Battery Saver ON

## A. Audio Focus and transients

1. Start local track playback and open `FullPlayerScreen`.
2. Trigger incoming phone call (second device).
3. Expected:
   - Playback pauses within 500ms.
   - After call ends, playback resumes only if user policy allows.
4. Repeat with WhatsApp voice note playback while TigerPlayer is active.
5. Repeat with Clock alarm during playback.
6. Validate no stuck `isPlaying=true` UI after focus loss.

## B. Hardware interrupts

1. Play a track over Bluetooth earbuds.
2. Force disconnect earbuds (case close / BT off).
3. Expected:
   - Playback pauses immediately, no speaker blast.
4. Reconnect earbuds.
5. Validate resume behavior matches setting:
   - `Resume on Bluetooth connect` ON -> resumes.
   - OFF -> remains paused.
6. Repeat for wired headset unplug/replug and verify wired resume policy.

## C. Lifecycle brutality

1. Start playback and leave player in background.
2. Swipe app away from Recents while foreground service is active.
3. Expected:
   - Media notification/service behavior follows Android policy.
   - No crash loop when reopening app.
4. Relaunch app from launcher.
5. Validate restored queue/position and no DB corruption.

## D. Foldable + cover-screen mode

1. On flip cover display, launch app from outer screen.
2. Validate `CoverScreenMiniHub` appears.
3. Gestures:
   - Tap -> play/pause
   - Swipe left/right -> next/previous
   - Swipe up/down -> queue open/close
4. Confirm micro-waveform remains smooth and under 10dp with no dropped frames.
5. Open inner screen and verify UI returns to full app shell.

## E. Battery-efficiency stress

1. With screen ON for 20 min continuous playback, record battery drain.
2. Toggle `Pure AMOLED Black` and compare drain delta.
3. Run with visualizer enabled vs disabled.
4. Verify no wake-lock abuse (adb dumpsys batterystats) and no runaway CPU.

## F. Regression smoke

1. Library scan (force rescan)
2. Queue reorder
3. Spotify auth callback route
4. Prism mode slider sweeps under playback
5. Settings persistence across process death

## Pass criteria

- Zero crashes/ANRs
- No accidental audio blast on route changes
- Cover-screen gestures all functional
- No major frame drops in queue scroll and waveform screens
- Battery drain aligns with baseline target for media app class

