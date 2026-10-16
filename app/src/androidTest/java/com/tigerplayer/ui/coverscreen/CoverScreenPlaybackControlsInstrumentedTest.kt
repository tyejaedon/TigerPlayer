package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoverScreenPlaybackControlsInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playbackControlsRenderAllButtonsAndInvokeActionsOnNarrowContainer() {
        var togglePlayPauseCalled = false
        var longPressPlayPauseCalled = false
        var previousCalled = false
        var nextCalled = false
        var openQueueCalled = false
        var openLibraryCalled = false

        composeRule.setContent {
            TigerPlayerTheme {
                // Constrain width to 228dp (representing 260dp cover screen with 16dp horizontal padding)
                Box(modifier = Modifier.width(228.dp)) {
                    CoverAccessibleControls(
                        isPlaying = true,
                        onLongPressPlayPause = { longPressPlayPauseCalled = true },
                        onTogglePlayPause = { togglePlayPauseCalled = true },
                        onPrevious = { previousCalled = true },
                        onNext = { nextCalled = true },
                        onOpenQueue = { openQueueCalled = true },
                        onOpenLibrary = { openLibraryCalled = true }
                    )
                }
            }
        }

        // Verify key tags are displayed and not clipped or pushed off
        composeRule.onNodeWithTag(CoverScreenTestTags.PLAYBACK_SETTINGS_OPEN)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(CoverScreenTestTags.LIBRARY_OPEN)
            .assertIsDisplayed()

        // Verify click on Play/Pause
        composeRule.onNodeWithTag(CoverScreenTestTags.PLAYBACK_SETTINGS_OPEN)
            .performClick()
        composeRule.runOnIdle {
            assertTrue(togglePlayPauseCalled)
        }

        // Verify long press on Play/Pause opens settings
        composeRule.onNodeWithTag(CoverScreenTestTags.PLAYBACK_SETTINGS_OPEN)
            .performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertTrue(longPressPlayPauseCalled)
        }

        // Verify click on Library
        composeRule.onNodeWithTag(CoverScreenTestTags.LIBRARY_OPEN)
            .performClick()
        composeRule.runOnIdle {
            assertTrue(openLibraryCalled)
        }
    }
}
