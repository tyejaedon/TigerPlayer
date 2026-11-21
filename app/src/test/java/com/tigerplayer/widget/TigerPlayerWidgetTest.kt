package com.tigerplayer.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TigerPlayerWidgetTest {

    @Test
    fun `computeProgressFraction handles edge cases and clamps`() {
        assertEquals(0f, WidgetPlaybackState.computeProgressFraction(0L, 0L), 0.0001f)
        assertEquals(0f, WidgetPlaybackState.computeProgressFraction(-500L, 10_000L), 0.0001f)
        assertEquals(0f, WidgetPlaybackState.computeProgressFraction(5_000L, -1L), 0.0001f)
        assertEquals(0.25f, WidgetPlaybackState.computeProgressFraction(25_000L, 100_000L), 0.0001f)
        assertEquals(1f, WidgetPlaybackState.computeProgressFraction(150_000L, 100_000L), 0.0001f)
    }

    @Test
    fun `resolveLayoutVariant maps compact cover screen, slim 4x1, and standard 4x2`() {
        assertEquals(
            WidgetLayoutVariant.COMPACT_2X2,
            TigerPlayerWidget.resolveLayoutVariant(DpSize(110.dp, 110.dp))
        )
        assertEquals(
            WidgetLayoutVariant.COMPACT_2X2,
            TigerPlayerWidget.resolveLayoutVariant(DpSize(180.dp, 180.dp))
        )
        assertEquals(
            WidgetLayoutVariant.SLIM_4X1,
            TigerPlayerWidget.resolveLayoutVariant(DpSize(250.dp, 56.dp))
        )
        assertEquals(
            WidgetLayoutVariant.STANDARD_4X2,
            TigerPlayerWidget.resolveLayoutVariant(DpSize(250.dp, 110.dp))
        )
    }

    @Test
    fun `currentDaySegment buckets hours correctly`() {
        assertEquals("MORNING", DaylistShortcutWidget.currentDaySegment(8))
        assertEquals("AFTERNOON", DaylistShortcutWidget.currentDaySegment(14))
        assertEquals("EVENING", DaylistShortcutWidget.currentDaySegment(19))
        assertEquals("NIGHT", DaylistShortcutWidget.currentDaySegment(23))
        assertEquals("NIGHT", DaylistShortcutWidget.currentDaySegment(2))
    }

    @Test
    fun `manifest registers both Glance widget receivers`() {
        val manifestFile = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        ).first { it.exists() }
        val manifest = manifestFile.readText()

        assertTrue(manifest.contains("com.tigerplayer.widget.TigerPlayerWidgetReceiver") ||
            manifest.contains(".widget.TigerPlayerWidgetReceiver"))
        assertTrue(manifest.contains("com.tigerplayer.widget.DaylistShortcutWidgetReceiver") ||
            manifest.contains(".widget.DaylistShortcutWidgetReceiver"))
        assertTrue(manifest.contains("android.appwidget.action.APPWIDGET_UPDATE"))
        assertTrue(manifest.contains("@xml/tiger_player_widget_info"))
        assertTrue(manifest.contains("@xml/daylist_shortcut_widget_info"))
    }
}
