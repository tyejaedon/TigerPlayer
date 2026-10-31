package com.tigerplayer.data.repository

import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.LyricsCacheEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.LrcLine
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.remote.api.KugouApi
import com.tigerplayer.data.remote.api.KugouCandidate
import com.tigerplayer.data.remote.api.KugouDownloadResponse
import com.tigerplayer.data.remote.api.KugouSearchResponse
import com.tigerplayer.data.remote.api.LrclibApi
import com.tigerplayer.data.remote.api.LrclibResponse
import com.tigerplayer.data.source.LocalLyricsSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
    private val kugouApi = mockk<KugouApi>(relaxed = true)
    private val tigerDao = mockk<TigerDao>(relaxed = true)
    private val localLyricsSource = mockk<LocalLyricsSource>()
    private val repository = LyricsRepository(lrclibApi, kugouApi, tigerDao, localLyricsSource)

    @Before
    fun setUp() {
        every { localLyricsSource.getSidecarLyrics(any()) } returns Lyrics.None
        every { localLyricsSource.getEmbeddedLyrics(any()) } returns Lyrics.None
        coEvery { lrclibApi.searchLyrics(any(), any()) } returns Response.success(emptyList())
        coEvery { kugouApi.searchLyrics(any(), any()) } returns Response.success(KugouSearchResponse(200, emptyList()))
    }

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

        assertEquals(Lyrics.Plain("Is this the real life?"), lyrics)
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

        assertEquals(Lyrics.Synced(lines = listOf(LrcLine(1000L, "Is this the real life?")), offsetMs = 0L), lyrics)
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

        assertEquals(Lyrics.None, lyrics)
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

        assertEquals(Lyrics.None, firstResult)
        assertEquals(Lyrics.None, cachedResult)
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
    fun `local lrc sidecar takes precedence over Room cache and remote API`() = runTest {
        val track = AudioTrack(
            id = "local-sidecar-1",
            title = "Sidecar Song",
            artist = "Local Artist",
            album = "Local Album",
            uri = android.net.Uri.EMPTY,
            artworkUri = android.net.Uri.EMPTY,
            durationMs = 200_000L,
            mimeType = "audio/mp3",
            isLocal = true,
            path = "/storage/music/Sidecar Song.mp3"
        )
        val sidecarLyrics = Lyrics.Synced(
            lines = listOf(LrcLine(1000L, "Local sidecar line")),
            offsetMs = 0L
        )
        every { localLyricsSource.getSidecarLyrics(track) } returns sidecarLyrics

        val result = repository.getLyrics(track).first()

        assertEquals(sidecarLyrics, result)
        // Neither Room cache nor remote LRCLIB should be consulted
        coVerify(exactly = 0) { tigerDao.getLyricsCache(any()) }
        coVerify(exactly = 0) { lrclibApi.getLyrics(any(), any(), any()) }
    }

    @Test
    fun `embedded lyrics take precedence over Room cache and remote API`() = runTest {
        val track = AudioTrack(
            id = "local-embedded-1",
            title = "Embedded Song",
            artist = "Local Artist",
            album = "Local Album",
            uri = android.net.Uri.EMPTY,
            artworkUri = android.net.Uri.EMPTY,
            durationMs = 200_000L,
            mimeType = "audio/flac",
            isLocal = true,
            path = "/storage/music/Embedded Song.flac"
        )
        val embeddedLyrics = Lyrics.Plain("Embedded FLAC lyrics")
        every { localLyricsSource.getSidecarLyrics(track) } returns Lyrics.None
        every { localLyricsSource.getEmbeddedLyrics(track) } returns embeddedLyrics

        val result = repository.getLyrics(track).first()

        assertEquals(embeddedLyrics, result)
        // Neither Room cache nor remote LRCLIB should be consulted
        coVerify(exactly = 0) { tigerDao.getLyricsCache(any()) }
        coVerify(exactly = 0) { lrclibApi.getLyrics(any(), any(), any()) }
    }

    @Test
    fun `LRCLIB search fallback is used when exact getLyrics misses`() = runTest {
        val track = AudioTrack(
            id = "search-1",
            title = "Search Song",
            artist = "Search Artist",
            album = "Search Album",
            uri = android.net.Uri.EMPTY,
            artworkUri = android.net.Uri.EMPTY,
            durationMs = 200_000L,
            mimeType = "audio/mp3",
            isLocal = true
        )
        coEvery { tigerDao.getLyricsCache(track.id) } returns null
        coEvery { lrclibApi.getLyrics(any(), any(), any()) } returns Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaTypeOrNull())
        )
        coEvery { lrclibApi.searchLyrics(any(), any()) } returns Response.success(
            listOf(
                LrclibResponse(
                    plainLyrics = "Search line plain",
                    syncedLyrics = "[00:05.00]Search line synced"
                )
            )
        )

        val result = repository.getLyrics(track).first()

        assertTrue(result is Lyrics.Synced)
        assertEquals("Search line synced", (result as Lyrics.Synced).lines.first().text)
    }

    @Test
    fun `Kugou secondary provider is consulted when LRCLIB has no synced lyrics`() = runTest {
        val track = AudioTrack(
            id = "kugou-1",
            title = "Kugou Track",
            artist = "Kugou Artist",
            album = "Kugou Album",
            uri = android.net.Uri.EMPTY,
            artworkUri = android.net.Uri.EMPTY,
            durationMs = 200_000L,
            mimeType = "audio/mp3",
            isLocal = true
        )
        coEvery { tigerDao.getLyricsCache(track.id) } returns null
        coEvery { lrclibApi.getLyrics(any(), any(), any()) } returns Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaTypeOrNull())
        )
        coEvery { lrclibApi.searchLyrics(any(), any()) } returns Response.success(emptyList())

        val candidate = KugouCandidate(id = "kg-123", accesskey = "kg-key", singer = "Kugou Artist", song = "Kugou Track", duration = 200_000L)
        coEvery { kugouApi.searchLyrics(any(), any()) } returns Response.success(
            KugouSearchResponse(status = 200, candidates = listOf(candidate))
        )
        // base64 for "[00:10.50]Kugou synced line"
        val lrcContent = "[00:10.50]Kugou synced line\n"
        val base64Lrc = java.util.Base64.getEncoder().encodeToString(lrcContent.toByteArray(Charsets.UTF_8))
        coEvery { kugouApi.downloadLyrics("kg-123", "kg-key") } returns Response.success(
            KugouDownloadResponse(status = 200, content = base64Lrc, fmt = "lrc")
        )

        val result = repository.getLyrics(track).first()

        assertTrue(result is Lyrics.Synced)
        val synced = result as Lyrics.Synced
        assertEquals(1, synced.lines.size)
        assertEquals(10500L, synced.lines.first().timeMs)
        assertEquals("Kugou synced line", synced.lines.first().text)
    }
}
