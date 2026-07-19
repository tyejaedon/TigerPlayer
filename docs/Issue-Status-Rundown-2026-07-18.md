# TigerPlayer Issue Status Rundown (2026-07-18)

Source: user-provided GitHub issue board snapshot.

## Snapshot Summary

- Total tracked issues: **35**
- Done: **16** (45.7%)
- In Progress: **3** (8.6%)
- Todo: **16** (45.7%)

## Status Breakdown

### Done (16)
- #4 Audit all artwork-driven screens for neon quantization consistency
- #5 Enforce snapped neon ambient gradients across player/library surfaces
- #6 Implement vorticity confinement pass in fluid simulation pipeline
- #10 Profile OpenGL shader passes and reduce mobile GPU branching
- #13 Validate playNext/addToQueue semantics under shuffle/repeat modes
- #14 Tune infinite tail auto-append candidate ranking quality
- #19 Add DSP unit tests for AcousticEnvironment modes output safety
- #21 Add Flow State user control for crossfade duration (0-12s)
- #22 Improve crossfade conflict handling with manual seek/skip actions
- #25 Add Sonic Footprint filter chips sorting/pinning preferences
- #27 Implement MainActivity PiP auto-enter when leaving app during playback
- #30 Add PiP behavior tests for pause/resume/skip/background transitions
- #32 Add Room integrity test for PlaybackHistory/CachedTrack operations
- #33 Add Compose UI test for FullPlayer play-pause state transitions
- #38 Finalize proguard-rules for Media3/Coil/Retrofit-Gson/Room
- #39 Finalize proguard-rules for Media3/Coil/Retrofit-Gson/Room (duplicate tracker)

### In Progress (3)
- #7 Map 6 FFT perceptual bands to multi-band chromatic fluid injection
- #9 Add kick-drum advection pulse dissipation behavior
- #16 Build dashboard analytics cards for Daylist and Vault performance

### Todo (16)
- #8 Add lightweight half-float bloom pass for high-energy glow
- #11 Harden QueueScreen reorder UX and persistence edge cases
- #12 Add QueueScreen instrumentation test for drag-drop
- #15 Add Daylist/Vault empty-state and stale-data handling
- #17 Fine-tune Vinyl Warmth harmonic/noise-floor coefficients
- #18 Fine-tune Concert Hall reverb decay/predelay for mobile speakers
- #20 Persist Acoustic Environment mode across service restarts
- #23 Add regression tests for volume restoration after crossfade disable
- #26 Improve Sonic axis classification heuristics from local metadata
- #28 Add Sonic Footprint instrumentation test for empty/non-empty states
- #29 Ensure PiP renders Fluid/Waveform visualizer without lifecycle glitches
- #31 Add AdaptiveDspEngine queueInput unit safety test suite
- #34 Add Compose UI test for FullPlayer play-pause state transitions (duplicate tracker)
- #35 Integrate LeakCanary for debug-only memory leak monitoring
- #36 Enable StrictMode policies for main-thread I/O/network detection
- #37 Add Macro benchmark for QueueScreen scroll/frame timing

## Milestone Rollup

| Milestone | Done | In Progress | Todo | Total |
|---|---:|---:|---:|---:|
| Neon + Visual Core | 4 | 2 | 1 | 7 |
| Queue + Daylist | 2 | 1 | 3 | 6 |
| Apex DSP + Crossfade | 3 | 0 | 4 | 7 |
| Sonic Footprint + PiP | 3 | 0 | 3 | 6 |
| QA + Release Hardening | 4 | 0 | 5 | 9 |

## Full Issue Table

