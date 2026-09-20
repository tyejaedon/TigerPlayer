package com.example.tigerplayer.service

import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tigerplayer.data.local.PlaybackPrefs
import com.example.tigerplayer.data.local.SettingsDataStore
import com.example.tigerplayer.data.local.TigerSettingsState
import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.data.repository.MediaDataRepository
import com.example.tigerplayer.utils.BluetoothDeviceManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaControllerManagerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val playbackPrefs = mockk<PlaybackPrefs>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    private val audioRepository = mockk<AudioRepository>(relaxed = true)
    private val mediaDataRepository = mockk<MediaDataRepository>(relaxed = true)
    private val bluetoothDeviceManager = mockk<BluetoothDeviceManager>(relaxed = true)
    private lateinit var settingsFlow: MutableStateFlow<TigerSettingsState>

    private lateinit var manager: MediaControllerManager

    @Before
    fun setup() {
        settingsFlow = MutableStateFlow(
            TigerSettingsState(
                crossfadeDurationSec = 6,
                gaplessPlayback = true
            )
        )
        every { settingsDataStore.settingsFlow } returns settingsFlow
        every { playbackPrefs.flowStateTrueOverlap } returns emptyFlow()

        manager = MediaControllerManager(
            context,
            playbackPrefs,
            settingsDataStore,
            audioRepository,
            mediaDataRepository,
            bluetoothDeviceManager
        )
    }

    @After
    fun tearDown() {
        // The position ticker runs on a real coroutine scope tied to Dispatchers.Main; release()
        // cancels it so it can't outlive this test and pollute a later one's mock verifications.
        manager.release()
    }

    @Test
    fun skipToNext_restores_full_volume() {
        val mockController = mockk<MediaController>(relaxed = true)
        manager.mediaController = mockController

        every { mockController.volume } returns 0.5f

        manager.skipToNext()

        verify { mockController.volume = 1.0f }
    }

    @Test
    fun skipToPrevious_restores_full_volume() {
        val mockController = mockk<MediaController>(relaxed = true)
        manager.mediaController = mockController

        every { mockController.volume } returns 0.35f

        manager.skipToPrevious()

        verify { mockController.volume = 1.0f }
    }

    @Test
    fun crossfade_disable_restores_full_volume() = runBlocking {
        val mockController = mockk<MediaController>(relaxed = true)
        manager.mediaController = mockController

        every { mockController.volume } returns 0.42f

        settingsFlow.value = settingsFlow.value.copy(crossfadeDurationSec = 0)
        delay(120.milliseconds)

        verify(atLeast = 1) { mockController.volume = 1.0f }
    }

    // Regression coverage for issue #48: release() previously had no call sites at all, so its
    // idempotency was never exercised. A double release (e.g. a duplicate process-teardown
    // callback) must not crash, and must actually tear the controller reference down.
    @Test
    fun release_is_idempotent_and_clears_media_controller() {
        val mockController = mockk<MediaController>(relaxed = true)
        manager.mediaController = mockController

        manager.release()
        manager.release()

        assertNull(manager.mediaController)
    }

    // Regression coverage for issue #55: position was previously only ever persisted on pause or
    // on a media item transition, never while a track was actively playing. A process death mid
    // -playback (the common case) lost the resume point entirely. The position ticker must now
    // throttle-persist the position on a ~15s interval while playing.
    @Test
    fun position_ticker_persists_position_periodically_while_playing() = runBlocking {
        val mockController = mockk<MediaController>(relaxed = true)
        manager.mediaController = mockController

        every { mockController.isPlaying } returns true
        every { mockController.currentPosition } returns 42_000L
        every { mockController.duration } returns C.TIME_UNSET

        // startPositionTicker() is only ever invoked from the private Player.Listener attached to
        // the real controller obtained via MediaController.Builder, which this test never
        // connects to. Reflectively invoke it directly against the manually-injected mock
        // controller instead, mirroring how it is started from onIsPlayingChanged(true).
        val startTicker = MediaControllerManager::class.java.getDeclaredMethod("startPositionTicker")
        startTicker.isAccessible = true
        startTicker.invoke(manager)

        // Well before the throttle window elapses, nothing should be persisted yet - the ticker
        // itself runs every 250ms purely to drive the UI position, not to write to disk.
        delay(5.seconds)
        coVerify(exactly = 0) { playbackPrefs.savePosition(any()) }

        // Once the ~15s throttle window elapses, exactly one persisted write should have
        // occurred - not one per 250ms tick - and it must reflect the actual current position.
        delay(11.seconds)
        coVerify(exactly = 1) { playbackPrefs.savePosition(42_000L) }
    }
}
