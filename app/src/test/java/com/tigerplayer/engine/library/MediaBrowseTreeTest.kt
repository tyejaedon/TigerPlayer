package com.tigerplayer.engine.library

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.tigerplayer.data.local.dao.TrackStats
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.data.repository.AudioRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaBrowseTreeTest {

    private val audioRepository = mockk<AudioRepository>(relaxed = true)
    private lateinit var browseTree: MediaBrowseTree

    private val dummyUri = Uri.parse("content://audio/1")
    private val dummyArtwork = Uri.parse("content://artwork/1")

    private fun createTrack(
        id: String,
        title: String,
        artist: String,
        album: String,
        trackNumber: Int = 1,
        path: String? = null,
        isLiked: Boolean = false,
        dateAdded: Long = 1000L
    ) = AudioTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        uri = dummyUri,
        artworkUri = dummyArtwork,
        durationMs = 240_000L,
        mimeType = "audio/mp3",
        isLocal = true,
        trackNumber = trackNumber,
        path = path,
        isLiked = isLiked,
        dateAdded = dateAdded
    )

    private val testTracks = listOf(
        createTrack("t1", "Bohemian Rhapsody", "Queen", "A Night at the Opera", 11, "/Music/Queen/01.mp3", true, 2000L),
        createTrack("t2", "Love of My Life", "Queen", "A Night at the Opera", 9, "/Music/Queen/02.mp3", false, 1500L),
        createTrack("t3", "Hotel California", "Eagles", "Hotel California", 1, "/Music/Eagles/01.mp3", true, 3000L),
        createTrack("t4", "Starboy", "The Weeknd feat. Daft Punk", "Starboy", 1, "/Music/Pop/01.mp3", false, 1200L)
    )

    @Before
    fun setup() {
        browseTree = MediaBrowseTree(audioRepository)
        coEvery { audioRepository.getCachedLocalTracks() } returns flowOf(testTracks)
        coEvery { audioRepository.getLocalTracks(any()) } returns flowOf(testTracks)
        coEvery { audioRepository.getCachedTrack(any()) } returns null
    }

    @Test
    fun getRootItem_returnsBrowsableNonPlayableRoot() {
        val root = browseTree.getRootItem()

        assertEquals(MediaBrowseConstants.ROOT_ID, root.mediaId)
        assertEquals("TigerPlayer", root.mediaMetadata.title?.toString())
        assertTrue(root.mediaMetadata.isBrowsable == true)
        assertFalse(root.mediaMetadata.isPlayable == true)
        assertEquals(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, root.mediaMetadata.mediaType)
    }

    @Test
    fun getRootCategories_returnsSixStandardCategories() = runTest {
        val categories = browseTree.getChildren(MediaBrowseConstants.ROOT_ID)

        assertEquals(6, categories.size)
        val ids = categories.map { it.mediaId }
        assertTrue(ids.contains(MediaBrowseConstants.CATEGORY_ALBUMS))
        assertTrue(ids.contains(MediaBrowseConstants.CATEGORY_ARTISTS))
        assertTrue(ids.contains(MediaBrowseConstants.CATEGORY_PLAYLISTS))
        assertTrue(ids.contains(MediaBrowseConstants.CATEGORY_FOLDERS))
        assertTrue(ids.contains(MediaBrowseConstants.CATEGORY_HEAVY_ROTATION))
        assertTrue(ids.contains(MediaBrowseConstants.CATEGORY_DAY_LIST))
    }

    @Test
    fun getChildren_albumsCategory_aggregatesAlbumsCorrectly() = runTest {
        val albums = browseTree.getChildren(MediaBrowseConstants.CATEGORY_ALBUMS)

        assertEquals(3, albums.size)
        val titles = albums.map { it.mediaMetadata.title?.toString() }
        assertEquals(listOf("A Night at the Opera", "Hotel California", "Starboy"), titles)

        val queenAlbum = albums.first()
        assertEquals("${MediaBrowseConstants.PREFIX_ALBUM}A Night at the Opera", queenAlbum.mediaId)
        assertEquals(2, queenAlbum.mediaMetadata.totalTrackCount)
        assertTrue(queenAlbum.mediaMetadata.isBrowsable == true)
        assertTrue(queenAlbum.mediaMetadata.isPlayable == true)
        assertEquals(MediaMetadata.MEDIA_TYPE_ALBUM, queenAlbum.mediaMetadata.mediaType)
    }

    @Test
    fun getChildren_specificAlbum_returnsTracksSortedByTrackNumber() = runTest {
        val albumId = "${MediaBrowseConstants.PREFIX_ALBUM}A Night at the Opera"
        val tracks = browseTree.getChildren(albumId)

        assertEquals(2, tracks.size)
        assertEquals("Love of My Life", tracks[0].mediaMetadata.title?.toString())
        assertEquals("Bohemian Rhapsody", tracks[1].mediaMetadata.title?.toString())
        assertTrue(tracks[0].mediaMetadata.isPlayable == true)
        assertFalse(tracks[0].mediaMetadata.isBrowsable == true)
    }

    @Test
    fun getChildren_artistsCategory_aggregatesByBaseArtist() = runTest {
        val artists = browseTree.getChildren(MediaBrowseConstants.CATEGORY_ARTISTS)

        assertEquals(3, artists.size)
        val titles = artists.map { it.mediaMetadata.title?.toString() }
        assertEquals(listOf("Eagles", "Queen", "The Weeknd"), titles)

        val theWeeknd = artists.last()
        assertEquals("${MediaBrowseConstants.PREFIX_ARTIST}The Weeknd", theWeeknd.mediaId)
        assertTrue(theWeeknd.mediaMetadata.isBrowsable == true)
        assertTrue(theWeeknd.mediaMetadata.isPlayable == true)
    }

    @Test
    fun getChildren_specificArtist_returnsTracksByThatArtist() = runTest {
        val artistId = "${MediaBrowseConstants.PREFIX_ARTIST}Queen"
        val tracks = browseTree.getChildren(artistId)

        assertEquals(2, tracks.size)
        assertTrue(tracks.all { it.mediaMetadata.artist?.toString() == "Queen" })
    }

    @Test
    fun getChildren_playlistsCategory_returnsLikedRecentlyAddedAndCustom() = runTest {
        val customPlaylists = listOf(
            Playlist(id = 101L, name = "Road Trip", trackCount = 8, artworkUri = null, createdAt = 1000L)
        )
        every { audioRepository.getCustomPlaylists() } returns flowOf(customPlaylists)

        val playlists = browseTree.getChildren(MediaBrowseConstants.CATEGORY_PLAYLISTS)

        assertEquals(3, playlists.size)
        assertEquals(MediaBrowseConstants.PLAYLIST_LIKED_SONGS, playlists[0].mediaId)
        assertEquals(MediaBrowseConstants.PLAYLIST_RECENTLY_ADDED, playlists[1].mediaId)
        assertEquals("${MediaBrowseConstants.PREFIX_PLAYLIST}101", playlists[2].mediaId)
        assertEquals("Road Trip", playlists[2].mediaMetadata.title?.toString())
    }

    @Test
    fun getChildren_likedSongsPlaylist_returnsOnlyLikedTracks() = runTest {
        val liked = browseTree.getChildren(MediaBrowseConstants.PLAYLIST_LIKED_SONGS)

        assertEquals(2, liked.size)
        val titles = liked.map { it.mediaMetadata.title?.toString() }
        assertTrue(titles.contains("Bohemian Rhapsody"))
        assertTrue(titles.contains("Hotel California"))
    }

    @Test
    fun getChildren_recentlyAddedPlaylist_returnsTracksSortedByDateAddedDescending() = runTest {
        val recent = browseTree.getChildren(MediaBrowseConstants.PLAYLIST_RECENTLY_ADDED)

        assertEquals(4, recent.size)
        assertEquals("Hotel California", recent[0].mediaMetadata.title?.toString())
        assertEquals("Bohemian Rhapsody", recent[1].mediaMetadata.title?.toString())
    }

    @Test
    fun getChildren_customPlaylist_delegatesToRepository() = runTest {
        every { audioRepository.getTracksForPlaylist(101L) } returns flowOf(listOf(testTracks[0]))

        val tracks = browseTree.getChildren("${MediaBrowseConstants.PREFIX_PLAYLIST}101")

        assertEquals(1, tracks.size)
        assertEquals("Bohemian Rhapsody", tracks[0].mediaMetadata.title?.toString())
    }

    @Test
    fun getChildren_foldersCategory_indexesDirectoriesAndSubfolders() = runTest {
        val rootFolders = browseTree.getChildren(MediaBrowseConstants.CATEGORY_FOLDERS)

        assertEquals(1, rootFolders.size)
        assertEquals("${MediaBrowseConstants.PREFIX_FOLDER}/Music", rootFolders[0].mediaId)

        val subfolders = browseTree.getChildren("${MediaBrowseConstants.PREFIX_FOLDER}/Music")
        assertEquals(3, subfolders.size)

        val queenTracks = browseTree.getChildren("${MediaBrowseConstants.PREFIX_FOLDER}/Music/Queen")
        assertEquals(2, queenTracks.size)
    }

    @Test
    fun getChildren_heavyRotation_returnsStatsOrFallback() = runTest {
        every { audioRepository.getHeavyRotation(any()) } returns flowOf(
            listOf(TrackStats("t3", "Hotel California", "Eagles", null, 25))
        )

        val heavy = browseTree.getChildren(MediaBrowseConstants.CATEGORY_HEAVY_ROTATION)

        assertEquals(1, heavy.size)
        assertEquals("Hotel California", heavy[0].mediaMetadata.title?.toString())
    }

    @Test
    fun getChildren_dayList_returnsDaylistOrFallback() = runTest {
        every { audioRepository.getNeonDaylistTracks(any(), any(), any()) } returns flowOf(
            listOf(testTracks[0], testTracks[1])
        )

        val daylist = browseTree.getChildren(MediaBrowseConstants.CATEGORY_DAY_LIST)

        assertEquals(2, daylist.size)
        assertEquals("Bohemian Rhapsody", daylist[0].mediaMetadata.title?.toString())
    }

    @Test
    fun getChildren_pagination_slicesCorrectly() = runTest {
        val page0 = browseTree.getChildren(MediaBrowseConstants.ROOT_ID, page = 0, pageSize = 2)
        val page1 = browseTree.getChildren(MediaBrowseConstants.ROOT_ID, page = 1, pageSize = 2)
        val pageOutOfRange = browseTree.getChildren(MediaBrowseConstants.ROOT_ID, page = 5, pageSize = 2)

        assertEquals(2, page0.size)
        assertEquals(2, page1.size)
        assertTrue(pageOutOfRange.isEmpty())
        assertEquals(MediaBrowseConstants.CATEGORY_ALBUMS, page0[0].mediaId)
        assertEquals(MediaBrowseConstants.CATEGORY_PLAYLISTS, page1[0].mediaId)
    }

    @Test
    fun getItem_resolvesCategoriesContainersAndTracks() = runTest {
        val root = browseTree.getItem(MediaBrowseConstants.ROOT_ID)
        assertNotNull(root)

        val album = browseTree.getItem("${MediaBrowseConstants.PREFIX_ALBUM}Hotel California")
        assertNotNull(album)
        assertEquals("Hotel California", album!!.mediaMetadata.title?.toString())

        val artist = browseTree.getItem("${MediaBrowseConstants.PREFIX_ARTIST}Queen")
        assertNotNull(artist)
        assertEquals("Queen", artist!!.mediaMetadata.title?.toString())

        val track = browseTree.getItem("t1")
        assertNotNull(track)
        assertEquals("Bohemian Rhapsody", track!!.mediaMetadata.title?.toString())

        val nonExistent = browseTree.getItem("non_existent_id")
        assertNull(nonExistent)
    }

    @Test
    fun search_matchesTitleArtistOrAlbum() = runTest {
        val byTitle = browseTree.search("Bohemian")
        assertEquals(1, byTitle.size)
        assertEquals("Bohemian Rhapsody", byTitle[0].mediaMetadata.title?.toString())

        val byArtist = browseTree.search("Queen")
        assertEquals(2, byArtist.size)

        val byAlbum = browseTree.search("Opera")
        assertEquals(2, byAlbum.size)
        assertEquals(2, browseTree.searchCount("Opera"))
    }

    @Test
    fun resolveVoiceQuery_handlesExactTitleAndFuzzyIntents() = runTest {
        // Exact
        val exact = browseTree.resolveVoiceQuery("Hotel California")
        assertEquals(1, exact.size)
        assertEquals("Hotel California", exact[0].mediaMetadata.title?.toString())

        // Substring
        val fuzzy = browseTree.resolveVoiceQuery("Rhapsody")
        assertEquals(1, fuzzy.size)
        assertEquals("Bohemian Rhapsody", fuzzy[0].mediaMetadata.title?.toString())

        // Artist query
        val artistQuery = browseTree.resolveVoiceQuery("Queen")
        assertEquals(2, artistQuery.size)

        // Generic "play music"
        val generic = browseTree.resolveVoiceQuery("play music")
        assertEquals(4, generic.size)
    }

    @Test
    fun expandItemToTracks_expandsContainersIntoPlayableTracks() = runTest {
        val albumItem = MediaItem.Builder()
            .setMediaId("${MediaBrowseConstants.PREFIX_ALBUM}A Night at the Opera")
            .build()
        val expandedAlbum = browseTree.expandItemToTracks(albumItem)
        assertEquals(2, expandedAlbum.size)

        val trackItem = MediaItem.Builder().setMediaId("t3").build()
        val expandedTrack = browseTree.expandItemToTracks(trackItem)
        assertEquals(1, expandedTrack.size)
        assertEquals("Hotel California", expandedTrack[0].mediaMetadata.title?.toString())
    }
}
