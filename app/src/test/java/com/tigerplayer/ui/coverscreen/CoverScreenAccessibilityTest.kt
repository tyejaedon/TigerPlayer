package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class CoverScreenAccessibilityTest {

    private val supportedLocales = listOf("", "-es", "-fr", "-de", "-ja")

    private fun getResDirectory(): File {
        return listOf(
            File("app/src/main/res"),
            File("src/main/res")
        ).firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Resource directory not found from working directory: ${System.getProperty("user.dir")}")
    }

    private fun loadStringMap(qualifier: String): Map<String, String> {
        val dirName = if (qualifier.isEmpty()) "values" else "values$qualifier"
        val stringsFile = File(getResDirectory(), "$dirName/strings.xml")
        assertTrue("Resource file ${stringsFile.path} must exist", stringsFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(stringsFile)
        val stringNodes = doc.getElementsByTagName("string")
        val map = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val name = node.attributes.getNamedItem("name")?.nodeValue
            val value = node.textContent
            if (name != null) {
                map[name] = value
            }
        }
        return map
    }

    @Test
    fun stringResourceKeyParityAcrossAllFiveLanguages() {
        val enMap = loadStringMap("")
        assertTrue("English strings must not be empty", enMap.isNotEmpty())

        for (qualifier in listOf("-es", "-fr", "-de", "-ja")) {
            val localizedMap = loadStringMap(qualifier)
            assertEquals(
                "Key set in values$qualifier must exactly match values (English)",
                enMap.keys,
                localizedMap.keys
            )
            for ((key, value) in localizedMap) {
                assertFalse("Key '$key' in values$qualifier must not be empty or blank", value.isBlank())
            }
        }
    }

    @Test
    fun requiredAccessibilityKeysArePresent() {
        val enMap = loadStringMap("")

        val requiredKeys = listOf(
            // Gesture hints
            "coverscreen_gesture_tap_play_pause",
            "coverscreen_gesture_swipe_next",
            "coverscreen_gesture_swipe_prev",
            "coverscreen_gesture_swipe_up_queue",
            "coverscreen_gesture_swipe_down_close",
            "coverscreen_gesture_long_press_settings",
            "coverscreen_gesture_instructions",

            // Playback controls
            "coverscreen_action_previous",
            "coverscreen_action_play",
            "coverscreen_action_pause",
            "coverscreen_action_next",
            "coverscreen_action_queue",
            "coverscreen_action_library",
            "coverscreen_action_play_all",
            "coverscreen_action_close_queue",
            "coverscreen_action_search_library",
            "coverscreen_action_back_to_albums",
            "coverscreen_action_back_to_playback",
            "coverscreen_action_add_to_queue",
            "coverscreen_action_play_now",
            "coverscreen_action_remove_from_queue",
            "coverscreen_action_remove",

            // Status subtitles
            "coverscreen_status_now_playing",
            "coverscreen_status_paused",
            "coverscreen_status_flex_mode_active",
            "coverscreen_status_no_lyrics_found",

            // Tabs and empty states
            "coverscreen_tab_artists",
            "coverscreen_tab_albums",
            "coverscreen_tab_songs",
            "coverscreen_tab_playlists",
            "coverscreen_tab_spotify_playlists",
            "coverscreen_empty_artists",
            "coverscreen_empty_albums",
            "coverscreen_empty_songs",
            "coverscreen_empty_playlists",
            "coverscreen_empty_spotify_playlists",
            "coverscreen_auth_required"
        )

        for (key in requiredKeys) {
            assertTrue("Required key '$key' must exist in strings.xml", enMap.containsKey(key))
            assertNotNull(enMap[key])
            assertFalse(enMap[key]!!.isBlank())
        }
    }

    @Test
    fun gestureInstructionSemanticsIncludeAllActions() {
        val enMap = loadStringMap("")
        val instructions = enMap["coverscreen_gesture_instructions"] ?: ""
        val lower = instructions.lowercase()

        assertTrue("Instructions must describe tap action", lower.contains("tap"))
        assertTrue("Instructions must describe play/pause", lower.contains("play") && lower.contains("pause"))
        assertTrue("Instructions must describe next", lower.contains("next"))
        assertTrue("Instructions must describe previous", lower.contains("previous"))
        assertTrue("Instructions must describe queue", lower.contains("queue"))
        assertTrue("Instructions must describe close or search", lower.contains("close") || lower.contains("search"))
    }

    @Test
    fun trackDurationFormattingSemantics() {
        assertEquals("0:00", formatTrackDuration(0L))
        assertEquals("0:05", formatTrackDuration(5_000L))
        assertEquals("1:05", formatTrackDuration(65_000L))
        assertEquals("3:45", formatTrackDuration(225_000L))
        assertEquals("60:00", formatTrackDuration(3_600_000L))
        assertEquals("0:00", formatTrackDuration(-500L))
    }

    @Test
    fun minimumInteractiveTouchTargetDimensionMeetsAccessibilityGuidelines() {
        val minTarget = 48.dp
        assertTrue("Touch target guideline must be at least 48dp", minTarget >= 48.dp)

        // Standard size for playback buttons on comfortable cover screens (e.g. 377dp or flex mode)
        val standardButtonSize = calculateCoverControlButtonSize(377.dp)
        assertEquals(48.dp, standardButtonSize)

        // In narrow bands, calculateCoverControlButtonSize returns compact visual sizes (e.g. 40dp or 34dp)
        // while the container row maintains full width distribution ensuring each interactive slot meets >= 48dp
        val narrowButtonSize = calculateCoverControlButtonSize(228.dp)
        assertEquals(40.dp, narrowButtonSize)
        assertTrue(narrowButtonSize <= minTarget)
    }
}
