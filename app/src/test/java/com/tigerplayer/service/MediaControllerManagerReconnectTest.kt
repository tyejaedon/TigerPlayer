package com.tigerplayer.service

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.tigerplayer.data.local.PlaybackPrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.data.repository.MediaDataRepository
import com.tigerplayer.engine.AcousticEnvironmentMode
import com.tigerplayer.engine.SleepTimerController
import com.tigerplayer.engine.SleepTimerState
import com.tigerplayer.utils.BluetoothDeviceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@AndroidxOptIn(UnstableApi::class)
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
        manager.awaitSetPlaylistJobForTest()
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
        manager.awaitRestoreStateJobForTest()
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
        assertEquals(3, manager.connectionRetryCountForTest())

        // Disconnect resets state & retry budget
        manager.onControllerDisconnected()
        assertNull(manager.mediaController)
        assertFalse(manager.isPlaying.value)
        assertEquals(0, manager.connectionRetryCountForTest())
        verify(atLeast = 1) { bluetoothDeviceManager.stopTrackingListeningTime() }

        // A transient failure after disconnect must keep queued actions for replay on reconnect
        manager.pause()
        manager.onControllerConnectFailed(RuntimeException("transient-after-disconnect"))
        assertEquals(1, manager.connectionRetryCountForTest())

        val reconnectedController = mockConnectedController(itemCount = 1, playing = false)
        manager.onControllerConnected(reconnectedController)
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { reconnectedController.pause() }
    }

    @Test
    fun `coalesces rapid setPlaylistAndPlay and seekTo actions while disconnected`() = runTest(testDispatcher) {
        val track1 = sampleTrack("1")
        val track2 = sampleTrack("2")
        val controller = mockConnectedController(itemCount = 0, playing = false)

        manager.setPlaylistAndPlay(listOf(track1), startIndex = 0)
        manager.awaitSetPlaylistJobForTest()
        manager.setPlaylistAndPlay(listOf(track2), startIndex = 0)
        manager.awaitSetPlaylistJobForTest()
        manager.seekTo(1_000L)
        manager.seekTo(12_000L)
        testScheduler.advanceUntilIdle()

        manager.onControllerConnected(controller)
        testScheduler.advanceUntilIdle()

        val itemsSlot = slot<List<MediaItem>>()
        verify(exactly = 1) { controller.setMediaItems(capture(itemsSlot), 0, C.TIME_UNSET) }
        assertEquals(listOf("2"), itemsSlot.captured.map { it.mediaId })
        verify(exactly = 1) { controller.seekTo(12_000L) }
        verify(exactly = 0) { controller.seekTo(1_000L) }
    }

    @Test
    fun `reconnecting to live service syncs current position and emits queue refresh without overwriting queue`() = runTest(testDispatcher) {
        every { playbackPrefs.lastTrackId } returns flowOf("old-1")
        every { playbackPrefs.lastQueueIds } returns flowOf(listOf("old-1"))
        every { audioRepository.getCachedLocalTracks() } returns flowOf(listOf(sampleTrack("old-1")))

        val liveController = mockConnectedController(itemCount = 2, playing = false).apply {
            every { currentPosition } returns 37_500L
        }

        val stateEmissions = mutableListOf<Unit>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.mediaControllerState.collect { stateEmissions.add(it) }
        }

        manager.onControllerConnected(liveController)
        testScheduler.advanceUntilIdle()
        collectJob.cancel()

        assertEquals(37_500L, manager.currentPosition.value)
        assertTrue(stateEmissions.isNotEmpty())
        verify(exactly = 0) { liveController.setMediaItems(any<List<MediaItem>>(), any<Int>(), any<Long>()) }
    }

    @Test
    fun `queued setPlaybackParameters while disconnected is not clobbered by restoreSavedPlaybackParameters`() = runTest(testDispatcher) {
        every { playbackPrefs.playbackSpeed } returns flowOf(1.0f)
        every { playbackPrefs.pitch } returns flowOf(1.0f)

        val controller = mockConnectedController(itemCount = 1, playing = false)

        manager.setPlaybackParameters(1.5f, 1.1f)
        manager.onControllerConnected(controller)
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { controller.setPlaybackParameters(PlaybackParameters(1.5f, 1.1f)) }
        verify(exactly = 0) { controller.setPlaybackParameters(PlaybackParameters(1.0f, 1.0f)) }
        assertEquals(1.5f, manager.playbackSpeed.value)
        assertEquals(1.1f, manager.pitch.value)
    }

    @Test
    fun `queued resume on cold empty controller runs after restorePlaybackState populates queue`() = runTest(testDispatcher) {
        every { playbackPrefs.lastTrackId } returns flowOf("saved-1")
        every { playbackPrefs.lastPosition } returns flowOf(0L)
        every { playbackPrefs.lastQueueIds } returns flowOf(listOf("saved-1"))
        every { audioRepository.getCachedLocalTracks() } returns flowOf(listOf(sampleTrack("saved-1")))

        var count = 0
        val controller = mockk<MediaController>(relaxed = true).apply {
            every { isConnected } returns true
            every { mediaItemCount } answers { count }
            every { isPlaying } returns false
            every { playbackState } returns Player.STATE_READY
            every { shuffleModeEnabled } returns false
            every { repeatMode } returns Player.REPEAT_MODE_OFF
            every { currentMediaItem } returns null
            every { currentMediaItemIndex } answers { if (count > 0) 0 else C.INDEX_UNSET }
            every { setMediaItems(any<List<MediaItem>>(), any<Int>(), any<Long>()) } answers {
                count = firstArg<List<MediaItem>>().size
            }
        }

        manager.resume()
        manager.onControllerConnected(controller)
        testScheduler.advanceUntilIdle()
        manager.awaitRestoreStateJobForTest()
        testScheduler.advanceUntilIdle()

        verifyOrder {
            controller.setMediaItems(any<List<MediaItem>>(), 0, 0L)
            controller.prepare()
            controller.play()
        }
    }

    @Test
    fun `maybeResumeOnRouteConnect and sendCustomCommand queue while disconnected and replay on reconnect`() = runTest(testDispatcher) {
        val controller = mockConnectedController(itemCount = 1, playing = false)
        every { controller.playbackState } returns Player.STATE_READY

        val command = SessionCommand(AudioPlayerService.ACTION_SET_ACOUSTIC_ENV, Bundle.EMPTY)
        val args = Bundle().apply {
            putString(AudioPlayerService.EXTRA_ACOUSTIC_ENV_MODE, AcousticEnvironmentMode.CONCERT_HALL.name)
        }

        manager.maybeResumeOnRouteConnect("bluetooth")
        manager.sendCustomCommand(command, args)
        manager.onControllerConnected(controller)
        testScheduler.advanceUntilIdle()

        verify(atLeast = 1) { controller.play() }
        verify(exactly = 1) { controller.sendCustomCommand(command, args) }
    }

    @Test
    fun `onAppForegrounded suppresses redundant init while connecting and resets retry budget when idle`() = runTest(testDispatcher) {
        repeat(3) {
            manager.onControllerConnectFailed(RuntimeException("transient"))
        }
        assertEquals(3, manager.connectionRetryCountForTest())
        assertFalse(manager.isConnectingForTest())

        manager.onAppForegrounded()
        assertEquals(0, manager.connectionRetryCountForTest())
        assertTrue(manager.isConnectingForTest())

        val firstFuture = manager.currentControllerFutureForTest()
        assertNotNull(firstFuture)
        manager.onAppForegrounded()
        assertSame(firstFuture, manager.currentControllerFutureForTest())
    }

    @Test
    fun `resolvePlayableItemsSynchronously resolves playable tracks with requestMetadata uri and returns null for browse containers or voice queries`() {
        val trackItem = MediaItemConverter.toMediaItem(sampleTrack("1"))
        val ipcStrippedItem = MediaItem.Builder()
            .setMediaId("track_no_local_config")
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(Uri.parse("content://media/external/audio/media/99"))
                    .build()
            )
            .build()

        val fastResolved = resolvePlayableItemsSynchronously(listOf(trackItem, ipcStrippedItem))
        assertNotNull(fastResolved)
        assertEquals(2, fastResolved!!.size)
        assertEquals(Uri.parse("content://media/external/audio/media/1"), fastResolved[0].localConfiguration?.uri)
        assertEquals(Uri.parse("content://media/external/audio/media/99"), fastResolved[1].localConfiguration?.uri)

        val browseContainer = MediaItem.Builder()
            .setMediaId("playlist:-1")
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(Uri.parse("content://media/external/audio/media/1"))
                    .build()
            )
            .build()
        assertNull(resolvePlayableItemsSynchronously(listOf(browseContainer)))

        val voiceSearchItem = MediaItem.Builder()
            .setMediaId("voice")
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setSearchQuery("jazz")
                    .build()
            )
            .build()
        assertNull(resolvePlayableItemsSynchronously(listOf(voiceSearchItem)))
    }

    @Test
    fun `onControllerDisconnected defers reconnect while backgrounded and reconnects on foreground`() = runTest(testDispatcher) {
        val controller = mockConnectedController(itemCount = 1, playing = true)
        manager.onControllerConnected(controller)

        manager.setAppForegroundForTest(false)
        manager.onControllerDisconnected()

        assertNull(manager.mediaController)
        assertFalse(manager.isConnectingForTest())
        assertNull(manager.currentControllerFutureForTest())

        manager.setAppForegroundForTest(true)
        manager.onAppForegrounded()
        assertTrue(manager.isConnectingForTest())
        assertNotNull(manager.currentControllerFutureForTest())
    }

    @Test
    fun `stalled initializeController past CONNECT_TIMEOUT_MS is superseded on foreground`() = runTest(testDispatcher) {
        var nowMs = 10_000L
        manager.elapsedRealtimeProvider = { nowMs }
        // Clear any in-flight connect started by init() so nowMs governs connectingStartedAtMs.
        manager.setAppForegroundForTest(false)
        manager.onControllerDisconnected()
        manager.setAppForegroundForTest(true)

        manager.onAppForegrounded()
        val firstFuture = manager.currentControllerFutureForTest()
        assertNotNull(firstFuture)
        assertTrue(manager.isConnectingForTest())

        // Before timeout, foregrounding again must not supersede the active connect attempt.
        nowMs += MediaControllerManager.CONNECT_TIMEOUT_MS - 1L
        manager.onAppForegrounded()
        assertSame(firstFuture, manager.currentControllerFutureForTest())

        // Past timeout, foregrounding must break the stale isConnecting lock with a fresh future.
        nowMs += 2L
        manager.onAppForegrounded()

        val secondFuture = manager.currentControllerFutureForTest()
        assertNotNull(secondFuture)
        assertTrue(firstFuture !== secondFuture)
        assertTrue(manager.isConnectingForTest())
    }
}
