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

- **Spotify connection**: the Spotify App Remote session is now actually opened after a successful
  login. Previously `isSpotifyRemoteConnected` stayed `false` indefinitely post-auth, leaving the
  Cloud screen stuck on "reconnecting". (#162)
- **Player/Spotify source switching**: fixed a race where the player UI could get stuck showing a
  Spotify track after the user started local playback, caused by stale Spotify state re-emissions
  clobbering the just-started local track. (#162)

[Unreleased]: https://github.com/tyejaedon/TigerPlayer/compare/master...HEAD
