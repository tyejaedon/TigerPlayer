package com.tigerplayer.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.tigerplayer.data.model.AudioTrack

/**
 * Bidirectional conversion between domain [AudioTrack] and AndroidX [MediaItem].
 *
 * Centralizes queue metadata serialization and deserialization across the application,
 * ensuring complete metadata preservation across IPC boundaries (MediaController, Android Auto,
 * Wear OS, and Assistant).
 */
object MediaItemConverter {

    const val META_IS_LOCAL = "tp_meta_is_local"
    const val META_IS_REMOTE = "tp_meta_is_remote"
    const val META_DURATION_MS = "tp_meta_duration_ms"
    const val META_MIME_TYPE = "tp_meta_mime_type"
    const val META_BITRATE = "tp_meta_bitrate"
    const val META_SAMPLE_RATE = "tp_meta_sample_rate"
    const val META_TRACK_NUMBER = "tp_meta_track_number"
    const val META_SERVER_PATH = "tp_meta_server_path"
    const val META_YEAR = "tp_meta_year"
    const val META_DATE_ADDED = "tp_meta_date_added"
    const val META_IS_LIKED = "tp_meta_is_liked"
    const val META_PATH = "tp_meta_path"
    const val META_REPLAYGAIN_TRACK_DB = "tp_meta_rg_track_db"
    const val META_REPLAYGAIN_ALBUM_DB = "tp_meta_rg_album_db"
    const val META_REPLAYGAIN_TRACK_PEAK = "tp_meta_rg_track_peak"
    const val META_REPLAYGAIN_ALBUM_PEAK = "tp_meta_rg_album_peak"

    /**
     * Converts an [AudioTrack] into a playable Media3 [MediaItem] with full metadata
     * and queue serialization extras.
     */
    fun toMediaItem(track: AudioTrack): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.uri)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(track.uri)
                    .build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.artworkUri)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(buildMetadataExtras(track))
                    .build()
            )
            .build()
    }

    /**
     * Serializes all 18 audio track attributes into a [Bundle] for MediaItem extras.
     */
    fun buildMetadataExtras(track: AudioTrack): Bundle {
        return Bundle().apply {
            putBoolean(META_IS_LOCAL, track.isLocal)
            putBoolean(META_IS_REMOTE, track.isRemote)
            putLong(META_DURATION_MS, track.durationMs)
            putString(META_MIME_TYPE, track.mimeType)
            putInt(META_BITRATE, track.bitrate)
            putInt(META_SAMPLE_RATE, track.sampleRate)
            putInt(META_TRACK_NUMBER, track.trackNumber)
            putString(META_SERVER_PATH, track.serverPath)
            putString(META_YEAR, track.year)
            putLong(META_DATE_ADDED, track.dateAdded)
            putBoolean(META_IS_LIKED, track.isLiked)
            putString(META_PATH, track.path)
            track.replayGainTrackDb?.let { putDouble(META_REPLAYGAIN_TRACK_DB, it) }
            track.replayGainAlbumDb?.let { putDouble(META_REPLAYGAIN_ALBUM_DB, it) }
            track.replayGainTrackPeak?.let { putDouble(META_REPLAYGAIN_TRACK_PEAK, it) }
            track.replayGainAlbumPeak?.let { putDouble(META_REPLAYGAIN_ALBUM_PEAK, it) }
        }
    }

    /**
     * Deserializes a [MediaItem] back into a domain [AudioTrack].
     */
    fun toAudioTrack(mediaItem: MediaItem): AudioTrack {
        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        val fallbackUri = mediaItem.localConfiguration?.uri
            ?: mediaItem.requestMetadata.mediaUri
            ?: Uri.EMPTY
        val mimeType = extras?.getString(META_MIME_TYPE).orEmpty()

        return AudioTrack(
            id = mediaItem.mediaId,
            title = metadata.title?.toString() ?: "Unknown",
            artist = metadata.artist?.toString() ?: "Unknown Artist",
            album = metadata.albumTitle?.toString() ?: "Unknown Album",
            uri = fallbackUri,
            artworkUri = metadata.artworkUri ?: Uri.EMPTY,
            durationMs = extras?.getLong(META_DURATION_MS, 0L) ?: 0L,
            mimeType = mimeType.ifBlank { "audio/unknown" },
            isLocal = extras?.getBoolean(META_IS_LOCAL, false) ?: false,
            isRemote = extras?.getBoolean(META_IS_REMOTE, fallbackUri != Uri.EMPTY)
                ?: (fallbackUri != Uri.EMPTY),
            bitrate = extras?.getInt(META_BITRATE, 0) ?: 0,
            sampleRate = extras?.getInt(META_SAMPLE_RATE, 0) ?: 0,
            trackNumber = extras?.getInt(META_TRACK_NUMBER, 0) ?: 0,
            serverPath = extras?.getString(META_SERVER_PATH),
            path = extras?.getString(META_PATH),
            year = extras?.getString(META_YEAR),
            dateAdded = extras?.getLong(META_DATE_ADDED, 0L) ?: 0L,
            isLiked = extras?.getBoolean(META_IS_LIKED, false) ?: false,
            replayGainTrackDb = if (extras?.containsKey(META_REPLAYGAIN_TRACK_DB) == true) extras.getDouble(META_REPLAYGAIN_TRACK_DB) else null,
            replayGainAlbumDb = if (extras?.containsKey(META_REPLAYGAIN_ALBUM_DB) == true) extras.getDouble(META_REPLAYGAIN_ALBUM_DB) else null,
            replayGainTrackPeak = if (extras?.containsKey(META_REPLAYGAIN_TRACK_PEAK) == true) extras.getDouble(META_REPLAYGAIN_TRACK_PEAK) else null,
            replayGainAlbumPeak = if (extras?.containsKey(META_REPLAYGAIN_ALBUM_PEAK) == true) extras.getDouble(META_REPLAYGAIN_ALBUM_PEAK) else null
        )
    }
}
