---
applyTo: '**/*.gradle.kts,**/libs.versions.toml,**/gradle.properties,**/proguard-rules.pro'
---

# Build & Dependency Guidelines

## Version catalog is the single source of truth

All dependencies and plugin versions live in `gradle/libs.versions.toml`.

```kotlin
// Correct
implementation(libs.androidx.media3.exoplayer)

// Never
implementation("androidx.media3:media3-exoplayer:1.4.1")
```

Add a `[versions]` entry, then a `[libraries]` alias referencing it. Do not inline a version string
in `build.gradle.kts`.

## Do not extend the resolution-strategy workaround

`app/build.gradle.kts` ends with:

```kotlin
configurations.all {
    resolutionStrategy { force("org.jetbrains.kotlin:kotlin-stdlib:2.2.20") /* ... */ }
}
```

This force-pins the Kotlin stdlib across **every** configuration to satisfy an alpha dependency
(`androidx.compose.remote.creation`). It is a known liability tracked in issue #75 and will mask
genuine incompatibilities on the next Kotlin/KSP/AGP upgrade.

- Do not add new `force(...)` entries.
- Do not add new dependencies that require it.
- If you can remove the need for it, do — that closes part of #75.

## Secrets

- Secrets are read from root `secrets.properties` into `BuildConfig` fields. The file is gitignored
  and must never be committed, printed, or echoed.
- Never add a `buildConfigField` containing a **client secret** or any credential that must stay
  confidential. Anything in `BuildConfig` is trivially extractable from the APK (issue #46).
  API keys that are merely rate-limiting identifiers are acceptable; authentication secrets are not.
- The build succeeds without `secrets.properties` using placeholder values, so a green build does
  **not** prove cloud integrations work. Verify those on device.

## Versioning

- `versionCode` must increase monotonically and never regress. It is currently `1` against a `2.x`
  `versionName`, which blocks store distribution (issue #45).
- `applicationId` is a one-way door once published. It is currently `com.example.tigerplayer`,
  which stores reject.

## Release configuration

- Keep `isMinifyEnabled` and `isShrinkResources` enabled for release.
- Any new library using reflection, serialization, or JNI needs matching `proguard-rules.pro` keep
  rules. Verify with an actual `assembleRelease` install — R8 problems do not appear in debug builds.
- Never commit signing credentials. Keystore paths and passwords come from environment variables or
  an untracked `keystore.properties`.

## Adding a dependency — checklist

- [ ] Actually necessary; not duplicating something already present
- [ ] Declared in `libs.versions.toml`
- [ ] Stable release, not alpha/beta, unless justified in the PR description
- [ ] Actively maintained; no vendored binaries (the Spotify `.aar` is a liability, not a template)
- [ ] License compatible with the project
- [ ] Does not require a new `force(...)`
- [ ] ProGuard rules added if needed
- [ ] Release build verified

## Do not touch

`app/build/`, `build/`, `.gradle/`, `app/release/` — generated output. Note that `.gitignore` has
been polluted with thousands of individual generated paths; do not add more. Prefer directory
patterns.

