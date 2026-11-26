package com.tigerplayer.engine

import com.tigerplayer.data.model.AudioDeviceType

/**
 * Pure decision policy for negotiating direct hi-res PCM playback vs. the Android software mixer
 * (issue #291).
 *
 * No Android framework types appear in any signature so the full sample-rate x device-type matrix
 * can be verified in pure JVM unit tests.
 */
object DirectOutputPolicy {

    const val STANDARD_MIXER_SAMPLE_RATE_HZ = 48_000
    const val MIN_DIRECT_SAMPLE_RATE_HZ = 44_100
    const val HI_RES_SAMPLE_RATE_THRESHOLD_HZ = 48_000

    /** Standard PCM sample rates targeted for native direct output negotiation. */
    val SUPPORTED_SAMPLE_RATES_HZ: List<Int> = listOf(
        44_100,
        48_000,
        88_200,
        96_000,
        176_400,
        192_000
    )

    /**
     * Decides whether to request direct PCM output for `(deviceType, sampleRate, encoding, isDirectSupported)`.
     *
     * Rules:
     * - If [isDirectSupported] is false, [sampleRate] < 44.1 kHz, or [encoding] is [DirectPcmEncoding.OTHER],
     *   fall back to the mixed path (`false`).
     * - [AudioDeviceType.PHONE_SPEAKER] and [AudioDeviceType.BLUETOOTH] always use the mixed path (`false`),
     *   because built-in speakers rely on system protection DSP and Bluetooth transcodes over A2DP/BLE.
     * - [AudioDeviceType.USB_DAC] requests direct PCM for all supported PCM formats >= 44.1 kHz
     *   (including 44.1 kHz Red Book to avoid 44.1k -> 48k mixer resampling, and 48k-192k native rates).
     * - [AudioDeviceType.WIRED_HEADPHONES] requests direct PCM when the stream is high-resolution
     *   (> 48 kHz sample rate or 24/32-bit/float PCM) and [isDirectSupported] is true; standard
     *   16-bit 44.1/48 kHz wired output stays on the mixed path.
     */
    fun shouldRequestDirectPcm(
        deviceType: AudioDeviceType,
        sampleRate: Int,
        encoding: DirectPcmEncoding,
        isDirectSupported: Boolean
    ): Boolean {
        if (!isDirectSupported) return false
        if (sampleRate < MIN_DIRECT_SAMPLE_RATE_HZ) return false
        if (encoding == DirectPcmEncoding.OTHER) return false

        return when (deviceType) {
            AudioDeviceType.USB_DAC -> true
            AudioDeviceType.WIRED_HEADPHONES ->
                sampleRate > HI_RES_SAMPLE_RATE_THRESHOLD_HZ || encoding.isHighResolution
            AudioDeviceType.PHONE_SPEAKER,
            AudioDeviceType.BLUETOOTH -> false
        }
    }

    /**
     * Computes whether float output should be enabled for 24/32-bit high-resolution paths.
     *
     * Preserves the Samsung OEM compatibility exception (HAL instability in float output mode)
     * and disables float conversion when direct hi-res output is unsupported.
     */
    fun shouldEnableFloatOutput(
        isSamsungDevice: Boolean,
        isDirectSupported: Boolean,
        encoding: DirectPcmEncoding = DirectPcmEncoding.PCM_24BIT
    ): Boolean {
        if (isSamsungDevice) return false
        if (!isDirectSupported) return false
        return encoding.isHighResolution
    }

    /**
     * Evaluates both direct PCM routing and float-output configuration for the active route and stream.
     */
    fun evaluate(
        deviceType: AudioDeviceType,
        sampleRate: Int,
        encoding: DirectPcmEncoding,
        isDirectSupported: Boolean,
        isSamsungDevice: Boolean = false
    ): DirectOutputDecision {
        val requestDirect = shouldRequestDirectPcm(
            deviceType = deviceType,
            sampleRate = sampleRate,
            encoding = encoding,
            isDirectSupported = isDirectSupported
        )
        val enableFloat = shouldEnableFloatOutput(
            isSamsungDevice = isSamsungDevice,
            isDirectSupported = isDirectSupported,
            encoding = encoding
        )
        return DirectOutputDecision(
            requestDirectPcm = requestDirect,
            enableFloatOutput = enableFloat,
            negotiatedSampleRateHz = if (requestDirect) sampleRate else STANDARD_MIXER_SAMPLE_RATE_HZ,
            negotiatedEncoding = if (requestDirect) encoding else DirectPcmEncoding.PCM_16BIT
        )
    }
}
