# Sonic Prism Implementation (Non-ML, High-Fidelity)

## Objective

This implementation upgrades Sonic Prism into a production DSP feature that is:

- Fully wired across UI, state, and audio execution pipeline.
- Focused on audible musical separation without machine learning.
- Tuned for minimal distortion, stable loudness, and predictable behavior.
- Persisted across sessions so user mix decisions are not lost.

## Scope

Implemented in these areas:

- DSP core and output safety:
  - `app/src/main/java/com/example/tigerplayer/engine/DspEngine.kt`
- Prism state orchestration and persistence:
  - `app/src/main/java/com/example/tigerplayer/ui/prism/PrismViewModel.kt`
  - `app/src/main/java/com/example/tigerplayer/data/local/SettingsDataStore.kt`
- UI wiring and controls (Home + Full Player + Prism Hub):
  - `app/src/main/java/com/example/tigerplayer/ui/home/HomeScreen.kt`
  - `app/src/main/java/com/example/tigerplayer/ui/player/FullPlayerScreen.kt`
  - `app/src/main/java/com/example/tigerplayer/ui/main/MainScreen.kt`
  - `app/src/main/java/com/example/tigerplayer/ui/prism/SonicPrismHub.kt`
- DSP tests:
  - `app/src/test/java/com/example/tigerplayer/engine/PrismIsolatorTest.kt`

## Design Principles

1. Separation must remain phase-aware and musical, not just aggressive filtering.
2. User controls must not cause sudden gain jumps (zipper noise / pumping).
3. Output must remain bounded under extreme slider settings.
4. UI state must be deterministic and synchronized across all entry points.
5. No ML dependency: all behavior is classical DSP + deterministic control logic.

## DSP Architecture Upgrade

### 1) Mid/Side 3-band decomposition

Sonic Prism now uses a clearer 3-way split for both Mid and Side channels:

- Low crossover: `160 Hz`
- High crossover: `3500 Hz`
- Filter topology: cascaded biquads approximating LR4 behavior

Signal flow:

- `L/R -> Mid/Side`
- `Mid -> Low, MidBand, High`
- `Side -> Low, MidBand, High`
- Reconstruct stems:
  - Vocals stem: mostly mid-band center with a small high-presence component
  - Beats stem: low-band dominant with slight side-low support
  - Instruments stem: side-mid/side-high dominant with subtle mono high rescue

This improves practical separation quality for real music while avoiding hard, brittle sounding isolation.

### 1.1) Real frequency-domain transformation (6 bands)

To satisfy strict spectral reactivity requirements without ML, the DSP pipeline now performs
real-time 6-band frequency analysis using an FFT path (JTransforms), with an A/B switch to compare
against the classic band-pass filter-bank path for profiling.

Implemented in `AdaptiveDspEngine` (`engine/DspEngine.kt`):

- Center frequencies/bands:
  - `60 Hz`
  - `250 Hz`
  - `1000 Hz`
  - `2500 Hz`
  - `6000 Hz`
  - `12000 Hz`
- FFT analyzer: `FftSpectralAnalyzer` with Hann-windowed `FloatFFT_1D`.
- A/B mode: `SpectralAnalysisMode.FFT` vs `SpectralAnalysisMode.BANDPASS`.
- Analysis runs sample-by-sample on the post-processed mono signal and closes every analysis window.
- Band energies are mapped from FFT bins (or band-pass accumulators), normalized with smoothing,
  then exposed via `AudioReactiveFrame.spectralBands`.
- Per-window profiling telemetry is exposed from DSP as:
  - `AudioReactiveFrame.analysisMode`
  - `AudioReactiveFrame.analysisCostMicros` (smoothed)

This means reactivity is now driven by true frequency-domain bins by default (FFT), with legacy
filter-bank A/B mode available for profiling and tuning.

### 2) Distortion and clipping control

To keep distortion low at runtime, two safety stages were added in `PrismIsolator`:

- Dynamic mix normalization:
  - Computes target gain from stem gain vector energy.
  - Uses attack/release smoothing to avoid audible pumping.
- Safety limiter + soft saturation:
  - Envelope-based limiter near `0.93` peak target.
  - Gentle cubic soft saturation to avoid hard clipping transients.

This preserves audible punch while reducing clipping risk when all stems are driven.

### 3) Existing pipeline compatibility

The upgraded Prism stage remains in the same execution path inside `AdaptiveDspEngine.queueInput(...)`:

- Headphone widening
- Device/EQ filters
- Acoustic environment
- **Prism isolation (upgraded)**
- AGC / limiter
- dither and final PCM16 output

No routing contract changes were introduced, so local playback behavior remains compatible with the existing service/controller stack.

## State + Persistence Wiring

### Prism state persisted in DataStore

Added persistent Prism fields in `TigerSettingsState`:

