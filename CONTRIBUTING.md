# Contributing to TigerPlayer

Thanks for considering a contribution. TigerPlayer is developed issue-first: please open or
link an issue before sending a non-trivial PR, so we can agree on the approach before you invest
time in it.

## Ground rules

- Be respectful. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
- One logical change per PR. Do not mix an unrelated refactor, a dependency bump, and a bug fix
  in the same diff.
- Follow the guidance in `.github/instructions/` — these are the same rules our automated
  contributors follow:
  - `gradle-build.instructions.md` — dependency/version catalog rules, the Kotlin
    resolution-strategy liability, secrets handling, release/ProGuard requirements.
  - `data-layer.instructions.md` — Room migrations, repository patterns, credential handling.
  - `audio-playback.instructions.md` — real-time audio path rules (no allocation/blocking on
    the audio thread), Media3 session conventions.
  - `testing.instructions.md` — test tooling, naming, determinism.
  - `git-workflow.instructions.md` — branch/PR conventions; `master` is protected.

## Building

TigerPlayer ships two product flavors:

| Flavor | Contains proprietary code? | Needs `secrets.properties`? |
|---|---|---|
| `foss` | No — no vendored Spotify AAR, no proprietary dependency | No |
| `full` | Yes — Spotify App Remote playback | Only to enable Spotify/Last.fm/YouTube features; the build still succeeds without it |

```bash
# FOSS build — no API keys, no proprietary AAR, this is what F-Droid builds
./gradlew :app:assembleFossDebug

# Full build
./gradlew :app:assembleFullDebug
```

If you need Spotify, Last.fm, or YouTube features locally, create `secrets.properties` in the
repo root (gitignored, never commit it):

```properties
SPOTIFY_CLIENT_ID=your_spotify_client_id
LASTFM_API_KEY=your_lastfm_api_key
YOUTUBE_API_KEY=your_youtube_api_key
```

## Before opening a PR

```bash
./gradlew :app:lintFossDebug
./gradlew :app:testFossDebugUnitTest
```

Both must pass. CI re-runs these on every PR; a red check will block merge.

## Adding Spotify-touching code

Spotify App Remote access must stay behind `SpotifyAppRemoteClient`
(`app/src/main/.../data/repository/SpotifyAppRemoteClient.kt`). Never import
`com.spotify.android.appremote.*` from common (`src/main`) code — that only compiles in the
`full` flavor and will break the `foss` build. Add real behavior to
`src/full/.../SpotifyAppRemoteClientImpl.kt` and keep `src/foss/.../SpotifyAppRemoteClientImpl.kt`
a no-op.

## Dependencies

Every new dependency must go through the checklist in `gradle-build.instructions.md`, in
particular: stable release (not alpha/beta) unless justified, no vendored binaries, and no new
`force(...)` in the Kotlin resolution strategy.

## Commit / PR conventions

Use [Conventional Commits](https://www.conventionalcommits.org/) (`fix:`, `feat:`, `docs:`,
`chore:`, ...). Reference the issue you're closing (`Closes #123`).

