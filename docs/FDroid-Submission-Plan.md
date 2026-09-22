# F-Droid Distribution Plan

Tracks the work to make the `foss` flavor submittable to F-Droid (issue #71: open-source release
readiness for 2.1.1).

## Status

`foss` flavor configured in `app/build.gradle.kts`:

- No vendored proprietary AAR (`libs/spotify-app-remote-release-0.8.0.aar` is
  `fullImplementation`-only).
- No proprietary `com.spotify.android:auth` dependency.
- No API keys required to build or run — `secrets.properties` is optional and only unlocks
  Last.fm / YouTube / Spotify features, all of which are `full`-only or degrade gracefully.
- Spotify App Remote access is behind `SpotifyAppRemoteClient`
  (`app/src/main/.../data/repository/SpotifyAppRemoteClient.kt`), with a real implementation in
  `src/full` and a no-op stub in `src/foss`, so `foss` never imports the proprietary SDK.
- Removed the unused alpha `androidx.compose.remote.creation.compose` dependency and the
  `force(kotlin-stdlib)` resolution-strategy workaround it required (tech-debt item, issue #75).
- Application ID and package namespace updated to `com.tigerplayer` (issue #45).
- R8 minification and reproducible F-Droid bundle build verified (`bundleFossRelease` builds cleanly with zero secrets).
- `versionCode` bumped to `2` / `versionName` to `2.1.1` (issue #71) ahead of the fdroiddata
  submission — `versionCode` must never regress once published (see `gradle-build.instructions.md`).

## Fastlane metadata

`fastlane/metadata/android/en-US/` contains `title.txt`, `short_description.txt`,
`full_description.txt`, and `changelogs/1.txt` + `changelogs/2.txt` (one file per `versionCode`),
matching the layout F-Droid's fastlane metadata scanner expects. Add screenshots under
`fastlane/metadata/android/en-US/images/phoneScreenshots/` before submitting — this is still
outstanding. F-Droid does not accept screenshots with copyrighted third-party artwork visible
(e.g. album art from major labels); the images checked into `screenshots/` at the repo root
(`Daylist.png`, `Player.png`, etc.) have **not** been vetted for this and must not be copied in
as-is. Recapture with royalty-free or self-owned content, or blur/replace any label-owned art,
before placing them under the fastlane path.

## Pre-submission checklist (resolved)

- [x] **`applicationId` is `com.tigerplayer`**: Changed from placeholder `com.example.*` in PR #152 (issue #45).
- [x] **`versionCode` / `versionName`**: `2` / `2.1.1` in `app/build.gradle.kts` (issue #71).
- [x] **Reproducible build verification**: `assembleFossRelease` and `bundleFossRelease` succeed with no `secrets.properties`, passing R8 shrinker and lint checks.
- [x] **Dependency provenance**: Dependencies verified against Google/MavenCentral OSI-approved sources; proprietary Spotify dependencies isolated to `full` flavor.
- [x] **Licensing & Community files**: `LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` present.
- [x] **CI pipeline**: GitHub Actions enforces lint, JVM unit tests, R8 shrinker, and bundle validation on every PR.

## Outstanding before submission

- [ ] **Screenshots**: capture/clear at least 2 clean `phoneScreenshots` and commit under
  `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
- [ ] **Tag the release**: once this PR merges, tag `v2.1.1` on `master` (`git tag v2.1.1 && git push origin v2.1.1`).
  The fdroiddata recipe's `commit:` field must reference this tag.

## Remaining external task

Only the external repository submission is required:

1. Fork `f-droid/fdroiddata`.
2. Add `metadata/com.tigerplayer.yml` describing the `assembleFossRelease` build recipe:
   ```yaml
   Categories:
     - Multimedia
   License: Apache-2.0
   SourceCode: https://github.com/tyejaedon/TigerPlayer
   IssueTracker: https://github.com/tyejaedon/TigerPlayer/issues

   AutoUpdateMode: Version
   UpdateCheckMode: Tags

   CurrentVersion: "2.1.1"
   CurrentVersionCode: 2

   Builds:
     - versionName: "2.1.1"
       versionCode: 2
       commit: v2.1.1
       subdir: app
       gradle:
         - fossRelease
   ```
3. Run `fdroid checkupdates` / `fdroid readmeta` locally.
4. Submit the merge request against `f-droid/fdroiddata`.

