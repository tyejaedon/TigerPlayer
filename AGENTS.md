# AGENTS.md

Machine-readable operating guide for AI coding agents working in the TigerPlayer repository.
Human contributors should read `README.md` first.

> **Companion documents**
> - `.github/instructions/issue-resolution-protocol.instructions.md` — the AIRP lifecycle for resolving an issue
> - `docs/Roadmap-v2.1-v2.3.md` — milestones, issue index, dependency graph

---

## 1. Project identity

TigerPlayer is an **Android** music player: Jetpack Compose UI, Media3/ExoPlayer playback, Hilt DI,
Room persistence, unifying a local library with Navidrome (Subsonic) and Spotify App Remote.

| Property | Value |
|---|---|
| Language | Kotlin `2.2.20` |
| JDK | 17 |
| `minSdk` / `targetSdk` / `compileSdk` | 29 / 36 / 37 |
| DI | Hilt (KSP) |
| Persistence | Room + DataStore Preferences |
| Playback | AndroidX Media3 (`MediaSessionService`) |
| Networking | Retrofit + Gson |
| Images | Coil |
| Build | Gradle Kotlin DSL + version catalog |

---

## 2. Quality toolchain (canonical commands)

AIRP Phase 3 requires these to pass. Run them **in this order**. Stop at the first failure.

This project is developed on **Windows**. Use `.\gradlew.bat`.
On macOS/Linux substitute `./gradlew`.

```powershell
# 1. Static analysis / lint
.\gradlew.bat :app:lintDebug

# 2. Unit tests (JVM)
.\gradlew.bat :app:testDebugUnitTest

# 3. Compile check
.\gradlew.bat :app:assembleDebug
```

Instrumented tests require a connected device or emulator and are **not** part of the default gate:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Run a single test class:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tigerplayer.engine.StatsEngineFilterTest"
```

> **Note:** `README.md` contains hardcoded macOS paths
> (`cd /Users/tyejaedon/StudioProjects/TigerPlayer`). Ignore them; they are stale.

### Build prerequisites

`secrets.properties` must exist at the repo root or cloud features compile with placeholder values:

```properties
SPOTIFY_CLIENT_ID=...
SPOTIFY_CLIENT_SECRET=...
LASTFM_API_KEY=...
YOUTUBE_API_KEY=...
```

The build **will still succeed** without it, so a green build does not prove cloud integrations work.

---

## 3. Repository map

```
app/src/main/java/com/example/tigerplayer/
  data/
    local/          Room DB, DAOs, entities, DataStore, encrypted prefs
    remote/api/     Retrofit interfaces (Subsonic, Spotify, Last.fm, LRCLIB, iTunes, YouTube, Wikipedia)
    repository/     Repositories - the only layer that talks to data sources
    source/         LocalAudioDataSource (MediaStore)
    model/          AudioTrack, Playlist domain models
  engine/           Domain orchestration - see section 4
  service/          AudioPlayerService, MediaControllerManager, audio processors
  ui/               Compose screens, grouped by feature
  navigation/       NavGraph + Screen routes
  di/               Hilt modules
  utils/            Pure helpers
docs/               Review notes, roadmap, release notes
gradle/             libs.versions.toml (version catalog) + wrapper
```

---

## 4. Architecture rules

**Layering — never skip a layer:**

```
UI (Compose) -> ViewModel -> Engine -> Repository -> DataSource / DAO / API
```

- **Composables** receive state and lambdas. No `ViewModel` business logic, no direct repository access.
- **ViewModels** expose `StateFlow`. They orchestrate engines; they do not contain domain logic.
- **Engines** (`engine/`) hold domain logic and are plain `@Inject` classes, not Android-aware.
  Existing: `PlaybackEngine`, `LibraryEngine`, `MetadataEngine`, `StatsEngine`, `NetworkEngine`,
  `WaveformEngine`, `AdaptiveDspEngine`, `GraphicsEngine`, `FluidRenderEngine`, `VizualizerEngine`.
- **Repositories** own data access and caching. `@Singleton`.
- Prefer adding to an existing engine over creating a new one.

**Concurrency:**

- Suspend functions must be main-safe — declare the dispatcher inside, via `flowOn`/`withContext`.
- Never call `Dispatchers.Main` blocking work from a repository.
- Always rethrow `CancellationException`. Never swallow it in a `catch (e: Exception)`.

---

## 5. Guardrails — do not do these

1. **Never** run `git add .` or `git add -A`. Stage explicit paths only.
   A release keystore and `secrets.properties` live in the working tree.
2. **Never** commit or print the contents of `secrets.properties`, `local.properties`, or any
   `*.jks` / `*.keystore`. Never echo a token, password, or `BuildConfig` secret into logs or chat.
3. **Never** log user library content (track titles, paths, artist names) at `Log.d`/`Log.i`
   in code that ships. Gate diagnostics behind `if (BuildConfig.DEBUG)`.
4. **Never** bump the Room `version` in `TigerDatabase` without adding a migration **and** a
   migration test. See issue #43 — there are currently no migrations at all.
5. **Never** add a dependency version inline. Use `gradle/libs.versions.toml`.
6. **Never** widen `configurations.all { resolutionStrategy { force(...) } }` in `app/build.gradle.kts`.
   It is an existing workaround tracked in issue #75; do not build on it.
7. **Never** edit anything under `app/build/`, `build/`, or `.gradle/` — generated output.
8. Do not "fix" unrelated files you happen to open. AIRP Phase 2 forbids out-of-scope changes.

---

## 6. Known-bad areas

Do not treat existing code in these areas as a pattern to copy. Each has an open issue.

| Area | Problem | Issue |
|---|---|---|
| `StatsEngine` / `HistoryRepository` | Plays recorded at track start with full duration; all analytics inflated | #42 |
| `DatabaseModule` / `TigerDatabase` | No migrations, `exportSchema = false` | #43 |
| `AudioRepository.toAudioTrack` | Malformed Navidrome URL; auth tokens cached into URIs | #44 |
| `SpotifyAuthManager` | Client secret shipped in APK; no refresh-token handling | #46 |
| `MediaControllerManager` | `release()` never called; receiver registered without export flag | #48 |
| `PlayerViewModel` | 603-line god object shared across all screens | #72 |
| `MediaControllerManager` queue serialization | 18 positional fields, index-parsed, untested | #73 |

---

## 7. Conventions

- **Naming:** identifiers are plain, descriptive English. The codebase contains themed prose in
  comments ("ritual", "vault", "archive", "grimoire") — do not extend this into new identifiers,
  log tags, or public API names.
- **Comments:** explain *why*, not *what*. Do not add decorative emoji banners or
  `// FileName.kt line 194` markers — several existing ones are already wrong.
- **Logging:** use a stable `TAG` constant per class. Match the tag to the class it lives in.
- **Compose:** stable/immutable parameters, hoisted state, `Modifier` as the first optional
  parameter, `@Preview` for non-trivial components.
- **Test tags:** UI elements needing instrumentation coverage get a `testTag`; centralize them like
  the existing `PrismTestTags`.
- **Commits:** Conventional Commits, per AIRP Phase 4.

---

## 8. Definition of done

- [ ] Lint, unit tests, and `assembleDebug` all pass
- [ ] New/changed behavior covered by a test
- [ ] No secrets, keystores, or generated build output staged
- [ ] No debug logging of user content left in shipping code
- [ ] Room schema change (if any) has a migration + migration test
- [ ] Change is scoped to the issue; no drive-by refactors

