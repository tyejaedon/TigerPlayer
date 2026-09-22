package com.tigerplayer.ui.prism

object PrismTestTags {
    const val ENABLE_SWITCH = "prism_enable_switch"
    const val RESET_BUTTON = "prism_reset_button"
    const val ANALYSIS_FFT_CHIP = "prism_analysis_fft_chip"
    const val ANALYSIS_BANDPASS_CHIP = "prism_analysis_bandpass_chip"
    const val ANALYSIS_PROFILE_LABEL = "prism_analysis_profile_label"
    const val DOMINANT_BAND_LABEL = "prism_dominant_band_label"
    const val SPECTRAL_SECTION = "prism_spectral_section"
    const val SCREEN_ROOT = "prism_screen_root"
    const val SCREEN_BACK_BUTTON = "prism_screen_back_button"
    const val SCREEN_MIXER = "prism_screen_mixer"
    const val HOME_ENTRY_CARD = "prism_home_entry_card"
    const val HOME_ENTRY_SWITCH = "prism_home_entry_switch"

    fun presetChip(preset: PrismPreset): String {
        return "prism_preset_${preset.name.lowercase()}"
    }
}

