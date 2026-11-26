package com.tigerplayer.data.repository

import android.net.Uri
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.remote.api.LastFmApi
import com.tigerplayer.data.remote.api.SpotifyApiService
import com.tigerplayer.data.remote.model.DeezerArtistDto
import com.tigerplayer.data.remote.model.DeezerTrackDto
import com.tigerplayer.data.local.entity.ArtistCacheEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaDataRepositoryDeezerFallbackTest {

    private val tigerDao = mockk<TigerDao>(relaxed = true)
    private val spotifyApiService = mockk<SpotifyApiService>(relaxed = true)
    private val authManager = mockk<SpotifyAuthManager>()
    private val lastFmApi = mockk<LastFmApi>()
    private val audioRepository = mockk<AudioRepository>(relaxed = true)
    private val deezerRepository = mockk<DeezerRepository>()

    private fun repository() = MediaDataRepository(
        tigerDao = tigerDao,
        spotifyApiService = spotifyApiService,
        authManager = authManager,
        lastFmApi = lastFmApi,
        audioRepository = audioRepository,
        deezerRepository = deezerRepository
    )

    @Test
    fun `getHighResAlbumArt falls back to Deezer when Spotify is unauthenticated`() = runTest {
        coEvery { authManager.getValidToken() } returns ""
        coEvery {
            deezerRepository.findHighResAlbumArt("Digital Love", "Daft Punk", "Discovery")
        } returns "https://cdn.deezer.com/discovery_xl.jpg"

        val artUrl = repository().getHighResAlbumArt("Digital Love", "Daft Punk", "Discovery").first()
        assertEquals("https://cdn.deezer.com/discovery_xl.jpg", artUrl)
    }

    @Test
    fun `getArtistDetails resolves 1000x1000 Deezer portrait when Spotify is unauthenticated`() = runTest {
        every { tigerDao.getArtistCache("daft punk") } returns flowOf(null)
        coEvery { tigerDao.getArtistPlayCount("Daft Punk") } returns 5
        coEvery { tigerDao.getArtistMinutesListened("Daft Punk") } returns 12
        every { tigerDao.getTotalListeningTimeMs() } returns flowOf(600_000L)
        coEvery { tigerDao.getLocalArtworkForArtist("Daft Punk") } returns null
        coEvery { tigerDao.insertArtistCache(any()) } returns 1L
        coEvery { authManager.getValidToken() } returns ""
        coEvery { lastFmApi.getArtistInfo(artistName = "Daft Punk") } returns Response.success(mockk(relaxed = true))
        coEvery { deezerRepository.findArtistInfo("Daft Punk") } returns DeezerArtist(
            id = 27L,
            name = "Daft Punk",
            imageUrl = "https://cdn.deezer.com/daft_xl.jpg"
        )

        val details = repository().getArtistDetails("Daft Punk").first()
        assertEquals("https://cdn.deezer.com/daft_xl.jpg", details.imageUrl)
    }

    @Test
    fun `getArtistDetails replaces cached album-art fallback with Deezer portrait`() = runTest {
        val albumArt = "content://media/external/audio/albumart/42"
        every { tigerDao.getArtistCache("daft punk") } returns flowOf(
            ArtistCacheEntity(artistName = "daft punk", imageUrl = albumArt, bio = "bio", genres = "")
        )
        coEvery { tigerDao.getArtistPlayCount("Daft Punk") } returns 5
        coEvery { tigerDao.getArtistMinutesListened("Daft Punk") } returns 12
        every { tigerDao.getTotalListeningTimeMs() } returns flowOf(600_000L)
        coEvery { tigerDao.getLocalArtworkForArtist("Daft Punk") } returns albumArt
        val saved = slot<ArtistCacheEntity>()
        coEvery { tigerDao.insertArtistCache(capture(saved)) } returns 1L
        coEvery { authManager.getValidToken() } returns ""
        coEvery { lastFmApi.getArtistInfo(artistName = "Daft Punk") } returns Response.success(mockk(relaxed = true))
        coEvery { deezerRepository.findArtistInfo("Daft Punk") } returns DeezerArtist(
            id = 27L,
            name = "Daft Punk",
            imageUrl = "https://cdn.deezer.com/daft_xl.jpg"
        )

        val emissions = repository().getArtistDetails("Daft Punk").toList()

        assertEquals(albumArt, emissions.first().imageUrl)
        assertEquals("https://cdn.deezer.com/daft_xl.jpg", emissions.last().imageUrl)
        assertEquals("https://cdn.deezer.com/daft_xl.jpg", saved.captured.imageUrl)
    }

    @Test
    fun `getArtistDetails does not rewrite album-art fallback when no portrait is found`() = runTest {
        val albumArt = "content://media/external/audio/albumart/42"
        every { tigerDao.getArtistCache("daft punk") } returns flowOf(
            ArtistCacheEntity(artistName = "daft punk", imageUrl = albumArt, bio = "bio", genres = "")
        )
        coEvery { tigerDao.getArtistPlayCount("Daft Punk") } returns 5
        coEvery { tigerDao.getArtistMinutesListened("Daft Punk") } returns 12
        every { tigerDao.getTotalListeningTimeMs() } returns flowOf(600_000L)
        coEvery { tigerDao.getLocalArtworkForArtist("Daft Punk") } returns albumArt
        coEvery { authManager.getValidToken() } returns ""
        coEvery { lastFmApi.getArtistInfo(artistName = "Daft Punk") } returns Response.success(mockk(relaxed = true))
        coEvery { deezerRepository.findArtistInfo("Daft Punk") } returns null

        val emissions = repository().getArtistDetails("Daft Punk").toList()

        assertEquals(listOf(albumArt), emissions.map { it.imageUrl })
        coVerify(exactly = 0) { tigerDao.insertArtistCache(any()) }
    }

    @Test
    fun `getInfinitePlayRecommendations uses Deezer top tracks when Spotify is unauthenticated`() = runTest {
        val anchor = AudioTrack(
            id = "local:1",
            title = "One More Time",
            artist = "Daft Punk",
            album = "Discovery",
            durationMs = 320_000L,
            artworkUri = Uri.EMPTY
        )
        val localCandidate = AudioTrack(
            id = "local:2",
            title = "Aerodynamic",
            artist = "Daft Punk",
            album = "Discovery",
            durationMs = 207_000L,
            artworkUri = Uri.EMPTY
        )
        every { audioRepository.getCachedLocalTracks() } returns flowOf(listOf(anchor, localCandidate))
        coEvery { authManager.getValidToken() } returns ""
        coEvery { deezerRepository.findArtistTopTracksByName("Daft Punk", any()) } returns listOf(
            DeezerTrackDto(
                id = 999L,
                title = "Aerodynamic",
                artist = DeezerArtistDto(id = 27L, name = "Daft Punk")
            )
        )

        val recs = repository().getInfinitePlayRecommendations(anchor, limit = 5)
        assertEquals(listOf(localCandidate), recs)
    }
}
