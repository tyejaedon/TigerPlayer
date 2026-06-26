# AGENTS Guide for TigerPlayer
## What this app is
- TigerPlayer is a dual-backend Android music app: local playback via Media3 and cloud playback via Spotify App Remote.
- Primary app module: `app/`; DI root: `app/src/main/java/com/example/tigerplayer/TigerPlayerApplication.kt`.
- Existing AI convention source found: `README.md` (note: some setup text is older than current Gradle/BuildConfig wiring).
## Architecture you should keep in mind
- UI shell and navigation live in `navigation/NavGraph.kt` and `ui/main/MainScreen.kt`; keep composables thin.
- `PlayerViewModel` is an orchestrator, not a heavy logic class; it composes engine classes (`PlaybackEngine`, `LibraryEngine`, `NetworkEngine`, etc.).
- Playback routing contract is critical: `PlaybackEngine` routes by track id prefix (`spotify:`) to Spotify or local controller.
- Local playback path: `AudioPlayerService` (MediaSessionService + ExoPlayer) <-> `MediaControllerManager` <-> engines/viewmodel.
- Library path: `LocalAudioDataSource` (MediaStore scan) + `AudioRepository` (Room cache + remote merge).
- Network boundary is DI-qualified in `di/NetworkModule.kt` (separate Retrofit clients/services by qualifier).
## Required workflows (use these first)
```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```
```bash
./gradlew :app:installDebug
./gradlew :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```
- If you changed Kotlin/Gradle deps, run `./gradlew :app:check`.
- Toolchain/version anchors: AGP `9.2.1`, Gradle `9.4.1`, JVM toolchain `17` (see `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`).
## Secrets and environment
- Local bootstrap depends on `secrets.properties` read by `app/build.gradle.kts`.
- Expected keys: `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`, `LASTFM_API_KEY`, `YOUTUBE_API_KEY`.
- Missing keys compile with placeholder BuildConfig values (`MISSING_*`), which breaks real integrations.
- Spotify redirect URI is declared in `AndroidManifest.xml` (`tigerplayer://callback`).

## Project-specific coding patterns
- Prefer adding behavior in engines/repositories; avoid direct playback/network logic inside composables.
- Preserve reactive state style: `Flow/StateFlow`, `combine`, `flatMapLatest`, and ViewModel `.update` state transitions.
- Do not bypass dual-routing: user actions should still go through `PlaybackEngine` methods (`playTrack`, `togglePlayPause`, `seekTo`, etc.).
- Keep cache-first behavior where present (example: metadata/artist detail flows in repository layer) and persist through Room/DataStore.
- Preserve DI qualifiers when adding APIs; do not reuse generic Retrofit/OkHttp instances across unrelated services.

## Integrations and boundaries to respect
- Spotify uses two channels: Web API/auth (`SpotifyAuthManager`, Retrofit services) and App Remote control (`SpotifyRepository`).
- Navidrome/Subsonic is host-configurable with dynamic URL interception (`DynamicUrlInterceptor` + `SubsonicHostManager`).
- Local persistence: Room (`TigerDatabase`) for media/history/cache/playlists and DataStore prefs (`PlaybackPrefs`, `NavidromePrefs`, `SpotifyPrefs`).
- `android:usesCleartextTraffic="true"` is enabled; avoid assumptions that all endpoints are HTTPS-only.

## Practical guardrails for AI edits
- Validate compile early after non-trivial edits; this codebase appears mid-refactor in places.
- README setup guidance is useful context, but trust current Gradle/manifest/source wiring when they differ.
- Tests are currently sparse (`ExampleUnitTest`, `ExampleInstrumentedTest`), so prioritize targeted verification commands after changes.

