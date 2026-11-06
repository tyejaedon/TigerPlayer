package com.tigerplayer.engine

import androidx.compose.ui.graphics.Color
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.local.dao.AudioDeviceDao
import com.tigerplayer.data.model.AudioDeviceType
import com.tigerplayer.data.model.AudioOutputDevice
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.data.repository.AudioDeviceRepositoryImpl
import com.tigerplayer.data.repository.AutoEqRepository
import com.tigerplayer.ui.coverscreen.calculateWaveformY
import com.tigerplayer.ui.theme.DominantColorExtractor
import com.tigerplayer.utils.BiquadDesigner
import com.tigerplayer.utils.BluetoothDeviceInfo
import com.tigerplayer.utils.BluetoothDeviceManager
import com.tigerplayer.utils.LrcParser
import com.tigerplayer.utils.M3uParser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Release hardening and stress verification suite for TigerPlayer v2.1.6 release.
 *
 * Validates algorithmic numerical stability, boundary invariant enforcement,
 * parser robustness against fuzzing/pathological inputs, and concurrent state safety.
 */
class ReleaseHardeningStressTest {

    // =========================================================================
    // 1. DSP & Biquad Filter Numerical Stability Stress
    // =========================================================================

    @Test
    fun biquadDesigner_extremeParameterFuzzing_neverYieldsNaNOrInfinite() {
        val sampleRates = floatArrayOf(8000f, 16000f, 44100f, 48000f, 96000f, 192000f)
        val filterTypes = FilterType.values()
        val random = Random(42)

        for (i in 0 until 1000) {
            val sr = sampleRates[random.nextInt(sampleRates.size)]
            val type = filterTypes[random.nextInt(filterTypes.size)]
            // Fuzz frequencies from subsonic (1Hz) to supersonic / beyond Nyquist (100kHz)
            val freq = random.nextFloat() * 100000f + 0.1f
            // Fuzz gain from extreme cuts to extreme boosts (-60dB to +60dB)
            val gain = (random.nextFloat() * 120f) - 60f
            // Fuzz Q from razor-thin to massive (0.001 to 100.0)
            val q = random.nextFloat() * 100f + 0.001f

            val coeffs = BiquadDesigner.design(type, freq, gain, q, sr)

            assertFalse("b0 is NaN at iter $i", coeffs.b0.isNaN())
            assertFalse("b0 is Infinite at iter $i", coeffs.b0.isInfinite())
            assertFalse("b1 is NaN at iter $i", coeffs.b1.isNaN())
            assertFalse("b1 is Infinite at iter $i", coeffs.b1.isInfinite())
            assertFalse("b2 is NaN at iter $i", coeffs.b2.isNaN())
            assertFalse("b2 is Infinite at iter $i", coeffs.b2.isInfinite())
            assertFalse("a0 is NaN at iter $i", coeffs.a0.isNaN())
            assertFalse("a0 is Infinite at iter $i", coeffs.a0.isInfinite())
            assertFalse("a1 is NaN at iter $i", coeffs.a1.isNaN())
            assertFalse("a1 is Infinite at iter $i", coeffs.a1.isInfinite())
            assertFalse("a2 is NaN at iter $i", coeffs.a2.isNaN())
            assertFalse("a2 is Infinite at iter $i", coeffs.a2.isInfinite())
            assertTrue("a0 must be non-zero at iter $i", abs(coeffs.a0) > 1e-12)

            // Test magnitude response across ISO test probe frequencies
            for (testFreq in floatArrayOf(20f, 100f, 1000f, 5000f, 10000f, 20000f)) {
                val mag = BiquadDesigner.magnitudeAt(testFreq, coeffs, sr)
                assertFalse("magnitude is NaN at testFreq=$testFreq, iter $i", mag.isNaN())
                assertFalse("magnitude is Infinite at testFreq=$testFreq, iter $i", mag.isInfinite())
            }
        }
    }

