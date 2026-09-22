package com.tigerplayer.ui.coverscreen

import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [rememberCoverScreenWindowState] end-to-end against a real Activity, rather than
 * only the pure [isCoverScreenHeuristic]/[resolveIsCoverScreen] functions covered by
 * [CoverScreenHeuristicTest]. [WindowLayoutInfoPublisherRule] lets the test publish a fake
 * [WindowLayoutInfo] to the same [androidx.window.layout.WindowInfoTracker] the composable
 * consumes, simulating hinge state changes on demand.
 *
 * There is no supported way to make a plain instrumented [ComponentActivity] appear to be
 * hosted on a genuine non-default [Display] within this harness (that requires real hardware or
 * a multi-display emulator profile - see issue #124), so the displayId-identity assertions here
 * confirm the read is *correct* against the real DisplayManager state (always the default
 * display under instrumentation) rather than simulating a secondary display.
 */
@RunWith(AndroidJUnit4::class)
class CoverScreenWindowStateInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val publisherRule = WindowLayoutInfoPublisherRule()

    private var latestState: CoverScreenWindowState? = null

    private fun setContentAndCapture() {
        composeTestRule.setContent {
            val state = rememberCoverScreenWindowState()
            SideEffect { latestState = state }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun reports_a_separating_hinge_when_the_fake_WindowInfoTracker_publishes_one() {
        setContentAndCapture()

        publisherRule.overrideWindowLayoutInfo(
            WindowLayoutInfo(
                listOf(
                    FoldingFeature(
                        activity = composeTestRule.activity,
                        state = FoldingFeature.State.HALF_OPENED
                    )
                )
            )
        )
        composeTestRule.waitForIdle()

        assertTrue(latestState?.hasSeparatingHinge == true)
    }

    @Test
    fun reports_no_separating_hinge_for_a_flat_fold_state() {
        setContentAndCapture()

        publisherRule.overrideWindowLayoutInfo(
            WindowLayoutInfo(
                listOf(
                    FoldingFeature(
                        activity = composeTestRule.activity,
                        state = FoldingFeature.State.FLAT
                    )
                )
            )
        )
        composeTestRule.waitForIdle()

        assertFalse(latestState?.hasSeparatingHinge == true)
    }

    @Test
    fun reports_no_separating_hinge_when_no_display_features_are_published() {
        setContentAndCapture()

        publisherRule.overrideWindowLayoutInfo(WindowLayoutInfo(emptyList()))
        composeTestRule.waitForIdle()

        assertFalse(latestState?.hasSeparatingHinge == true)
    }

    @Test
    fun resolves_displayId_against_the_hosting_activitys_real_DisplayManager_state() {
        setContentAndCapture()

        assertEquals(Display.DEFAULT_DISPLAY, latestState?.displayId)
        assertFalse(latestState?.isSecondaryDisplay == true)
    }
}
