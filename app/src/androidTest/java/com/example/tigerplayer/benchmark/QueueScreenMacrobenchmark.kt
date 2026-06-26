package com.example.tigerplayer.benchmark

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RequiresApi(Build.VERSION_CODES.Q)
@RunWith(AndroidJUnit4::class)
class QueueScreenMacrobenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollQueueScreenLazyColumn() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()

                // Open mini player/full player and jump to queue action.
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                val display = device.displayWidth to device.displayHeight
                val centerX = display.first / 2
                val bottomY = (display.second * 0.93f).toInt()
                device.click(centerX, bottomY)
                device.waitForIdle()

                // Queue icon has no contentDescription, so coordinate fallback near top-right action row.
                device.click((display.first * 0.84f).toInt(), (display.second * 0.16f).toInt())
                device.wait(Until.findObject(By.textContains("UP NEXT")), 3_500)
            }
        ) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val list = device.findObject(By.scrollable(true))

            if (list != null) {
                list.scroll(Direction.DOWN, 1.0f)
                list.scroll(Direction.UP, 1.0f)
            } else {
                // Fallback swipe in list region to keep frame metrics active.
                val x = device.displayWidth / 2
                val top = (device.displayHeight * 0.30f).toInt()
                val bottom = (device.displayHeight * 0.82f).toInt()
                device.swipe(x, bottom, x, top, 30)
                device.swipe(x, top, x, bottom, 30)
            }
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.tigerplayer"
    }
}


