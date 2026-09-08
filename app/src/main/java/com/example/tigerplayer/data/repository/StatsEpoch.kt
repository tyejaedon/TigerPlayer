package com.example.tigerplayer.data.repository

import com.example.tigerplayer.data.local.PlaybackPrefs
import com.example.tigerplayer.data.local.dao.TigerDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The point in time from which listening analytics are trustworthy (issue #80).
 *
 * History rows written before the #42 fix recorded `durationListenedMs = track.durationMs` - the
 * track's nominal length - regardless of how long it was actually played. That value is not
 * recoverable, and play counts are inflated too because every skip produced a row.
 *
 * Those rows are **not** deleted: `PlaybackHistoryEntity` is irreplaceable user data, and the
 * timestamp/track/artist on each row is genuine. Instead every analytics query is floored at this
 * epoch, which is non-destructive and fully reversible. Once #43 provides Room migrations, a real
 * marker column can replace this and the rows can be repaired or formally retired.
 */
@Singleton
class StatsEpoch @Inject constructor(
    private val playbackPrefs: PlaybackPrefs,
    private val tigerDao: TigerDao
) {

    /** Analytics ignore anything older than this. `0` means "no legacy data, count everything". */
    val epochMs: Flow<Long> = playbackPrefs.statsEpochMs
        .map { it ?: NO_EPOCH }
        .distinctUntilChanged()

    /**
     * Raises [startTime] to the epoch so no caller can accidentally read across it.
     *
     * Emits again if the epoch changes, so callers built on this stay correct without re-querying.
     */
    fun effectiveStart(startTime: Long): Flow<Long> = epochMs
        .map { epoch -> maxOf(startTime, epoch) }
        .distinctUntilChanged()

    /**
     * Sets the epoch exactly once, on the first launch after this change ships.
     *
     * - Existing install with history -> epoch is "now", so pre-fix rows drop out of analytics.
     * - Fresh install (no history) -> epoch is 0, so nothing is ever hidden.
     *
     * Once written the epoch never moves; a second call is a no-op.
     */
    suspend fun initializeIfNeeded(nowMs: Long = System.currentTimeMillis()) {
        if (playbackPrefs.statsEpochMs.first() != null) return

        val hasLegacyHistory = tigerDao.getHistoryCountSync() > 0
        playbackPrefs.saveStatsEpochMs(if (hasLegacyHistory) nowMs else NO_EPOCH)
    }

    private companion object {
        const val NO_EPOCH = 0L
    }
}

