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
matching the layout F-Droid's fastlane metadata scanner expects.

`images/phoneScreenshots/` now contains 4 screenshots, each chosen because the underlying screen
is verified (by reading the composable source, not just eyeballing the PNG) to never render
track-specific artwork, so there is no third-party/label album-art exposure:

- `1_Daylist.png` — `DaylistDetailScreen`: abstract time-of-day gradients only, no `AsyncImage`.
- `2_Fluid_Visualizer.png` — `FluidRenderEngine`/`TigerVortexRender`: procedural GLSL noise shader.
- `3_Waveform_Visualizer.png` — `PlayerVisualizers`: Canvas-drawn procedural waveform.
- `4_Sonic_Footprint.png` — `SonicFootprintScreen`: radar chart plus text-only Last.fm genre tags.

The remaining root `screenshots/` images (`Player.png`, `Home.png`, `Discover_Weekly.png`,
`Galaxy_View_Artist.png`, `Galaxy_View_Full.png`, `Youtube_Integration.png`) all correspond to
screens that load real album/artist artwork via Coil (`FullPlayerScreen`, `HomeScreen`'s
recommended-albums carousel, `DiscoverWeeklyDetailScreen`'s `DiscoveryDeck`, `Constellation.kt`
node images) or real third-party YouTube thumbnails (`YouTubeSearchScreen`). These must **not** be
copied into the fastlane path as-is — recapture against royalty-free/self-owned content, or
blur/crop the artwork, before adding any of them here.

## Pre-submission checklist (resolved)

- [x] **`applicationId` is `com.tigerplayer`**: Changed from placeholder `com.example.*` in PR #152 (issue #45).
- [x] **`versionCode` / `versionName`**: `2` / `2.1.1` in `app/build.gradle.kts` (issue #71).
- [x] **Reproducible build verification**: `assembleFossRelease` and `bundleFossRelease` succeed with no `secrets.properties`, passing R8 shrinker and lint checks.
- [x] **Dependency provenance**: Dependencies verified against Google/MavenCentral OSI-approved sources; proprietary Spotify dependencies isolated to `full` flavor.
- [x] **Licensing & Community files**: `LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` present.
- [x] **CI pipeline**: GitHub Actions enforces lint, JVM unit tests, R8 shrinker, and bundle validation on every PR.

## Outstanding before submission

- [x] **Screenshots**: 4 verified-clean `phoneScreenshots` committed under
  `fastlane/metadata/android/en-US/images/phoneScreenshots/` (see "Fastlane metadata" above).
- [x] **Tag the release**: `v2.1.1` tagged on `master` at `ef7a114` and pushed
  (`git tag v2.1.1 && git push origin v2.1.1`), triggering `release.yml`. The fdroiddata recipe's
  `commit:` field references this tag.

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

