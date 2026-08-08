package com.example.tigerplayer.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tigerplayer.R
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class AudioPlayerServiceCustomLayoutTest {

    private lateinit var mediaController: MediaController

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionToken = SessionToken(context, ComponentName(context, AudioPlayerService::class.java))
        mediaController = MediaController.Builder(context, sessionToken)
            .buildAsync()
            .get(15, TimeUnit.SECONDS)
    }

    @After
    fun tearDown() {
        if (::mediaController.isInitialized) {
            mediaController.release()
        }
    }

    @Test
    fun shuffleCustomButton_iconSwapsAfterShuffleCommand() {
        val beforeIcon = waitForShuffleButtonIcon()

        assertTrue(
            "Expected shuffle icon to be one of the shuffle resources, got $beforeIcon",
            beforeIcon == R.drawable.ic_material_shuffle_on || beforeIcon == R.drawable.ic_material_shuffle_off
        )

        val result = mediaController.sendCustomCommand(
            SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY),
            Bundle.EMPTY
        ).get(10, TimeUnit.SECONDS)

        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)

        val afterIcon = waitForShuffleButtonIcon { icon -> icon != beforeIcon }
        assertNotEquals(beforeIcon, afterIcon)

        val expectedAfterIcon =
            if (beforeIcon == R.drawable.ic_material_shuffle_on) {
                R.drawable.ic_material_shuffle_off
            } else {
                R.drawable.ic_material_shuffle_on
            }

        assertEquals(expectedAfterIcon, afterIcon)
    }

    @Test
    fun shuffleCustomButton_iconRoundTripsAfterTwoToggles() {
        val initialIcon = waitForShuffleButtonIcon()

        val firstResult = mediaController.sendCustomCommand(
            SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY),
            Bundle.EMPTY
        ).get(10, TimeUnit.SECONDS)
        assertEquals(SessionResult.RESULT_SUCCESS, firstResult.resultCode)

        val toggledIcon = waitForShuffleButtonIcon { icon -> icon != initialIcon }
        assertNotEquals(initialIcon, toggledIcon)

        val secondResult = mediaController.sendCustomCommand(
            SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY),
            Bundle.EMPTY
        ).get(10, TimeUnit.SECONDS)
        assertEquals(SessionResult.RESULT_SUCCESS, secondResult.resultCode)

        val roundTripIcon = waitForShuffleButtonIcon { icon -> icon == initialIcon }
        assertEquals(initialIcon, roundTripIcon)
    }

    @Test
    fun repeatCustomButton_iconCyclesAfterRepeatCommand() {
        val beforeIcon = waitForRepeatButtonIcon()

        assertTrue(
            "Expected repeat icon to be one of the repeat resources, got $beforeIcon",
            beforeIcon == R.drawable.ic_material_repeat_off ||
                beforeIcon == R.drawable.ic_material_repeat_all ||
                beforeIcon == R.drawable.ic_material_repeat_one
        )

        val firstResult = mediaController.sendCustomCommand(
            SessionCommand(CUSTOM_COMMAND_REPEAT, Bundle.EMPTY),
            Bundle.EMPTY
        ).get(10, TimeUnit.SECONDS)

        assertEquals(SessionResult.RESULT_SUCCESS, firstResult.resultCode)

        val firstAfterIcon = waitForRepeatButtonIcon { icon -> icon != beforeIcon }
        assertEquals(expectedNextRepeatIcon(beforeIcon), firstAfterIcon)

        val secondResult = mediaController.sendCustomCommand(
            SessionCommand(CUSTOM_COMMAND_REPEAT, Bundle.EMPTY),
            Bundle.EMPTY
        ).get(10, TimeUnit.SECONDS)

        assertEquals(SessionResult.RESULT_SUCCESS, secondResult.resultCode)

        val secondAfterIcon = waitForRepeatButtonIcon { icon -> icon != firstAfterIcon }
        assertEquals(expectedNextRepeatIcon(firstAfterIcon), secondAfterIcon)

        val thirdResult = mediaController.sendCustomCommand(
            SessionCommand(CUSTOM_COMMAND_REPEAT, Bundle.EMPTY),
            Bundle.EMPTY
        ).get(10, TimeUnit.SECONDS)

        assertEquals(SessionResult.RESULT_SUCCESS, thirdResult.resultCode)

        val thirdAfterIcon = waitForRepeatButtonIcon { icon -> icon != secondAfterIcon }
        assertEquals(expectedNextRepeatIcon(secondAfterIcon), thirdAfterIcon)
        assertEquals(beforeIcon, thirdAfterIcon)
    }

    @Test
    fun dspCustomButton_iconSwapsAfterDspCommand() {
        val beforeIcon = waitForDspButtonIcon()

        assertTrue(
            "Expected DSP icon to be one of the DSP resources, got $beforeIcon",
            beforeIcon == R.drawable.ic_material_dsp_on || beforeIcon == R.drawable.ic_material_dsp_off
        )

        val result = mediaController.sendCustomCommand(
            SessionCommand(CUSTOM_COMMAND_DSP, Bundle.EMPTY),
            Bundle.EMPTY
        ).get(10, TimeUnit.SECONDS)

        assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)

        val afterIcon = waitForDspButtonIcon { icon -> icon != beforeIcon }
        assertNotEquals(beforeIcon, afterIcon)

        val expectedAfterIcon =
            if (beforeIcon == R.drawable.ic_material_dsp_on) {
                R.drawable.ic_material_dsp_off
            } else {
                R.drawable.ic_material_dsp_on
            }

        assertEquals(expectedAfterIcon, afterIcon)
    }

    private fun waitForShuffleButtonIcon(predicate: (Int) -> Boolean = { true }): Int {
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var latest: Int? = null

        while (System.nanoTime() < deadlineNs) {
            val icon = currentShuffleButtonIcon()
            if (icon != null) {
                latest = icon
                if (predicate(icon)) {
                    return icon
                }
            }
            Thread.sleep(100)
        }

        throw AssertionError("Timed out waiting for shuffle icon update. Last icon=$latest")
    }

    private fun waitForDspButtonIcon(predicate: (Int) -> Boolean = { true }): Int {
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var latest: Int? = null

        while (System.nanoTime() < deadlineNs) {
            val icon = currentDspButtonIcon()
            if (icon != null) {
                latest = icon
                if (predicate(icon)) {
                    return icon
                }
            }
            Thread.sleep(100)
        }

        throw AssertionError("Timed out waiting for DSP icon update. Last icon=$latest")
    }

    private fun waitForRepeatButtonIcon(predicate: (Int) -> Boolean = { true }): Int {
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var latest: Int? = null

        while (System.nanoTime() < deadlineNs) {
            val icon = currentRepeatButtonIcon()
            if (icon != null) {
                latest = icon
                if (predicate(icon)) {
                    return icon
                }
            }
            Thread.sleep(100)
        }

        throw AssertionError("Timed out waiting for repeat icon update. Last icon=$latest")
    }

    private fun currentShuffleButtonIcon(): Int? {
        val layout = getCustomLayoutViaReflection(mediaController)
        val shuffleButton = layout.firstOrNull() ?: return null

        val label = shuffleButton.displayName.toString()
        assertTrue("Expected first custom button to be shuffle, got '$label'", label.contains("shuffle", ignoreCase = true))

        return shuffleButton.iconResId
    }

    private fun currentDspButtonIcon(): Int? {
        val layout = getCustomLayoutViaReflection(mediaController)
        val dspButton = layout.getOrNull(2) ?: return null

        val label = dspButton.displayName.toString()
        assertTrue(
            "Expected third custom button to be DSP, got '$label'",
            label.contains("bit-perfect", ignoreCase = true) ||
                label.contains("aural nexus", ignoreCase = true)
        )

        return dspButton.iconResId
    }

    private fun currentRepeatButtonIcon(): Int? {
        val layout = getCustomLayoutViaReflection(mediaController)
        val repeatButton = layout.getOrNull(1) ?: return null

        val label = repeatButton.displayName.toString()
        assertTrue("Expected second custom button to be repeat, got '$label'", label.contains("repeat", ignoreCase = true))

        return repeatButton.iconResId
    }

    private fun expectedNextRepeatIcon(currentIcon: Int): Int {
        return when (currentIcon) {
            R.drawable.ic_material_repeat_off -> R.drawable.ic_material_repeat_all
            R.drawable.ic_material_repeat_all -> R.drawable.ic_material_repeat_one
            R.drawable.ic_material_repeat_one -> R.drawable.ic_material_repeat_off
            else -> throw AssertionError("Unexpected repeat icon: $currentIcon")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getCustomLayoutViaReflection(controller: MediaController): List<CommandButton> {
        val method = controller.javaClass.methods.firstOrNull {
            it.name == "getCustomLayout" && it.parameterCount == 0
        } ?: throw AssertionError("MediaController.getCustomLayout() not found; API changed")

        return method.invoke(controller) as? List<CommandButton>
            ?: throw AssertionError("MediaController.getCustomLayout() returned non-list")
    }

    companion object {
        private const val CUSTOM_COMMAND_SHUFFLE = "ACTION_SHUFFLE"
        private const val CUSTOM_COMMAND_REPEAT = "ACTION_REPEAT"
        private const val CUSTOM_COMMAND_DSP = "ACTION_TOGGLE_DSP"
    }
}

