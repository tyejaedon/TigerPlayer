package com.tigerplayer.engine

import com.tigerplayer.data.repository.HistoryRepository
import io.mockk.mockk
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private fun DailyPlayCountWindow.contains(timestamp: Long): Boolean =
        timestamp >= startInclusive && timestamp < endExclusive
}
