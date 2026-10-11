package com.tigerplayer.ui.coverscreen

import com.tigerplayer.data.model.Artist
import com.tigerplayer.data.model.ArtistSource
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
