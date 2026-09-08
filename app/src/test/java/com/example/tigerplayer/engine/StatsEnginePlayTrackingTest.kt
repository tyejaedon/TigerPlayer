package com.example.tigerplayer.engine

import android.net.Uri
import com.example.tigerplayer.data.local.MediaSource
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.HistoryRepository
import com.example.tigerplayer.utils.ElapsedTimeSource
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for issue #42.
 *
 * Before the fix, a history row was written on every track transition using the track's nominal
 * length, so skipping inflated every analytics surface. These tests pin the corrected behaviour:
 * a play is committed when the track is left, and records the time actually listened.
 */
class StatsEnginePlayTrackingTest {

    /** Virtual monotonic clock - no test may depend on the wall clock. */
    private class FakeElapsedTimeSource(private var nowMs: Long = 1_000L) : ElapsedTimeSource {
        override fun elapsedMs(): Long = nowMs
        fun advance(byMs: Long) {
            nowMs += byMs
        }
    }

    private val clock = FakeElapsedTimeSource()
    private val historyRepository = mockk<HistoryRepository>(relaxed = true)
    private val engine = StatsEngine(historyRepository, clock)

    private fun track(
        id: String = "track-1",
        durationMs: Long = FIVE_MINUTES
    ) = AudioTrack(
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        uri = mockk<Uri>(relaxed = true),
        artworkUri = mockk<Uri>(relaxed = true),
        durationMs = durationMs,
        mimeType = "audio/flac",
        isLocal = true
    )

    private fun capturedListenedMs(): Long {
        val listened = slot<Long>()
        coVerify(exactly = 1) {
            historyRepository.addTrackToHistory(
                trackId = any(),
                title = any(),
                artist = any(),
                album = any(),
                imageUrl = any(),
                durationListenedMs = capture(listened),
                source = any()
            )
        }
        return listened.captured
    }

