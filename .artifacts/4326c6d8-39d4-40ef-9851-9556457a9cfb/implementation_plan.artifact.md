# Fix Unresolved Reference 'window' in build.gradle.kts

The project is failing to sync because `libs.androidx.window` is referenced in `app/build.gradle.kts` but is not defined in the version catalog (`gradle/libs.versions.toml`).

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///Users/tyejaedon/StudioProjects/TigerPlayer/gradle/libs.versions.toml)
- Add `window` version (1.5.1).
- Add `androidx-window` library definition using the `androidx.window:window` module.

## Verification Plan

### Automated Tests
- I will attempt to run a Gradle sync (if possible via shell) or check the build file consistency.
- Since I cannot directly "sync" in the IDE, I will verify the file content is correct and matches the naming convention used in `build.gradle.kts`.

### Manual Verification
- The user should trigger a Gradle sync in Android Studio after the changes are applied.
