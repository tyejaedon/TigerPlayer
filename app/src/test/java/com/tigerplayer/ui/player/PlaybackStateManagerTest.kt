package com.tigerplayer.ui.player

import androidx.media3.common.Player
import com.tigerplayer.data.local.CoverScreenEqPreset
import com.tigerplayer.data.local.PlaybackPrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.engine.AdaptiveDspEngine
import com.tigerplayer.engine.AudioReactiveFrame
import com.tigerplayer.engine.MetadataEngine
import com.tigerplayer.engine.PlaybackEngine
import com.tigerplayer.engine.StatsEngine
import com.tigerplayer.engine.WaveformEngine
import com.tigerplayer.service.MediaControllerManager
import com.tigerplayer.utils.BluetoothDeviceInfo
import com.tigerplayer.utils.BluetoothDeviceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStateManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private val playbackEngine = mockk<PlaybackEngine>(relaxed = true)
    private val mediaControllerManager = mockk<MediaControllerManager>(relaxed = true)
    private val metadataEngine = mockk<MetadataEngine>(relaxed = true)
    private val statsEngine = mockk<StatsEngine>(relaxed = true)
    private val waveformEngine = mockk<WaveformEngine>(relaxed = true)
    private val adaptiveDspEngine = mockk<AdaptiveDspEngine>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    private val playbackPrefs = mockk<PlaybackPrefs>(relaxed = true)
    private val audioRepository = mockk<AudioRepository>(relaxed = true)
    private val bluetoothDeviceManager = mockk<BluetoothDeviceManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        every { playbackEngine.spotifyPlaybackState } returns MutableStateFlow(null)
        every { playbackEngine.spotifyReauthRequired } returns MutableStateFlow(false)
        every { playbackEngine.getQueueSnapshotFlow() } returns flowOf(MediaControllerManager.QueueSnapshot())

        every { mediaControllerManager.isPlaying } returns MutableStateFlow(false)
        every { mediaControllerManager.currentPosition } returns MutableStateFlow(0L)
        every { mediaControllerManager.shuffleModeEnabled } returns MutableStateFlow(false)
        every { mediaControllerManager.repeatMode } returns MutableStateFlow(Player.REPEAT_MODE_OFF)
        every { mediaControllerManager.volume } returns MutableStateFlow(1f)
        every { mediaControllerManager.playbackSpeed } returns MutableStateFlow(1f)
        every { mediaControllerManager.pitch } returns MutableStateFlow(1f)

        every { metadataEngine.currentLyrics } returns MutableStateFlow(Lyrics.None)
        every { metadataEngine.currentArtistImageUrl } returns MutableStateFlow(null)

        every { adaptiveDspEngine.audioReactiveFrame } returns MutableStateFlow(AudioReactiveFrame())
        every { settingsDataStore.settingsFlow } returns MutableStateFlow(TigerSettingsState())
        every { bluetoothDeviceManager.connectedDevice } returns MutableStateFlow(BluetoothDeviceInfo())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createManager(): PlaybackStateManager = PlaybackStateManager(
        playbackEngine = playbackEngine,
        mediaControllerManager = mediaControllerManager,
        metadataEngine = metadataEngine,
        statsEngine = statsEngine,
        waveformEngine = waveformEngine,
        adaptiveDspEngine = adaptiveDspEngine,
        settingsDataStore = settingsDataStore,
        playbackPrefs = playbackPrefs,
        audioRepository = audioRepository,
        bluetoothDeviceManager = bluetoothDeviceManager,
        coroutineScope = testScope
    )

    @Test
    fun `cover screen volume is normalized persisted and applied to media controller`() = runTest(dispatcher) {
        val manager = createManager()
        advanceUntilIdle()

        manager.setCoverScreenVolume(0.55f)
        advanceUntilIdle()

        assertEquals(0.55f, manager.playbackState.value.coverScreenVolume, 0.001f)
        coVerify(exactly = 1) { settingsDataStore.setCoverScreenVolume(0.55f) }
        verify(exactly = 1) { mediaControllerManager.setVolume(0.55f) }

        manager.setCoverScreenVolume(Float.NaN)
        advanceUntilIdle()
        assertEquals(1f, manager.playbackState.value.coverScreenVolume, 0f)
    }

    @Test
    fun `playAll with tracks initiates playback from first index`() = runTest(dispatcher) {
        val manager = createManager()
        val track = AudioTrack(
            id = "track-1",
            title = "Test",
            artist = "Artist",
            album = "Album",
            uri = mockk(relaxed = true),
            artworkUri = mockk(relaxed = true),
            durationMs = 120_000L,
            mimeType = "audio/mp3"
        )
        val tracks = listOf(track)

        manager.playAll(tracks)
        verify(exactly = 1) { playbackEngine.setPlaylistAndPlay(tracks, 0) }
    }

    @Test
    fun `playAll with empty list does nothing`() = runTest(dispatcher) {
        val manager = createManager()

        manager.playAll(emptyList())
        verify(exactly = 0) { playbackEngine.setPlaylistAndPlay(any(), any()) }
    }

    @Test
    fun `playback speed normalization adheres to policy`() = runTest(dispatcher) {
        val manager = createManager()
        advanceUntilIdle()

        manager.setPlaybackSpeed(1.5f)
        advanceUntilIdle()

        verify(exactly = 1) { mediaControllerManager.setPlaybackParameters(1.5f, 1f) }

        manager.setPlaybackSpeed(5.0f) // exceeds MAX_VALUE 2.0f
        advanceUntilIdle()
        verify(exactly = 1) { mediaControllerManager.setPlaybackParameters(2.0f, 1f) }
    }

    @Test
    fun `queue management actions delegate to playback engine`() = runTest(dispatcher) {
        val manager = createManager()
        val track = AudioTrack(
            id = "q-1",
            title = "Queue Track",
            artist = "Artist",
            album = "Album",
            uri = mockk(relaxed = true),
            artworkUri = mockk(relaxed = true),
            durationMs = 90_000L,
            mimeType = "audio/mp3"
        )

        manager.addToQueue(track)
        verify(exactly = 1) { playbackEngine.addToQueue(track) }

        manager.addNextToQueue(track)
        verify(exactly = 1) { playbackEngine.playNext(track) }

        manager.removeFromQueue(2)
        verify(exactly = 1) { playbackEngine.removeFromQueue(2) }

        manager.clearQueue()
        verify(exactly = 1) { playbackEngine.clearQueue() }

        manager.moveQueueItem(0, 3)
        verify(exactly = 1) { playbackEngine.moveQueueItem(0, 3) }
    }

    @Test
    fun `sleep timer controls delegate to playback engine`() = runTest(dispatcher) {
        val manager = createManager()

        manager.setSleepTimerDuration(30)
        verify(exactly = 1) { playbackEngine.setSleepTimerDuration(30) }

        manager.setSleepTimerEndOfTrack()
        verify(exactly = 1) { playbackEngine.setSleepTimerEndOfTrack() }

        manager.setSleepTimerEndOfQueue()
        verify(exactly = 1) { playbackEngine.setSleepTimerEndOfQueue() }

        manager.cancelSleepTimer()
        verify(exactly = 1) { playbackEngine.cancelSleepTimer() }
    }
}
