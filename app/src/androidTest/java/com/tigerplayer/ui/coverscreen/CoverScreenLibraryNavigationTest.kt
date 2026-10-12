package com.tigerplayer.ui.coverscreen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CoverScreenLibraryNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingSpotifyTabUpdatesNavigationSelection() {
        lateinit var selectedTab: androidx.compose.runtime.MutableState<CoverScreenLibraryTab>

        composeRule.setContent {
            TigerPlayerTheme {
                selectedTab = remember { mutableStateOf(CoverScreenLibraryTab.ARTISTS) }
                CoverScreenLibraryTabRow(
                    selectedTab = selectedTab.value,
                    onTabSelected = { selectedTab.value = it }
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.libraryTab("SPOTIFY_PLAYLISTS"))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(CoverScreenTestTags.libraryTab("SPOTIFY_PLAYLISTS"))
            .assertIsSelected()

        composeRule.runOnIdle {
            assertEquals(CoverScreenLibraryTab.SPOTIFY_PLAYLISTS, selectedTab.value)
        }
    }

    @Test
    fun allFourLibraryTabsAreReachable() {
        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenLibraryTabRow(
                    selectedTab = CoverScreenLibraryTab.ARTISTS,
                    onTabSelected = {}
                )
            }
        }

        CoverScreenLibraryTab.entries.forEach { tab ->
            composeRule.onNodeWithTag(CoverScreenTestTags.libraryTab(tab.name))
                .assertIsDisplayed()
        }
    }
}
