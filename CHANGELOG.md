# Changelog

All notable changes to TigerPlayer are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versioning follows the
`versionName` in `app/build.gradle.kts` (currently pre-1.0 internal versioning; see issue #45 for the
`versionCode`/package-name cleanup tracked ahead of a real store release).

For prose-style release summaries at major milestones, see `docs/Release-Notes-*.md`. This file is
the terse, per-change log — add an entry here in the same PR as the change, under `[Unreleased]`.

## [Unreleased]

### Added

- **Full player**: the Speed/Pitch dialog has a restore-to-default action that resets both to
  `1.0x` in one tap. (#167)
- **Connected Accounts**: a new Settings section to log out of Spotify and Navidrome independently,
  with a confirm-before-logout prompt. Logging out of Navidrome also triggers a library rescan so
  its tracks disappear from the unified library immediately. (#162)
- **Constellation**: the bottom "Cosmic Insight" overlay panel is now collapsible, so the starfield
  can be viewed unobstructed. Added a two-layer parallax starfield with a subtle twinkle behind the
  orbiting nodes for a stronger sense of depth. (#162)

### Changed

- **Sonic Prism**: the mixer moved from a cramped expandable Home dashboard card to its own
  full-screen destination, where the faders scale to the available height (up to 360 dp) instead of
  being clamped to 180 dp. The Home feed now shows a compact entry card with a quick enable toggle
  that opens the new screen on tap. Removed the dead, commented-out mixer block from the full
  player. (#173)
- **Sonic Prism**: removed the developer-only FFT-vs-Bandpass spectral analysis toggle and its
  profiler readout from the shipped Home screen card (still available in debug builds); hid dead,
  unreachable "Sonic Prism full-screen hub" and duplicate mixer code paths that had been superseding
  each other.
- **Sonic Footprint**: the acoustic/electronic/bass-heavy/vocal/atmospheric axes are now weighted by
  actual time listened instead of raw play count, and prefer cached Last.fm genre tags over
  guessing from track title text when genre data is available for the artist. (#162)
- **Cloud screen**: the YouTube search entry point now uses a distinct video icon instead of
  reusing the same magnifying-glass icon as the search field next to it. (#162)
- **Full player**: the Speed/Pitch controls are now a compact summary row (e.g. "Speed 1.25x -
  Pitch 1.00x") that opens a dedicated glass-styled dialog on tap, instead of permanently reserving
  space for two full-width sliders inline - fixes a layout overlap on shorter screens where the
  sliders used to always be expanded. (#162, #167)
- **Queue screens**: the drag-to-reorder handle now renders as a proper icon instead of a raw text
  glyph that had been mangled into mojibake. (#163)

### Fixed

- **Spotify**: starting a Spotify track no longer shows a meaningless string (the raw Spotify
  track ID) stuck at `0:00`. The player now shows the real title, artist, album art, and duration
  the moment playback is requested, because the metadata already on screen is passed through to
  the interim state instead of being re-derived from the URI. (#169)
- **Spotify**: a Spotify App Remote connection that fails — the Spotify app isn't installed or
  running, the account isn't Premium, or the IPC connection is refused — now shows an explanatory
  message instead of silently leaving the player parked at `0:00` forever. Playback that is never
  confirmed by the Spotify app is also timed out and reported rather than left hanging, and builds
  without App Remote support say so. (#170)
- **Spotify playlist screen**: the header artwork is now centred and captioned with the playlist
  name and track count, and no longer draws its glow shadow twice. Both the playlist and album
  detail screens now show an explicit empty state, surface errors that occur while they are open,
  and can no longer crash on a collection that lists the same track ID more than once. Album track
  rows gained artwork and now follow the screen's extracted accent colour. (#171)

- **Sonic Prism**: fixed a bug where the isolation mix could be silently disabled (with playback
  continuing unaffected/unannounced) purely as a side effect of the controlling screen's ViewModel
  being torn down - e.g. when the system destroys the Activity in the background while a foreground
  playback service keeps the music going. The DSP engine's enabled/disabled state is now driven only
  by the user's persisted setting, never reset by UI lifecycle.
- **Sonic Prism**: fixed a layout bug where the Home screen mixer card clipped its own vertical
  fader and controls (the card was capped at a fixed height shorter than the fader itself). The
  card now sizes to its content and uses a more compact fader suited to a dashboard card.
- **Spotify connection**: the Spotify App Remote session is now actually opened after a successful
  login. Previously `isSpotifyRemoteConnected` stayed `false` indefinitely post-auth, leaving the
  Cloud screen stuck on "reconnecting". (#162)
- **Player/Spotify source switching**: fixed a race where the player UI could get stuck showing a
  Spotify track after the user started local playback, caused by stale Spotify state re-emissions
  clobbering the just-started local track. (#162)

[Unreleased]: https://github.com/tyejaedon/TigerPlayer/compare/master...HEAD
