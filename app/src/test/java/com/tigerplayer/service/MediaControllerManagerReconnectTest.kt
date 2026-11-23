package com.tigerplayer.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.tigerplayer.data.local.PlaybackPrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.data.repository.MediaDataRepository
import com.tigerplayer.engine.SleepTimerController
import com.tigerplayer.engine.SleepTimerState
import com.tigerplayer.utils.BluetoothDeviceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaControllerManagerReconnectTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private val playbackPrefs = mockk<PlaybackPrefs>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    private val audioRepository = mockk<AudioRepository>(relaxed = true)
    private val mediaDataRepository = mockk<MediaDataRepository>(relaxed = true)
    private val bluetoothDeviceManager = mockk<BluetoothDeviceManager>(relaxed = true)
    private val sleepTimerController = mockk<SleepTimerController>(relaxed = true)

    private lateinit var manager: MediaControllerManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()

        every { settingsDataStore.settingsFlow } returns MutableStateFlow(TigerSettingsState())
        every { playbackPrefs.lastTrackId } returns flowOf(null)
        every { playbackPrefs.lastPosition } returns flowOf(0L)
        every { playbackPrefs.lastQueueIds } returns flowOf(emptyList())
        every { playbackPrefs.lastQueueSnapshot } returns flowOf(null)
        every { playbackPrefs.shuffleMode } returns flowOf(false)
        every { playbackPrefs.repeatMode } returns flowOf(Player.REPEAT_MODE_OFF)
        every { playbackPrefs.playbackSpeed } returns flowOf(1f)
        every { playbackPrefs.pitch } returns flowOf(1f)
        every { audioRepository.getCachedLocalTracks() } returns flowOf(emptyList())
        every { sleepTimerController.state } returns MutableStateFlow(SleepTimerState())

        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareActionUnbindable("androidx.media3.session.MediaSessionService")

        manager = MediaControllerManager(
            context = context,
            playbackPrefs = playbackPrefs,
            settingsDataStore = settingsDataStore,
            audioRepository = audioRepository,
            mediaDataRepository = mediaDataRepository,
            bluetoothDeviceManager = bluetoothDeviceManager,
            sleepTimerController = sleepTimerController
        )
        // Drain ProcessLifecycleOwner registration on main looper
        shadowOf(android.os.Looper.getMainLooper()).idle()
        manager.mediaController = null
    }

    @After
    fun tearDown() {
        manager.release()
        Dispatchers.resetMain()
    }

    private fun mockConnectedController(
        itemCount: Int = 0,
        playing: Boolean = false
    ): MediaController {
        return mockk<MediaController>(relaxed = true).apply {
            every { isConnected } returns true
            every { mediaItemCount } returns itemCount
            every { isPlaying } returns playing
            every { shuffleModeEnabled } returns false
            every { repeatMode } returns Player.REPEAT_MODE_OFF
            every { currentMediaItem } returns null
            every { currentMediaItemIndex } returns if (itemCount > 0) 0 else C.INDEX_UNSET
        }
    }

    private fun sampleTrack(id: String = "track-1"): AudioTrack = AudioTrack(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        uri = Uri.parse("content://media/external/audio/media/1"),
        artworkUri = Uri.EMPTY,
        durationMs = 180_000L,
        mimeType = "audio/mpeg",
        isLocal = true,
        isRemote = false
    )

    @Test
    fun `setPlaylistAndPlay queued while disconnected replays when controller connects`() = runTest(testDispatcher) {
        val track = sampleTrack("42")
        val controller = mockConnectedController(itemCount = 0, playing = false)

        manager.setPlaylistAndPlay(listOf(track), startIndex = 0)
        testScheduler.advanceUntilIdle()

        // Action should be queued; simulate controller connecting
        manager.onControllerConnected(controller)
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { controller.setMediaItems(any<List<MediaItem>>(), 0, C.TIME_UNSET) }
        verify(atLeast = 1) { controller.prepare() }
        verify(atLeast = 1) { controller.play() }
    }

    @Test
    fun `resume queued while disconnected replays play on reconnect`() = runTest(testDispatcher) {
        val controller = mockConnectedController(itemCount = 1, playing = false)
        every { controller.playbackState } returns Player.STATE_READY

        manager.resume()
        manager.onControllerConnected(controller)
        testScheduler.advanceUntilIdle()

        verify(atLeast = 1) { controller.play() }
    }

    @Test
    fun `restorePlaybackState does not overwrite live queue on reconnect`() = runTest(testDispatcher) {
        every { playbackPrefs.lastTrackId } returns flowOf("old-1")
        every { playbackPrefs.lastQueueIds } returns flowOf(listOf("old-1"))
        every { audioRepository.getCachedLocalTracks() } returns flowOf(listOf(sampleTrack("old-1")))

        val liveController = mockConnectedController(itemCount = 3, playing = false)
        manager.onControllerConnected(liveController)
        testScheduler.advanceUntilIdle()

        verify(exactly = 0) { liveController.setMediaItems(any<List<MediaItem>>(), any<Int>(), any<Long>()) }
    }

    @Test
    fun `onControllerDisconnected clears controller, resets playing state, and resets retry budget`() = runTest(testDispatcher) {
        val controller = mockConnectedController(itemCount = 1, playing = true)
        manager.onControllerConnected(controller)

        // Exhaust retry budget with 3 failures
        repeat(3) {
            manager.onControllerConnectFailed(RuntimeException("transient"))
        }

        // Disconnect resets state & retry budget
        manager.onControllerDisconnected()
        assertNull(manager.mediaController)
        assertFalse(manager.isPlaying.value)
        verify(atLeast = 1) { bluetoothDeviceManager.stopTrackingListeningTime() }

        // A subsequent failure after disconnect should still be able to schedule a retry
        manager.onControllerConnectFailed(RuntimeException("transient-after-disconnect"))
    }
}
