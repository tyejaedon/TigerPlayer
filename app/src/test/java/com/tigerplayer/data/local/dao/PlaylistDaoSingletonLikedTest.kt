package com.tigerplayer.data.local.dao

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tigerplayer.data.local.TigerDatabase
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistDaoSingletonLikedTest {

    private lateinit var database: TigerDatabase
    private lateinit var playlistDao: PlaylistDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TigerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playlistDao = database.playlistDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `ensureSingletonLikedPlaylist creates singleton liked playlist if none exists`() = runTest {
        val trackIds = playlistDao.ensureSingletonLikedPlaylist()

        assertTrue(trackIds.isEmpty())
        val singleton = playlistDao.getPlaylistById(PlaylistEntity.LIKED_SONGS_PLAYLIST_ID)
        assertNotNull(singleton)
        assertEquals(PlaylistEntity.LIKED_SONGS_PLAYLIST_ID, singleton?.playlistId)
        assertEquals(PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME, singleton?.name)
    }

    @Test
    fun `ensureSingletonLikedPlaylist merges legacy playlists matching liked songs into singleton`() = runTest {
        // 1. Create a legacy "Liked Songs" playlist with id = 1
        val legacy1Id = playlistDao.insertPlaylist(
            PlaylistEntity(playlistId = 1L, name = "Liked Songs", createdAt = 100L)
        )
        playlistDao.addTrackToPlaylist(legacy1Id, "track_1")
        playlistDao.addTrackToPlaylist(legacy1Id, "track_2")

        // 2. Create another legacy "liked songs" playlist (different casing) with id = 2
        val legacy2Id = playlistDao.insertPlaylist(
            PlaylistEntity(playlistId = 2L, name = "liked songs", createdAt = 200L)
        )
        playlistDao.addTrackToPlaylist(legacy2Id, "track_2") // duplicate
        playlistDao.addTrackToPlaylist(legacy2Id, "track_3")

        // 3. Create an unrelated user playlist with id = 3
        val otherPlaylistId = playlistDao.insertPlaylist(
            PlaylistEntity(playlistId = 3L, name = "Synthwave Vault", createdAt = 300L)
        )
        playlistDao.addTrackToPlaylist(otherPlaylistId, "track_4")

        // Execute merge
        val mergedTrackIds = playlistDao.ensureSingletonLikedPlaylist()

        // Verify:
        // - Merged track IDs contains track_1, track_2, track_3 without duplicates
        assertEquals(setOf("track_1", "track_2", "track_3"), mergedTrackIds.toSet())

        // - The legacy playlists (1 and 2) are removed
        val allPlaylists = playlistDao.getAllPlaylistsSync()
        val playlistIds = allPlaylists.map { it.playlistId }.toSet()
        assertEquals(setOf(PlaylistEntity.LIKED_SONGS_PLAYLIST_ID, otherPlaylistId), playlistIds)

        // - Tracks for singleton liked playlist are track_1, track_2, track_3
        val singletonTracks = playlistDao.getTrackIdsForPlaylist(PlaylistEntity.LIKED_SONGS_PLAYLIST_ID).first()
        assertEquals(listOf("track_1", "track_2", "track_3"), singletonTracks)

        // - The other playlist was untouched
        val otherTracks = playlistDao.getTrackIdsForPlaylist(otherPlaylistId).first()
        assertEquals(listOf("track_4"), otherTracks)
    }

    @Test
    fun `insertPlaylistSafely redirects creation of Liked Songs to singleton`() = runTest {
        val returnedId = playlistDao.insertPlaylistSafely(
            PlaylistEntity(playlistId = 0L, name = "liked songs")
        )

        assertEquals(PlaylistEntity.LIKED_SONGS_PLAYLIST_ID, returnedId)
        val allPlaylists = playlistDao.getAllPlaylistsSync()
        assertEquals(1, allPlaylists.size)
        assertEquals(PlaylistEntity.LIKED_SONGS_PLAYLIST_ID, allPlaylists[0].playlistId)
    }

    @Test
    fun `renamePlaylistSafely merges into singleton when renamed to Liked Songs`() = runTest {
        // Create an existing playlist
        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(playlistId = 10L, name = "Favorites", createdAt = 500L)
        )
        playlistDao.addTrackToPlaylist(playlistId, "track_10")

        // Rename to "Liked Songs"
        playlistDao.renamePlaylistSafely(playlistId, "Liked Songs")

        // Playlist 10 should be merged into singleton -1L and removed
        val allPlaylists = playlistDao.getAllPlaylistsSync()
        assertEquals(1, allPlaylists.size)
        assertEquals(PlaylistEntity.LIKED_SONGS_PLAYLIST_ID, allPlaylists[0].playlistId)

        val tracks = playlistDao.getTrackIdsForPlaylist(PlaylistEntity.LIKED_SONGS_PLAYLIST_ID).first()
        assertEquals(listOf("track_10"), tracks)
    }
}
