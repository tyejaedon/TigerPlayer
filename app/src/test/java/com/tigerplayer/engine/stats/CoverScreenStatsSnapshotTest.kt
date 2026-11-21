package com.tigerplayer.engine

import com.tigerplayer.data.repository.HistoryRepository
import com.tigerplayer.data.local.dao.ArtistStats
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class CoverScreenStatsSnapshotTest {

    private val engine = StatsEngine(mockk<HistoryRepository>(relaxed = true)) { 0L }

    @Test
    fun dailyPlayCountWindowsUseLocalCalendarDaysAcrossTimeZones() {
        val now = Instant.parse("2026-10-14T08:00:00Z").toEpochMilli()
        val timestamp = Instant.parse("2026-10-14T06:00:00Z").toEpochMilli()
        val laWindows = engine.dailyPlayCountWindows(
            now,
            TimeZone.getTimeZone("America/Los_Angeles")
        )
        val kiritimatiWindows = engine.dailyPlayCountWindows(
            now,
            TimeZone.getTimeZone("Pacific/Kiritimati")
        )

        assertEquals(CoverScreenStatsSnapshot.STATS_DAYS, laWindows.size)
        assertTrue(laWindows[CoverScreenStatsSnapshot.STATS_DAYS - 2].contains(timestamp))
        assertTrue(!laWindows.last().contains(timestamp))
        assertTrue(kiritimatiWindows.last().contains(timestamp))
    }

    @Test
    fun dailyPlayCountWindowsRespectDaylightSavingTransitions() {
        val now = Instant.parse("2026-03-10T12:00:00Z").toEpochMilli()
        val windows = engine.dailyPlayCountWindows(
            now,
            TimeZone.getTimeZone("America/Los_Angeles")
        )

        assertEquals(CoverScreenStatsSnapshot.STATS_DAYS, windows.size)
        assertEquals(23 * 60 * 60 * 1000L, windows[4].endExclusive - windows[4].startInclusive)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun snapshotUpdatesSessionArtistsAndPlayCountsWhenHistoryChanges() = runTest {
        val repository = mockk<HistoryRepository>()
        val artists = MutableStateFlow(emptyList<ArtistStats>())
        val listeningTime = MutableStateFlow<Long?>(0L)
        val playCount = MutableStateFlow(0)
        every { repository.getTopArtists(any(), any()) } returns artists
        every { repository.getTotalListeningTime(any()) } returns listeningTime
        every { repository.getTrackPlayCount(any()) } returns playCount
        every { repository.getTrackPlayCountBetween(any(), any()) } returns playCount

        val statsEngine = StatsEngine(repository) { 0L }
        val snapshots = mutableListOf<CoverScreenStatsSnapshot>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            statsEngine.getCoverScreenStatsSnapshotFlow().collect(snapshots::add)
        }
        runCurrent()
        assertEquals(0, snapshots.last().tracksPlayedToday)

        artists.value = listOf(ArtistStats("Session Artist", 2, 120_000L))
        listeningTime.value = 120_000L
        playCount.value = 2
        runCurrent()

        assertEquals("Session Artist", snapshots.last().topArtists.single().artistName)
        assertEquals(2, snapshots.last().tracksPlayedToday)
        collector.cancel()
    }

    private fun DailyPlayCountWindow.contains(timestamp: Long): Boolean =
        timestamp >= startInclusive && timestamp < endExclusive
}
