# TigerPlayer 2.0 Release Notes

TigerPlayer 2.0 is a major update focused on smarter discovery, stronger playback control, and better reliability across local and cloud listening.

## Highlights

- New curated listening surfaces: **Day List** and **Discovery Weekly**.
- Improved dual-backend playback routing between local Media3 and Spotify App Remote.
- Expanded DSP and listening experience controls, including crossfade flow controls and Sonic Prism improvements.
- Better queue behavior and playback continuity, including persistence and restoration.
- Improved device experience with Picture-in-Picture behavior and cover-screen mini controls.

## What is new in 2.0

### Discovery and curation

- Added **Discovery Weekly** flow from Home curation row to a dedicated detail screen.
- Added **Day List** curation based on time-bucket listening patterns.
- Improved recommendation freshness by prioritizing never-played or stale tracks with genre affinity.

### Playback and queue

- Refined playback command routing for local and Spotify sources.
- Improved queue operations: play next, add to queue, move/reorder, and restore queue state.
- Better long-session continuity with infinite-tail style candidate appends.

### Audio and DSP

- Expanded **Sonic Prism** controls and persistence.
- Added/improved crossfade duration control (0-12s) and conflict handling around manual transport actions.
- Continued tuning and safety work for advanced DSP paths.

### UI and experience

- Enhanced Home dashboard surfaces and curation entry points.
- Improved full player and mini-player behavior transitions.
- Picture-in-Picture auto-enter behavior for active playback.
- Cover-screen mini hub support for foldable/outer-display usage.

### Stability and release hardening

- Ongoing strictness and debug tooling integration for app health checks.
- Release build hardening with proguard/r8 rule finalization.
- Room/data integrity and targeted UI behavior tests expanded.

## Notes

- Cloud integrations require valid credentials in `secrets.properties` (`SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`, `LASTFM_API_KEY`, `YOUTUBE_API_KEY`).
- Without valid keys, placeholder `BuildConfig` values are used and related integrations will not function fully.

## Known gaps

- Some planned QA and instrumentation coverage remains in progress.
- A subset of backlog items (visual tuning, additional lifecycle regression tests) is still tracked for post-2.0 iterations.

## Thanks

Thanks for using TigerPlayer and helping shape this release.
