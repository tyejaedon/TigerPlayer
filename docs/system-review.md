I'll start by exploring the project structure and source files.

I'll read the core playback, service, repository, and DI files.

I've reviewed the build config, manifest, service/playback layer, data layer, DI, settings, and stats pipeline. Here's the full critique.

---

# 1. Architecture snapshot

| Layer | Files | Verdict |
|---|---|---|
| Service | [`AudioPlayerService.kt`](app/src/main/java/com/example/tigerplayer/service/AudioPlayerService.kt), [`MediaControllerManager.kt`](app/src/main/java/com/example/tigerplayer/service/MediaControllerManager.kt) | Solid Media3 base, but doing far too much |
| Engines | `engine/` (10 classes) | Good seam, but `PlaybackEngine` is a thin `if (spotify)` router repeated 8× |
| Data | `AudioRepository`, `HistoryRepository`, `LyricsRepository`, Room + DataStore | Reasonable; caching logic is naive in places |
| UI | ~60 Compose files, one god-ViewModel (`PlayerViewModel`, 603 lines) | Biggest structural weakness |

The bones are good (Media3 + Hilt + Room + Compose + DataStore is the right 2026 stack). The problems are concentrated in **correctness bugs, a god ViewModel, and missing table-stakes features** that every notable OSS player has.

---

# 2. Confirmed bugs (ordered by severity)

### 🔴 B1 — Listening stats are systematically inflated
[`PlayerViewModel`](app/src/main/java/com/example/tigerplayer/ui/player/PlayerViewModel.kt) calls `statsEngine.recordPlaybackHistory(track)` **on track transition**, and [`HistoryRepository.addTrackToHistory`](app/src/main/java/com/example/tigerplayer/data/repository/HistoryRepository.kt) writes `durationListenedMs = track.durationMs` — the *full track length*. Skipping 40 tracks records 40 full plays. Every downstream surface (Heavy Rotation, Sonic Footprint, Day List, Discovery Weekly, "global listening share") is built on corrupt data. The comment "*If duration is < 5s we skip*" checks the **track's** duration, not time listened.

**Fix:** track actual elapsed playback and commit on completion or ≥50%/4-min threshold (the Last.fm scrobble rule).

```kotlin
// StatsEngine
private var playStartElapsed = 0L
private var accumulatedMs = 0L
private var pendingTrack: AudioTrack? = null

fun onTrackStarted(track: AudioTrack) { commitPending(); pendingTrack = track; accumulatedMs = 0L; playStartElapsed = SystemClock.elapsedRealtime() }
fun onPaused() { accumulatedMs += SystemClock.elapsedRealtime() - playStartElapsed }
fun onResumed() { playStartElapsed = SystemClock.elapsedRealtime() }

private suspend fun commitPending() {
    val t = pendingTrack ?: return
    val listened = accumulatedMs + (SystemClock.elapsedRealtime() - playStartElapsed)
    val qualifies = listened >= 240_000L || listened >= t.durationMs / 2
    if (qualifies) historyRepository.addTrackToHistory(..., durationMs = listened, ...)
}
```

### 🔴 B2 — Room has zero migrations → guaranteed crash on upgrade
[`DatabaseModule`](app/src/main/java/com/example/tigerplayer/di/DatabaseModule.kt) builds at `version = 11` with **no `addMigrations()` and no `fallbackToDestructiveMigration()`**. Any schema change ships an `IllegalStateException` on first launch for every existing user. `exportSchema = false` also means you have no schema history to write migrations *from*, and no `MigrationTestHelper` coverage.

**Fix:** `exportSchema = true`, commit `app/schemas/`, add explicit migrations, add `@AutoMigration` where possible, and add a `MigrationTest`.

### 🔴 B3 — Navidrome auth tokens are baked into MediaItem URIs
[`AudioRepository.toAudioTrack`](app/src/main/java/com/example/tigerplayer/data/repository/AudioRepository.kt) embeds `u=&t=&s=` into the stream URL and caches it in Room. Salted tokens expire / rotate; a restored queue (`restorePlaybackState`) will replay **stale URLs** → silent playback failures after a restart or long session. Also, credentials leak into the Room DB in plaintext and into logs.

