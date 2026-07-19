package com.example.tigerplayer.service

import androidx.media3.session.MediaController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tigerplayer.data.local.PlaybackPrefs
import com.example.tigerplayer.data.local.SettingsDataStore
import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.data.repository.MediaDataRepository
import com.example.tigerplayer.utils.BluetoothDeviceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
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

    private lateinit var manager: MediaControllerManager

    @Before
    fun setup() {
        every { settingsDataStore.settingsFlow } returns emptyFlow()
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
}
