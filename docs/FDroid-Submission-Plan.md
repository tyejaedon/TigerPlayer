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
- `versionCode` bumped to `3` / `versionName` to `2.1.2` (issue #71) ahead of the fdroiddata
  submission — `versionCode` must never regress once published (see `gradle-build.instructions.md`).
  `v2.1.1` was tagged first but never produced a successful F-Droid build or GitHub release, so it
  was superseded by `v2.1.2` rather than moved/reused.
- **F-Droid's own build server confirmed `assembleFossRelease` succeeds end-to-end** against
  `v2.1.2` (`fdroid build --test`, 2026-09-23): `BUILD SUCCESSFUL`, `app-foss-release-unsigned.apk`
  produced, `1 build succeeded`. The submission's merge request is now in packager review.

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
- [x] **`versionCode` / `versionName`**: `3` / `2.1.2` in `app/build.gradle.kts` (issue #71).
- [x] **Reproducible build verification**: `assembleFossRelease` and `bundleFossRelease` succeed with no `secrets.properties`, passing R8 shrinker and lint checks — confirmed both locally/in GitHub CI and independently on F-Droid's own build server.
- [x] **Dependency provenance**: Dependencies verified against Google/MavenCentral OSI-approved sources; proprietary Spotify dependencies isolated to `full` flavor.
- [x] **Licensing & Community files**: `LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` present.
- [x] **CI pipeline**: GitHub Actions enforces lint, JVM unit tests, R8 shrinker, and bundle validation on every PR.

## Outstanding before submission

- [x] **Screenshots**: 4 verified-clean `phoneScreenshots` committed under
  `fastlane/metadata/android/en-US/images/phoneScreenshots/` (see "Fastlane metadata" above).
- [x] **Tag the release**: `v2.1.2` tagged on `master` (superseding `v2.1.1`, which never built
  successfully) and pushed (`git tag v2.1.2 && git push origin v2.1.2`), triggering `release.yml`.
  The fdroiddata recipe's `commit:` field references this tag.

## Remaining external task

Only the external repository submission is required. **`fdroiddata`'s canonical repo is on
GitLab** (`https://gitlab.com/fdroid/fdroiddata`) — `f-droid/fdroiddata` on GitHub is a read-only
mirror; a fork or merge request opened there is never seen by F-Droid's maintainers. All of this
happens outside this repo (`TigerPlayer` itself never needs a GitLab account or a `fdroiddata`
remote), so it must be done by whoever holds/creates the GitLab account submitting the app —
typically done once, by a maintainer, not repeated by every contributor.

1. Register a GitLab account, then fork `https://gitlab.com/fdroid/fdroiddata`.
2. Create a branch on the fork named after the app id (`com.tigerplayer`), **not** on the fork's
   `master` — `master` is protected there too, and pushing to it desyncs future upstream pulls.
3. Add `metadata/com.tigerplayer.yml` (either via the GitLab web UI or a local clone) describing
   the `foss` build recipe. `Repo`/`RepoType` are required by the schema validation CI
   job — they're the actual clone source for `fdroid build`/`checkupdates`, separate from the
   human-facing `SourceCode` link; omitting them fails schema validation, `checkupdates` (`Tags
   update mode only works for git repositories currently`), and `fdroid build`:
   ```yaml
   Categories:
     - Multimedia
   License: Apache-2.0
   SourceCode: https://github.com/tyejaedon/TigerPlayer
   IssueTracker: https://github.com/tyejaedon/TigerPlayer/issues

   AutoName: TigerPlayer

   RepoType: git
   Repo: https://github.com/tyejaedon/TigerPlayer.git

   Builds:
     - versionName: 2.1.2
       versionCode: 3
       commit: v2.1.2
       subdir: app
       gradle:
         - foss
       scandelete:
         - app/libs/spotify-app-remote-release-0.8.0.aar

   AutoUpdateMode: Version
   UpdateCheckMode: Tags
   CurrentVersion: 2.1.2
   CurrentVersionCode: 3
   ```
   Two easy-to-miss gotchas discovered while validating this recipe against F-Droid's own build
   server:
   - `gradle:` takes the **product flavor name only** (`foss`), not the flavor+build-type
     (`fossRelease`) — fdroidserver appends `Release` itself, so `fossRelease` produces the
     non-existent task `assembleFossReleaseRelease`.
   - The vendored `app/libs/spotify-app-remote-release-0.8.0.aar` trips F-Droid's "usual suspects"
     scanner regardless of which flavor references it, because the scanner walks the whole checked-
     out tree, not just the flavor being built. `scandelete` removes the file from F-Droid's build
     checkout only (harmless, since `foss` never references it) without touching the file in this
     repo, so the `full` flavor still builds normally everywhere else (GitHub CI, local, Play).
4. If using `fdroidserver` locally (`pip install git+https://gitlab.com/fdroid/fdroidserver.git`):
   run `fdroid readmeta`, `fdroid rewritemeta com.tigerplayer`, `fdroid checkupdates com.tigerplayer`
   to fill automated fields, `fdroid lint com.tigerplayer` (must report zero warnings), and
   `fdroid build -v -l com.tigerplayer` to test the recipe end-to-end before opening the MR. If
   editing only via the GitLab web UI, instead push the branch and confirm the fork's CI/CD
   pipeline (GitLab → CI/CD tab) passes on the commit.
5. Open the merge request against `gitlab.com/fdroid/fdroiddata` (not the GitHub mirror), filling
   in their MR template, then respond to packager review questions.

Because `AutoUpdateMode: Version` / `UpdateCheckMode: Tags` are already set above, this MR is a
**one-time onboarding step** — once merged, F-Droid's bot picks up every future `v*` tag on this
repo automatically (subject to `versionCode` incrementing per the build rule in
`gradle-build.instructions.md`) without a new MR per release, unless the build recipe itself
changes (new dependency, new Gradle task, etc.).

## What happens after the MR's CI check passes

The pipeline CI check (`fdroid build --test`) only proves the recipe *can* build — it does not
publish anything. The full path to being installable from F-Droid:

1. **Packager/maintainer review.** Since `com.tigerplayer` is a first-time submission, a volunteer
   reviewer checks licensing, anti-features, and app-store-listing quality, and may request changes
   on the MR. This step is manual and can take days to weeks.
2. **Merge into `fdroiddata`.** Once approved, a maintainer merges the MR. Nothing is published to
   users yet — the recipe is just queued for F-Droid's production build server.
3. **Production build + F-Droid signing.** On its own schedule, F-Droid's build infrastructure
   (separate from the MR-check runner) clones the tag, builds `assembleFossRelease` again, and
   signs the resulting APK with **F-Droid's own release key** — not the key used for GitHub
   Releases. The two distributions are independently signed builds of the same source and are not
   interchangeable/updatable into each other by Android's package installer.
4. **Repo index publish.** F-Droid's repo index regenerates on its regular cycle; once that runs,
   the app is searchable and installable from `f-droid.org` and the F-Droid client.
5. **Every release after this is automatic.** Push a new `v*` tag with an incremented `versionCode`
   (same pattern as `v2.1.2`) and F-Droid's `checkupdates` bot picks it up, builds, and republishes
   without a new MR — typically much faster than the initial review, since the app is already
   vetted.

