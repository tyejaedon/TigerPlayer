package com.tigerplayer.data.local.dao

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tigerplayer.data.local.MediaSource
import com.tigerplayer.data.local.TigerDatabase
import com.tigerplayer.data.local.entity.ArtistCacheEntity
import com.tigerplayer.data.local.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Coverage for the duration-weighted, genre-aware [SonicFootprintStats] query.
 *
 * Runs against a real in-memory Room database via Robolectric so the raw `@Query` SQL itself is
 * exercised, not just the surrounding Kotlin.
 */
@RunWith(RobolectricTestRunner::class)
class SonicFootprintStatsQueryTest {

    private lateinit var database: TigerDatabase
    private lateinit var dao: TigerDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TigerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.tigerDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun history(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        durationListenedMs: Long
    ) = PlaybackHistoryEntity(
        trackId = trackId,
        title = title,
        artist = artist,
        album = album,
        imageUrl = null,
        durationListenedMs = durationListenedMs,
        timestamp = System.currentTimeMillis(),
        source = MediaSource.LOCAL
    )

    @Test
    fun `axis weighting reflects duration listened, not raw play count`() = runTest {
        // A short 20s "skip" and a long 20 minute loop of the same acoustic-tagged track: the
        // long loop should dominate the acoustic axis, not be counted equally with the skip.
        dao.insertHistory(history("t1", "Unplugged Session", "Folk Duo", "Live Sessions", 20_000L))
        dao.insertHistory(history("t2", "Unplugged Session", "Folk Duo", "Live Sessions", 1_200_000L))

        val stats = dao.getSonicFootprintStats(startTime = 0L).first()

        assertEquals(1_220_000L, stats.acoustic)
        assertEquals(1_220_000L, stats.total)
        assertEquals(0L, stats.electronic)
    }

    @Test
    fun `prefers cached genre tags over title keyword guessing when available`() = runTest {
        // Title contains no genre-ish keywords at all, but the artist has a cached "techno" genre.
        dao.insertArtistCache(
            ArtistCacheEntity(
                artistName = "nova pulse",
                imageUrl = null,
                bio = null,
                genres = "techno, electronic"
            )
        )
        dao.insertHistory(history("t3", "Midnight Run", "Nova Pulse", "Signals", 300_000L))

        val stats = dao.getSonicFootprintStats(startTime = 0L).first()

        assertEquals(300_000L, stats.electronic)
        assertEquals(0L, stats.acoustic)
    }

    @Test
    fun `falls back to title keyword heuristic when artist has no cached genres`() = runTest {
        dao.insertHistory(history("t4", "Ambient Dreamscape", "Unknown Artist Name", "Chill Vibes", 90_000L))

        val stats = dao.getSonicFootprintStats(startTime = 0L).first()

        assertEquals(90_000L, stats.atmospheric)
        assertEquals(0L, stats.electronic)
    }

    @Test
    fun `respects the startTime floor`() = runTest {
        val old = history("t5", "Ambient Dreamscape", "Someone", "Chill", 60_000L).copy(timestamp = 1_000L)
        dao.insertHistory(old)
        dao.insertHistory(history("t6", "Ambient Dreamscape", "Someone", "Chill", 60_000L))

        val stats = dao.getSonicFootprintStats(startTime = 2_000L).first()

        assertEquals(60_000L, stats.total)
    }
}
