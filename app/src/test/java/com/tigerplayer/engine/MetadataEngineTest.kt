package com.tigerplayer.engine

import android.net.Uri
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.CachedTrackEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.repository.ArtistDetails
import com.tigerplayer.data.repository.LyricsRepository
import com.tigerplayer.data.repository.MediaDataRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for issue #116.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetadataEngineTest {

    private val mediaDataRepository = mockk<MediaDataRepository>()
    private val lyricsRepository = mockk<LyricsRepository>(relaxed = true)
    private val tigerDao = mockk<TigerDao>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>()

    private fun engine(): MetadataEngine {
        every { tigerDao.getAllArtistCache() } returns flowOf(emptyList())
        every { settingsDataStore.settingsFlow } returns flowOf(TigerSettingsState())
        return MetadataEngine(mediaDataRepository, lyricsRepository, tigerDao, settingsDataStore)
    }

    private fun track(id: Int, artist: String) = AudioTrack(
        id = "track-$id",
        title = "Title $id",
        artist = artist,
        album = "Album",
        uri = mockk<Uri>(relaxed = true),
        artworkUri = mockk<Uri>(relaxed = true),
        durationMs = 200_000L,
        mimeType = "audio/flac",
        isLocal = true
    )

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
        every { lyricsRepository.getLyrics(any()) } returns flowOf(Lyrics.None)
        coEvery { tigerDao.getTracksWithoutLyricsCache(20) } returns listOf(testEntity)
        every { settingsDataStore.settingsFlow } returns flowOf(TigerSettingsState())

        val engine = MetadataEngine(
            mediaDataRepository = mediaDataRepository,
            lyricsRepository = lyricsRepository,
            tigerDao = tigerDao,
            settingsDataStore = settingsDataStore
        )

        val processedCount = engine.crawlMissingLyricsBatch(20)

        assertEquals(1, processedCount)
        coVerify(exactly = 1) {
            lyricsRepository.getLyrics(match { it.id == "pending-1" && it.title == "Pending Song" })
        }
    }
}
