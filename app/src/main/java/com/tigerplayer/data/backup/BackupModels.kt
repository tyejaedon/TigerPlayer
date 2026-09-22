package com.tigerplayer.data.backup

/**
 * On-disk JSON shape written/read by [BackupManager]. This is a public contract — a backup file
 * created by one app version must remain readable (or fail loudly, never silently corrupt data)
 * by a later version.
 *
 * Deliberately excluded:
 *  - Navidrome/Spotify credentials (`NavidromePrefs` / `SpotifyPrefs`). These are encrypted at
 *    rest via `SecurePrefsCipher` for a reason; writing them out to a plaintext JSON file the user
 *    can email or drop in a shared folder would defeat that. The user re-authenticates after a
 *    restore instead.
 *  - `CachedTrackEntity` / library scan cache. It is fully derived from the device's MediaStore
 *    and is rebuilt by the next library scan; shipping stale entries would only risk drift.
 *  - Artwork/lyrics/waveform caches — same rationale, purely derived data.
 *
 * Known limitation: [PlaylistTrackBackup.trackId] and [HistoryBackup.trackId] are MediaStore-
 * derived local track ids. Restoring onto a different device (or after files moved) can leave
 * cross-refs pointing at tracks that no longer exist; the UI already treats a missing track id as
 * "not found" and simply omits it, so this degrades gracefully rather than crashing.
 */
data class BackupManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appVersionName: String,
    val createdAtEpochMs: Long,
    val settings: SettingsBackup,
    val playlists: List<PlaylistBackup>,
    val history: List<HistoryBackup>
) {
    companion object {
        /** Bump whenever the shape below changes in a way older parsers can't ignore. */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * Mirrors [com.tigerplayer.data.local.TigerSettingsState]. Enums are stored as their
 * `name` string (not ordinal) so reordering an enum's declaration never silently changes meaning,
 * and are parsed defensively on restore (unknown/blank -> default) rather than via unguarded
 * `enumValueOf`.
 */
data class SettingsBackup(
    val themeMode: String,
    val pureAmoledBlack: Boolean,
    val disablePip: Boolean,
    val accentStyle: String,
    val defaultPlayerView: String,
    val crossfadeDurationSec: Int,
    val gaplessPlayback: Boolean,
    val audioReactiveHaptics: Boolean,
    val audioReactiveHapticsProfile: String,
    val skipShortAudio: String,
    val routeToSystemDecoderDsp: Boolean,
    val resumeOnBluetoothConnect: Boolean,
    val resumeOnWiredHeadsetConnect: Boolean,
    val prismEnabled: Boolean,
    val prismVocals: Float,
    val prismBeats: Float,
    val prismInstruments: Float,
    val prismSpectralAnalysis: String
)

data class PlaylistBackup(
    val name: String,
    val artworkUri: String?,
    val createdAt: Long,
    val position: Int,
    val tracks: List<PlaylistTrackBackup>
)

data class PlaylistTrackBackup(
    val trackId: String,
    val dateAdded: Long,
    val position: Int
)

data class HistoryBackup(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val imageUrl: String?,
    val durationListenedMs: Long,
    val timestamp: Long,
    val source: String
)

/** How an import reconciles restored rows against what is already on-device. */
enum class RestoreStrategy {
    /** Delete existing playlists/history first, then write the backup's rows. */
    REPLACE,

    /** Keep existing rows; restored playlists/history are added alongside them. */
    MERGE
}

data class BackupSummary(
    val playlistCount: Int,
    val trackRefCount: Int,
    val historyCount: Int
)

