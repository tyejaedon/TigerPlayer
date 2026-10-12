package com.tigerplayer.ui.coverscreen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoverScreenSearchBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typingAndVoiceActionAreReportedToTheCaller() {
        var voiceSearchRequested = false
        lateinit var query: androidx.compose.runtime.MutableState<String>

        composeRule.setContent {
            TigerPlayerTheme {
                query = remember { mutableStateOf("") }
                CoverScreenSearchBar(
                    query = query.value,
                    onQueryChange = { query.value = it },
                    onVoiceSearch = { voiceSearchRequested = true },
                    onClose = {},
                    voiceSearchAvailable = true
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.SEARCH_FIELD).performTextInput("jazz")
        composeRule.onNodeWithTag(CoverScreenTestTags.SEARCH_VOICE).performClick()

        composeRule.runOnIdle {
            assertEquals("jazz", query.value)
            assertTrue(voiceSearchRequested)
        }
    }

    @Test
    fun unavailableVoiceSearchIsExplainedAndDisabled() {
        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenSearchBar(
                    query = "",
                    onQueryChange = {},
                    onVoiceSearch = {},
                    onClose = {},
                    voiceSearchAvailable = false
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.SEARCH_VOICE).assertIsNotEnabled()
        composeRule.onNodeWithTag(CoverScreenTestTags.SEARCH_VOICE_STATUS).assertIsDisplayed()
        composeRule.onNodeWithText("Voice search is unavailable on this device").assertIsDisplayed()
    }

    @Test
    fun closeActionIsReportedToTheCaller() {
        var closed = false
        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenSearchBar(
                    query = "",
                    onQueryChange = {},
                    onVoiceSearch = {},
                    onClose = { closed = true },
                    voiceSearchAvailable = true
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.SEARCH_CLOSE).performClick()
        composeRule.runOnIdle { assertTrue(closed) }
    }
}
