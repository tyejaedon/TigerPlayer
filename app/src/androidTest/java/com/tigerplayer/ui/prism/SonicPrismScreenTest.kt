package com.tigerplayer.ui.prism

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tigerplayer.data.local.PrismSpectralAnalysis
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Coverage for issue #173 — Sonic Prism's full-screen destination.
 *
 * The mixer used to be an expandable card inside the Home feed; this asserts the standalone screen
 * exposes the same controls and reports back navigation.
 */
class SonicPrismScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun state(
        isEnabled: Boolean = true,
        preset: PrismPreset = PrismPreset.BALANCED
    ) = PrismUiState(
        vocals = 0.8f,
        beats = 0.6f,
        instruments = 0.9f,
        isPrismEnabled = isEnabled,
        preset = preset,
        spectralAnalysis = PrismSpectralAnalysis.FFT,
        spectralBands = listOf(0.9f, 0.4f, 0.2f, 0.1f, 0.05f, 0.02f)
    )

    @Test
    fun screen_shows_the_mixer_and_its_controls() {
        composeRule.setContent {
            TigerPlayerTheme {
                SonicPrismScreen(
                    state = state(),
                    onBackClick = {},
                    onVocalsChange = {},
                    onBeatsChange = {},
                    onInstrumentsChange = {},
                    onEnabledChange = {},
                    onPresetSelected = {},
                    onResetRequested = {}
                )
            }
        }

        composeRule.onNodeWithTag(PrismTestTags.SCREEN_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(PrismTestTags.SCREEN_MIXER).assertIsDisplayed()
        composeRule.onNodeWithTag(PrismTestTags.SPECTRAL_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(PrismTestTags.RESET_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(PrismTestTags.DOMINANT_BAND_LABEL).assertIsDisplayed()
    }

    @Test
    fun back_button_reports_navigation() {
        var backCount = 0

        composeRule.setContent {
            TigerPlayerTheme {
                SonicPrismScreen(
                    state = state(),
                    onBackClick = { backCount += 1 },
                    onVocalsChange = {},
                    onBeatsChange = {},
                    onInstrumentsChange = {},
                    onEnabledChange = {},
                    onPresetSelected = {},
                    onResetRequested = {}
                )
            }
        }

        composeRule.onNodeWithTag(PrismTestTags.SCREEN_BACK_BUTTON).performClick()

        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    @Test
    fun the_app_bar_switch_reflects_and_toggles_enabled_state() {
        var enabled = false

        composeRule.setContent {
            TigerPlayerTheme {
                SonicPrismScreen(
                    state = state(isEnabled = enabled),
                    onBackClick = {},
                    onVocalsChange = {},
                    onBeatsChange = {},
                    onInstrumentsChange = {},
                    onEnabledChange = { enabled = it },
                    onPresetSelected = {},
                    onResetRequested = {}
                )
            }
        }

        composeRule.onNodeWithTag(PrismTestTags.ENABLE_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(PrismTestTags.ENABLE_SWITCH).performClick()

        composeRule.runOnIdle { assertTrue("switch must report enabling Prism", enabled) }
    }

    @Test
    fun an_enabled_state_renders_the_switch_as_on() {
        composeRule.setContent {
            TigerPlayerTheme {
                SonicPrismScreen(
                    state = state(isEnabled = true),
                    onBackClick = {},
                    onVocalsChange = {},
                    onBeatsChange = {},
                    onInstrumentsChange = {},
                    onEnabledChange = {},
                    onPresetSelected = {},
                    onResetRequested = {}
                )
            }
        }

        composeRule.onNodeWithTag(PrismTestTags.ENABLE_SWITCH).assertIsOn()
    }

    @Test
    fun selecting_a_preset_reports_it() {
        var selected: PrismPreset? = null

        composeRule.setContent {
            TigerPlayerTheme {
                SonicPrismScreen(
                    state = state(),
                    onBackClick = {},
                    onVocalsChange = {},
                    onBeatsChange = {},
                    onInstrumentsChange = {},
                    onEnabledChange = {},
                    onPresetSelected = { selected = it },
                    onResetRequested = {}
                )
            }
        }

        composeRule.onNodeWithTag(PrismTestTags.presetChip(PrismPreset.VOCAL_FOCUS)).performClick()

        composeRule.runOnIdle { assertEquals(PrismPreset.VOCAL_FOCUS, selected) }
    }

    @Test
    fun the_screen_does_not_duplicate_the_enable_switch() {
        composeRule.setContent {
            TigerPlayerTheme {
                SonicPrismScreen(
                    state = state(),
                    onBackClick = {},
                    onVocalsChange = {},
                    onBeatsChange = {},
                    onInstrumentsChange = {},
                    onEnabledChange = {},
                    onPresetSelected = {},
                    onResetRequested = {}
                )
            }
        }

        // The app bar owns the switch; the embedded mixer must not render a second one carrying
        // the same tag, which would make every tag lookup ambiguous.
        composeRule.onNodeWithTag(PrismTestTags.ENABLE_SWITCH).assertIsDisplayed()
    }
}
