# Unit Test Report

Date: 2026-06-27
Type: unit

## Scope
This report summarizes unit-test commands run during the recent M2 queue/routing hardening work.

## Runs So Far

1. Command:
```zsh
./gradlew :app:testDebugUnitTest --tests com.example.tigerplayer.service.PlaybackSemanticsTest --rerun-tasks
```
Result: PASS (`BUILD SUCCESSFUL`)
Notes: Verified shared shuffle/repeat and queue bounds semantics tests.

2. Command:
```zsh
./gradlew :app:testDebugUnitTest --tests com.example.tigerplayer.engine.PlaybackEngineRoutingTest --rerun-tasks
```
Result: Initially FAIL (NPE in early test version), then PASS after refactor to ID-based helper assertions.
Notes: Final version uses pure routing helpers and no Android `Uri` construction.

3. Command:
```zsh
./gradlew :app:testDebugUnitTest --tests com.example.tigerplayer.engine.PlaybackEngineDelegationTest --tests com.example.tigerplayer.engine.PlaybackEngineRoutingTest
```
Result: Initially FAIL (Spotify queue warning path using `Log.w` in local JVM tests), then PASS after static mocking `Log.w` and assertion split adjustments.
Notes: Final run passed with both classes in one command.

4. Command:
```zsh
./gradlew :app:testDebugUnitTest --tests com.example.tigerplayer.engine.AdaptiveDspEngineSafetyTest --rerun-tasks
```
Result: PASS (`BUILD SUCCESSFUL`)
Notes: Focused DSP safety regression check remained green.

5. Command:
```zsh
./gradlew :app:testDebugUnitTest --tests com.example.tigerplayer.engine.ReactiveMotionFrameTest
```
Result: PASS (`BUILD SUCCESSFUL`)
Notes: Added CPU-side fluid mapping coverage for `ReactiveMotionFrame.fromAudio` (clamping, range envelopes, and directional behavior checks for expansion/flow/turbulence).

## Current Status
- `PlaybackSemanticsTest`: passing
- `PlaybackEngineRoutingTest`: passing
- `PlaybackEngineDelegationTest`: passing
- `AdaptiveDspEngineSafetyTest`: passing
- `ReactiveMotionFrameTest`: passing

## Key Warnings Seen
- AGP/Kotlin deprecation warnings (legacy variant API, built-in Kotlin migration notes).
- Existing project warnings unrelated to these tests (deprecated APIs and lint-style warnings).

## Follow-up
- Optional: add explicit `skipToNext`/`skipToPrevious` interaction assertions in `PlaybackEngineDelegationTest`.

