package com.tigerplayer.data.local.dao

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tigerplayer.data.local.TigerDatabase
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
class PlaylistDaoCuratedPlaylistTest {

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
    fun `createPlaylistWithTracks inserts playlist entity and cross refs in order`() = runTest {
        val trackIds = listOf("track_alpha", "track_beta", "track_gamma")
        val playlistId = playlistDao.createPlaylistWithTracks(
            name = "Daylist - MORNING WAVE (Nov 1, 2026)",
            trackIds = trackIds,
            artworkUri = "content://media/art/1"
        )

        assertTrue(playlistId > 0)
        val playlist = playlistDao.getPlaylistById(playlistId)
        assertNotNull(playlist)
        assertEquals("Daylist - MORNING WAVE (Nov 1, 2026)", playlist?.name)
        assertEquals("content://media/art/1", playlist?.artworkUri)

        val crossRefs = playlistDao.getCrossRefsForPlaylistSync(playlistId)
        assertEquals(3, crossRefs.size)
        assertEquals("track_alpha", crossRefs[0].trackId)
        assertEquals(0, crossRefs[0].position)
        assertEquals("track_beta", crossRefs[1].trackId)
        assertEquals(1, crossRefs[1].position)
        assertEquals("track_gamma", crossRefs[2].trackId)
        assertEquals(2, crossRefs[2].position)

        val playlistsWithCount = playlistDao.getPlaylistsWithCount().first()
        val matching = playlistsWithCount.find { it.id == playlistId }
        assertNotNull(matching)
        assertEquals(3, matching?.trackCount)
    }

    @Test
    fun `multiple curated playlists create distinct playlist instances`() = runTest {
        val morningTracks = listOf("morning_1", "morning_2")
        val eveningTracks = listOf("evening_1", "evening_2", "evening_3")

        val morningId = playlistDao.createPlaylistWithTracks("Daylist - MORNING WAVE", morningTracks)
        val eveningId = playlistDao.createPlaylistWithTracks("Daylist - EVENING GLOW", eveningTracks)

        assertTrue(morningId != eveningId)
        val morningRefs = playlistDao.getCrossRefsForPlaylistSync(morningId)
        val eveningRefs = playlistDao.getCrossRefsForPlaylistSync(eveningId)

        assertEquals(2, morningRefs.size)
        assertEquals(3, eveningRefs.size)
    }
}
