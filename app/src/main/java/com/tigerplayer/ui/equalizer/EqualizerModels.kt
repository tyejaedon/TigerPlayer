package com.tigerplayer.ui.equalizer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.tigerplayer.engine.AcousticEnvironmentMode
import com.tigerplayer.engine.AcousticNode
import com.tigerplayer.engine.FilterType

/**
 * Filter band representation for the Parametric Equalizer Hub.
 *
 * @property id Unique identifier for the band.
 * @property label Display label (e.g., "31 Hz", "1 kHz").
 * @property frequencyHz Center frequency in Hz (20 Hz - 20,000 Hz).
 * @property gainDb Gain boost or cut in dB (-15.0 dB to +15.0 dB).
 * @property qFactor Filter resonance / bandwidth Q-factor (0.1 to 10.0).
 * @property filterType Filter topology: [FilterType.LOW_SHELF], [FilterType.PEAKING], or [FilterType.HIGH_SHELF].
 * @property isEnabled Whether this individual band is active.
 * @property color Display accent color for curve nodes and UI representation.
 */
data class ParametricBand(
    val id: String,
    val label: String,
    val frequencyHz: Float,
    val gainDb: Float,
    val qFactor: Float = 1.41f,
    val filterType: FilterType = FilterType.PEAKING,
    val isEnabled: Boolean = true,
    val color: Color = Color(0xFF00E5FF)
) {
    fun toAcousticNode(): AcousticNode {
        return AcousticNode(
            id = id,
            label = label,
            filterType = filterType,
            frequency = frequencyHz.coerceIn(20f, 20_000f),
            gainDb = if (isEnabled) gainDb.coerceIn(-15f, 15f) else 0f,
            qFactor = qFactor.coerceIn(0.1f, 10f)
        )
    }
}

/**
 * Supported band layout modes for the Equalizer Hub.
 */
enum class EqualizerBandMode(val title: String) {
    FIVE_BAND("5-Band"),
    TEN_BAND("10-Band ISO"),
    AUTOEQ_PARAMETRIC("AutoEq PEQ")
}

/**
 * Top-level tabs for the Main App Studio Equalizer view.
 */
enum class EqualizerStudioTab(val title: String) {
    STUDIO_EQ("Equalizer"),
    AUTOEQ_CATALOG("AutoEq Database"),
    ACOUSTIC_SPACE("Acoustic Space")
}

/**
 * Industry-standard 10-band ISO octave center frequencies.
 */
val ISO_10_BANDS = listOf(
    ParametricBand("b1", "31 Hz", 31f, 0f, 1.0f, FilterType.LOW_SHELF, color = Color(0xFFFF007F)),
    ParametricBand("b2", "63 Hz", 63f, 0f, 1.41f, FilterType.PEAKING, color = Color(0xFFFF3366)),
    ParametricBand("b3", "125 Hz", 125f, 0f, 1.41f, FilterType.PEAKING, color = Color(0xFFFF6633)),
    ParametricBand("b4", "250 Hz", 250f, 0f, 1.41f, FilterType.PEAKING, color = Color(0xFFFF9900)),
    ParametricBand("b5", "500 Hz", 500f, 0f, 1.41f, FilterType.PEAKING, color = Color(0xFFFFD500)),
    ParametricBand("b6", "1 kHz", 1000f, 0f, 1.41f, FilterType.PEAKING, color = Color(0xFF67F7B1)),
    ParametricBand("b7", "2 kHz", 2000f, 0f, 1.41f, FilterType.PEAKING, color = Color(0xFF39FF14)),
    ParametricBand("b8", "4 kHz", 4000f, 0f, 1.41f, FilterType.PEAKING, color = Color(0xFF00FFCC)),
    ParametricBand("b9", "8 kHz", 8000f, 0f, 1.41f, FilterType.PEAKING, color = Color(0xFF00E5FF)),
    ParametricBand("b10", "16 kHz", 16000f, 0f, 1.0f, FilterType.HIGH_SHELF, color = Color(0xFF8B5CF6))
)

/**
 * Compact 5-band frequency bands optimized for quick adjustment.
 */
val COMPACT_5_BANDS = listOf(
    ParametricBand("b_sub", "60 Hz", 60f, 0f, 0.707f, FilterType.LOW_SHELF, color = Color(0xFFFF007F)),
    ParametricBand("b_bass", "230 Hz", 230f, 0f, 1.0f, FilterType.PEAKING, color = Color(0xFFFFD500)),
    ParametricBand("b_mid", "910 Hz", 910f, 0f, 1.2f, FilterType.PEAKING, color = Color(0xFF67F7B1)),
    ParametricBand("b_pres", "3.6 kHz", 3600f, 0f, 1.2f, FilterType.PEAKING, color = Color(0xFF00E5FF)),
    ParametricBand("b_air", "14 kHz", 14000f, 0f, 0.707f, FilterType.HIGH_SHELF, color = Color(0xFF8B5CF6))
)

/**
 * Standard industry equalizer curve presets.
 */
object EqualizerPresets {
    val FLAT = "Flat"
    val BASS_BOOST = "Bass Boost"
    val BASS_REDUCER = "Bass Reducer"
    val VOCAL_BOOSTER = "Vocal Booster"
    val TREBLE_BOOST = "Treble Boost"
    val TREBLE_REDUCER = "Treble Reducer"
    val ROCK = "Rock"
    val POP = "Pop"
    val ELECTRONIC = "Electronic"
    val JAZZ = "Jazz"
    val CLASSICAL = "Classical"
    val ACOUSTIC = "Acoustic"
    val HIP_HOP = "Hip-Hop"
    val R_AND_B = "R&B"
    val NIGHT_DRIVE = "Night Drive"

