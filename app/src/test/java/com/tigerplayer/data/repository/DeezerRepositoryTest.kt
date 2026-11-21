package com.tigerplayer.data.repository

import com.tigerplayer.data.remote.api.DeezerApiService
import com.tigerplayer.data.remote.model.DeezerAlbumDto
import com.tigerplayer.data.remote.model.DeezerArtistDto
import com.tigerplayer.data.remote.model.DeezerSearchResponse
import com.tigerplayer.data.remote.model.DeezerTrackDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeezerRepositoryTest {

    private val apiService = mockk<DeezerApiService>()

    private fun sampleTrackDto(
        id: Long = 101L,
        title: String = "Instant Crush",
        artist: String = "Daft Punk",
        album: String = "Random Access Memories",
        coverXl: String = "https://cdn.deezer.com/cover_xl.jpg",
        preview: String = "https://cdn.deezer.com/preview.mp3"
    ) = DeezerTrackDto(
        id = id,
        title = title,
        duration = 337,
        preview = preview,
        gain = -9.5f,
        artist = DeezerArtistDto(id = 27L, name = artist, pictureXl = "https://cdn.deezer.com/artist_xl.jpg"),
        album = DeezerAlbumDto(id = 6575789L, title = album, coverXl = coverXl)
    )

    @Test
    fun `getChartTracks maps DTOs to AudioTrack and caches subsequent calls`() = runTest {
        coEvery { apiService.getChartTracks(25) } returns Response.success(
            DeezerSearchResponse(data = listOf(sampleTrackDto()), total = 1)
        )
        val repo = DeezerRepository(apiService)

        val first = repo.getChartTracks(25).first()
        val second = repo.getChartTracks(25).first()

        assertTrue(first.isSuccess)
        val track = first.getOrThrow().single()
        assertEquals("deezer:101", track.id)
        assertEquals("Instant Crush", track.title)
        assertEquals("Daft Punk", track.artist)
        assertEquals("https://cdn.deezer.com/preview.mp3", track.uri.toString())
        assertEquals("https://cdn.deezer.com/cover_xl.jpg", track.artworkUri.toString())
        assertEquals(337_000L, track.durationMs)
        assertEquals(second.getOrThrow(), first.getOrThrow())
        coVerify(exactly = 1) { apiService.getChartTracks(25) }
    }

    @Test
    fun `findHighResAlbumArt matches normalized title and artist`() = runTest {
        coEvery { apiService.searchTracks(any(), 5, 0) } returns Response.success(
            DeezerSearchResponse(data = listOf(sampleTrackDto()), total = 1)
        )
        val repo = DeezerRepository(apiService)

        val url = repo.findHighResAlbumArt("Instant Crush", "Daft Punk", "Random Access Memories")
        assertEquals("https://cdn.deezer.com/cover_xl.jpg", url)
    }

    @Test
    fun `findArtistInfo returns 1000x1000 picture_xl for matching artist`() = runTest {
        coEvery { apiService.searchArtists("Daft Punk", 5, 0) } returns Response.success(
            DeezerSearchResponse(
                data = listOf(
                    DeezerArtistDto(
                        id = 27L,
                        name = "Daft Punk",
                        pictureXl = "https://cdn.deezer.com/artist_xl.jpg"
                    )
                ),
                total = 1
            )
        )
        val repo = DeezerRepository(apiService)

        val artist = repo.findArtistInfo("Daft Punk")
        assertEquals(27L, artist?.id)
        assertEquals("https://cdn.deezer.com/artist_xl.jpg", artist?.imageUrl)
    }
}