**Fix:** store a bare `navidrome://<id>` URI and resolve auth at request time via an OkHttp `Interceptor` + a custom `DataSource.Factory`.

Also note the dead-code smell: `toAudioTrack(baseUrl, u, p)` regenerates a salt internally, so the "PERFORMANCE FIX: generate salt ONCE" comment above it is a lie — the passed `authQuery` is used as `u`, meaning `u=u=user&t=...`. **This is an actual malformed-URL bug.**

### 🟠 B4 — `setAudioOffloadEnabled` destroys playback position on remote tracks
It calls `player.stop()` then `player.seekTo(currentPosition); player.prepare()`. For HTTP sources this re-buffers from zero, and `player.volume` is read *after* being set to 1f (line 310 → 321) so `targetVolume` is wrong. Toggling DSP mid-song will produce an audible restart + wrong volume.

### 🟠 B5 — `MediaControllerManager` is a `@Singleton` that never gets released
`release()` exists but nothing calls it. The `BroadcastReceiver` (`ACTION_HEADSET_PLUG`, `BluetoothA2dp`) stays registered for the app's life, and `registerReceiver` without `RECEIVER_NOT_EXPORTED` will **throw on Android 14+** for non-system broadcasts in some OEM configurations. Add the flag explicitly:

```kotlin
ContextCompat.registerReceiver(context, audioRouteReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
```

### 🟠 B6 — Fatal-by-design library scan
[`LocalAudioDataSource`](app/src/main/java/com/example/tigerplayer/data/source/LocalAudioDataSource.kt) accumulates the **entire library into a `List<AudioTrack>` in memory**, then `AudioRepository.refreshLocalCache` compares `cachedTracks != scannedTracks` — a full O(n) deep equality over every field of every track, on every app start. For a 20k-track library that's two full materializations plus a `insertCachedTracksTransaction` of everything. Diff by `(id, dateModified)` instead, and use `ContentObserver` on `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` for incremental updates.

Also: it only reads MediaStore. **No folder-based browsing, no `.nomedia` handling, no user-selected music directories** — a hard requirement for the audiophile crowd you're targeting.

### 🟠 B7 — `getUnifiedTracks` remote branch never re-emits
The inner `flow { ... emit(remoteCache) }` emits once. `combine` will re-run on local changes but the remote lambda is captured — and `remoteCache` is a mutable non-thread-safe `var` on a `@Singleton` written from a `Dispatchers.IO` coroutine. Race + stale data. Make it a `StateFlow` or Room-backed.

### 🟡 B8 — `versionCode = 1` with `versionName = "2.1"`
[`build.gradle.kts`](app/build.gradle.kts) line 33. **You cannot ship an update to Play with versionCode 1** if 0.1.1/2.0 were ever published. Blocking release issue.

### 🟡 B9 — `android:usesCleartextTraffic="true"` app-wide
Manifest line 53. This is for self-hosted Navidrome, but it disables HTTPS enforcement for *every* request including Spotify/Last.fm/LRCLIB. Replace with a `network_security_config.xml` that permits cleartext only for user-configured domains.

### 🟡 B10 — Spotify client **secret** shipped in the APK
`SPOTIFY_CLIENT_SECRET` → `BuildConfig` → used for Basic auth in [`SpotifyAuthManager`](app/src/main/java/com/example/tigerplayer/data/repository/SpotifyAuthManager.kt). A client secret in a distributable binary is extractable in seconds and is a **Spotify TOS violation**. Use **Authorization Code + PKCE** (no secret) and drop the client-credentials service-token path.

### 🟡 B11 — Position never persisted while playing
`playbackPrefs.savePosition` runs only in `onIsPlayingChanged(false)`. Process death while playing (very common) loses the resume position entirely. Persist every ~15s from the ticker, or in `onMediaItemTransition`/`onStop`.

### 🟡 B12 — Synced lyrics fetched but not synced
`LyricsRepository` returns `syncedLyrics` as a raw `String?` with LRC timestamps, and `PlayerUiState.currentLyrics` is a plain `String`. Unless `PlayerLyrics.kt` parses it, users see literal `[00:12.34]` prefixes. Parse to `List<LrcLine(timeMs, text)>` and drive from `currentPosition`.

