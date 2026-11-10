package com.tigerplayer.service

import android.net.Uri
import androidx.media3.common.MediaMetadata
import com.tigerplayer.data.model.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemConverterTest {

    private val sampleUri = Uri.parse("content://media/external/audio/media/42")
    private val sampleArtworkUri = Uri.parse("content://media/external/audio/albumart/1")

    private val track = AudioTrack(
        id = "track_42",
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "A Night at the Opera",
        uri = sampleUri,
        artworkUri = sampleArtworkUri,
        durationMs = 354_000L,
        mimeType = "audio/flac",
        isLocal = true,
        isRemote = false,
        bitrate = 920_000,
        sampleRate = 44_100,
        trackNumber = 11,
        serverPath = null,
        year = "1975",
        dateAdded = 1600000000L,
        isLiked = true,
        path = "/storage/emulated/0/Music/Queen/bohemian.flac",
        replayGainTrackDb = -4.5,
        replayGainAlbumDb = -5.2,
        replayGainTrackPeak = 0.98,
        replayGainAlbumPeak = 0.99
    )

    @Test
    fun toMediaItem_populatesAllMetadataAndFlags() {
        val mediaItem = MediaItemConverter.toMediaItem(track)

        assertEquals("track_42", mediaItem.mediaId)
        assertEquals(sampleUri, mediaItem.localConfiguration?.uri)
        assertEquals(sampleUri, mediaItem.requestMetadata.mediaUri)

        val metadata = mediaItem.mediaMetadata
        assertEquals("Bohemian Rhapsody", metadata.title?.toString())
        assertEquals("Queen", metadata.artist?.toString())
        assertEquals("A Night at the Opera", metadata.albumTitle?.toString())
        assertEquals(sampleArtworkUri, metadata.artworkUri)
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, metadata.mediaType)
        assertTrue(metadata.isPlayable == true)
        assertFalse(metadata.isBrowsable == true)

        val extras = metadata.extras
        assertNotNull(extras)
        assertTrue(extras!!.getBoolean(MediaItemConverter.META_IS_LOCAL))
        assertFalse(extras.getBoolean(MediaItemConverter.META_IS_REMOTE))
        assertEquals(354_000L, extras.getLong(MediaItemConverter.META_DURATION_MS))
        assertEquals("audio/flac", extras.getString(MediaItemConverter.META_MIME_TYPE))
        assertEquals(920_000, extras.getInt(MediaItemConverter.META_BITRATE))
        assertEquals(44_100, extras.getInt(MediaItemConverter.META_SAMPLE_RATE))
        assertEquals(11, extras.getInt(MediaItemConverter.META_TRACK_NUMBER))
        assertEquals("1975", extras.getString(MediaItemConverter.META_YEAR))
        assertEquals(1600000000L, extras.getLong(MediaItemConverter.META_DATE_ADDED))
        assertTrue(extras.getBoolean(MediaItemConverter.META_IS_LIKED))
        assertEquals("/storage/emulated/0/Music/Queen/bohemian.flac", extras.getString(MediaItemConverter.META_PATH))
        assertEquals(-4.5, extras.getDouble(MediaItemConverter.META_REPLAYGAIN_TRACK_DB), 0.001)
        assertEquals(-5.2, extras.getDouble(MediaItemConverter.META_REPLAYGAIN_ALBUM_DB), 0.001)
        assertEquals(0.98, extras.getDouble(MediaItemConverter.META_REPLAYGAIN_TRACK_PEAK), 0.001)
        assertEquals(0.99, extras.getDouble(MediaItemConverter.META_REPLAYGAIN_ALBUM_PEAK), 0.001)
    }

    @Test
    fun toAudioTrack_restoresAllFieldsFromMediaItem() {
        val mediaItem = MediaItemConverter.toMediaItem(track)
        val restored = MediaItemConverter.toAudioTrack(mediaItem)

        assertEquals(track.id, restored.id)
        assertEquals(track.title, restored.title)
        assertEquals(track.artist, restored.artist)
        assertEquals(track.album, restored.album)
        assertEquals(track.uri, restored.uri)
        assertEquals(track.artworkUri, restored.artworkUri)
        assertEquals(track.durationMs, restored.durationMs)
        assertEquals(track.mimeType, restored.mimeType)
        assertEquals(track.isLocal, restored.isLocal)
        assertEquals(track.isRemote, restored.isRemote)
        assertEquals(track.bitrate, restored.bitrate)
        assertEquals(track.sampleRate, restored.sampleRate)
        assertEquals(track.trackNumber, restored.trackNumber)
        assertEquals(track.year, restored.year)
        assertEquals(track.dateAdded, restored.dateAdded)
        assertEquals(track.isLiked, restored.isLiked)
        assertEquals(track.path, restored.path)
        assertEquals(track.replayGainTrackDb, restored.replayGainTrackDb)
        assertEquals(track.replayGainAlbumDb, restored.replayGainAlbumDb)
        assertEquals(track.replayGainTrackPeak, restored.replayGainTrackPeak)
        assertEquals(track.replayGainAlbumPeak, restored.replayGainAlbumPeak)
    }
}
