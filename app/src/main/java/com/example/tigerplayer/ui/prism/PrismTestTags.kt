package com.example.tigerplayer.ui.prism

object PrismTestTags {
    const val ENABLE_SWITCH = "prism_enable_switch"
    const val RESET_BUTTON = "prism_reset_button"
    const val ANALYSIS_FFT_CHIP = "prism_analysis_fft_chip"
    const val ANALYSIS_BANDPASS_CHIP = "prism_analysis_bandpass_chip"
    const val ANALYSIS_PROFILE_LABEL = "prism_analysis_profile_label"
    const val DOMINANT_BAND_LABEL = "prism_dominant_band_label"
    const val SPECTRAL_SECTION = "prism_spectral_section"

    fun presetChip(preset: PrismPreset): String {
        return "prism_preset_${preset.name.lowercase()}"
    }
}

