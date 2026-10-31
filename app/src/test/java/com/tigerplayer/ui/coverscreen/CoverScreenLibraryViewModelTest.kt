package com.tigerplayer.ui.coverscreen

import android.net.Uri
import com.tigerplayer.data.model.Artist
import com.tigerplayer.data.model.ArtistSource
import com.tigerplayer.data.model.AudioTrack
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
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
    fun `default selected tab is HISTORY`() = runTest {
        val viewModel = CoverScreenLibraryViewModel(
            audioRepository = mockk(relaxed = true),
            spotifyRepository = mockk(relaxed = true),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        assertEquals(CoverScreenLibraryTab.HISTORY, viewModel.selectedTab.value)
    }

    @Test
    fun `selectTab updates and remembers selected tab`() = runTest {
        val viewModel = CoverScreenLibraryViewModel(
            audioRepository = mockk(relaxed = true),
            spotifyRepository = mockk(relaxed = true),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        viewModel.selectTab(CoverScreenLibraryTab.ARTISTS)
        assertEquals(CoverScreenLibraryTab.ARTISTS, viewModel.selectedTab.value)

        viewModel.selectTab(CoverScreenLibraryTab.SONGS)
        assertEquals(CoverScreenLibraryTab.SONGS, viewModel.selectedTab.value)

        viewModel.selectTab(CoverScreenLibraryTab.SPOTIFY_PLAYLISTS)
        assertEquals(CoverScreenLibraryTab.SPOTIFY_PLAYLISTS, viewModel.selectedTab.value)
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
    fun `CoverScreenLibraryTab does not contain constellation tab`() {
        val tabNames = CoverScreenLibraryTab.entries.map { it.name }
        org.junit.Assert.assertFalse(tabNames.contains("CONSTELLATION"))
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

    @Test
    fun `buildCoverScreenAlbums groups multiple tracks of same album and counts songs accurately`() {
        val tracks = listOf(
            track(id = "1", title = "One More Time", artist = "Daft Punk feat. Romanthony", album = "Discovery", trackNumber = 1),
            track(id = "2", title = "Aerodynamic", artist = "Daft Punk", album = "Discovery", trackNumber = 2),
            track(id = "3", title = "Digital Love", artist = "Daft Punk", album = "Discovery", trackNumber = 3),
            track(id = "4", title = "Da Funk", artist = "Daft Punk", album = "Homework", trackNumber = 1)
        )

        val albums = buildCoverScreenAlbums(tracks)

        assertEquals(2, albums.size)
        val discovery = albums.first { it.name == "Discovery" }
        assertEquals("Discovery", discovery.name)
        assertEquals(3, discovery.trackCount)
        assertEquals("Daft Punk", discovery.artistId)

        val homework = albums.first { it.name == "Homework" }
        assertEquals(1, homework.trackCount)
    }

    @Test
    fun `buildCoverScreenAlbums ignores blank albums and removes duplicates by id`() {
        val tracks = listOf(
            track(id = "1", title = "Orphan Track", artist = "Artist", album = ""),
            track(id = "2", title = "Whitespace Album", artist = "Artist", album = "   "),
            track(id = "3", title = "Valid Track", artist = "Artist", album = "Great Album"),
            track(id = "3", title = "Valid Track Duplicate", artist = "Artist", album = "Great Album")
        )

        val albums = buildCoverScreenAlbums(tracks)

        assertEquals(1, albums.size)
        assertEquals("Great Album", albums[0].name)
        assertEquals(1, albums[0].trackCount)
    }

    @Test
    fun `getCoverScreenAlbumTracks returns all songs of album sorted by trackNumber without dropping featured artists`() {
        val tracks = listOf(
            track(id = "3", title = "Digital Love", artist = "Daft Punk", album = "Discovery", trackNumber = 3),
            track(id = "1", title = "One More Time", artist = "Daft Punk feat. Romanthony", album = "Discovery", trackNumber = 1),
            track(id = "2", title = "Aerodynamic", artist = "Daft Punk", album = "Discovery", trackNumber = 2),
            track(id = "4", title = "Around the World", artist = "Daft Punk", album = "Homework", trackNumber = 1)
        )
        val album = CoverScreenAlbum(
            id = "alb_discovery",
            artistId = "Daft Punk",
            name = "Discovery",
            trackCount = 3
        )

        val albumSongs = getCoverScreenAlbumTracks(album, tracks)

        assertEquals(3, albumSongs.size)
        assertEquals(listOf("One More Time", "Aerodynamic", "Digital Love"), albumSongs.map { it.title })
    }

    @Test
    fun `getCoverScreenAlbumTracks returns empty list for null album`() {
        val tracks = listOf(track(id = "1", title = "Track", album = "Album"))
        val albumSongs = getCoverScreenAlbumTracks(null, tracks)
        assertTrue(albumSongs.isEmpty())
    }

    @Test
    fun `filterCoverScreenAlbums matches by album name, artist, and track titles or artists`() {
        val tracks = listOf(
            track(id = "1", title = "Get Lucky", artist = "Daft Punk feat. Pharrell Williams", album = "Random Access Memories"),
            track(id = "2", title = "Beat It", artist = "Michael Jackson", album = "Thriller")
        )
        val albums = buildCoverScreenAlbums(tracks)

        val byAlbumName = filterCoverScreenAlbums(albums, tracks, query = "Thriller")
        val byArtist = filterCoverScreenAlbums(albums, tracks, query = "Daft Punk")
        val byTrackTitle = filterCoverScreenAlbums(albums, tracks, query = "Get Lucky")
        val byTrackArtist = filterCoverScreenAlbums(albums, tracks, query = "Pharrell")
        val blankQuery = filterCoverScreenAlbums(albums, tracks, query = "   ")

        assertEquals(listOf("Thriller"), byAlbumName.map { it.name })
        assertEquals(listOf("Random Access Memories"), byArtist.map { it.name })
        assertEquals(listOf("Random Access Memories"), byTrackTitle.map { it.name })
        assertEquals(listOf("Random Access Memories"), byTrackArtist.map { it.name })
        assertEquals(2, blankQuery.size)
    }

    private fun track(
        id: String,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        trackNumber: Int = 0
    ) = AudioTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        uri = mockk<Uri>(relaxed = true),
        artworkUri = mockk<Uri>(relaxed = true),
        durationMs = 180_000L,
        mimeType = "audio/mpeg",
        trackNumber = trackNumber
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
