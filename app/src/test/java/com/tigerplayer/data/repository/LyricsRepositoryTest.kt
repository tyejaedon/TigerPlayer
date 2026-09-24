package com.tigerplayer.data.repository

import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.LyricsCacheEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.remote.api.LrclibApi
import com.tigerplayer.data.remote.api.LrclibResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LyricsRepositoryTest {

    private val lrclibApi = mockk<LrclibApi>()
    private val tigerDao = mockk<TigerDao>(relaxed = true)
    private val repository = LyricsRepository(lrclibApi, tigerDao)

    @Test
    fun `spotify placeholder album falls back to albumless lookup and returns lyrics`() = runTest {
        val track = AudioTrack(
            id = "spotify:track:123",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "Spotify",
            uri = android.net.Uri.EMPTY,
            artworkUri = android.net.Uri.EMPTY,
            durationMs = 355_000L,
            mimeType = "audio/spotify",
            isRemote = true
        )
        coEvery { tigerDao.getLyricsCache(track.id) } returns null
        coEvery {
            lrclibApi.getLyrics(
                trackName = "Bohemian Rhapsody",
                artistName = "Queen",
                albumName = null
            )
        } returns Response.success(
            LrclibResponse(
                plainLyrics = "Is this the real life?",
                syncedLyrics = null
            )
        )

        val lyrics = repository.getLyrics(track).first()

        assertEquals("Is this the real life?", lyrics)
        coVerify(exactly = 1) {
            lrclibApi.getLyrics(
                trackName = "Bohemian Rhapsody",
                artistName = "Queen",
                albumName = null
            )
        }
        coVerify(exactly = 1) {
            tigerDao.insertLyricsCache(
                match {
                    it.trackId == track.id &&
                        it.plainLyrics == "Is this the real life?" &&
                        it.syncedLyrics == null
                }
            )
        }
    }

    @Test
    fun `strict lookup retries with cleaned metadata after a miss`() = runTest {
        val track = AudioTrack(
            id = "local-1",
            title = "Bohemian Rhapsody - Remastered 2011",
            artist = "Queen feat. Someone",
            album = "A Night at the Opera (Deluxe)",
            uri = android.net.Uri.EMPTY,
            artworkUri = android.net.Uri.EMPTY,
            durationMs = 355_000L,
            mimeType = "audio/mp3",
            isLocal = true
        )
        coEvery { tigerDao.getLyricsCache(track.id) } returns null
        coEvery {
            lrclibApi.getLyrics(any(), any(), any())
        } returns Response.error(
            404,
            """{"message":"Failed to find specified track"}""".toResponseBody("application/json".toMediaTypeOrNull())
        )
        coEvery {
            lrclibApi.getLyrics(
                trackName = "Bohemian Rhapsody",
                artistName = "Queen",
                albumName = "A Night at the Opera"
            )
        } returns Response.success(
            LrclibResponse(
                plainLyrics = null,
                syncedLyrics = "[00:01.00]Is this the real life?"
            )
        )

        val lyrics = repository.getLyrics(track).first()

        assertEquals("[00:01.00]Is this the real life?", lyrics)
        coVerify {
            lrclibApi.getLyrics(
                trackName = "Bohemian Rhapsody - Remastered 2011",
                artistName = "Queen feat. Someone",
                albumName = "A Night at the Opera"
            )
        }
        coVerify {
            lrclibApi.getLyrics(
                trackName = "Bohemian Rhapsody",
                artistName = "Queen",
                albumName = "A Night at the Opera"
            )
        }
    }

    @Test
    fun `server error aborts remaining lookup variants and caches the miss`() = runTest {
        val track = AudioTrack(
            id = "local-5xx",
            title = "Bohemian Rhapsody - Remastered 2011",
            artist = "Queen feat. Someone",
            album = "A Night at the Opera (Deluxe)",
            uri = android.net.Uri.EMPTY,
            artworkUri = android.net.Uri.EMPTY,
            durationMs = 355_000L,
            mimeType = "audio/mp3",
            isLocal = true
        )
        coEvery { tigerDao.getLyricsCache(track.id) } returns null
        coEvery { lrclibApi.getLyrics(any(), any(), any()) } returns Response.error(
            520,
            "Cloudflare error".toResponseBody("text/plain".toMediaTypeOrNull())
        )

        val lyrics = repository.getLyrics(track).first()

        assertEquals(null, lyrics)
        coVerify(exactly = 1) { lrclibApi.getLyrics(any(), any(), any()) }
        coVerify(exactly = 1) {
            tigerDao.insertLyricsCache(
                match {
                    it.trackId == track.id &&
                        it.plainLyrics == null &&
                        it.syncedLyrics == null
                }
            )
        }
    }

    @Test
    fun `negative cache hit avoids another remote lookup`() = runTest {
        val track = AudioTrack(
            id = "local-no-lyrics",
            title = "Unknown Song",
            artist = "Unknown Artist",
            album = "Unknown Album",
            uri = android.net.Uri.EMPTY,
            artworkUri = android.net.Uri.EMPTY,
            durationMs = 180_000L,
            mimeType = "audio/mp3",
            isLocal = true
        )
        coEvery { tigerDao.getLyricsCache(track.id) } returns null andThen LyricsCacheEntity(
            trackId = track.id,
            plainLyrics = null,
            syncedLyrics = null,
            lastAccessed = 4_000_000_000_000L
        )
        coEvery { lrclibApi.getLyrics(any(), any(), any()) } returns Response.error(
            404,
            "Not found".toResponseBody("text/plain".toMediaTypeOrNull())
        )

        val firstResult = repository.getLyrics(track).first()
        val cachedResult = repository.getLyrics(track).first()

        assertEquals(null, firstResult)
        assertEquals(null, cachedResult)
        coVerify(exactly = 1) { lrclibApi.getLyrics(any(), any(), any()) }
        coVerify(exactly = 1) {
            tigerDao.insertLyricsCache(
                match {
                    it.trackId == track.id &&
                        it.plainLyrics == null &&
                        it.syncedLyrics == null
                }
            )
        }
    }
}
