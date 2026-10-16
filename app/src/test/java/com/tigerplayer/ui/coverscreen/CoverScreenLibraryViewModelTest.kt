package com.tigerplayer.ui.coverscreen

import android.net.Uri
import com.tigerplayer.data.model.Artist
import com.tigerplayer.data.model.ArtistSource
import com.tigerplayer.data.model.AudioTrack
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverScreenLibraryViewModelTest {

    @Test
    fun `page is capped at fifty artists`() = runTest {
        val artists = (1..75).map { artist(it) }

        val page = filterAndPageArtists(artists, query = "", page = 0, pageSize = 100)

        assertEquals(50, page.size)
    }

    @Test
    fun `page uses offset and returns empty when offset exceeds results`() = runTest {
        val artists = (1..5).map { artist(it) }

        val secondPage = filterAndPageArtists(artists, query = "", page = 1, pageSize = 2)
        val beyondLastPage = filterAndPageArtists(artists, query = "", page = 3, pageSize = 2)

        assertEquals(listOf("Artist 3", "Artist 4"), secondPage.map { it.name })
        assertTrue(beyondLastPage.isEmpty())
    }

    @Test
    fun `negative page is treated as first page`() = runTest {
        val page = filterAndPageArtists(
            artists = listOf(artist(2), artist(1)),
            query = "",
            page = -1,
            pageSize = 2
        )

        assertEquals(listOf("Artist 1", "Artist 2"), page.map { it.name })
    }

    @Test
    fun `query matches artist name or genre without changing page size`() = runTest {
        val artists = listOf(
            artist(1, name = "Ambient Group"),
            artist(2, name = "The Echoes", genres = listOf("ambient pop")),
            artist(3, name = "Other")
        )

        val page = filterAndPageArtists(artists, query = "AMBIENT", page = 0, pageSize = 10)

        assertEquals(listOf("Ambient Group", "The Echoes"), page.map { it.name })
    }

    @Test
    fun `CoverScreenLibraryTab contains SONGS placed between ALBUMS and PLAYLISTS`() {
        val tabs = CoverScreenLibraryTab.entries
        val albumsIndex = tabs.indexOf(CoverScreenLibraryTab.ALBUMS)
        val songsIndex = tabs.indexOf(CoverScreenLibraryTab.SONGS)
        val playlistsIndex = tabs.indexOf(CoverScreenLibraryTab.PLAYLISTS)

        assertEquals("Songs", CoverScreenLibraryTab.SONGS.label)
        assertEquals(albumsIndex + 1, songsIndex)
        assertEquals(songsIndex + 1, playlistsIndex)
    }

    @Test
    fun `filterAndSortTracks sorts tracks alphabetically by title case-insensitively`() {
        val tracks = listOf(
            track(id = "1", title = "banana"),
            track(id = "2", title = "Apple"),
            track(id = "3", title = "cherry")
        )

        val sorted = filterAndSortTracks(tracks)

        assertEquals(listOf("Apple", "banana", "cherry"), sorted.map { it.title })
    }

    @Test
    fun `filterAndSortTracks filters by title or artist case-insensitively`() {
        val tracks = listOf(
            track(id = "1", title = "Song A", artist = "The Beat"),
            track(id = "2", title = "Beat It", artist = "Michael"),
            track(id = "3", title = "Other Song", artist = "Other Artist")
        )

        val resultTitle = filterAndSortTracks(tracks, query = "song a")
        val resultArtistOrTitle = filterAndSortTracks(tracks, query = "beat")

        assertEquals(listOf("Song A"), resultTitle.map { it.title })
        assertEquals(listOf("Beat It", "Song A"), resultArtistOrTitle.map { it.title })
    }

    @Test
    fun `filterAndSortTracks returns all tracks sorted when query is blank`() {
        val tracks = listOf(
            track(id = "1", title = "Zebra"),
            track(id = "2", title = "Alpha")
        )

        val result = filterAndSortTracks(tracks, query = "   ")

        assertEquals(listOf("Alpha", "Zebra"), result.map { it.title })
    }

    private fun track(
        id: String,
        title: String,
        artist: String = "Artist"
    ) = AudioTrack(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        uri = mockk<Uri>(relaxed = true),
        artworkUri = mockk<Uri>(relaxed = true),
        durationMs = 180_000L,
        mimeType = "audio/mpeg"
    )

    private fun artist(
        number: Int,
        name: String = "Artist $number",
        genres: List<String> = emptyList()
    ) = Artist(
        id = number.toString(),
        name = name,
        source = ArtistSource.SPOTIFY,
        genres = genres
    )
}
