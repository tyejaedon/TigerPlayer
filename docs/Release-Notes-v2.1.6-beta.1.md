# TigerPlayer v2.1.6-beta.1

## Beta Release Notes

TigerPlayer **v2.1.6-beta.1** introduces complete Cover Screen settings parity, an intuitive and actionable Spotify connection diagnostics dialog replacing fleeting toasts, observable-safe locale handling, and visual refinements across clamshell foldable displays (Samsung Galaxy Z Flip, Motorola Razr).

---

### Highlights & Key Improvements

#### 1. Spotify Authentication Diagnostics & Pop-Up View
- Replaced ephemeral, brief toast messages with a persistent, responsive **Arcane Glass Diagnostics Dialog** (`SpotifyAuthErrorDialog`).
- Translates cryptic OAuth callback codes and setup omissions into plain-English explanations with numbered, actionable resolution steps:
  - **Missing Client ID**: Explains missing `SPOTIFY_CLIENT_ID` in `secrets.properties` and provides repository setup instructions.
  - **`redirect_uri_mismatch`**: Clarifies that `tigerplayer://callback` must be registered in the Spotify Developer Dashboard.
  - **`access_denied`**: Explains cancelled logins or declined permissions with instructions on how to grant access.
  - **`invalid_client` & `unauthorized_client`**: Diagnoses mismatched developer credentials and user allowlisting requirements.
  - **Offline & Network Issues**: Guides connectivity troubleshooting.
- Accessible 48dp minimum touch target **DISMISS** and **RETRY** actions.
- Supported in both standard display mode and compact Cover Screen mode with responsive scrolling and gesture dismiss (`BackHandler`).

#### 2. Comprehensive Cover Screen Settings (`CoverScreenSettingsView`)
- Fully mirrors the primary application settings on outer clamshell displays:
  - **Connected Accounts**: One-tap Spotify CONNECT button when disconnected, confirmation-guarded DISCONNECT button when authenticated, and Navidrome server indicators.
  - **Theme & Appearance**: Pure AMOLED black toggle, neon accent color palette swatches, and default player view selection.
  - **Audio Flow**: Crossfade duration slider, gapless flow toggle, and system decoder/DSP offload routes.
  - **Haptic Resonance**: Kick-drum mechanical feedback toggle with intensity profile chips.
  - **System Behavior**: Bluetooth auto-resume, wired headset auto-resume, PiP controls, and short audio skip filters.
- Direct entry via the newly enabled Settings button in `CoverScreenLibraryMenu`.

#### 3. Visual Modernization & Lint Compliance
- Refreshed Album, Song, and Artist detailed views to Vanguard Arcane Glass design standards.
- Replaced non-observable `Locale.getDefault()` invocations with `Locale.ROOT` in `CoverScreenStatsView`, eliminating Android Lint errors while ensuring observable safety.
- Bounded interactive element touch targets to >= 48dp across all outer screen views.

---

### Installation & Artifacts

#### Distribution Flavors
- **`app-full-release.apk`**: Full feature set with Spotify App Remote integration and cloud streaming.
- **`app-foss-release.apk`**: 100% libre/F-Droid compatible build without proprietary dependencies.
- **`app-fullRelease.aab`**: Android App Bundle for Google Play distribution.

#### Verification & Checksums
- `SHA256SUMS.txt`: SHA-256 checksums for all release binaries.
- `mapping-full.txt` & `mapping-foss.txt`: R8 ProGuard de-obfuscation mapping files.
- `tigerplayer-sbom.spdx.json`: SPDX software bill of materials.
