# F-Droid Distribution Plan

Tracks the work to make the `foss` flavor submittable to F-Droid (issue: OSS distributability).

## Status

`foss` flavor added in `app/build.gradle.kts`:

- No vendored proprietary AAR (`libs/spotify-app-remote-release-0.8.0.aar` is
  `fullImplementation`-only).
- No proprietary `com.spotify.android:auth` dependency.
- No API keys required to build or run — `secrets.properties` is optional and only unlocks
  Last.fm / YouTube / Spotify features, all of which are `full`-only or degrade gracefully.
- Spotify App Remote access is behind `SpotifyAppRemoteClient`
  (`app/src/main/.../data/repository/SpotifyAppRemoteClient.kt`), with a real implementation in
  `src/full` and a no-op stub in `src/foss`, so `foss` never imports the proprietary SDK.
- Removed the unused alpha `androidx.compose.remote.creation.compose` dependency and the
  `force(kotlin-stdlib)` resolution-strategy workaround it required (tech-debt item).

## Fastlane metadata

`fastlane/metadata/android/en-US/` contains `title.txt`, `short_description.txt`,
`full_description.txt`, and `changelogs/1.txt`, matching the layout F-Droid's fastlane metadata
scanner expects. Add screenshots under `fastlane/metadata/android/en-US/images/phoneScreenshots/`
before submitting (F-Droid does not accept screenshots with copyrighted third-party artwork
visible, e.g. album art from major labels — recapture with royalty-free or self-owned content).

## Known blockers still open

These must be resolved before an F-Droid merge request will be accepted, independent of the
flavor split above:

1. **`applicationId` is `com.example.tigerplayer`.** This is a placeholder namespace under a
   domain the project does not own. F-Droid (and the Play Store) reject `com.example.*`
   application IDs. This must change before first publication — and per
   `.github/instructions/gradle-build.instructions.md`, it is a one-way door once published, so
   it should change now while there is no existing install base to break, in a dedicated PR with
   its own test coverage (deep link handling, Spotify redirect URI registration, DataStore/Room
   file paths where applicable).
2. **`versionCode` does not monotonically track `versionName`** (`versionCode = 1` against
   `versionName = "2.1"` — see issue #45). F-Droid's reproducible-build tooling keys off
   `versionCode`; this needs a real, incrementing scheme before submission.
3. **Reproducible build verification.** F-Droid builds from source in a clean container and
   compares the output APK. Before submitting:
   - Confirm `assembleFossRelease` succeeds with no `secrets.properties`, no network access to
     anything other than the declared dependency repositories, and no local Gradle caches.
   - Confirm `isMinifyEnabled`/`isShrinkResources` R8 output is deterministic across two clean
     builds (`diffoscope` the two APKs).
4. **Dependency provenance.** Every dependency in `gradle/libs.versions.toml` must be available
   from Maven Central/Google's Maven (already true here) and under an OSI-approved license.
   Re-verify licenses for `kotlin-youtubeExtractor` and `youtubeextractor` (both third-party
   GitHub-hosted artifacts) — F-Droid's inclusion policy is stricter about YouTube-adjacent
   functionality (ToS concerns), so confirm this doesn't itself block inclusion.

## Submission steps (once blockers above are closed)

1. Fork `f-droid/fdroiddata`.
2. Add `metadata/<final-application-id>.yml` describing the `assembleFossRelease` build recipe,
   pinned to a tagged commit/release in this repository (not a floating branch).
3. Run `fdroid readmeta` / `fdroid checkupdates` / `fdroid build --test` locally against the new
   metadata file.
4. Open a merge request against `fdroiddata` referencing this repository's release tag.

