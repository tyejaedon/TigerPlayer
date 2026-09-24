package com.tigerplayer.data.backup

import com.google.gson.annotations.SerializedName

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
    @SerializedName("schemaVersion")
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerializedName("appVersionName")
    val appVersionName: String,
    @SerializedName("createdAtEpochMs")
    val createdAtEpochMs: Long,
    @SerializedName("settings")
    val settings: SettingsBackup,
    @SerializedName("playlists")
    val playlists: List<PlaylistBackup>,
    @SerializedName("history")
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
    @SerializedName("themeMode")
    val themeMode: String,
    @SerializedName("pureAmoledBlack")
    val pureAmoledBlack: Boolean,
    @SerializedName("disablePip")
    val disablePip: Boolean,
    @SerializedName("accentStyle")
    val accentStyle: String,
    @SerializedName("defaultPlayerView")
    val defaultPlayerView: String,
    @SerializedName("crossfadeDurationSec")
    val crossfadeDurationSec: Int,
    @SerializedName("gaplessPlayback")
    val gaplessPlayback: Boolean,
    @SerializedName("audioReactiveHaptics")
    val audioReactiveHaptics: Boolean,
    @SerializedName("audioReactiveHapticsProfile")
    val audioReactiveHapticsProfile: String,
    @SerializedName("skipShortAudio")
    val skipShortAudio: String,
    @SerializedName("routeToSystemDecoderDsp")
    val routeToSystemDecoderDsp: Boolean,
    @SerializedName("resumeOnBluetoothConnect")
    val resumeOnBluetoothConnect: Boolean,
    @SerializedName("resumeOnWiredHeadsetConnect")
    val resumeOnWiredHeadsetConnect: Boolean,
    @SerializedName("prismEnabled")
    val prismEnabled: Boolean,
    @SerializedName("prismVocals")
    val prismVocals: Float,
    @SerializedName("prismBeats")
    val prismBeats: Float,
    @SerializedName("prismInstruments")
    val prismInstruments: Float,
    @SerializedName("prismSpectralAnalysis")
    val prismSpectralAnalysis: String
)

data class PlaylistBackup(
    @SerializedName("name")
    val name: String,
    @SerializedName("artworkUri")
    val artworkUri: String?,
    @SerializedName("createdAt")
    val createdAt: Long,
    @SerializedName("position")
    val position: Int,
    @SerializedName("tracks")
    val tracks: List<PlaylistTrackBackup>
)

data class PlaylistTrackBackup(
    @SerializedName("trackId")
    val trackId: String,
    @SerializedName("dateAdded")
    val dateAdded: Long,
    @SerializedName("position")
    val position: Int
)

data class HistoryBackup(
    @SerializedName("trackId")
    val trackId: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("artist")
    val artist: String,
    @SerializedName("album")
    val album: String,
    @SerializedName("imageUrl")
    val imageUrl: String?,
    @SerializedName("durationListenedMs")
    val durationListenedMs: Long,
    @SerializedName("timestamp")
    val timestamp: Long,
    @SerializedName("source")
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

