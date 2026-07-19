package com.example.tigerplayer.ui.equalizer

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tigerplayer.engine.AudioReactiveFrame
import com.example.tigerplayer.engine.FilterType
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuralNexusScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun drag_and_reactive_updates_remain_stable() {
        val hapticEvents = AtomicInteger(0)
        val dragEvents = AtomicInteger(0)
        lateinit var frameStateRef: MutableState<AudioReactiveFrame>

        composeRule.setContent {
            val frameState = remember { mutableStateOf(AudioReactiveFrame()) }
            frameStateRef = frameState
            val curveState = remember {
                mutableStateOf(
                    List(24) { index ->
                        val x = index / 23f
                        val y = kotlin.math.sin(index * 0.4f) * 0.25f
                        Offset(x, y)
                    }
                )
            }

            val fakeHaptic = object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    hapticEvents.incrementAndGet()
                }
            }

            CompositionLocalProvider(LocalHapticFeedback provides fakeHaptic) {
                MaterialTheme {
                    NexusSpatialCanvas(
                        nodes = listOf(
                            SpatialNode(
                                id = "sub",
                                label = "Sub-Bass",
                                type = FilterType.LOW_SHELF,
                                baseFreq = 60f,
                                spatialPos = Offset(-0.5f, 0.15f),
                                color = Color(0xFFFFD500)
                            )
                        ),
                        frequencyResponseCurve = curveState.value,
                        audioReactiveFrame = frameState.value,
                        onNodeDragged = { _, _ -> dragEvents.incrementAndGet() }
                    )
                }
            }
        }

        composeRule.runOnUiThread {
            repeat(20) { step ->
                frameStateRef.value = AudioReactiveFrame(
                    bass = (step % 10) / 10f,
                    mid = ((step + 2) % 10) / 10f,
                    treble = ((step + 4) % 10) / 10f,
                    energy = ((step + 1) % 10) / 10f,
                    flux = ((step + 3) % 10) / 10f
                )
            }
        }

        composeRule.onNodeWithTag("nexus_node_sub").performTouchInput {
            down(center)
            moveBy(Offset(46f, -24f))
            moveBy(Offset(22f, -12f))
            up()
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nexus_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("nexus_node_sub").assertIsDisplayed()
        assertTrue("Expected node drag callbacks", dragEvents.get() > 0)
        assertTrue("Expected haptic feedback callbacks", hapticEvents.get() > 0)
    }
}

