package com.example.tigerplayer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tigerplayer.data.local.entity.CachedTrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TigerDaoIntegrityTest {

    private lateinit var database: TigerDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TigerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun cachedTrack_roundTrip_persistsAllColumns_withoutConstraintFailures() = runBlocking {
        val track = CachedTrackEntity(
            id = "local_track_001",
            title = "Neon Drift",
            artist = "Tiger Unit",
            album = "Midnight Circuit",
            uriString = "content://media/external/audio/media/42",
            artworkUriString = "content://media/external/audio/albumart/7",
            durationMs = 243_000L,
            mimeType = "audio/flac",
            bitrate = 960_000,
            sampleRate = 48_000,
            trackNumber = 3,
            year = "2026",
            dateAdded = 1_719_374_400L,
            isLiked = true,
            path = "/storage/emulated/0/Music/Tiger/Neon Drift.flac"
        )

        val insertResults = database.tigerDao().insertCachedTracks(listOf(track))
        val allCached = database.tigerDao().getCachedTracksSync()

        assertTrue("insert should produce one result", insertResults.size == 1)
        assertEquals("expected exactly one cached row", 1, allCached.size)

        val persisted = allCached.first()
        assertEquals(track.id, persisted.id)
        assertEquals(track.title, persisted.title)
        assertEquals(track.artist, persisted.artist)
        assertEquals(track.album, persisted.album)
        assertEquals(track.uriString, persisted.uriString)
        assertEquals(track.artworkUriString, persisted.artworkUriString)
        assertEquals(track.durationMs, persisted.durationMs)
        assertEquals(track.mimeType, persisted.mimeType)
        assertEquals(track.bitrate, persisted.bitrate)
        assertEquals(track.sampleRate, persisted.sampleRate)
        assertEquals(track.trackNumber, persisted.trackNumber)
        assertEquals(track.year, persisted.year)
        assertEquals(track.dateAdded, persisted.dateAdded)
        assertEquals(track.isLiked, persisted.isLiked)
        assertEquals(track.path, persisted.path)
    }
}