### 🟡 B13 — `PlayerViewModel` re-fetches Spotify hi-res art on *every* track change
Lines 338–350 fire a network call for any local track with a `content://` artwork URI. There's no negative cache — a track with no Spotify match re-queries forever, every play.

### 🟡 B14 — Minor
- `AutoEqParser` uses `parts[3]` unguarded → silently swallowed by bare `catch (_: Exception)`; malformed AutoEq files fail invisibly.
- `setAudioOffloadEnabled`'s `if (isSamsungDevice) enabled else enabled` — identical branches, dead logic.
- `getLocalTracksWithProgress` and `refreshLocalCache` duplicate the same cache-diff logic.
- `MediaPlayer` import in `MediaControllerManager` is unused (leftover from the commented-out crossfade).
- Room DB comment says "Bumped to 5" at `version = 11`.
- `@Suppress("DEPRECATION")` at class level on the service hides real API issues.

---

# 3. Structural critique

**`PlayerViewModel` is a god object.** It owns playback, library, scanning, playlists, stats, metadata, sorting, search, Bluetooth, Navidrome auth, Spotify auth, waveforms and visual modes — and it's shared across *every screen* via `MainActivity`. Consequences: every screen recomposes on unrelated state changes; 14 concurrent `viewModelScope.launch` collectors run permanently; testing is impossible. Split into `PlaybackViewModel`, `LibraryViewModel`, `StatsViewModel`, and hoist shared playback state into a `@Singleton` state holder.

**Package naming.** `com.example.tigerplayer` — Play Store **rejects `com.example.*`**. Rename before release (this is a one-way door; do it now).

**Business logic in the service.** ~250 lines of haptics tuning in `AudioPlayerService` belongs in an injectable `AudioReactiveHapticsController`. The service should own the player + session, nothing else.

**Test coverage is thin for the risk profile.** 6 unit tests, mostly UI/DSP. There is nothing covering `AudioRepository` cache diffing, queue serialization round-trip (`serializeQueueSnapshot`/`deserializeQueueSnapshot` — 18 positional fields, extremely fragile), stats aggregation, or Room migrations. Those four are exactly where the bugs are.

**Dependency risk.** `libs/spotify-app-remote-release-0.8.0.aar` is a vendored, unmaintained binary. `androidx.compose.remote.creation.compose` is alpha and you're force-pinning Kotlin stdlib versions to make it resolve — that `configurations.all { force(...) }` block is a time bomb.

---

# 4. Competitive comparison

| Capability | TigerPlayer | Auxio | Retro/Metro | Symphony | Gramophone | Namida | Fossify |
|---|---|---|---|---|---|---|---|
| Folder browsing / custom dirs | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Sleep timer | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| ReplayGain | ❌ | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Tag editor | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ |
| M3U import/export | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Android Auto / Wear | ❌ | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ |
| Home-screen widgets | partial (Glance dep, `NowBriefWidget`) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Last.fm scrobbling | ❌ (API present, unused) | ❌ | ✅ | ❌ | ✅ | ✅ | ❌ |
| Synced lyrics | partial (B12) | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Backup/restore | ❌ | ❌ | ✅ | ✅ | ❌ | ✅ | ❌ |
| Equalizer | ✅ **in-app PEQ** | system | system | system | system | ✅ | system |
| Audio-reactive haptics | ✅ **unique** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| GPU fluid visualizer | ✅ **unique** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Listening analytics | ✅ **strong** | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| Subsonic/Navidrome | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

**Read:** your differentiators (in-app parametric EQ, fluid visualizer, haptics, analytics, Navidrome+Spotify unification) are genuinely uncontested. But you're missing ~7 features that users treat as *non-negotiable defaults*. A reviewer will not reach your visualizer if there's no sleep timer. **The highest-ROI work for v2.1 is closing the table-stakes gap, not adding more novelty.**

Two more competitive notes:
- **Auxio** is the reference for a fast, correct MediaStore+folder library. Study its incremental indexer.
- **Gramophone** is the reference for a modern Media3 + Material3 architecture with proper `MediaLibraryService` browse trees.

