---
mode: agent
description: Add a Room schema change with the full migration gate - version bump, schema export, migration, and a data-survival test.
---

# Add a Room migration

Implement this schema change: **${input:change:Describe the schema change (e.g. add replayGainDb to CachedTrackEntity)}**

## Context — read this first

`TigerDatabase` currently ships at `version = 11` with **no migrations registered** and
`exportSchema = false` (issue #43). A schema change without a migration crashes **every** existing
user on launch with `IllegalStateException`, with no recovery path short of clearing app data.

`PlaybackHistoryEntity` is irreplaceable user data. `PlaylistTrackCrossRef` carries user-authored
ordering. Neither may be lost.

## Required steps — all four are mandatory

### 1. Enable schema export (if not already done)

In `app/build.gradle.kts`:

```kotlin
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```

And in `TigerDatabase`: `exportSchema = true`. Commit the generated `app/schemas/` JSON.

If no schema JSON exists for the current version 11, generate and commit it **before** making any
change, so there is a baseline to migrate from.

### 2. Make the entity change and bump the version

Bump `version` in `TigerDatabase` by exactly one. Update the stale comment on that line — it
currently reads "Bumped to 5" at version 11.

### 3. Write and register the migration

Prefer `@AutoMigration` for purely additive changes. Use an explicit `Migration` when columns are
renamed, dropped, retyped, or need backfilling.

```kotlin
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ... ")
    }
}
```

Register it in `DatabaseModule`:

```kotlin
Room.databaseBuilder(context, TigerDatabase::class.java, "tiger_player_vault.db")
    .addMigrations(MIGRATION_11_12)
    .build()
```

**Never** use `fallbackToDestructiveMigration()`.

### 4. Write a migration test that proves data survives

In `app/src/androidTest`, using `MigrationTestHelper`:

- Create the DB at the old version and **insert representative rows**
- Run the migration
- Assert the rows still exist with correct values — not merely that the schema validates
- Cover `PlaybackHistoryEntity` and, if affected, `PlaylistTrackCrossRef` ordering

## Also consider

- Index any new column used in a `WHERE`, `ORDER BY`, or `JOIN`.
- Supply a sensible default for existing rows — `NOT NULL` without a default will fail the migration.
- Update the corresponding domain model mapping in the repository (`toDomainModel` / `toEntity`).

## Verify

```powershell
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest   # requires a device/emulator
```

Report the new version number, the migration strategy chosen, and the test results.

