 # Sonic Prism

`Sonic Prism` is TigerPlayer's local-only stem isolation hub.

## Architecture

- `AdaptiveDspEngine` (`engine/DspEngine.kt`)
  - Adds `PrismMode` and `PrismMixLevels`.
  - Performs Mid/Side decomposition in `queueInput(...)` on PCM frames, feeding an internal
    `PrismIsolator` that splits Mid and Side into a 3-way low/mid/high crossover (160 Hz / 3500 Hz,
    cascaded biquads approximating LR4 behavior) and recombines them into vocals/beats/instruments
    stems.
  - Uses per-sample gain interpolation to avoid zipper noise/clicks, plus a dynamic mix
    normalizer and a safety limiter/soft-saturation stage to keep output bounded.

- `PrismViewModel` (`ui/prism/PrismViewModel.kt`)
  - Owns slider/preset/enabled state with `StateFlow`, hydrated from and persisted to
    `SettingsDataStore` (debounced) so a user's mix survives navigation and app restarts.
  - Bridges UI to DSP via `AdaptiveDspEngine.setPrismMode` / `updatePrismMix`. The engine's own
    processing state is the source of truth and is only ever driven by this observed UI state -
    it is never reset as a side effect of the ViewModel (or its hosting screen) being torn down.

- `SonicPrismHubCard` (`ui/home/HomeScreen.kt`) + `PrismInlineMixer` (`ui/prism/SonicPrismHub.kt`)
  - The only production entry point today: a card on the Home dashboard with an enable switch,
    presets, live spectral bars, and three neon vertical faders (Vocals/Beats/Melody).
  - `PrismInlineMixer` accepts an optional `onSpectralAnalysisChange` callback that toggles the
    FFT-vs-Bandpass analyzer for developer A/B profiling; callers should only wire it up in debug
    builds (`BuildConfig.DEBUG`) since it's not meant for end users.
  - `PlayerVisualMode.SONIC_PRISM` and `DefaultPlayerView.SONIC_PRISM` exist as reserved enum
    values but are intentionally excluded from the Full Player's visual-mode cycle and from the
    Settings default-view picker - Sonic Prism is Home-only for now.

## Quick verify

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.tigerplayer.engine.PrismIsolatorTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.tigerplayer.engine.AdaptiveDspEngineTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.tigerplayer.ui.prism.PrismViewModelTest"
```