---

# 5. Recommended release plan

## v2.1 "Foundation" — ship-blockers only
1. Fix stats recording (**B1**) — everything downstream is wrong until this lands.
2. Room migrations + `exportSchema` + migration tests (**B2**).
3. Fix Navidrome URL construction and auth-at-request-time (**B3**).
4. `versionCode` bump + package rename off `com.example` (**B8**).
5. Spotify PKCE, drop the client secret (**B10**).
6. Scoped `network_security_config` (**B9**).
7. `RECEIVER_NOT_EXPORTED` + call `release()` (**B5**).
8. **Sleep timer** — the single most-requested missing feature. Cheap to build:

```kotlin
@Singleton
class SleepTimerController @Inject constructor(...) {
    // modes: duration, end-of-track, end-of-queue; + optional volume fade-out
}
```

9. **Folder / custom directory browsing** + `.nomedia` respect (**B6**).
10. **Playback speed & pitch** (`player.setPlaybackParameters`) — 3 lines, audiobook/podcast users demand it.

## v2.2 "Parity"
- **ReplayGain** — you already have a DSP chain; read `REPLAYGAIN_TRACK_GAIN`/`ALBUM_GAIN` tags and apply preamp in `AdaptiveDspEngine`. This is a natural fit for your audiophile positioning and only Auxio/Symphony/Gramophone have it.
- **Synced lyrics rendering** with auto-scroll + seek-on-tap (**B12**), plus local `.lrc` sidecar file support.
- **M3U/M3U8 import + export**, and full **backup/restore** (playlists, history, settings) as a single JSON/zip.
- **`MediaLibraryService`** migration → unlocks **Android Auto, Wear OS, Google Assistant** in one change. Currently `MediaSessionService` blocks all three.
- **Glance widgets** (2×2 and 4×2) — the `androidx.glance` dependency is already there and unused.
- **Last.fm scrobbling** — `LastfmApi` and the key already exist; only the auth + submit flow is missing. Pairs perfectly with the fixed stats engine.
- **Tag editor** for local files.

## v2.3 "Signature" — lean into what only you have
- **Waveform seekbar** (you have `WaveformEngine` + `WaveformCacheEntity` — surface it in the seeker like SoundCloud).
- **Auto-EQ profile per output device** — detect the connected Bluetooth device (`BluetoothDeviceManager` already tracks it) and auto-load its AutoEq PEQ profile. Nobody else does this on Android outside Wavelet. Huge, defensible feature.
- **Crossfade done right** — currently `flowStateTrueOverlap` is commented out and you only duck volume to 0.18 then hard-cut. Implement real overlap via two `ExoPlayer` instances or a custom `MixingAudioProcessor` in your existing DSP chain.
- **Smart playlists / rules engine** ("liked + not played in 90 days + >3 plays") on the now-trustworthy history data.
- **Year in Review / Wrapped** export card from `SonicFootprint` — highly shareable, drives organic installs.
- **Gapless verification + bit-perfect indicator** in the now-playing UI (sample rate / bit depth / decoder path badge). Audiophiles will screenshot this.
- **Open-source it properly**: F-Droid submission (you'd need to drop the proprietary Spotify AAR into a flavor), reproducible builds, and a `CONTRIBUTING.md`. Your README already reads like an OSS project — the missing LICENSE-in-README, flavor split, and F-Droid metadata are the gap.

## Continuous
- Split `PlayerViewModel` into 3–4 scoped ViewModels.
- Add unit tests for: queue snapshot round-trip, stats aggregation windows, cache diffing, AutoEq parsing.
- Add Room migration tests + expand the existing macrobenchmark to cover library scroll and player open.
- Add `androidx.media3` `PlayerNotificationManager` custom actions parity for the sleep timer.

---

**Bottom line:** the visual/DSP/analytics work is genuinely ahead of the OSS field, but it's sitting on a data layer that records incorrect stats, can't survive a schema change, and builds broken remote URLs. Fix B1–B3 and B8–B10 first; they're all small, and until B1 is fixed every "intelligence" feature in the app is presenting fabricated numbers.