- `prismEnabled: Boolean`
- `prismVocals: Float`
- `prismBeats: Float`
- `prismInstruments: Float`

Added persistence methods:

- `setPrismEnabled(enabled: Boolean)`
- `setPrismMix(vocals, beats, instruments)`

### ViewModel orchestration improvements

`PrismViewModel` now:

- Hydrates initial Prism state from `SettingsDataStore`.
- Applies hydrated state into `AdaptiveDspEngine` deterministically.
- Debounces and persists user changes back to DataStore.
- Synchronizes external DataStore changes to UI state.
- Supports production presets:
  - `Balanced`
  - `Vocal Focus`
  - `Beat Punch`
  - `Instrumental`
  - `Custom` (when sliders diverge)

### Lifecycle behavior

`disablePrismAndReset()` behavior is now disable-only (preserves mix).

Rationale:

- Closing a screen should not silently destroy a user mix.
- Preset/mix continuity is important for a meaningful audio experience.

## Interface Upgrade

### Shared Prism ViewModel across Home and Full Player

`MainScreen` now provides one shared `PrismViewModel` instance to:

- `HomeScreen`
- `FullPlayerScreen`

This prevents state divergence between surfaces and keeps Prism behavior coherent across navigation and sheet transitions.

### Enhanced Prism mixer controls

`PrismInlineMixer` (in `ui/prism/SonicPrismHub.kt`) now supports:

- Prism enable/disable switch
- Preset chips
- Reset-to-balanced action
- Spectral analysis mode chips (`FFT` / `Bandpass`) for A/B profiling
- Live profiling label (`mode + ms/window`) to compare FFT vs Bandpass cost in real time
- Live spectral bars and dominant-band readout
- Existing neon vertical fader interaction

### Home card behavior

`SonicPrismHubCard` now binds switch visibility and expansion directly to `state.isPrismEnabled`, removing local-only UI drift.

### Full Player behavior

`FullPlayerScreen` now uses the Prism package mixer explicitly and wires:

- slider events
- enable/disable
- preset selection
- reset action

## Testing and Verification

### Added/expanded tests

`PrismIsolatorTest` now covers:

1. Center suppression in instruments-only mode.
2. Low-frequency survival in beats-only mode.
3. Bounded output under hot full-stem input.
4. Mono-track residual presence in instruments mode.

New Compose/instrumentation hardening tests:

- `app/src/androidTest/java/com/example/tigerplayer/ui/prism/PrismInlineMixerTest.kt`
  - Verifies Prism UI callbacks for:
    - enable/disable switch
    - preset chip selection
    - reset action
    - spectral analysis mode chip selection (`FFT` vs `Bandpass`)
  - Verifies spectral visual readout reacts differently for low-tone vs high-tone synthetic input.
- `app/src/androidTest/java/com/example/tigerplayer/ui/player/FullPlayerScreenTest.kt`
  - Verifies visual mode transition wiring toggles Prism enable binding:
    - `SONIC_PRISM` -> enabled
    - non-Prism visual mode -> disabled

Supporting test tags were added in:

- `app/src/main/java/com/example/tigerplayer/ui/prism/PrismTestTags.kt`
- `app/src/main/java/com/example/tigerplayer/ui/prism/SonicPrismHub.kt`

### Commands run during implementation

```zsh
cd /Users/tyejaedon/StudioProjects/TigerPlayer
./gradlew :app:testDebugUnitTest --console=plain
./gradlew :app:assembleDebug --console=plain
./gradlew :app:lintDebug --console=plain
./gradlew :app:assembleDebugAndroidTest --console=plain
```

Observed status in this environment:

- Unit tests: successful
- Debug assemble: successful
- Lint debug: successful
- Android test APK assemble: successful

Instrumentation execution note:

- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.tigerplayer.ui.prism.PrismInlineMixerTest --console=plain`
  - Fails in this environment due to no connected device/emulator.

## Practical Audio Tuning Notes

If you want even cleaner separation for specific genres/devices, tune these constants first:

- Crossovers in `PrismIsolator.configure(...)`:
  - `lowCutoffHz`
  - `highCutoffHz`
- Stem blend weights in `PrismIsolator.process(...)`
- Safety behavior:
  - normalization attack/release
  - limiter attack/release and threshold
  - soft saturation curvature

Recommended strategy:

- Keep limiter threshold conservative for mobile speakers.
- Adjust stem blend weights before increasing saturation.
- Re-run unit tests and listening tests after each tuning pass.

## Non-Goals (intentional)

- No ML stem separation.
- No network dependency for DSP behavior.
- No changes to Spotify playback routing contract.

## Summary

Sonic Prism is now fully wired and production-usable as a deterministic, non-ML DSP feature:

- coherent separation architecture,
- persisted and synchronized controls,
- shared state across major UI surfaces,
- safer output with minimal distortion intent,
- verification coverage for core isolation/safety behavior.