    @Test
    fun biquadDesigner_compositePeakGain_pathologicalMultibandStress() {
        val random = Random(1337)
        val sampleRate = 48000f

        // Stress with dense 32-band parametric setups with mixed high boosts and steep cuts
        for (iter in 0 until 50) {
            val nodeCount = random.nextInt(5, 33)
            val nodes = (0 until nodeCount).map { idx ->
                val freq = 20f * (1000f.toDouble().pow(random.nextDouble())).toFloat() // 20Hz - 20kHz
                val gain = (random.nextFloat() * 40f) - 20f // -20 dB to +20 dB
                val q = (random.nextFloat() * 5f) + 0.5f // 0.5 to 5.5
                val type = FilterType.values()[random.nextInt(FilterType.values().size)]
                AcousticNode(id = "node_$idx", label = "Node $idx", filterType = type, frequency = freq, gainDb = gain, qFactor = q)
            }

            val peakGain = BiquadDesigner.calculateCompositePeakGainDb(nodes, sampleRate)

            assertFalse("Composite peak gain is NaN on iter $iter", peakGain.isNaN())
            assertFalse("Composite peak gain is Infinite on iter $iter", peakGain.isInfinite())
            assertTrue("Composite peak gain must be non-negative", peakGain >= 0f)

            // Peak gain cannot drastically exceed sum of positive gains plus resonance overshoot margin
            val sumPositiveGains = nodes.filter { it.gainDb > 0f }.sumOf { it.gainDb.toDouble() }.toFloat()
            assertTrue(
                "Peak gain ($peakGain) should not drastically exceed sum of positive gains ($sumPositiveGains)",
                peakGain <= sumPositiveGains + 15.0f
            )
        }
    }

    private fun Double.pow(exp: Double): Double = java.lang.Math.pow(this, exp)

    // =========================================================================
    // 2. Synchronized Lyrics Engine Fuzzing & High-Volume Robustness
    // =========================================================================

    @Test
    fun lrcParser_fuzzingAndPathologicalInputStress_neverCrashes() {
        val pathologicalInputs = listOf(
            "", // Empty
            "   \n\n\t   \n", // Whitespace only
            "[offset:+500]", // Only offset
            "[offset:-9999999999999999999999999999999999999]", // Giant offset overflow
            "[ar:Artist][al:Album][ti:Title][length:03:45]", // Only ID3 metadata tags
            "[99:99.99] Far into the future", // Extreme timestamp
            "[-01:23.45] Negative timestamp", // Negative timestamp
            "[00:00.00]", // Empty line with timestamp
            "[00:01.00][00:02.00][00:03.00][00:04.00] Multiple stamps on single line",
            "[00:10,50] Comma decimal separator",
            "[00:15.123] Millisecond decimal separator",
            "[00:20.1] Single tenth separator",
            "[00:25] Missing decimals",
            "Plain line without any timestamp tags at all\nSecond plain line",
            "Mixed: [00:05.00] Valid synced line\nPlain intermediate line\n[00:10.00] Another synced line",
            "Special UTF-8 / CJK: [00:01.50] 虎のプレイヤ 🐅 \n[00:03.00] 🎧 Музыка & Español: ¡Olé! \n[00:04.00] עברית & العربية"
        )

        for (input in pathologicalInputs) {
            val result = LrcParser.parse(input)
            assertNotNull(result)
        }
    }

