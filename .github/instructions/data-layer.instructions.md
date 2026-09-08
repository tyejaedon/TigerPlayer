---
applyTo: 'app/src/main/java/com/example/tigerplayer/data/**'
---

# Data Layer Guidelines

Covers Room, DataStore, repositories, remote APIs, and the local MediaStore source.

## Room — schema changes are irreversible in the field

The database currently ships at `version = 11` with **no migrations registered** and
`exportSchema = false` (issue #43). Until that is fixed, treat any entity change as a release blocker.

Every schema change requires **all** of:

1. A `version` bump in `TigerDatabase`
2. A committed schema JSON under `app/schemas/`
3. A registered `Migration` (or `@AutoMigration` for purely additive changes)
4. A `MigrationTestHelper` test asserting **data survives**, not merely that the schema validates

Never resolve a migration failure with `fallbackToDestructiveMigration()`. `PlaybackHistoryEntity`
is irreplaceable user data, and `PlaylistTrackCrossRef` carries explicit user-authored ordering.

### Query rules

- DAO reads that feed the UI return `Flow`. Reserve `...Sync()` suspend variants for one-shot
  internal work.
- Multi-statement writes go in an `@Transaction` method.
- Index any column used in a `WHERE`, `ORDER BY`, or `JOIN`. The analytics queries scan
  `PlaybackHistoryEntity` by `timestamp` constantly.
- Do not do in-Kotlin filtering that SQL can do. Do not load a whole table to find one row.

## Credentials & secrets

- Credentials belong in `NavidromePrefs` / `SpotifyPrefs`, encrypted via `SecurePrefsCipher`.
  Never in Room, never in DataStore, never in a plain `SharedPreferences`.
- **Never embed auth material in a persisted URI.** Subsonic salted tokens rotate; a cached signed
  URL becomes a silent playback failure (issue #44). Persist an opaque identifier and sign the
  request at call time via an interceptor.
- Never log a URL that carries a query string with credentials. Never log a token, salt, or password.

## Repositories

- `@Singleton`, and the only layer permitted to touch a DAO, DataStore, or Retrofit service.
- No mutable shared state. A bare `private var cache: List<T>` written from a background dispatcher
  and read from a flow is a data race (issue #54). Use `MutableStateFlow`, or persist and read back
  through Room.
- Expose `Flow` for anything observable. A cold `flow { emit(x) }` that emits once inside a
  `combine` will never update — prefer a genuine reactive source.
- Declare the dispatcher inside the repository (`.flowOn(Dispatchers.IO)`), so callers are main-safe.
- Return `Result<T>` for fallible remote operations rather than throwing across the boundary.
- Always rethrow `CancellationException`:
  ```kotlin
  } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(TAG, "…", e)
  }
  ```

## Library scanning

- Diff on cheap identity — `(id, dateModified)` — never deep structural equality across every field.
- Apply deltas (insert / update / delete). Never rewrite the whole table because one row changed.
- Bound memory: do not materialize an entire multi-thousand-track library into a `List` when a
  batched or streaming read will do.
- Keep the scan/diff logic in exactly one place. It is currently duplicated between
  `refreshLocalCache` and `getLocalTracksWithProgress`.

## Remote APIs

- One Retrofit interface per service, models in `data/remote/model`, mapping to domain models in the
  repository — never leak a DTO above the repository layer.
- Every remote call needs a timeout, a failure path, and a cache/negative-cache policy.
  An unmatched lookup must not re-query forever (issue #64).
- Rate-limit and back off. Do not fire a network request per item in a list.
- All endpoints must be HTTPS. Cleartext is permitted only for a user-configured self-hosted
  server, behind an explicit user acknowledgement.

## DataStore

- Add a key to the private companion, a field on `TigerSettingsState`, a mapping in `settingsFlow`,
  and a setter. Keep all four in sync.
- Always supply a default and coerce into a valid range on read — persisted values may be stale or
  out of bounds after an app update.
- Parse enums defensively via the existing `enumOrDefault` helper; never `enumValueOf` unguarded.

## Analytics correctness

Listening history drives Heavy Rotation, Sonic Footprint, Day List, Discovery Weekly and the stats
screens. Record **actual listened duration**, never nominal track length (issue #42). A play qualifies
at ≥ 50% of the track or ≥ 4 minutes, consistent with the standard scrobble rule.

