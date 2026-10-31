package com.tigerplayer.data.model

enum class AudioDeviceType {
    PHONE_SPEAKER,
    WIRED_HEADPHONES,
    BLUETOOTH,
    USB_DAC;

    companion object {
        fun fromString(value: String): AudioDeviceType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BLUETOOTH
        }
    }
}

/**
 * Domain representation of an audio output destination in TigerPlayer.
 */
data class AudioOutputDevice(
    val deviceId: String,
    val displayName: String,
    val type: AudioDeviceType,
    val isConnected: Boolean,
    val isActiveRoute: Boolean,
    val lastConnectedEpochMs: Long,
    val codec: String = "N/A",
    val batteryLevel: Int = -1,
    val assignedPresetId: String? = null,
    val matchedAutoEqModel: String? = null,
    val matchedAutoEqSource: String? = null,
    val targetCurve: String? = null,
    val formFactor: String? = null,
    val listeningTimeMs: Long = 0L
) {
    companion object {
        const val DEVICE_ID_SPEAKER = "builtin:phone_speaker"
        const val DEVICE_ID_WIRED = "builtin:wired_headset"
    }
}
