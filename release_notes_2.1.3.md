# TigerPlayer v2.1.3

## Release Notes

### Fixed
- **Spotify**: Surface actionable UI element for Spotify App Remote reauthorization in Cloud Archives and Full Player screens. Added a persistent reauth banner with one-tap "Re-authorize Spotify" button and clear status feedback on successful reauthorization. (#201)

### Added
- **F-Droid**: Reproducible builds support with binary verification metadata and allowed signing keys documentation. (#246)

## What's New in v2.1.3

This patch release includes:
- Improved Spotify App Remote reauthorization workflow
- F-Droid reproducible builds support for better transparency and security
- Enhanced documentation for release signing and key management

## Installation

### For F-Droid
The FOSS flavor (`app-foss-release.apk`) is available on F-Droid and can be installed without any proprietary dependencies.

### For Google Play / Direct Install
The full flavor (`app-full-release.apk`) includes all features including Spotify App Remote integration.

## Features
- 🎵 Local music playback with Media3 / ExoPlayer
- 🎧 Spotify App Remote integration (full flavor only)
- 🧠 On-device music discovery and listening analytics
- 🌊 Real-time fluid visualizer with audio-reactive effects
- ⚡ Jetpack Compose UI with a custom neon design system
- 💾 Room-powered library, history, and analytics storage
- 🔒 Spotify Authorization Code + PKCE authentication

## Downloads

- **app-foss-release.apk** - F-Droid compatible, no proprietary dependencies
- **app-full-release.apk** - Full feature set with Spotify integration

## Certificate Information

For reproducible build verification:
- **SHA-256**: `0aabbc79e9db6666f1f814487d5607865f2fc643ca940217ff941947c0fb0c9b`

See [docs/SIGNING_KEY_SECURITY.md](https://github.com/tyejaedon/TigerPlayer/blob/master/docs/SIGNING_KEY_SECURITY.md) for signing key security procedures.

## Previous Releases
- [v2.1.2](https://github.com/tyejaedon/TigerPlayer/releases/tag/v2.1.2)
- [v2.1.1](https://github.com/tyejaedon/TigerPlayer/releases/tag/v2.1.1)

## Contributors
Built with ❤️ by Jaedon in Nairobi, Kenya.

