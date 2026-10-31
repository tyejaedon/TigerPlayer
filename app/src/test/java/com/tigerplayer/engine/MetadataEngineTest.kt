package com.tigerplayer.engine

import android.net.Uri
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.CachedTrackEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.repository.ArtistDetails
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.data.repository.LyricsRepository
import com.tigerplayer.data.repository.MediaDataRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for issue #116 and issue #64.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetadataEngineTest {

    private val mediaDataRepository = mockk<MediaDataRepository>(relaxed = true)
    private val lyricsRepository = mockk<LyricsRepository>(relaxed = true)
    private val tigerDao = mockk<TigerDao>(relaxed = true)
    private val audioRepository = mockk<AudioRepository>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)

    private fun engine(fetchOnlineArtwork: Boolean = true): MetadataEngine {
        every { tigerDao.getAllArtistCache() } returns flowOf(emptyList())
        every { settingsDataStore.settingsFlow } returns flowOf(TigerSettingsState(fetchOnlineArtwork = fetchOnlineArtwork))
        return MetadataEngine(
            mediaDataRepository = mediaDataRepository,
            lyricsRepository = lyricsRepository,
            tigerDao = tigerDao,
            audioRepository = audioRepository,
            settingsDataStore = settingsDataStore
        )
    }

    private fun track(
        id: Int,
        artist: String,
        isLocal: Boolean = true,
        artworkUri: String = "content://media/external/audio/albumart/$id",
        failedAt: Long = 0L,
        attempts: Int = 0,
        hasEmbedded: Boolean? = null
    ): AudioTrack {
        val uriMock = mockk<Uri>(relaxed = true)
        val artUriMock = mockk<Uri>(relaxed = true)
        every { artUriMock.toString() } returns artworkUri
        return AudioTrack(
            id = "track-$id",
            title = "Title $id",
            artist = artist,
            album = "Album",
            uri = uriMock,
            artworkUri = artUriMock,
            durationMs = 200_000L,
            mimeType = "audio/flac",
            isLocal = isLocal,
            artworkLookupFailedAt = failedAt,
            artworkLookupAttempts = attempts,
            hasEmbeddedArtwork = hasEmbedded
        )
    }

    @Test
    fun `preSeedArtistCache limits concurrent artist lookups`() = runTest {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        every { mediaDataRepository.getArtistDetails(any()) } answers {
            val artistName = firstArg<String>()
            flow {
                val active = inFlight.incrementAndGet()
                maxInFlight.updateAndGet { previous -> maxOf(previous, active) }
                try {
                    delay(1_000)
                    emit(ArtistDetails(name = artistName, imageUrl = "https://example.com/image.jpg", bio = null))
                } finally {
                    inFlight.decrementAndGet()
                }
            }
        }
        val tracks = (1..100).map { index -> track(index, artist = "Artist $index") }

        engine().preSeedArtistCache(tracks)

        assertEquals(ARTIST_PRESEED_CONCURRENCY, maxInFlight.get())
        assertTrue(maxInFlight.get() <= ARTIST_PRESEED_CONCURRENCY)
    }

    @Test
    fun `shouldRetryArtworkLookup enforces cool-off and exponential backoff`() {
        val now = 100_000_000_000L
        val dayMs = 24 * 60 * 60 * 1000L

        // Never attempted / never failed
        assertTrue(MetadataEngine.shouldRetryArtworkLookup(failedAt = 0L, attempts = 0, currentTimeMs = now))

        // Attempt 1: 7-day cool-off
        val failed1 = now - (3 * dayMs) // 3 days ago (< 7)
        assertFalse(MetadataEngine.shouldRetryArtworkLookup(failedAt = failed1, attempts = 1, currentTimeMs = now))
        val failed1Expired = now - (7 * dayMs) // 7 days ago
        assertTrue(MetadataEngine.shouldRetryArtworkLookup(failedAt = failed1Expired, attempts = 1, currentTimeMs = now))

        // Attempt 2: 14-day cool-off
        val failed2 = now - (10 * dayMs) // 10 days ago (< 14)
        assertFalse(MetadataEngine.shouldRetryArtworkLookup(failedAt = failed2, attempts = 2, currentTimeMs = now))
        val failed2Expired = now - (14 * dayMs) // 14 days ago
        assertTrue(MetadataEngine.shouldRetryArtworkLookup(failedAt = failed2Expired, attempts = 2, currentTimeMs = now))

        // Attempt 3: 28-day cool-off
        val failed3 = now - (20 * dayMs) // 20 days ago (< 28)
        assertFalse(MetadataEngine.shouldRetryArtworkLookup(failedAt = failed3, attempts = 3, currentTimeMs = now))
        val failed3Expired = now - (28 * dayMs) // 28 days ago
        assertTrue(MetadataEngine.shouldRetryArtworkLookup(failedAt = failed3Expired, attempts = 3, currentTimeMs = now))

        // Attempt 4+: capped at 30-day max interval
        val failed4 = now - (29 * dayMs) // 29 days ago (< 30)
        assertFalse(MetadataEngine.shouldRetryArtworkLookup(failedAt = failed4, attempts = 4, currentTimeMs = now))
        val failed4Expired = now - (30 * dayMs) // 30 days ago
        assertTrue(MetadataEngine.shouldRetryArtworkLookup(failedAt = failed4Expired, attempts = 4, currentTimeMs = now))
    }

    @Test
    fun `resolveTrackArtwork skips network lookup when track has embedded artwork`() = runTest {
        val testTrack = track(1, "Artist 1")
        coEvery { audioRepository.hasEmbeddedArtwork(testTrack) } returns true

        val result = engine().resolveTrackArtwork(testTrack)

        assertNull(result)
        coVerify(exactly = 0) { mediaDataRepository.getHighResAlbumArt(any(), any(), any()) }
    }

    @Test
    fun `resolveTrackArtwork skips network lookup when user setting disables online artwork`() = runTest {
        val testTrack = track(2, "Artist 2")
        coEvery { audioRepository.hasEmbeddedArtwork(testTrack) } returns false

        val result = engine(fetchOnlineArtwork = false).resolveTrackArtwork(testTrack)

        assertNull(result)
        coVerify(exactly = 0) { mediaDataRepository.getHighResAlbumArt(any(), any(), any()) }
    }

    @Test
    fun `resolveTrackArtwork skips retry when negative cache cool-off is active`() = runTest {
        val twoDaysAgo = System.currentTimeMillis() - (2L * 24 * 60 * 60 * 1000L)
        val testTrack = track(3, "Artist 3", failedAt = twoDaysAgo, attempts = 1)
        coEvery { audioRepository.hasEmbeddedArtwork(testTrack) } returns false
        coEvery { audioRepository.getCachedTrack("track-3") } returns null

        val result = engine().resolveTrackArtwork(testTrack)

        assertNull(result)
        coVerify(exactly = 0) { mediaDataRepository.getHighResAlbumArt(any(), any(), any()) }
    }

    @Test
    fun `resolveTrackArtwork records failure and increments attempts on Spotify miss`() = runTest {
        val eightDaysAgo = System.currentTimeMillis() - (8L * 24 * 60 * 60 * 1000L)
        val testTrack = track(4, "Artist 4", failedAt = eightDaysAgo, attempts = 1)
        coEvery { audioRepository.hasEmbeddedArtwork(testTrack) } returns false
        coEvery { audioRepository.getCachedTrack("track-4") } returns null
        every { mediaDataRepository.getHighResAlbumArt(any(), any(), any()) } returns flowOf(null)

        val result = engine().resolveTrackArtwork(testTrack)

        assertNull(result)
        coVerify(exactly = 1) {
            audioRepository.recordArtworkLookupFailure(
                trackId = "track-4",
                failedAt = any(),
                attempts = 2
            )
        }
    }

    @Test
    fun `resolveTrackArtwork updates track artwork on Spotify hit`() = runTest {
        val testTrack = track(5, "Artist 5")
        val highResUrl = "https://i.scdn.co/image/abc12345"
        coEvery { audioRepository.hasEmbeddedArtwork(testTrack) } returns false
        coEvery { audioRepository.getCachedTrack("track-5") } returns null
        every { mediaDataRepository.getHighResAlbumArt(any(), any(), any()) } returns flowOf(highResUrl)

        val result = engine().resolveTrackArtwork(testTrack)

        assertEquals(highResUrl, result?.toString())
        coVerify(exactly = 1) { audioRepository.updateTrackArtworkUri("track-5", highResUrl) }
        coVerify(exactly = 0) { audioRepository.recordArtworkLookupFailure(any(), any(), any()) }
    }

    @Test
    fun `crawlMissingLyricsBatch downloads lyrics and respects batch limits`() = runTest {
        val testEntity = CachedTrackEntity(
            id = "pending-1",
            title = "Pending Song",
            artist = "Pending Artist",
            album = "Pending Album",
            uriString = "content://audio/1",
            artworkUriString = "content://art/1",
            durationMs = 180_000L,
            mimeType = "audio/mp3",
            bitrate = 320,
            sampleRate = 44100,
            trackNumber = 1,
            isLiked = false,
            path = "/storage/music/pending.mp3"
        )
        every { tigerDao.getAllArtistCache() } returns flowOf(emptyList())
        every { lyricsRepository.getLyrics(any()) } returns flowOf(com.tigerplayer.data.model.Lyrics.None)
        coEvery { tigerDao.getTracksWithoutLyricsCache(20) } returns listOf(testEntity)
        every { settingsDataStore.settingsFlow } returns flowOf(TigerSettingsState())

        val engine = MetadataEngine(
            mediaDataRepository = mediaDataRepository,
            lyricsRepository = lyricsRepository,
            tigerDao = tigerDao,
            audioRepository = audioRepository,
            settingsDataStore = settingsDataStore
        )

        val processedCount = engine.crawlMissingLyricsBatch(20)

        assertEquals(1, processedCount)
        coVerify(exactly = 1) {
            lyricsRepository.getLyrics(match { it.id == "pending-1" && it.title == "Pending Song" })
        }
    }
}
