package com.tigerplayer.ui.player

import android.graphics.RectF
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import com.tigerplayer.data.local.PlaybackPrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.engine.AdaptiveDspEngine
import com.tigerplayer.engine.MetadataEngine
import com.tigerplayer.engine.PlaybackEngine
import com.tigerplayer.engine.StatsEngine
import com.tigerplayer.engine.WaveformEngine
import com.tigerplayer.service.MediaControllerManager
import com.tigerplayer.utils.BluetoothDeviceManager
import com.tigerplayer.ui.coverscreen.populatePositiveWaveformPath
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerSeekerTest {

    // --- 1. DURATION FORMATTING TESTS ---

    @Test
    fun `formatDuration formats zero and negative milliseconds safely`() {
        assertEquals("00:00", formatDuration(0L))
        assertEquals("00:00", formatDuration(-1000L))
        assertEquals("00:00", formatDuration(Long.MIN_VALUE))
    }

    @Test
    fun `formatDuration formats standard duration with zero-padding`() {
        assertEquals("00:07", formatDuration(7_000L))
        assertEquals("00:45", formatDuration(45_000L))
        assertEquals("01:00", formatDuration(60_000L))
        assertEquals("01:05", formatDuration(65_000L))
        assertEquals("03:35", formatDuration(215_000L))
        assertEquals("12:42", formatDuration(762_000L))
    }

    // --- 2. MULTI-TIER HARMONIC WAVE PATH TESTS ---

    @Test
    fun `populatePositiveWaveformPath generates valid non-empty bounds for Wave 1, 2, and 3`() {
        val path1 = Path()
        val fillPath1 = Path()
        val path2 = Path()
        val fillPath2 = Path()
        val path3 = Path()
        val fillPath3 = Path()

        val points = floatArrayOf(0.2f, 0.5f, 0.8f, 0.4f, 0.9f, 0.3f)
        val width = 1000f
        val wave1Amp = 20f
        val wave2Amp = wave1Amp * 4f
        val wave3Amp = wave2Amp * 3f
        val baselineY = 100f
        val overlapPx = 4f

        // Populate Wave 1 (Base Sine)
        populatePositiveWaveformPath(
            path = path1,
            fillPath = fillPath1,
            points = points,
            width = width,
            maxHeight = wave1Amp,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 0f,
            phaseOffset = 0f,
            isCosine = false
        )

        // Populate Wave 2 (Middle Sine at PI/4)
        populatePositiveWaveformPath(
            path = path2,
            fillPath = fillPath2,
            points = points,
            width = width,
            maxHeight = wave2Amp,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 0f,
            phaseOffset = (Math.PI / 4.0).toFloat(),
            isCosine = false
        )

        // Populate Wave 3 (Apex Cosine)
        populatePositiveWaveformPath(
            path = path3,
            fillPath = fillPath3,
            points = points,
            width = width,
            maxHeight = wave3Amp,
            baselineY = baselineY,
            overlapPx = overlapPx,
            phase = 0f,
            phaseOffset = 0f,
            isCosine = true
        )

        val bounds1 = RectF()
        path1.asAndroidPath().computeBounds(bounds1, true)
        val bounds2 = RectF()
        path2.asAndroidPath().computeBounds(bounds2, true)
        val bounds3 = RectF()
        path3.asAndroidPath().computeBounds(bounds3, true)

        assertFalse(bounds1.isEmpty)
        assertFalse(bounds2.isEmpty)
        assertFalse(bounds3.isEmpty)

        // Verify harmonic amplitude hierarchy: Wave 3 > Wave 2 > Wave 1
        assertTrue(bounds3.height() > bounds2.height())
        assertTrue(bounds2.height() > bounds1.height())
    }

    @Test
    fun `populatePositiveWaveformPath reuses paths cleanly across simulated frames`() {
        val path = Path()
        val fillPath = Path()
        val points = floatArrayOf(0.1f, 0.4f, 0.7f)

        for (frame in 0 until 5) {
            val phase = frame * 0.2f
            populatePositiveWaveformPath(
                path = path,
                fillPath = fillPath,
                points = points,
                width = 500f,
                maxHeight = 30f,
                baselineY = 50f,
                overlapPx = 2f,
                phase = phase,
                isCosine = (frame % 2 == 0)
            )

            val bounds = RectF()
            path.asAndroidPath().computeBounds(bounds, true)
            assertFalse("Path must not be empty on frame $frame", bounds.isEmpty)
        }
    }

    // --- 3. VISUAL MODE TOGGLE HARMONIZATION TESTS ---

    @Test
    fun `toggleVisualMode toggles directly between ARTWORK and VORTEX`() {
        val playbackEngine: PlaybackEngine = mockk(relaxed = true)
        val mediaControllerManager: MediaControllerManager = mockk(relaxed = true)
        val metadataEngine: MetadataEngine = mockk(relaxed = true)
        val statsEngine: StatsEngine = mockk(relaxed = true)
        val waveformEngine: WaveformEngine = mockk(relaxed = true)
        val adaptiveDspEngine: AdaptiveDspEngine = mockk(relaxed = true)
        val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
        val playbackPrefs: PlaybackPrefs = mockk(relaxed = true)
        val audioRepository: AudioRepository = mockk(relaxed = true)
        val bluetoothDeviceManager: BluetoothDeviceManager = mockk(relaxed = true)

        val stateManager = PlaybackStateManager(
            playbackEngine = playbackEngine,
            mediaControllerManager = mediaControllerManager,
            metadataEngine = metadataEngine,
            statsEngine = statsEngine,
            waveformEngine = waveformEngine,
            adaptiveDspEngine = adaptiveDspEngine,
            settingsDataStore = settingsDataStore,
            playbackPrefs = playbackPrefs,
            audioRepository = audioRepository,
            bluetoothDeviceManager = bluetoothDeviceManager
        )

        // Initial default state is ARTWORK
        assertEquals(PlayerVisualMode.ARTWORK, stateManager.playbackState.value.visualMode)

        // First tap: ARTWORK -> VORTEX (Background Visualizer enabled)
        stateManager.toggleVisualMode()
        assertEquals(PlayerVisualMode.VORTEX, stateManager.playbackState.value.visualMode)

        // Second tap: VORTEX -> ARTWORK (Background Visualizer disabled, clean artist backdrop)
        stateManager.toggleVisualMode()
        assertEquals(PlayerVisualMode.ARTWORK, stateManager.playbackState.value.visualMode)
    }
}
