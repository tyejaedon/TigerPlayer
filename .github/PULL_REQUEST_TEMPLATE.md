<!--
  AIRP Phase 4. Keep this structure - CI and reviewers rely on it.
  Delete sections that genuinely do not apply, and say why.
-->

### Summary of Changes

<!-- What changed and why. One paragraph. -->

-

### Root Cause

<!-- For a fix: what actually caused the defect? For a feature: what gap does this close? -->

### Verification

<!-- Paste real results. "Should work" is not verification. -->

```
.\gradlew.bat :app:lintDebug          ->
.\gradlew.bat :app:testDebugUnitTest  ->
.\gradlew.bat :app:assembleDebug      ->
```

- [ ] Lint passes
- [ ] Unit tests pass
- [ ] Debug build compiles
- [ ] New/changed behavior is covered by a test
- [ ] Manually verified on a device (state device + Android version)

**Device tested:**

### Scope

- [ ] Changes are limited to what the issue requires — no drive-by refactors
- [ ] No unrelated formatting or whitespace churn

### Data & Security

- [ ] No Room schema change **or** — version bumped, schema committed, migration registered, and a migration test asserts data survives
- [ ] No secrets, keystores, or `BuildConfig` credentials added, printed, or committed
- [ ] No new dependency **or** — declared in `libs.versions.toml`, stable release, no new `force(...)`
- [ ] No debug logging of user library content left in shipping code

### Audio / Playback

<!-- Only if this PR touches service/ or engine/ -->

- [ ] No allocation or blocking work added to an `AudioProcessor.queueInput` path
- [ ] Behavior in bit-perfect / offload mode is defined and degrades gracefully
- [ ] Every volume fade path has a terminal restore
- [ ] Queue metadata changes updated all four sites (constant, extras builder, `mediaItemToAudioTrack`, serializer round-trip) with a test

### Screenshots / Recording

<!-- Required for UI changes. Before and after. -->

---

Closes #

