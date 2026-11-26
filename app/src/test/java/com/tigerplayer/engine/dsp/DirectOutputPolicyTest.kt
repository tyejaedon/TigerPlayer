package com.tigerplayer.engine

import com.tigerplayer.data.model.AudioDeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DirectOutputPolicy] (issue #291).
 *
 * Covers the full sample-rate x device-type decision table (44.1k, 48k, 88.2k, 96k, 176.4k, 192k
 * across USB DAC, wired headphones, phone speaker, and Bluetooth), fallback paths when platform
 * direct playback is unsupported, and Samsung float-output compatibility guarding.
 */
class DirectOutputPolicyTest {

    private val targetRates = listOf(44_100, 48_000, 88_200, 96_000, 176_400, 192_000)

    @Test
    fun `usb dac requests direct pcm across all target sample rates when supported`() {
        for (rate in targetRates) {
            for (encoding in listOf(
                DirectPcmEncoding.PCM_16BIT,
                DirectPcmEncoding.PCM_24BIT,
                DirectPcmEncoding.PCM_32BIT,
                DirectPcmEncoding.PCM_FLOAT
            )) {
                assertTrue(
                    "Expected direct PCM for USB_DAC at ${rate}Hz ($encoding)",
                    DirectOutputPolicy.shouldRequestDirectPcm(
                        deviceType = AudioDeviceType.USB_DAC,
                        sampleRate = rate,
                        encoding = encoding,
                        isDirectSupported = true
                    )
                )
            }
        }
    }

    @Test
    fun `wired headphones request direct pcm for hi-res rates and 24 or 32-bit formats when supported`() {
        val hiResRates = listOf(88_200, 96_000, 176_400, 192_000)
        for (rate in hiResRates) {
            assertTrue(
                "Expected direct PCM for WIRED_HEADPHONES at hi-res ${rate}Hz 16-bit",
                DirectOutputPolicy.shouldRequestDirectPcm(
                    deviceType = AudioDeviceType.WIRED_HEADPHONES,
                    sampleRate = rate,
                    encoding = DirectPcmEncoding.PCM_16BIT,
                    isDirectSupported = true
                )
            )
        }

        for (rate in targetRates) {
            for (encoding in listOf(
                DirectPcmEncoding.PCM_24BIT,
                DirectPcmEncoding.PCM_32BIT,
                DirectPcmEncoding.PCM_FLOAT
            )) {
                assertTrue(
                    "Expected direct PCM for WIRED_HEADPHONES at ${rate}Hz ($encoding)",
                    DirectOutputPolicy.shouldRequestDirectPcm(
                        deviceType = AudioDeviceType.WIRED_HEADPHONES,
                        sampleRate = rate,
                        encoding = encoding,
                        isDirectSupported = true
                    )
                )
            }
        }

        for (standardRate in listOf(44_100, 48_000)) {
            assertFalse(
                "Expected mixed path for standard 16-bit wired output at ${standardRate}Hz",
                DirectOutputPolicy.shouldRequestDirectPcm(
                    deviceType = AudioDeviceType.WIRED_HEADPHONES,
                    sampleRate = standardRate,
                    encoding = DirectPcmEncoding.PCM_16BIT,
                    isDirectSupported = true
                )
            )
        }
    }

    @Test
    fun `phone speaker and bluetooth always stay on mixed path across all sample rates`() {
        for (deviceType in listOf(AudioDeviceType.PHONE_SPEAKER, AudioDeviceType.BLUETOOTH)) {
            for (rate in targetRates) {
                for (encoding in DirectPcmEncoding.entries) {
                    assertFalse(
                        "Expected mixed path for $deviceType at ${rate}Hz ($encoding)",
                        DirectOutputPolicy.shouldRequestDirectPcm(
                            deviceType = deviceType,
                            sampleRate = rate,
                            encoding = encoding,
                            isDirectSupported = true
                        )
                    )
                }
            }
        }
    }

    @Test
    fun `unsupported platform flag always falls back to mixed path`() {
        for (deviceType in AudioDeviceType.entries) {
            for (rate in targetRates) {
                for (encoding in DirectPcmEncoding.entries) {
                    assertFalse(
                        "Expected fallback when isDirectSupported=false for $deviceType at ${rate}Hz",
                        DirectOutputPolicy.shouldRequestDirectPcm(
                            deviceType = deviceType,
                            sampleRate = rate,
                            encoding = encoding,
                            isDirectSupported = false
                        )
                    )
                }
            }
        }
    }

    @Test
    fun `invalid sample rates and non-pcm encodings fall back to mixed path`() {
        for (badRate in listOf(-1, 0, 8_000, 22_050, 32_000)) {
            assertFalse(
                DirectOutputPolicy.shouldRequestDirectPcm(
                    deviceType = AudioDeviceType.USB_DAC,
                    sampleRate = badRate,
                    encoding = DirectPcmEncoding.PCM_24BIT,
                    isDirectSupported = true
                )
            )
        }

        assertFalse(
            DirectOutputPolicy.shouldRequestDirectPcm(
                deviceType = AudioDeviceType.USB_DAC,
                sampleRate = 96_000,
                encoding = DirectPcmEncoding.OTHER,
                isDirectSupported = true
            )
        )
    }

    @Test
    fun `float output enables for 24 and 32-bit when direct supported and not samsung`() {
        assertTrue(
            DirectOutputPolicy.shouldEnableFloatOutput(
                isSamsungDevice = false,
                isDirectSupported = true,
                encoding = DirectPcmEncoding.PCM_24BIT
            )
        )
        assertTrue(
            DirectOutputPolicy.shouldEnableFloatOutput(
                isSamsungDevice = false,
                isDirectSupported = true,
                encoding = DirectPcmEncoding.PCM_32BIT
            )
        )
        assertFalse(
            DirectOutputPolicy.shouldEnableFloatOutput(
                isSamsungDevice = true,
                isDirectSupported = true,
                encoding = DirectPcmEncoding.PCM_24BIT
            )
        )
        assertFalse(
            DirectOutputPolicy.shouldEnableFloatOutput(
                isSamsungDevice = false,
                isDirectSupported = false,
                encoding = DirectPcmEncoding.PCM_24BIT
            )
        )
        assertFalse(
            DirectOutputPolicy.shouldEnableFloatOutput(
                isSamsungDevice = false,
                isDirectSupported = true,
                encoding = DirectPcmEncoding.PCM_16BIT
            )
        )
    }

    @Test
    fun `evaluate returns negotiated rate and encoding when direct is active and mixer defaults otherwise`() {
        val directDecision = DirectOutputPolicy.evaluate(
            deviceType = AudioDeviceType.USB_DAC,
            sampleRate = 192_000,
            encoding = DirectPcmEncoding.PCM_24BIT,
            isDirectSupported = true,
            isSamsungDevice = false
        )
        assertTrue(directDecision.requestDirectPcm)
        assertTrue(directDecision.enableFloatOutput)
        assertEquals(192_000, directDecision.negotiatedSampleRateHz)
        assertEquals(DirectPcmEncoding.PCM_24BIT, directDecision.negotiatedEncoding)

        val fallbackDecision = DirectOutputPolicy.evaluate(
            deviceType = AudioDeviceType.BLUETOOTH,
            sampleRate = 96_000,
            encoding = DirectPcmEncoding.PCM_24BIT,
            isDirectSupported = true,
            isSamsungDevice = false
        )
        assertFalse(fallbackDecision.requestDirectPcm)
        assertEquals(DirectOutputPolicy.STANDARD_MIXER_SAMPLE_RATE_HZ, fallbackDecision.negotiatedSampleRateHz)
        assertEquals(DirectPcmEncoding.PCM_16BIT, fallbackDecision.negotiatedEncoding)
    }
}
