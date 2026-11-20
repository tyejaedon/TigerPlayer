package com.tigerplayer.engine.scrobble

import android.content.Context
import com.tigerplayer.data.local.MediaSource
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.local.dao.PendingScrobbleDao
import com.tigerplayer.data.local.entity.PendingScrobbleEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.HistoryRepository
import com.tigerplayer.data.repository.LastFmRepository
import com.tigerplayer.data.repository.ScrobbleOutcome
import com.tigerplayer.engine.QualifiedPlay
import com.tigerplayer.engine.StatsEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrobbleEngineTest {

    private lateinit var lastFmRepository: LastFmRepository
    private lateinit var pendingScrobbleDao: PendingScrobbleDao
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var statsEngine: StatsEngine
    private lateinit var context: Context
    private lateinit var engine: ScrobbleEngine

    private var currentNowMs = 1_000_000L
    private val settingsFlow = MutableStateFlow(TigerSettingsState())
    private val isConnectedFlow = MutableStateFlow(true)

    @Before
    fun setUp() {
        lastFmRepository = mockk(relaxed = true)
        pendingScrobbleDao = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)
        statsEngine = StatsEngine(mockk<HistoryRepository>(relaxed = true)) { 0L }
        context = mockk(relaxed = true)

        every { lastFmRepository.isConnected } returns isConnectedFlow
        every { settingsDataStore.settingsFlow } returns settingsFlow
        every { pendingScrobbleDao.observeCount() } returns flowOf(0)

        engine = ScrobbleEngine(
            lastFmRepository = lastFmRepository,
            pendingScrobbleDao = pendingScrobbleDao,
            settingsDataStore = settingsDataStore,
            statsEngine = statsEngine,
            context = context,
            ioDispatcher = UnconfinedTestDispatcher(),
            clockMs = { currentNowMs }
        )
    }

    private fun track(id: String = "local_1", artist: String = "Boards of Canada", title: String = "Roygbiv") =
        AudioTrack(
            id = id,
            title = title,
            artist = artist,
            album = "Music Has the Right to Children",
            durationMs = 151_000L,
            mimeType = "audio/flac",
            uri = mockk<android.net.Uri>(relaxed = true),
            artworkUri = mockk<android.net.Uri>(relaxed = true)
        )

    @Test
    fun `handleQualifiedPlay skips remote tracks when lastfmScrobbleRemote is false`() = runTest {
        settingsFlow.value = TigerSettingsState(lastfmScrobbleEnabled = true, lastfmScrobbleRemote = false)

        engine.handleQualifiedPlay(
            QualifiedPlay(
                track = track(id = "spotify:track:123"),
                listenedMs = 100_000L,
                startedAtEpochSec = 1700000000L,
                source = MediaSource.SPOTIFY
            )
        )

        coVerify(exactly = 0) { pendingScrobbleDao.insert(any()) }
    }

    @Test
    fun `handleQualifiedPlay enqueues and flushes local track when enabled`() = runTest {
        settingsFlow.value = TigerSettingsState(lastfmScrobbleEnabled = true, lastfmScrobbleRemote = false)
        val insertedSlot = slot<PendingScrobbleEntity>()
        coEvery { pendingScrobbleDao.insert(capture(insertedSlot)) } returns 42L
        coEvery { pendingScrobbleDao.getDueScrobbles(any(), any()) } returnsMany listOf(
            listOf(PendingScrobbleEntity(id = 42L, trackId = "local_1", artist = "Boards of Canada", track = "Roygbiv", album = "Music Has the Right to Children", durationSec = 151, timestampSec = 1700000000L)),
            emptyList()
        )
        coEvery { lastFmRepository.scrobbleBatch(any()) } returns ScrobbleOutcome.Success

        engine.handleQualifiedPlay(
            QualifiedPlay(
                track = track(),
                listenedMs = 90_000L,
                startedAtEpochSec = 1700000000L,
                source = MediaSource.LOCAL
            )
        )

        assertEquals("Boards of Canada", insertedSlot.captured.artist)
        assertEquals("Roygbiv", insertedSlot.captured.track)
        coVerify(exactly = 1) { pendingScrobbleDao.deleteByIds(listOf(42L)) }
    }

    @Test
    fun `flushQueue applies exponential backoff on TransientFailure`() = runTest {
        val pending = PendingScrobbleEntity(
            id = 7L,
            trackId = "local_1",
            artist = "Artist",
            track = "Track",
            durationSec = 200,
            timestampSec = 1700000000L,
            attemptCount = 1
        )
        coEvery { pendingScrobbleDao.getDueScrobbles(currentNowMs, 50) } returns listOf(pending)
        coEvery { lastFmRepository.scrobbleBatch(listOf(pending)) } returns ScrobbleOutcome.TransientFailure("Offline")

        engine.flushQueue()

        // attempt 1 -> 2, backoff = 30_000 * 2^(2-1) = 60_000ms
        coVerify(exactly = 1) { pendingScrobbleDao.updateRetry(7L, 2, currentNowMs + 60_000L) }
        coVerify(exactly = 0) { pendingScrobbleDao.deleteByIds(any()) }
    }

    @Test
    fun `flushQueue sets authError and keeps queued scrobbles on AuthInvalid`() = runTest {
        val pending = PendingScrobbleEntity(
            id = 9L,
            trackId = "local_1",
            artist = "Artist",
            track = "Track",
            durationSec = 200,
            timestampSec = 1700000000L
        )
        coEvery { pendingScrobbleDao.getDueScrobbles(currentNowMs, 50) } returns listOf(pending)
        coEvery { lastFmRepository.scrobbleBatch(listOf(pending)) } returns ScrobbleOutcome.AuthInvalid("Invalid session")

        engine.flushQueue()

        assertEquals("Invalid session", engine.authError.value)
        coVerify(exactly = 0) { pendingScrobbleDao.deleteByIds(any()) }
        coVerify(exactly = 0) { pendingScrobbleDao.updateRetry(any(), any(), any()) }
    }

    @Test
    fun `onTrackStarted sends updateNowPlaying when enabled and connected`() = runTest {
        settingsFlow.value = TigerSettingsState(lastfmScrobbleEnabled = true, lastfmNowPlayingEnabled = true)
        coEvery { lastFmRepository.updateNowPlaying(any(), any(), any(), any()) } returns ScrobbleOutcome.Success

        engine.onTrackStarted(track())

        coVerify(exactly = 1) {
            lastFmRepository.updateNowPlaying(
                artist = "Boards of Canada",
                track = "Roygbiv",
                album = "Music Has the Right to Children",
                durationSec = 151
            )
        }
    }

    @Test
    fun `backoffMs caps at 6 hours`() {
        assertEquals(30_000L, engine.backoffMs(1))
        assertEquals(60_000L, engine.backoffMs(2))
        assertTrue(engine.backoffMs(20) <= 6 * 60 * 60 * 1000L)
    }
}
