package com.tigerplayer.data.repository.deezer

import com.tigerplayer.data.remote.api.deezer.DeezerAlbumDto
import com.tigerplayer.data.remote.api.deezer.DeezerApiService
import com.tigerplayer.data.remote.api.deezer.DeezerArtistDto
import com.tigerplayer.data.remote.api.deezer.DeezerErrorDto
import com.tigerplayer.data.remote.api.deezer.DeezerSearchResponse
import com.tigerplayer.data.remote.api.deezer.DeezerTrackDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeezerRepositoryTest {

    private val api = mockk<DeezerApiService>()
    private val repository = DeezerRepository(api)

    @Test
    fun `getTrack returns track and caches subsequent calls`() = runTest {
        val dto = DeezerTrackDto(id = 123L, title = "Around the World")
        coEvery { api.getTrack(123L) } returns dto

        val first = repository.getTrack(123L)
        val second = repository.getTrack(123L)

        assertEquals(dto, first)
        assertEquals(dto, second)
        coVerify(exactly = 1) { api.getTrack(123L) }
    }

    @Test
    fun `getTrack returns null when Deezer returns 200 OK error payload`() = runTest {
        coEvery { api.getTrack(999L) } returns DeezerTrackDto(
            id = 0L,
            error = DeezerErrorDto(type = "DataException", message = "no data", code = 800)
        )

        val result = repository.getTrack(999L)
        assertNull(result)
    }

    @Test
    fun `getAlbumArt sanitizes quotes and falls back to unquoted search`() = runTest {
        coEvery { api.searchAlbum("album:\"Greatest  Hits\" artist:\"Daft  Punk\"") } returns DeezerSearchResponse()
        coEvery { api.searchAlbum("Greatest  Hits Daft  Punk") } returns DeezerSearchResponse(
            data = listOf(DeezerAlbumDto(id = 1L, title = "Greatest Hits", coverXl = "https://dzcdn.net/cover.jpg"))
        )

        val art = repository.getAlbumArt("\"Greatest\" Hits", "Daft \"Punk\"")
        assertEquals("https://dzcdn.net/cover.jpg", art)
    }

    @Test
    fun `getAlbumArt falls back and caches cover URL`() = runTest {
        coEvery { api.searchAlbum("album:\"Discovery\" artist:\"Daft Punk\"") } returns DeezerSearchResponse()
        coEvery { api.searchAlbum("Discovery Daft Punk") } returns DeezerSearchResponse(
            data = listOf(DeezerAlbumDto(id = 1L, title = "Discovery", coverXl = "https://dzcdn.net/discovery.jpg"))
        )

        val first = repository.getAlbumArt("Discovery", "Daft Punk")
        val second = repository.getAlbumArt("Discovery", "Daft Punk")

        assertEquals("https://dzcdn.net/discovery.jpg", first)
        assertEquals("https://dzcdn.net/discovery.jpg", second)
        coVerify(exactly = 1) { api.searchAlbum("Discovery Daft Punk") }
    }

    @Test
    fun `getArtistPicture returns null on quota error`() = runTest {
        coEvery { api.searchArtist("Daft Punk") } returns DeezerSearchResponse(
            error = DeezerErrorDto(type = "Exception", message = "Quota limit exceeded", code = 4)
        )

        assertNull(repository.getArtistPicture("Daft Punk"))
    }

    @Test
    fun `searchTracks returns empty list on blank query and rethrows CancellationException`() = runTest {
        assertTrue(repository.searchTracks("   ").isEmpty())

        coEvery { api.searchTrack("test") } throws CancellationException("cancelled")
        var caught = false
        try {
            repository.searchTracks("test")
        } catch (_: CancellationException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `getArtistTopTracks fetches top tracks and caches result`() = runTest {
        val artist = DeezerArtistDto(id = 27L, name = "Daft Punk")
        val topTrack = DeezerTrackDto(id = 10L, title = "One More Time")
        coEvery { api.searchArtist("Daft Punk") } returns DeezerSearchResponse(data = listOf(artist))
        coEvery { api.getArtistTopTracks(27L, any()) } returns DeezerSearchResponse(data = listOf(topTrack))

        val first = repository.getArtistTopTracks("Daft Punk")
        val second = repository.getArtistTopTracks("Daft Punk")

        assertEquals(listOf(topTrack), first)
        assertEquals(listOf(topTrack), second)
        coVerify(exactly = 1) { api.getArtistTopTracks(27L, any()) }
    }
}