    @Test
    fun `skipping a track before the threshold records no history row`() = runTest {
        engine.onTrackChanged(track(), isPlaying = true)
        clock.advance(3_000L)

        engine.onTrackChanged(track(id = "track-2"), isPlaying = true)

        coVerify(exactly = 0) { historyRepository.addTrackToHistory(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `playing past half the track records the real listened duration, not the track length`() = runTest {
        engine.onTrackChanged(track(durationMs = FIVE_MINUTES), isPlaying = true)
        clock.advance(THREE_MINUTES)

        engine.flushPendingPlay()

        assertEquals(THREE_MINUTES, capturedListenedMs())
    }

    @Test
    fun `pause and resume accumulate only time actually played`() = runTest {
        engine.onTrackChanged(track(durationMs = FIVE_MINUTES), isPlaying = true)

        clock.advance(90_000L)
        engine.onPlayingChanged(isPlaying = false)

        // Paused for a long time - this must not be counted.
        clock.advance(10 * 60 * 1000L)
        engine.onPlayingChanged(isPlaying = true)

        clock.advance(60_000L)
        engine.flushPendingPlay()

        assertEquals(150_000L, capturedListenedMs())
    }

    @Test
    fun `repeated pause events do not double count`() = runTest {
        engine.onTrackChanged(track(durationMs = FIVE_MINUTES), isPlaying = true)
        clock.advance(THREE_MINUTES)

        engine.onPlayingChanged(isPlaying = false)
        clock.advance(30_000L)
        engine.onPlayingChanged(isPlaying = false)
        engine.onPlayingChanged(isPlaying = false)

        engine.flushPendingPlay()

        assertEquals(THREE_MINUTES, capturedListenedMs())
    }

    @Test
    fun `four minutes qualifies a long track even below fifty percent`() = runTest {
        val twentyMinutes = 20 * 60 * 1000L
        engine.onTrackChanged(track(durationMs = twentyMinutes), isPlaying = true)

        clock.advance(4 * 60 * 1000L)
        engine.flushPendingPlay()

        assertEquals(4 * 60 * 1000L, capturedListenedMs())
    }

    @Test
    fun `listening just under half a long track does not qualify`() = runTest {
        val twentyMinutes = 20 * 60 * 1000L
        engine.onTrackChanged(track(durationMs = twentyMinutes), isPlaying = true)

        // Below both the 50% and the four-minute rule.
        clock.advance(3 * 60 * 1000L)
        engine.flushPendingPlay()

        coVerify(exactly = 0) { historyRepository.addTrackToHistory(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `tracks shorter than thirty seconds never qualify`() = runTest {
        engine.onTrackChanged(track(durationMs = 20_000L), isPlaying = true)

        // Played to completion, and past the absolute floor.
        clock.advance(20_000L)
        engine.flushPendingPlay()

        coVerify(exactly = 0) { historyRepository.addTrackToHistory(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `listened time is capped at the track duration when seeking backwards`() = runTest {
        val duration = FIVE_MINUTES
        engine.onTrackChanged(track(durationMs = duration), isPlaying = true)

        // Replaying via seek-back accumulates more wall-clock time than the track is long.
        clock.advance(duration * 3)
        engine.flushPendingPlay()

        assertEquals(duration, capturedListenedMs())
    }

    @Test
    fun `a track started while paused accrues nothing until playback begins`() = runTest {
        engine.onTrackChanged(track(durationMs = FIVE_MINUTES), isPlaying = false)

        clock.advance(10 * 60 * 1000L)
        engine.onPlayingChanged(isPlaying = true)
        clock.advance(THREE_MINUTES)

        engine.flushPendingPlay()

        assertEquals(THREE_MINUTES, capturedListenedMs())
    }

    @Test
    fun `unknown duration qualifies only via the four minute rule`() = runTest {
        engine.onTrackChanged(track(durationMs = 0L), isPlaying = true)
        clock.advance(THREE_MINUTES)
        engine.flushPendingPlay()

        coVerify(exactly = 0) { historyRepository.addTrackToHistory(any(), any(), any(), any(), any(), any(), any()) }

        engine.onTrackChanged(track(id = "stream-2", durationMs = 0L), isPlaying = true)
        clock.advance(5 * 60 * 1000L)
        engine.flushPendingPlay()

        assertEquals(5 * 60 * 1000L, capturedListenedMs())
    }

    @Test
    fun `flushing with no pending play is a no-op`() = runTest {
        engine.flushPendingPlay()
        engine.flushPendingPlay()

        coVerify(exactly = 0) { historyRepository.addTrackToHistory(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a committed play is not committed twice`() = runTest {
        engine.onTrackChanged(track(durationMs = FIVE_MINUTES), isPlaying = true)
        clock.advance(THREE_MINUTES)

        engine.flushPendingPlay()
        clock.advance(THREE_MINUTES)
        engine.flushPendingPlay()

        assertEquals(THREE_MINUTES, capturedListenedMs())
    }

    @Test
    fun `source is derived from the track id prefix`() = runTest {
        val source = slot<MediaSource>()
        engine.onTrackChanged(track(id = "spotify:track:abc", durationMs = FIVE_MINUTES), isPlaying = true)
        clock.advance(THREE_MINUTES)
        engine.flushPendingPlay()

        coVerify(exactly = 1) {
            historyRepository.addTrackToHistory(
                trackId = any(),
                title = any(),
                artist = any(),
                album = any(),
                imageUrl = any(),
                durationListenedMs = any(),
                source = capture(source)
            )
        }
        assertTrue(source.captured == MediaSource.SPOTIFY)
    }

    @Test
    fun `both navidrome id separators resolve to the navidrome source`() = runTest {
        // Ids written before issue #44 may carry either separator. Misattributing a play would
        // silently corrupt the per-source stats breakdown.
        listOf("navidrome_abc", "navidrome:abc").forEach { id ->
            val repository = mockk<HistoryRepository>(relaxed = true)
            val localClock = FakeElapsedTimeSource()
            val localEngine = StatsEngine(repository, localClock)
            val source = slot<MediaSource>()
            localEngine.onTrackChanged(track(id = id, durationMs = FIVE_MINUTES), isPlaying = true)
            localClock.advance(THREE_MINUTES)
            localEngine.flushPendingPlay()
            coVerify(exactly = 1) {
                repository.addTrackToHistory(
                    trackId = id,
                    title = any(),
                    artist = any(),
                    album = any(),
                    imageUrl = any(),
                    durationListenedMs = any(),
                    source = capture(source)
                )
            }
            assertEquals(MediaSource.NAVIDROME, source.captured)
        }
    }
    private companion object {
        const val FIVE_MINUTES = 5 * 60 * 1000L
        const val THREE_MINUTES = 3 * 60 * 1000L
    }
}