|  # | Title                                                                   | URL                                                | Assignees | Status      | Linked PRs | Sub-issues | Comments/Notes                 | Milestone              |
|---:|-------------------------------------------------------------------------|----------------------------------------------------|-----------|-------------|------------|------------|--------------------------------|------------------------|
|  4 | Audit all artwork-driven screens for neon quantization consistency      | https://github.com/tyejaedon/TigerPlayer/issues/4  | tyejaedon | Done        | -          | -          | Completed per board snapshot   | Neon + Visual Core     |
|  5 | Enforce snapped neon ambient gradients across player/library surfaces   | https://github.com/tyejaedon/TigerPlayer/issues/5  | -         | Done        | -          | -          | Completed per board snapshot   | Neon + Visual Core     |
|  6 | Implement vorticity confinement pass in fluid simulation pipeline       | https://github.com/tyejaedon/TigerPlayer/issues/6  | -         | Done        | -          | -          | Completed per board snapshot   | Neon + Visual Core     |
|  7 | Map 6 FFT perceptual bands to multi-band chromatic fluid injection      | https://github.com/tyejaedon/TigerPlayer/issues/7  | -         | In Progress | -          | -          | Active work in progress        | Neon + Visual Core     |
|  8 | Add lightweight half-float bloom pass for high-energy glow              | https://github.com/tyejaedon/TigerPlayer/issues/8  | tyejaedon | Todo        | -          | -          | Not started                    | Neon + Visual Core     |
|  9 | Add kick-drum advection pulse dissipation behavior                      | https://github.com/tyejaedon/TigerPlayer/issues/9  | -         | In Progress | -          | -          | Active work in progress        | Neon + Visual Core     |
| 10 | Profile OpenGL shader passes and reduce mobile GPU branching            | https://github.com/tyejaedon/TigerPlayer/issues/10 | -         | Done        | -          | -          | Completed per board snapshot   | Neon + Visual Core     |
| 11 | Harden QueueScreen reorder UX and persistence edge cases                | https://github.com/tyejaedon/TigerPlayer/issues/11 | tyejaedon | Todo        | -          | -          | Not started                    | Queue + Daylist        |
| 12 | Add QueueScreen instrumentation test for drag-drop                      | https://github.com/tyejaedon/TigerPlayer/issues/12 | tyejaedon | Todo        | -          | -          | Not started                    | Queue + Daylist        |
| 13 | Validate playNext/addToQueue semantics under shuffle/repeat modes       | https://github.com/tyejaedon/TigerPlayer/issues/13 | tyejaedon | Done        | -          | -          | Completed per board snapshot   | Queue + Daylist        |
| 14 | Tune infinite tail auto-append candidate ranking quality                | https://github.com/tyejaedon/TigerPlayer/issues/14 | tyejaedon | Done        | -          | -          | Completed per board snapshot   | Queue + Daylist        |
| 15 | Add Daylist/Vault empty-state and stale-data handling                   | https://github.com/tyejaedon/TigerPlayer/issues/15 | tyejaedon | Todo        | -          | -          | Not started                    | Queue + Daylist        |
| 16 | Build dashboard analytics cards for Daylist and Vault performance       | https://github.com/tyejaedon/TigerPlayer/issues/16 | tyejaedon | In Progress | -          | -          | Active work in progress        | Queue + Daylist        |
| 17 | Fine-tune Vinyl Warmth harmonic/noise-floor coefficients                | https://github.com/tyejaedon/TigerPlayer/issues/17 | tyejaedon | Todo        | -          | -          | Not started                    | Apex DSP + Crossfade   |
| 18 | Fine-tune Concert Hall reverb decay/predelay for mobile speakers        | https://github.com/tyejaedon/TigerPlayer/issues/18 | tyejaedon | Todo        | -          | -          | Not started                    | Apex DSP + Crossfade   |
| 19 | Add DSP unit tests for AcousticEnvironment modes output safety          | https://github.com/tyejaedon/TigerPlayer/issues/19 | tyejaedon | Done        | -          | -          | Completed per board snapshot   | Apex DSP + Crossfade   |
| 20 | Persist Acoustic Environment mode across service restarts               | https://github.com/tyejaedon/TigerPlayer/issues/20 | tyejaedon | Todo        | -          | -          | Not started                    | Apex DSP + Crossfade   |
| 21 | Add Flow State user control for crossfade duration (0-12s)              | https://github.com/tyejaedon/TigerPlayer/issues/21 | tyejaedon | Done        | -          | -          | Completed per board snapshot   | Apex DSP + Crossfade   |
| 22 | Improve crossfade conflict handling with manual seek/skip actions       | https://github.com/tyejaedon/TigerPlayer/issues/22 | tyejaedon | Done        | -          | -          | Completed per board snapshot   | Apex DSP + Crossfade   |
| 23 | Add regression tests for volume restoration after crossfade disable     | https://github.com/tyejaedon/TigerPlayer/issues/23 | tyejaedon | Todo        | -          | -          | Not started                    | Apex DSP + Crossfade   |
| 25 | Add Sonic Footprint filter chips sorting/pinning preferences            | https://github.com/tyejaedon/TigerPlayer/issues/25 | tyejaedon | Done        | -          | -          | Completed per board snapshot   | Sonic Footprint + PiP  |
| 26 | Improve Sonic axis classification heuristics from local metadata        | https://github.com/tyejaedon/TigerPlayer/issues/26 | -         | Todo        | -          | -          | Not started                    | Sonic Footprint + PiP  |
| 27 | Implement MainActivity PiP auto-enter when leaving app during playback  | https://github.com/tyejaedon/TigerPlayer/issues/27 | -         | Done        | -          | -          | Completed per board snapshot   | Sonic Footprint + PiP  |
| 28 | Add Sonic Footprint instrumentation test for empty/non-empty states     | https://github.com/tyejaedon/TigerPlayer/issues/28 | -         | Todo        | -          | -          | Not started                    | Sonic Footprint + PiP  |
| 29 | Ensure PiP renders Fluid/Waveform visualizer without lifecycle glitches | https://github.com/tyejaedon/TigerPlayer/issues/29 | -         | Todo        | -          | -          | Not started                    | Sonic Footprint + PiP  |
| 30 | Add PiP behavior tests for pause/resume/skip/background transitions     | https://github.com/tyejaedon/TigerPlayer/issues/30 | -         | Done        | -          | -          | Completed per board snapshot   | Sonic Footprint + PiP  |
| 31 | Add AdaptiveDspEngine queueInput unit safety test suite                 | https://github.com/tyejaedon/TigerPlayer/issues/31 | -         | Todo        | -          | -          | Not started                    | QA + Release Hardening |
| 32 | Add Room integrity test for PlaybackHistory/CachedTrack operations      | https://github.com/tyejaedon/TigerPlayer/issues/32 | -         | Done        | -          | -          | Completed per board snapshot   | QA + Release Hardening |
| 33 | Add Compose UI test for FullPlayer play-pause state transitions         | https://github.com/tyejaedon/TigerPlayer/issues/33 | -         | Done        | -          | -          | Completed per board snapshot   | QA + Release Hardening |
| 34 | Add Compose UI test for FullPlayer play-pause state transitions         | https://github.com/tyejaedon/TigerPlayer/issues/34 | -         | Todo        | -          | -          | Duplicate tracker title of #33 | QA + Release Hardening |
| 35 | Integrate LeakCanary for debug-only memory leak monitoring              | https://github.com/tyejaedon/TigerPlayer/issues/35 | -         | Todo        | -          | -          | Not started                    | QA + Release Hardening |
| 36 | Enable StrictMode policies for main-thread I/O/network detection        | https://github.com/tyejaedon/TigerPlayer/issues/36 | -         | Todo        | -          | -          | Not started                    | QA + Release Hardening |
| 37 | Add Macrobenchmark for QueueScreen scroll/frame timing                  | https://github.com/tyejaedon/TigerPlayer/issues/37 | -         | Todo        | -          | -          | Not started                    | QA + Release Hardening |
| 38 | Finalize proguard-rules for Media3/Coil/Retrofit-Gson/Room              | https://github.com/tyejaedon/TigerPlayer/issues/38 | -         | Done        | -          | -          | Completed per board snapshot   | QA + Release Hardening |
| 39 | Finalize proguard-rules for Media3/Coil/Retrofit-Gson/Room              | https://github.com/tyejaedon/TigerPlayer/issues/39 | -         | Done        | -          | -          | Duplicate tracker title of #38 | QA + Release Hardening |

