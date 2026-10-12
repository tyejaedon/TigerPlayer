package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import com.tigerplayer.data.local.CoverScreenEqPreset
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoverScreenPlaybackSettingsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playbackControlsUpdateShuffleRepeatAndEqualizerPreset() {
        var shuffleToggled = false
        var repeatCycles = 0
        var selectedPreset: CoverScreenEqPreset? = null

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenPlaybackSettings(
                    isShuffleEnabled = false,
                    repeatMode = 0,
                    volume = 0.5f,
                    selectedEqPreset = CoverScreenEqPreset.NEURAL_ADAPTIVE.displayName,
                    onToggleShuffle = { shuffleToggled = true },
                    onCycleRepeat = { repeatCycles++ },
                    onAdjustVolume = {},
                    onSelectEqPreset = { selectedPreset = it },
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.PLAYBACK_SETTINGS_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(CoverScreenTestTags.PLAYBACK_SHUFFLE).performClick()
        composeRule.onNodeWithTag(CoverScreenTestTags.PLAYBACK_REPEAT).performClick()
        composeRule.onNodeWithTag(CoverScreenTestTags.eqPreset(CoverScreenEqPreset.NIGHT_DRIVE.name))
            .performClick()

        composeRule.runOnIdle {
            assertTrue(shuffleToggled)
            assertEquals(1, repeatCycles)
            assertEquals(CoverScreenEqPreset.NIGHT_DRIVE, selectedPreset)
        }
    }

    @Test
    fun verticalVolumeGesturesAdjustThePersistedValueInEitherDirection() {
        var totalVolumeDelta = 0f

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenPlaybackSettings(
                    isShuffleEnabled = false,
                    repeatMode = 0,
                    volume = 0.5f,
                    selectedEqPreset = CoverScreenEqPreset.NEURAL_ADAPTIVE.displayName,
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onAdjustVolume = { totalVolumeDelta += it },
                    onSelectEqPreset = {},
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.PLAYBACK_VOLUME)
            .performTouchInput { swipeUp() }
        composeRule.runOnIdle { assertTrue(totalVolumeDelta > 0f) }

        composeRule.onNodeWithTag(CoverScreenTestTags.PLAYBACK_VOLUME)
            .performTouchInput { swipeDown() }
        composeRule.runOnIdle { assertEquals(0f, totalVolumeDelta, 0.001f) }
    }
}
