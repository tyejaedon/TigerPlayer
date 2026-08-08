# Control Matrix Settings

Control Matrix is TigerPlayer's DataStore-backed settings architecture for appearance, DSP behavior, and library policies.

## Files

- `data/local/SettingsDataStore.kt`
  - Single source of truth for persisted settings.
  - Exposes `Flow<TigerSettingsState>`.

- `ui/settings/SettingsViewModel.kt`
  - Exposes `StateFlow<TigerSettingsState>` for Compose.
  - Handles user intents and forwards writes to `SettingsDataStore`.
  - Triggers manual library rescan through `LibraryEngine.getLocalAudioScanFlow(forceRefresh = true)`.

- `ui/settings/SettingScreen.kt`
  - Neon/Cyberpunk Control Matrix UI with categorized sections and animated controls.

## Runtime consumers

- `MainActivity.kt` consumes `settingsState` for:
  - Theme mode
  - Pure AMOLED black
  - Accent style

- `MediaControllerManager.kt` consumes `settingsFlow` for:
  - Crossfade window + enable state
  - Gapless toggle state
  - Audio-reactive haptics toggle state
  - Headset resume policy toggles

- `LocalAudioDataSource.kt` consumes `skipShortAudio` to filter short files at scan time.

## Verify

```zsh
cd "/Users/tyejaedon/StudioProjects/TigerPlayer"
./gradlew :app:testDebugUnitTest
```

