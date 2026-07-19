package com.example.tigerplayer.ui.prism

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.tigerplayer.data.local.PrismSpectralAnalysis
import com.example.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class PrismInlineMixerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mixer_controls_emit_expected_callbacks() {
        var enabled = true
        var selectedPreset: PrismPreset? = null
        var selectedAnalysis = PrismSpectralAnalysis.FFT
        var resetCount = 0

        composeRule.setContent {
            TigerPlayerTheme {
                PrismInlineMixer(
                    state = PrismUiState(
                        vocals = 0.85f,
                        beats = 0.75f,
                        instruments = 0.9f,
                        isPrismEnabled = enabled,
                        preset = PrismPreset.BALANCED,
                        spectralAnalysis = selectedAnalysis,
                        spectralBands = listOf(0.9f, 0.15f, 0.05f, 0.04f, 0.02f, 0.01f)
                    ),
                    onVocalsChange = {},
                    onBeatsChange = {},
                    onInstrumentsChange = {},
                    onEnabledChange = { enabled = it },
                    onPresetSelected = { selectedPreset = it },
                    onResetRequested = { resetCount += 1 },
                    onSpectralAnalysisChange = { selectedAnalysis = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                )
            }
        }

        composeRule.onNodeWithTag(PrismTestTags.ENABLE_SWITCH).performClick()
        composeRule.runOnIdle {
            assertFalse(enabled)
        }

        composeRule.onNodeWithTag(PrismTestTags.presetChip(PrismPreset.VOCAL_FOCUS)).performClick()
        composeRule.runOnIdle {
            assertEquals(PrismPreset.VOCAL_FOCUS, selectedPreset)
        }

        composeRule.onNodeWithTag(PrismTestTags.ANALYSIS_BANDPASS_CHIP).performClick()
        composeRule.runOnIdle {
            assertEquals(PrismSpectralAnalysis.BANDPASS, selectedAnalysis)
        }

        composeRule.onNodeWithTag(PrismTestTags.RESET_BUTTON).performClick()
        composeRule.runOnIdle {
            assertEquals(1, resetCount)
        }
    }

    @Test
    fun spectral_visuals_react_to_low_vs_high_synthetic_input() {
        var state by mutableStateOf(
            PrismUiState(
                vocals = 1f,
                beats = 1f,
                instruments = 1f,
                isPrismEnabled = true,
                spectralAnalysis = PrismSpectralAnalysis.FFT,
                spectralBands = listOf(0.95f, 0.60f, 0.15f, 0.07f, 0.04f, 0.02f)
            )
        )

        composeRule.setContent {
            TigerPlayerTheme {
                PrismInlineMixer(
                    state = state,
                    onVocalsChange = {},
                    onBeatsChange = {},
                    onInstrumentsChange = {},
                    onEnabledChange = {},
                    onPresetSelected = {},
                    onResetRequested = {},
                    onSpectralAnalysisChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                )
            }
        }

        composeRule.onNodeWithTag(PrismTestTags.DOMINANT_BAND_LABEL)
            .assertTextContains("60 Hz")

        composeRule.runOnIdle {
            state = state.copy(
                spectralBands = listOf(0.02f, 0.05f, 0.10f, 0.30f, 0.82f, 0.94f)
            )
        }

        composeRule.onNodeWithTag(PrismTestTags.DOMINANT_BAND_LABEL)
            .assertTextContains("12 kHz")
    }

    @Test
    fun analysis_profiler_label_reflects_mode_and_cost_changes() {
        var state by mutableStateOf(
            PrismUiState(
                isPrismEnabled = true,
                spectralAnalysis = PrismSpectralAnalysis.FFT,
                observedAnalysisMode = PrismSpectralAnalysis.FFT,
                analysisCostMicros = 850f
            )
        )

        composeRule.setContent {
            TigerPlayerTheme {
                PrismInlineMixer(
                    state = state,
                    onVocalsChange = {},
                    onBeatsChange = {},
                    onInstrumentsChange = {},
                    onEnabledChange = {},
                    onPresetSelected = {},
                    onResetRequested = {},
                    onSpectralAnalysisChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                )
            }
        }

        composeRule.onNodeWithTag(PrismTestTags.ANALYSIS_PROFILE_LABEL)
            .assertTextContains("FFT")
        composeRule.onNodeWithTag(PrismTestTags.ANALYSIS_PROFILE_LABEL)
            .assertTextContains("0.85")

        composeRule.runOnIdle {
            state = state.copy(
                observedAnalysisMode = PrismSpectralAnalysis.BANDPASS,
                analysisCostMicros = 2100f
            )
        }

        composeRule.onNodeWithTag(PrismTestTags.ANALYSIS_PROFILE_LABEL)
            .assertTextContains("Bandpass")
        composeRule.onNodeWithTag(PrismTestTags.ANALYSIS_PROFILE_LABEL)
            .assertTextContains("2.10")
    }
}