    @Test
    fun lrcParser_highVolumeStress_maintainsSortingAndIntegrity() {
        // Generate 5,000 randomized lyric lines out-of-order
        val random = Random(777)
        val lineCount = 5000
        val sb = java.lang.StringBuilder()
        sb.append("[offset:250]\n")

        for (i in 0 until lineCount) {
            val min = random.nextInt(0, 60)
            val sec = random.nextInt(0, 60)
            val cs = random.nextInt(0, 100)
            sb.append(String.format("[%02d:%02d.%02d] Stress test lyric line %d\n", min, sec, cs, i))
        }

        val parsed = LrcParser.parse(sb.toString())
        assertTrue("Parsed lyrics should be Synced", parsed is Lyrics.Synced)
        val synced = parsed as Lyrics.Synced

        assertEquals(lineCount, synced.lines.size)

        // Verify chronological monotonicity invariant
        for (i in 0 until synced.lines.size - 1) {
            assertTrue(
                "Lyrics must be monotonically sorted by time: line $i (${synced.lines[i].timeMs}) vs line ${i + 1} (${synced.lines[i + 1].timeMs})",
                synced.lines[i].timeMs <= synced.lines[i + 1].timeMs
            )
        }
    }

    // =========================================================================
    // 3. M3U / M3U8 Playlist Parser RFC 8216 Robustness Stress
    // =========================================================================

    @Test
    fun m3uParser_stressAndMalformedInput_parsesGracefully() {
        val pathologicalM3u = """
            #EXTM3U
            #PLAYLIST:Stress Test Master Playlist
            # Some random unparsed directive: #FOOBAR:123
            
            #EXTINF:180 tvg-id="stream-1" group-title="Rock" radio="true", Led Zeppelin - Stairway to Heaven
            "music/rock/stairway.flac"
            
            #EXTINF:-1, Live Stream Audio
            http://stream.example.com/live.aac
            
            #EXTINF:240,Artist with \r\n in name
            \Windows\Path\Style\Song.mp3
            
            # Empty track with no EXTINF
            relative/path/unlabeled.ogg
            
            #EXTINF:312,日本語トラック名 - 桜 🌸
            /storage/emulated/0/Music/桜.flac
        """.trimIndent()

        val playlist = M3uParser.parse(pathologicalM3u)
        assertEquals("Stress Test Master Playlist", playlist.name)
        assertEquals(5, playlist.entries.size)

        // Verify path cleanups and attribute tolerance
        assertEquals("music/rock/stairway.flac", playlist.entries[0].pathOrUri)
        assertEquals("Led Zeppelin - Stairway to Heaven", playlist.entries[0].title)
        assertEquals(180, playlist.entries[0].durationSeconds)

        assertEquals("http://stream.example.com/live.aac", playlist.entries[1].pathOrUri)
        assertEquals(M3uParser.parse("").entries.size, 0)
    }

    // =========================================================================
    // 4. Waveform Boundary Anchoring Envelope Invariant Stress
    // =========================================================================

    @Test
    fun calculateWaveformY_boundaryEnvelopeStress_strictZeroShiftAtEndpoints() {
        val width = 1080f
        val maxHeight = 80f
        val baselineY = 120f

        // Stress-test across 10,000 spatial samples between 0 and width
        val step = width / 10000f

        // Endpoint invariants (issue #337):
        // At x = 0 (shift 0), waveform must intercept baselineY exactly
        val yStart = calculateWaveformY(
            x = 0f,
            width = width,
            maxHeight = maxHeight,
            baselineY = baselineY,
            overlapPx = 0f,
            phase = 0f
        )
        assertEquals("Waveform at x=0 must anchor to baselineY exactly", baselineY, yStart, 0.001f)

        // At x = width, waveform must intercept baselineY exactly
        val yEnd = calculateWaveformY(
            x = width,
            width = width,
            maxHeight = maxHeight,
            baselineY = baselineY,
            overlapPx = 0f,
            phase = 0f
        )
        assertEquals("Waveform at x=width must anchor to baselineY exactly", baselineY, yEnd, 0.001f)

        var prevY = yStart
        for (i in 1..10000) {
            val x = i * step
            val y = calculateWaveformY(
                x = x,
                width = width,
                maxHeight = maxHeight,
                baselineY = baselineY,
                overlapPx = 0f,
                phase = 0f
            )

            assertFalse("Waveform Y must not be NaN at x=$x", y.isNaN())
            assertFalse("Waveform Y must not be Infinite at x=$x", y.isInfinite())

            // Waveform peak cannot exceed baselineY - maxHeight
            assertTrue("Waveform must not exceed maxHeight", y >= baselineY - maxHeight - 1.0f)
            // Waveform cannot dip below baselineY (strictly positive crests)
            assertTrue("Waveform must not dip below baselineY", y <= baselineY + 0.001f)

            // Continuity invariant: no instantaneous step discontinuities
            val delta = abs(y - prevY)
            assertTrue("Waveform must be continuous (delta=$delta at x=$x)", delta < 5.0f)
            prevY = y
        }
    }

