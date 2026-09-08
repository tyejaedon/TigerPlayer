package com.example.tigerplayer.data.repository

import com.example.tigerplayer.data.local.PlaybackPrefs
import com.example.tigerplayer.data.local.dao.TigerDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for issue #80.
 *
 * Pre-#42 history rows recorded the track's nominal length instead of the time actually listened.
 * They are excluded from analytics by flooring every query at a one-time epoch, rather than being
 * deleted - PlaybackHistoryEntity is irreplaceable user data.
 */
class StatsEpochTest {

    private val playbackPrefs = mockk<PlaybackPrefs>(relaxed = true)
    private val tigerDao = mockk<TigerDao>(relaxed = true)

    private fun epoch() = StatsEpoch(playbackPrefs, tigerDao)

    // --- initialization ---

    @Test
    fun `an existing install with history gets an epoch of now`() = runTest {
        every { playbackPrefs.statsEpochMs } returns flowOf(null)
        coEvery { tigerDao.getHistoryCountSync() } returns 42

        epoch().initializeIfNeeded(nowMs = 1_700_000_000_000L)

        coVerify(exactly = 1) { playbackPrefs.saveStatsEpochMs(1_700_000_000_000L) }
    }

    @Test
    fun `a fresh install with no history gets a zero epoch so nothing is hidden`() = runTest {
        every { playbackPrefs.statsEpochMs } returns flowOf(null)
        coEvery { tigerDao.getHistoryCountSync() } returns 0

        epoch().initializeIfNeeded(nowMs = 1_700_000_000_000L)

        coVerify(exactly = 1) { playbackPrefs.saveStatsEpochMs(0L) }
    }

    @Test
    fun `an already initialized epoch is never overwritten`() = runTest {
        every { playbackPrefs.statsEpochMs } returns flowOf(123L)

        epoch().initializeIfNeeded(nowMs = 1_700_000_000_000L)

        coVerify(exactly = 0) { playbackPrefs.saveStatsEpochMs(any()) }
        coVerify(exactly = 0) { tigerDao.getHistoryCountSync() }
    }

    @Test
    fun `a stored zero epoch counts as initialized and is not re-evaluated`() = runTest {
        every { playbackPrefs.statsEpochMs } returns flowOf(0L)

        epoch().initializeIfNeeded(nowMs = 1_700_000_000_000L)

        coVerify(exactly = 0) { playbackPrefs.saveStatsEpochMs(any()) }
    }

    // --- coercion ---

    @Test
    fun `a start time older than the epoch is raised to the epoch`() = runTest {
        every { playbackPrefs.statsEpochMs } returns flowOf(5_000L)

        assertEquals(5_000L, epoch().effectiveStart(0L).first())
        assertEquals(5_000L, epoch().effectiveStart(4_999L).first())
    }

    @Test
    fun `a start time newer than the epoch is left alone`() = runTest {
        every { playbackPrefs.statsEpochMs } returns flowOf(5_000L)

        assertEquals(9_000L, epoch().effectiveStart(9_000L).first())
    }

    @Test
    fun `the boundary value is inclusive and unchanged`() = runTest {
        every { playbackPrefs.statsEpochMs } returns flowOf(5_000L)

        assertEquals(5_000L, epoch().effectiveStart(5_000L).first())
    }

    @Test
    fun `a zero epoch leaves every start time untouched`() = runTest {
        every { playbackPrefs.statsEpochMs } returns flowOf(0L)

        assertEquals(0L, epoch().effectiveStart(0L).first())
        assertEquals(1_234L, epoch().effectiveStart(1_234L).first())
    }

    @Test
    fun `an unset epoch behaves as zero`() = runTest {
        every { playbackPrefs.statsEpochMs } returns flowOf(null)

        assertEquals(0L, epoch().effectiveStart(0L).first())
        assertEquals(777L, epoch().effectiveStart(777L).first())
    }

    @Test
    fun `effective start re-emits when the epoch changes`() = runTest {
        every { playbackPrefs.statsEpochMs } returns flowOf(0L, 8_000L)

        val emissions = epoch().effectiveStart(1_000L).take(2).toList()

        assertEquals(listOf(1_000L, 8_000L), emissions)
    }

    @Test
    fun `repeated identical epoch values do not produce duplicate emissions`() = runTest {
        every { playbackPrefs.statsEpochMs } returns flowOf(5_000L, 5_000L, 5_000L)

        val emissions = epoch().effectiveStart(0L).toList()

        assertEquals(listOf(5_000L), emissions)
    }
}

