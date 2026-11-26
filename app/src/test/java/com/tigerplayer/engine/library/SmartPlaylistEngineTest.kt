package com.tigerplayer.engine.library

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tigerplayer.data.local.MediaSource
import com.tigerplayer.data.local.TigerDatabase
import com.tigerplayer.data.local.dao.PlaylistDao
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.ArtistCacheEntity
import com.tigerplayer.data.local.entity.CachedTrackEntity
import com.tigerplayer.data.local.entity.PlaybackHistoryEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmartPlaylistEngineTest {

    private lateinit var database: TigerDatabase
    private lateinit var playlistDao: PlaylistDao
    private lateinit var tigerDao: TigerDao
    private lateinit var engine: SmartPlaylistEngine

    private val nowMs = 1_800_000_000_000L // fixed test clock

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TigerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playlistDao = database.playlistDao()
        tigerDao = database.tigerDao()
        engine = SmartPlaylistEngine.forTesting(playlistDao) { nowMs }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun track(
        id: String,
        title: String = "Track $id",
        artist: String = "Artist A",
        album: String = "Album A",
        durationMs: Long = 200_000L,
        year: String? = "2024",
        dateAdded: Long = (nowMs - 5 * SmartPlaylistEngine.MILLIS_PER_DAY) / 1000L, // MediaStore seconds
        isLiked: Boolean = false
    ) = CachedTrackEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        uriString = if (id.startsWith("navidrome_")) "navidrome://stream/$id" else "content://media/audio/$id",
        artworkUriString = "",
        durationMs = durationMs,
        mimeType = "audio/flac",
        bitrate = 960_000,
        sampleRate = 48_000,
        trackNumber = 1,
        year = year,
        dateAdded = dateAdded,
        isLiked = isLiked,
        path = if (id.startsWith("navidrome_")) null else "/music/$id.flac"
    )

    private fun play(
        trackId: String,
        timestampMs: Long,
        artist: String = "Artist A",
        source: MediaSource = MediaSource.LOCAL
    ) = PlaybackHistoryEntity(
        trackId = trackId,
        title = "Track $trackId",
        artist = artist,
        album = "Album A",
        imageUrl = null,
        durationListenedMs = 180_000L,
        timestamp = timestampMs,
        source = source
    )

    @Test
    fun `compileQuery binds all user values as parameters with zero string interpolation`() {
        val dangerousArtist = "O'Reilly\"; DROP TABLE cached_tracks; -- %_"
        val predicate = SmartPredicate.And(
            listOf(
                SmartPredicate.Artist(TextOp.CONTAINS, dangerousArtist),
                SmartPredicate.Album(TextOp.EQUALS, "Album'Test"),
                SmartPredicate.PlayCount(NumberOp.GREATER_OR_EQUAL, 2),
                SmartPredicate.Year(NumberOp.EQUALS, 2025)
            )
        )

        val compiled = engine.compileQuery(predicate, limitCount = 25)

        assertFalse("raw user string must never be interpolated into SQL", compiled.sql.contains("DROP TABLE"))
        assertFalse("album literal must not be interpolated into SQL", compiled.sql.contains("Album'Test"))
        assertTrue(compiled.sql.contains("LIMIT ?"))
        assertTrue(compiled.args.contains(2))
        assertTrue(compiled.args.contains(2025))
        assertTrue(compiled.args.contains(25))
        assertTrue(compiled.args.any { it is String && it.contains("drop table") })
    }

    @Test
    fun `rulesJson encode and decode round-trips nested AND OR NOT and all predicate types`() {
        val original = SmartPredicate.And(
            listOf(
                SmartPredicate.Liked(true),
                SmartPredicate.Or(
                    listOf(
                        SmartPredicate.PlayCount(NumberOp.GREATER_THAN, 3),
                        SmartPredicate.LastPlayed(DateOp.NOT_IN_LAST_DAYS, 90L)
                    )
                ),
                SmartPredicate.Not(SmartPredicate.Genre(TextOp.CONTAINS, "country")),
                SmartPredicate.DateAdded(DateOp.WITHIN_LAST_DAYS, 30L),
                SmartPredicate.Artist(TextOp.STARTS_WITH, "Daft"),
                SmartPredicate.Album(TextOp.EQUALS, "Discovery"),
                SmartPredicate.Year(NumberOp.GREATER_OR_EQUAL, 2001),
                SmartPredicate.Duration(NumberOp.LESS_THAN, 360_000L),
                SmartPredicate.Source(MediaSource.NAVIDROME),
                SmartPredicate.Title(TextOp.CONTAINS, "Digital")
            )
        )

        val json = engine.encodeRules(original)
        val decoded = engine.decodeRules(json)

        assertEquals(original, decoded)
    }

    @Test
    fun `evaluates playCount, lastPlayed, liked, dateAdded, artist, album, genre, year, duration, source predicates`() = runTest {
        val tracks = listOf(
            track(
                id = "t_liked_stale",
                title = "Stale Gem",
                artist = "Daft Punk",
                album = "Discovery",
                durationMs = 240_000L,
                year = "2001",
                dateAdded = (nowMs - 10 * SmartPlaylistEngine.MILLIS_PER_DAY) / 1000L,
                isLiked = true
            ),
            track(
                id = "t_recent_heavy",
                title = "Heavy Hit",
                artist = "Nova Pulse",
                album = "Neon City",
                durationMs = 180_000L,
                year = "2025",
                dateAdded = (nowMs - 3 * SmartPlaylistEngine.MILLIS_PER_DAY) / 1000L,
                isLiked = true
            ),
            track(
                id = "navidrome_1",
                title = "Remote Signal",
                artist = "Nova Pulse",
                album = "Neon City",
                durationMs = 420_000L,
                year = "2024",
                dateAdded = (nowMs - 100 * SmartPlaylistEngine.MILLIS_PER_DAY) / 1000L,
                isLiked = false
            )
        )
        tigerDao.internalInsertCachedTracks(tracks)
        tigerDao.insertArtistCache(
            ArtistCacheEntity(artistName = "daft punk", imageUrl = null, bio = null, genres = "french house, electronic")
        )
        tigerDao.insertArtistCache(
            ArtistCacheEntity(artistName = "nova pulse", imageUrl = null, bio = null, genres = "synthwave, electronic")
        )

        // Play t_liked_stale once 120 days ago
        tigerDao.insertHistory(play("t_liked_stale", nowMs - 120 * SmartPlaylistEngine.MILLIS_PER_DAY, "Daft Punk"))
        // Play t_recent_heavy 4 times recently
        repeat(4) { idx ->
            tigerDao.insertHistory(play("t_recent_heavy", nowMs - (idx + 1) * SmartPlaylistEngine.MILLIS_PER_DAY, "Nova Pulse"))
        }

        suspend fun eval(
            predicate: SmartPredicate,
            sortBy: SmartPlaylistSortBy = SmartPlaylistSortBy.TITLE,
            desc: Boolean = false,
            limit: Int? = null
        ): List<String> {
            val id = engine.createSmartPlaylist("test", predicate, sortBy, desc, limit)
            val entity = playlistDao.getSmartPlaylistById(id)!!
            return engine.observeTracks(
                with(engine) { entity.toDomain() }
            ).first().map { it.id }
        }

        // 1. Liked AND not played in 90 days
        assertEquals(
            listOf("t_liked_stale"),
            eval(
                SmartPredicate.And(
                    listOf(
                        SmartPredicate.Liked(true),
                        SmartPredicate.LastPlayed(DateOp.NOT_IN_LAST_DAYS, 90L)
                    )
                )
            )
        )

        // 2. Play count > 3 AND added in last 30 days
        assertEquals(
            listOf("t_recent_heavy"),
            eval(
                SmartPredicate.And(
                    listOf(
                        SmartPredicate.PlayCount(NumberOp.GREATER_THAN, 3),
                        SmartPredicate.DateAdded(DateOp.WITHIN_LAST_DAYS, 30L)
                    )
                )
            )
        )

        // 3. Never played
        assertEquals(listOf("navidrome_1"), eval(SmartPredicate.PlayCount(NumberOp.EQUALS, 0)))

        // 4. Artist + Album
        assertEquals(
            listOf("t_liked_stale"),
            eval(
                SmartPredicate.And(
                    listOf(
                        SmartPredicate.Artist(TextOp.EQUALS, "Daft Punk"),
                        SmartPredicate.Album(TextOp.CONTAINS, "discov")
                    )
                )
            )
        )

        // 5. Genre (via artist_cache)
        assertEquals(listOf("t_liked_stale"), eval(SmartPredicate.Genre(TextOp.CONTAINS, "french house")))

        // 6. Year
        assertEquals(listOf("t_recent_heavy"), eval(SmartPredicate.Year(NumberOp.EQUALS, 2025)))

        // 7. Duration (> 5 minutes)
        assertEquals(listOf("navidrome_1"), eval(SmartPredicate.Duration(NumberOp.GREATER_THAN, 300_000L)))

        // 8. Source (NAVIDROME vs LOCAL), sorted by TITLE ASC ("Heavy Hit" < "Stale Gem")
        assertEquals(listOf("navidrome_1"), eval(SmartPredicate.Source(MediaSource.NAVIDROME)))
        assertEquals(
            listOf("t_recent_heavy", "t_liked_stale"),
            eval(SmartPredicate.Source(MediaSource.LOCAL), SmartPlaylistSortBy.TITLE, desc = false)
        )

        // 9. OR composition + Sort DESC + Limit
        val orLimited = eval(
            predicate = SmartPredicate.Or(
                listOf(
                    SmartPredicate.Year(NumberOp.EQUALS, 2001),
                    SmartPredicate.Year(NumberOp.EQUALS, 2025)
                )
            ),
            sortBy = SmartPlaylistSortBy.PLAY_COUNT,
            desc = true,
            limit = 1
        )
        assertEquals(listOf("t_recent_heavy"), orLimited)
    }

    @Test
    fun `observeTracks updates live as playback_history changes`() = runBlocking {
        tigerDao.internalInsertCachedTracks(
            listOf(
                track("t1", title = "Rising Star", isLiked = true),
                track("t2", title = "Sleeper", isLiked = true)
            )
        )
        val spId = engine.createSmartPlaylist(
            name = "Played Favorites",
            predicate = SmartPredicate.And(
                listOf(
                    SmartPredicate.Liked(true),
                    SmartPredicate.PlayCount(NumberOp.GREATER_OR_EQUAL, 1)
                )
            )
        )
        val smartPlaylist = with(engine) { playlistDao.getSmartPlaylistById(spId)!!.toDomain() }

        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        val job = launch {
            engine.observeTracks(smartPlaylist).collect { list ->
                emissions.trySend(list.map { it.id })
            }
        }

        val initial = withTimeout(5_000L) { emissions.receive() }
        assertEquals(emptyList<String>(), initial)

        // Insert a play for t1 — Room InvalidationTracker must automatically re-emit [t1]
        tigerDao.insertHistory(play("t1", nowMs - 1000L))

        var updated = withTimeout(5_000L) { emissions.receive() }
        while (updated.isEmpty()) {
            updated = withTimeout(5_000L) { emissions.receive() }
        }
        assertEquals(listOf("t1"), updated)

        job.cancel()
    }
}