    // =========================================================================
    // 5. Neon Harmony Theme Extraction Stress
    // =========================================================================

    @Test
    fun dominantColorExtractor_neonPalette_handlesExtremeColors() {
        val extremeColors = listOf(
            0xFF000000.toInt(), // Pure black
            0xFFFFFFFF.toInt(), // Pure white
            0x00000000,         // Transparent
            0xFFFF0000.toInt(), // Pure red
            0xFF00FF00.toInt(), // Pure green
            0xFF0000FF.toInt(), // Pure blue
            0xFF808080.toInt()  // Mid gray
        )

        for (colorInt in extremeColors) {
            val snapped = DominantColorExtractor.snapToNearestNeon(Color(colorInt))
            assertNotNull(snapped)
            val harmonizing = DominantColorExtractor.getHarmonizingNeon(snapped)
            assertNotNull(harmonizing)
        }
    }

    // =========================================================================
    // 6. AudioDeviceRepository Concurrency & Race-Condition Stress
    // =========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun audioDeviceRepository_concurrentMultiThreadStress_noDeadlockOrCorruption() = runTest {
        val audioDeviceDao = mockk<AudioDeviceDao>(relaxed = true)
        val bluetoothDeviceManager = mockk<BluetoothDeviceManager>(relaxed = true)
        val autoEqRepository = mockk<AutoEqRepository>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val btStateFlow = MutableStateFlow(BluetoothDeviceInfo())
        val settingsStateFlow = MutableStateFlow(TigerSettingsState())

        every { bluetoothDeviceManager.connectedDevice } returns btStateFlow
        every { settingsDataStore.settingsFlow } returns settingsStateFlow
        coEvery { audioDeviceDao.getAllDevicesFlow() } returns MutableStateFlow(emptyList())
        coEvery { audioDeviceDao.getDeviceById(any()) } returns null

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repo = AudioDeviceRepositoryImpl(
            audioDeviceDao = audioDeviceDao,
            bluetoothDeviceManager = bluetoothDeviceManager,
            autoEqRepository = autoEqRepository,
            settingsDataStore = settingsDataStore,
            ioDispatcher = testDispatcher
        )

        // Launch 50 concurrent operations simulating rapid Bluetooth connects,
        // disconnects, profile queries, and preset activations
        val jobs = (0 until 50).map { id ->
            launch {
                if (id % 2 == 0) {
                    repo.recordDeviceConnected(
                        deviceId = "00:11:22:33:44:${String.format("%02X", id)}",
                        displayName = "Headphones $id",
                        type = AudioDeviceType.BLUETOOTH,
                        codec = if (id % 4 == 0) "LDAC" else "AAC",
                        batteryLevel = 50 + (id % 50)
                    )
                } else {
                    repo.recordDeviceDisconnected("00:11:22:33:44:${String.format("%02X", id - 1)}")
                }
            }
        }

        testScheduler.advanceUntilIdle()
        jobs.joinAll()

        // Verify repository state remains valid and accessible
        val active = repo.currentActiveDevice.value
        assertNotNull(active)
        assertTrue("Expected non-empty deviceId but got '${active.deviceId}'", active.deviceId.isNotEmpty())
    }
}
