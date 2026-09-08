---
applyTo: 'app/src/main/java/com/example/tigerplayer/service/**,app/src/main/java/com/example/tigerplayer/engine/**'
---

# Audio, Playback & DSP Guidelines

These files sit on the real-time audio path and inside the Media3 session. Defects here are
audible, hard to reproduce, and frequently device-specific. Apply extra care.

## Real-time audio path

`AudioPlayerService` installs a custom `DefaultAudioSink` whose processor chain is, in order:

```
AdaptiveDspEngine -> FftProcessor -> WaveformCaptureProcessor
```

Anything inside an `AudioProcessor.queueInput` implementation runs on the **audio thread**.

- **Never allocate** in `queueInput`. No `List`, no boxing, no string formatting, no lambdas that
  capture. Pre-allocate buffers in `onConfigure` and reuse them.
- **Never block**: no I/O, no `runBlocking`, no locks, no `Log` calls in the hot path.
- **Never touch Room, DataStore, Retrofit, or SharedPreferences** from a processor.
- Publish analysis results to the UI via a **conflated** flow (`MutableStateFlow` or
  `MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)`). Never a
  suspending emit that can back-pressure the audio thread.
- Clamp and sanitize every output. `NaN` or `Inf` reaching the sink produces loud artifacts.
  Guard divisions and `log`/`sqrt` inputs.

## Gain, volume & clipping

- Sum all gain stages (PEQ preamp, ReplayGain, crossfade attenuation) and clamp **once** at the end.
  Applying them independently double-attenuates or clips.
- Never leave `player.volume` at an intermediate value on an early return. Every fade path needs a
  terminal restore. Capture the pre-fade volume **before** any mutation and restore to that value —
  reading it back after mutation is the bug in issue #53.
- Preamp gains must be `<= 0 dB` unless clipping protection is explicitly implemented.

## Bit-perfect / offload mode

The app offers a bit-perfect route (`routeToSystemDecoderDsp` / audio offload). When it is active:

- The in-app DSP chain is bypassed. Do not assume `AdaptiveDspEngine` output is meaningful.
- Features depending on FFT/waveform analysis (audio-reactive haptics, visualizers) must degrade
  gracefully and say why, rather than silently doing nothing.
- Any new audio feature must declare its behavior in this mode. If it forces the DSP path, surface
  that conflict to the user instead of silently defeating their bit-perfect preference.
- Toggling this mode reconfigures the sink. Preserve playback position **and** the pre-toggle volume.
  Remote/HTTP sources re-buffer — do not assume a `stop()`/`prepare()` cycle is free.

## Media3 session rules

- `AudioPlayerService` is a `MediaSessionService`. All `Player` access must be on the application
  main thread — Media3 is not thread-safe.
- Register custom `SessionCommand`s in `onConnect` **and** handle them in `onCustomCommand`.
  Call `invalidateCustomLayout()` after any state change that alters a button icon or label.
- After mutating the queue (`addMediaItem`, `removeMediaItem`, `moveMediaItem`), always:
  update the queue pointer, emit the controller-state signal, and persist state.
- Metadata travels in `MediaMetadata.extras` using the `META_*` keys in `MediaControllerManager`.
  Adding a field means updating **four** places: the `META_*` constant, `buildQueueMetadataExtras`,
  `mediaItemToAudioTrack`, and both `serializeQueueSnapshot` / `deserializeQueueSnapshot`.
  That serializer is positional and index-parsed — see issue #73. Add a round-trip test with any change.

## Lifecycle & leaks

- `MediaControllerManager` is a `@Singleton` holding a `MediaController`, a `CoroutineScope`, jobs,
  and a `BroadcastReceiver`. Anything acquired must have a matching teardown in `release()`.
- Register receivers with an explicit export flag:
  ```kotlin
  ContextCompat.registerReceiver(
      context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
  )
  ```
  The bare two-argument overload throws on Android 14+ (issue #48).
- Cancel and null out jobs on teardown. Do not leave a `while (isActive)` ticker running when
  playback stops.
- Do not wrap registration/teardown in a bare `runCatching {}` that discards the error — log it.

## Persistence from the playback layer

- Never write to DataStore or Room on every position tick. Throttle (≈15s) or write on state change.
- Conversely, do not persist **only** on pause — process death while playing is the common case
  and loses state (issue #55).

## Device-specific behavior

- Guard OEM workarounds behind an explicit, named condition and comment the symptom being worked
  around. There is an existing Samsung float-output workaround; follow its shape but never leave a
  branch where both arms are identical (issue #53).
- Always feature-detect before use: `vibrator.hasAmplitudeControl()`, `hasVibrator()`,
  `Build.VERSION.SDK_INT` checks.

## Testing expectations

- DSP changes need a unit test asserting output safety: no `NaN`/`Inf`, bounded range, correct
  behavior at buffer boundaries and on format change.
- Playback/queue changes need a test covering shuffle and repeat interaction.
- Prefer testing an engine class directly; `AudioPlayerService` itself is hard to unit test, so keep
  logic out of it.

