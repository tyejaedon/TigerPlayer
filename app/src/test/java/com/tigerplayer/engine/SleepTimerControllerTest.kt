package com.tigerplayer.engine

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit coverage for [SleepTimerController] - the pure clock/state half of the sleep timer feature
 * (issue #51). Player-boundary resolution (end-of-track / end-of-queue timing, fade-and-pause)
 * lives in MediaControllerManager/PlaybackEngine and is deliberately not exercised here, per
 * testing.instructions.md's "prefer testing an engine class directly" guidance.
 *
 * The controller's internal scope is pinned to `Dispatchers.Main.immediate`, so every test routes
 * virtual time through the same [StandardTestDispatcher] installed as Main - no real `delay`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var controller: SleepTimerController

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        controller = SleepTimerController()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starting a duration timer immediately reflects the full remaining time`() = runTest(dispatcher) {
        controller.startDuration(durationMs = 5_000L)

        val state = controller.state.value
        assertEquals(SleepTimerMode.DURATION, state.mode)
        assertEquals(5_000L, state.remainingMs)
    }

    @Test
    fun `duration timer counts down in one-second steps`() = runTest(dispatcher) {
        controller.startDuration(durationMs = 5_000L)

        advanceTimeBy(2_500L.milliseconds)
        runCurrent()

        assertEquals(3_000L, controller.state.value.remainingMs)
    }

    @Test
    fun `duration timer fires onExpired exactly once when it elapses`() = runTest(dispatcher) {
        var expiredCount = 0
        controller.onExpired = { expiredCount++ }

        controller.startDuration(durationMs = 3_000L)
        advanceUntilIdle()

        assertEquals(1, expiredCount)
    }

    @Test
    fun `state resets to OFF after a duration timer expires`() = runTest(dispatcher) {
        controller.startDuration(durationMs = 1_000L)
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(SleepTimerMode.OFF, state.mode)
        assertEquals(0L, state.remainingMs)
    }

    @Test
    fun `remaining time never goes negative even at the final tick`() = runTest(dispatcher) {
        controller.startDuration(durationMs = 900L)
        advanceUntilIdle()

        assertFalse(controller.state.value.remainingMs < 0L)
    }

    @Test
    fun `cancel stops an in-flight timer and does not fire onExpired`() = runTest(dispatcher) {
        var expiredCount = 0
        controller.onExpired = { expiredCount++ }

        controller.startDuration(durationMs = 10_000L)
        advanceTimeBy(2_000L)
        runCurrent()

        controller.cancel()
        advanceUntilIdle()

        assertEquals(SleepTimerMode.OFF, controller.state.value.mode)
        assertEquals(0, expiredCount)
    }

    @Test
    fun `starting a new duration cancels a previous in-flight timer`() = runTest(dispatcher) {
        var expiredCount = 0
        controller.onExpired = { expiredCount++ }

        controller.startDuration(durationMs = 10_000L)
        advanceTimeBy(1_000L)
        runCurrent()

        // Re-arming should discard the first timer rather than letting both tick concurrently.
        controller.startDuration(durationMs = 4_000L)
        advanceTimeBy(4_000L)
        runCurrent()

        assertEquals(1, expiredCount)
    }

    @Test
    fun `fadeOutEnabled flag is carried through to state`() = runTest(dispatcher) {
        controller.startDuration(durationMs = 2_000L, fadeOutEnabled = false)

        assertFalse(controller.state.value.fadeOutEnabled)
    }

    @Test
    fun `armEndOfTrack sets mode without starting a countdown`() = runTest(dispatcher) {
        controller.armEndOfTrack()

        val state = controller.state.value
        assertEquals(SleepTimerMode.END_OF_TRACK, state.mode)
        assertEquals(0L, state.remainingMs)

        // No ticking coroutine should be running for this mode; advancing time must not change it.
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(SleepTimerMode.END_OF_TRACK, controller.state.value.mode)
    }

    @Test
    fun `armEndOfQueue sets mode without starting a countdown`() = runTest(dispatcher) {
        controller.armEndOfQueue()

        val state = controller.state.value
        assertEquals(SleepTimerMode.END_OF_QUEUE, state.mode)
        assertEquals(0L, state.remainingMs)

        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(SleepTimerMode.END_OF_QUEUE, controller.state.value.mode)
    }

    @Test
    fun `arming end-of-track cancels a previously running duration timer`() = runTest(dispatcher) {
        var expiredCount = 0
        controller.onExpired = { expiredCount++ }

        controller.startDuration(durationMs = 10_000L)
        advanceTimeBy(1_000L)
        runCurrent()

        controller.armEndOfTrack()
        advanceUntilIdle()

        // The cancelled duration timer must not fire after being superseded.
        assertEquals(0, expiredCount)
        assertEquals(SleepTimerMode.END_OF_TRACK, controller.state.value.mode)
    }

    @Test
    fun `cancel on an already-idle controller is a no-op`() = runTest(dispatcher) {
        controller.cancel()

        assertEquals(SleepTimerMode.OFF, controller.state.value.mode)
    }

    @Test
    fun `onExpired callback is optional and a missing callback does not throw`() = runTest(dispatcher) {
        assertNull(controller.onExpired)

        controller.startDuration(durationMs = 500L)
        advanceUntilIdle()

        assertEquals(SleepTimerMode.OFF, controller.state.value.mode)
    }
}

