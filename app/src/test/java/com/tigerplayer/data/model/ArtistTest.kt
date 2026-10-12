package com.tigerplayer.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistTest {

    @Test
    fun `default values for optional fields are set correctly`() {
        val artist = Artist(
            id = "test-artist",
            name = "Test Artist",
            source = ArtistSource.LOCAL
        )

        assertEquals("test-artist", artist.id)
        assertEquals("Test Artist", artist.name)
        assertEquals(ArtistSource.LOCAL, artist.source)
        assertEquals(0, artist.trackCount)
        assertEquals(0, artist.albumCount)
        assertEquals(emptyList<String>(), artist.genres)
        assertNull(artist.imageUrl)
    }

    @Test
    fun `all properties are retained when explicitly initialized`() {
        val artist = Artist(
            id = "spotify-123",
            name = "The Sound",
            source = ArtistSource.SPOTIFY,
            trackCount = 25,
            albumCount = 3,
            genres = listOf("indie", "rock"),
            imageUrl = "https://example.com/artist.jpg"
        )

        assertEquals("spotify-123", artist.id)
        assertEquals("The Sound", artist.name)
        assertEquals(ArtistSource.SPOTIFY, artist.source)
        assertEquals(25, artist.trackCount)
        assertEquals(3, artist.albumCount)
        assertEquals(listOf("indie", "rock"), artist.genres)
        assertEquals("https://example.com/artist.jpg", artist.imageUrl)
    }

    @Test
    fun `copy allows updating individual properties`() {
        val original = Artist(
            id = "artist-1",
            name = "Original Name",
            source = ArtistSource.LOCAL
        )

        val updated = original.copy(
            imageUrl = "https://example.com/art.png",
            albumCount = 5
        )

        assertEquals(original.id, updated.id)
        assertEquals(original.name, updated.name)
        assertEquals(original.source, updated.source)
        assertEquals(5, updated.albumCount)
        assertEquals("https://example.com/art.png", updated.imageUrl)
    }
}
