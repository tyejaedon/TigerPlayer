package com.example.tigerplayer.service

import androidx.media3.session.MediaController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tigerplayer.data.local.PlaybackPrefs
import com.example.tigerplayer.data.local.SettingsDataStore
import com.example.tigerplayer.data.local.TigerSettingsState
import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.data.repository.MediaDataRepository
import com.example.tigerplayer.utils.BluetoothDeviceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
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
        delay(120)

        verify(atLeast = 1) { mockController.volume = 1.0f }
    }
}
