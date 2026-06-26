# Sonic Prism

`Sonic Prism` is TigerPlayer's local-only stem isolation hub.

## Architecture

- `AdaptiveDspEngine` (`engine/DspEngine.kt`)
  - Adds `PrismMode` and `PrismMixLevels`.
  - Performs Mid/Side separation in `queueInput(...)` on PCM16 frames.
  - Applies steep cascaded filters:
    - Vocals: HP 200 Hz + LP 3000 Hz over Mid.
    - Beats: LP 150 Hz over Mid.
    - Instruments: HP 180 Hz over Side.
  - Uses per-sample gain interpolation to avoid zipper noise/clicks.

- `PrismViewModel` (`ui/prism/PrismViewModel.kt`)
  - Owns slider state with `StateFlow`.
  - Bridges UI to DSP via debounced updates.

- `SonicPrismHub` (`ui/prism/SonicPrismHub.kt`)
  - DJ-style neon UI with three vertical faders.
  - Mute-to-zero haptic cue and dimmed visual state.
  - UDF: UI emits events -> ViewModel updates state -> DSP updates.

## Quick verify

Run unit tests including `PrismIsolatorTest`:

```zsh
cd "/Users/tyejaedon/StudioProjects/TigerPlayer"
./gradlew :app:testDebugUnitTest
```

