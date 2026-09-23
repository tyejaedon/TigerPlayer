package com.tigerplayer.data.repository

import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.ArtistCacheEntity
import com.tigerplayer.data.remote.api.LastFmApi
import com.tigerplayer.data.remote.api.SpotifyApiService
import com.tigerplayer.data.remote.model.LastFmArtist
import com.tigerplayer.data.remote.model.LastFmResponse
import com.tigerplayer.data.remote.model.SpotifyArtistDetail
import com.tigerplayer.data.remote.model.SpotifyFollowers
import com.tigerplayer.data.remote.model.SpotifyImage
import com.tigerplayer.data.remote.model.SpotifyPaging
import com.tigerplayer.data.remote.model.SpotifySearchResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import retrofit2.Response

/**
 * Regression coverage for the "wrong artist" bug: Spotify's search endpoint is a fuzzy,
 * relevance-ranked query, and Last.fm's `artist.getinfo` has `autocorrect=1` baked in - both
 * can resolve a lookup to an artist other than the one requested. `getArtistDetails` must reject
 * any oracle result whose returned name doesn't actually match what was asked for, rather than
 * caching a mismatched image/bio under the requested artist's cache key.
 */
class MediaDataRepositoryArtistIdentityTest {

    private val tigerDao = mockk<TigerDao>(relaxed = true)
    private val spotifyApiService = mockk<SpotifyApiService>()
    private val authManager = mockk<SpotifyAuthManager>()
    private val lastFmApi = mockk<LastFmApi>()
    private val audioRepository = mockk<AudioRepository>(relaxed = true)

    private fun repository() = MediaDataRepository(
        tigerDao = tigerDao,
        spotifyApiService = spotifyApiService,
        authManager = authManager,
        lastFmApi = lastFmApi,
        audioRepository = audioRepository
    )

    private fun stubLocalStats(artist: String) {
        every { tigerDao.getArtistCache(artist.lowercase()) } returns flowOf(null)
        coEvery { tigerDao.getArtistPlayCount(artist) } returns 0
        coEvery { tigerDao.getArtistMinutesListened(artist) } returns 0
        every { tigerDao.getTotalListeningTimeMs() } returns flowOf(0L)
        coEvery { tigerDao.getLocalArtworkForArtist(artist) } returns null
        coEvery { tigerDao.insertArtistCache(any()) } returns 1L
    }

    private fun spotifyArtist(name: String, imageUrl: String = "https://img/$name") = SpotifyArtistDetail(
        id = name,
        name = name,
        uri = "spotify:artist:$name",
        images = listOf(SpotifyImage(url = imageUrl, height = 640, width = 640)),
        genres = listOf("rock"),
        popularity = 90,
        followers = SpotifyFollowers(total = 1000)
    )

    private fun lastFmArtist(name: String, imageUrl: String = "https://img-lastfm/$name") = LastFmArtist(
        name = name,
        mbid = null,
        url = null,
        image = listOf(com.tigerplayer.data.remote.model.LastFmImage(url = imageUrl, size = "mega")),
        stats = null,
        tags = null,
        bio = com.tigerplayer.data.remote.model.LastFmBio(summary = "A genuine bio for $name.", content = null)
    )

    @Test
    fun `does not use a Spotify search result whose name does not match the requested artist`() = runTest {
        val requested = "Envy"
        stubLocalStats(requested)
        coEvery { authManager.getValidToken() } returns "token"
        // Spotify's fuzzy search returns an unrelated, more popular artist as the top hit.
        coEvery { spotifyApiService.searchArtist(any(), any(), any(), any()) } returns Response.success(
            SpotifySearchResponse(
                artists = SpotifyPaging(
                    items = listOf(spotifyArtist("Envy On The Coast", imageUrl = "https://img/wrong-artist")),
                    total = 1,
                    next = null,
                    limit = 5,
                    offset = 0
                ),
                albums = null,
                tracks = null,
                playlists = null
            )
        )
        coEvery { lastFmApi.getArtistInfo(artistName = requested, apiKey = any()) } returns Response.success(
            LastFmResponse(artist = null)
        )

        val sut = repository()
        val result = sut.getArtistDetails(requested).let { flow ->
            var last: ArtistDetails? = null
            flow.collect { last = it }
            last
        }

        assertNotEquals("https://img/wrong-artist", result?.imageUrl)
    }

    @Test
    fun `does not use a Last-fm autocorrected result whose name does not match the requested artist`() = runTest {
        val requested = "Envy"
        stubLocalStats(requested)
        coEvery { authManager.getValidToken() } returns ""
        coEvery { lastFmApi.getArtistInfo(artistName = requested, apiKey = any()) } returns Response.success(
            LastFmResponse(artist = lastFmArtist("Envy On The Coast", imageUrl = "https://img-lastfm/wrong-artist"))
        )

        val sut = repository()
        val result = sut.getArtistDetails(requested).let { flow ->
            var last: ArtistDetails? = null
            flow.collect { last = it }
            last
        }

        assertNotEquals("https://img-lastfm/wrong-artist", result?.imageUrl)
        assertNotEquals("A genuine bio for Envy On The Coast.", result?.bio)
    }

    @Test
    fun `accepts a Spotify result whose name matches the requested artist, ignoring accents and case`() = runTest {
        val requested = "Beyonce"
        stubLocalStats(requested)
        coEvery { authManager.getValidToken() } returns "token"
        coEvery { spotifyApiService.searchArtist(any(), any(), any(), any()) } returns Response.success(
            SpotifySearchResponse(
                artists = SpotifyPaging(
                    // Real Spotify name uses the accented form; must still match "Beyonce".
                    items = listOf(spotifyArtist("Beyoncé", imageUrl = "https://img/correct-artist")),
                    total = 1,
                    next = null,
                    limit = 5,
                    offset = 0
                ),
                albums = null,
                tracks = null,
                playlists = null
            )
        )
        coEvery { lastFmApi.getArtistInfo(artistName = requested, apiKey = any()) } returns Response.success(
            LastFmResponse(artist = null)
        )

        val sut = repository()
        val result = sut.getArtistDetails(requested).let { flow ->
            var last: ArtistDetails? = null
            flow.collect { last = it }
            last
        }

        assertEquals("https://img/correct-artist", result?.imageUrl)
    }

    @Test
    fun `accepts a Last-fm result whose name matches the requested artist exactly`() = runTest {
        val requested = "Radiohead"
        stubLocalStats(requested)
        coEvery { authManager.getValidToken() } returns ""
        coEvery { lastFmApi.getArtistInfo(artistName = requested, apiKey = any()) } returns Response.success(
            LastFmResponse(artist = lastFmArtist("Radiohead", imageUrl = "https://img-lastfm/correct-artist"))
        )

        val sut = repository()
        val result = sut.getArtistDetails(requested).let { flow ->
            var last: ArtistDetails? = null
            flow.collect { last = it }
            last
        }

        assertEquals("https://img-lastfm/correct-artist", result?.imageUrl)
        assertEquals("A genuine bio for Radiohead.", result?.bio)
    }
}