    val ALL_PRESETS = listOf(
        FLAT,
        BASS_BOOST,
        VOCAL_BOOSTER,
        TREBLE_BOOST,
        ELECTRONIC,
        ROCK,
        HIP_HOP,
        POP,
        JAZZ,
        ACOUSTIC,
        CLASSICAL,
        R_AND_B,
        BASS_REDUCER,
        TREBLE_REDUCER,
        NIGHT_DRIVE
    )

    /**
     * Map of 10-band target gain curves for standard industry presets.
     */
    val TEN_BAND_GAINS = mapOf(
        FLAT to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        BASS_BOOST to listOf(6f, 5f, 3.5f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f),
        BASS_REDUCER to listOf(-5f, -4f, -2.5f, -1f, 0f, 0f, 0f, 0f, 0f, 0f),
        VOCAL_BOOSTER to listOf(-1.5f, -1f, 0f, 1f, 2.5f, 3.5f, 3.5f, 2f, 1f, 0f),
        TREBLE_BOOST to listOf(0f, 0f, 0f, 0f, 0.5f, 1.5f, 3f, 4.5f, 6f, 6.5f),
        TREBLE_REDUCER to listOf(0f, 0f, 0f, 0f, -0.5f, -1.5f, -3f, -4.5f, -6f, -6.5f),
        ROCK to listOf(4.5f, 3.5f, 1.5f, -0.5f, -1.5f, 0f, 1.5f, 3f, 4f, 4.5f),
        POP to listOf(1.5f, 2f, 3f, 1.5f, 0f, -1f, 1f, 2f, 3f, 3.5f),
        ELECTRONIC to listOf(5.5f, 4.5f, 2f, 0f, -1.5f, 1f, 2f, 3f, 4.5f, 5f),
        JAZZ to listOf(3f, 2f, 0f, 1.5f, 1.5f, 1f, 1.5f, 2f, 2.5f, 3f),
        CLASSICAL to listOf(4f, 3f, 2f, 1.5f, -1f, -1f, 0f, 2f, 3f, 3.5f),
        ACOUSTIC to listOf(3.5f, 2.5f, 1.5f, 1f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f),
        HIP_HOP to listOf(6f, 5f, 3f, 1f, -1f, 1f, 1.5f, 2.5f, 3.5f, 4f),
        R_AND_B to listOf(4.5f, 3.5f, 2f, 0.5f, -0.5f, 1f, 1.5f, 2f, 3f, 3.5f),
        NIGHT_DRIVE to listOf(5f, 4f, 2f, 0.5f, -1f, 0f, 1.5f, 2.5f, 3.5f, 4.5f)
    )

    /**
     * Map of 5-band target gain curves.
     */
    val FIVE_BAND_GAINS = mapOf(
        FLAT to listOf(0f, 0f, 0f, 0f, 0f),
        BASS_BOOST to listOf(5.5f, 3f, 0f, 0f, 0f),
        BASS_REDUCER to listOf(-5f, -2.5f, 0f, 0f, 0f),
        VOCAL_BOOSTER to listOf(-1.5f, 0.5f, 2.5f, 3.5f, 0.5f),
        TREBLE_BOOST to listOf(0f, 0f, 1f, 3.5f, 6f),
        TREBLE_REDUCER to listOf(0f, 0f, -1f, -3.5f, -6f),
        ROCK to listOf(4f, 1.5f, -1f, 2f, 4f),
        POP to listOf(2f, 2.5f, -0.5f, 1.5f, 3.5f),
        ELECTRONIC to listOf(5f, 2f, -1f, 2.5f, 4.5f),
        JAZZ to listOf(2.5f, 0.5f, 1.5f, 1.5f, 2.5f),
        CLASSICAL to listOf(3.5f, 2f, -1f, 1.5f, 3.5f),
        ACOUSTIC to listOf(3f, 1.5f, 1f, 2f, 3f),
        HIP_HOP to listOf(5.5f, 2.5f, -0.5f, 2f, 3.5f),
        R_AND_B to listOf(4f, 1.5f, 0f, 2f, 3.5f),
        NIGHT_DRIVE to listOf(4.5f, 1.5f, -0.5f, 2f, 4f)
    )
}

/**
 * State container for the Equalizer Hub UI.
 */
data class EqualizerHubUiState(
    val isEqEnabled: Boolean = true,
    val bandMode: EqualizerBandMode = EqualizerBandMode.TEN_BAND,
    val bands: List<ParametricBand> = ISO_10_BANDS,
    val selectedBandIndex: Int = 0,
    val preampDb: Float = 0f,
    val compositePeakDb: Float = 0f,
    val autoMakeupGain: Float = 1.0f,
    val activePresetName: String = EqualizerPresets.FLAT,
    val autoEqModel: String? = null,
    val autoEqSource: String? = null,
    val targetCurve: String? = null,
    val connectedDeviceName: String = "Internal Speaker",
    val environmentMode: AcousticEnvironmentMode = AcousticEnvironmentMode.STUDIO,
    val studioTab: EqualizerStudioTab = EqualizerStudioTab.STUDIO_EQ,
    val frequencyCurvePoints: List<Offset> = emptyList(),
    val isSearchingAutoEq: Boolean = false,
    val autoEqQuery: String = "",
    val autoEqSearchResults: List<com.tigerplayer.data.model.HeadphoneSearchResult> = emptyList(),
    val statusMessage: String? = null
) {
    val isClippingImminent: Boolean
        get() = (compositePeakDb + preampDb) > 0.05f
}
