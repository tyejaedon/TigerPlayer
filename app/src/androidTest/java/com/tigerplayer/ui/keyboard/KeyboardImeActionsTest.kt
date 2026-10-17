package com.tigerplayer.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.tigerplayer.ui.KeyboardTestTags
import com.tigerplayer.ui.cloud.CloudHeader
import com.tigerplayer.ui.home.HomeHeader
import com.tigerplayer.ui.home.StatsSearchBar
import com.tigerplayer.ui.library.AnimatedLibraryHeader
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KeyboardImeActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryHeaderSearchField_acceptsInputAndInvokesSearchAction() {
        var query by mutableStateOf("")

        composeRule.setContent {
            TigerPlayerTheme {
                AnimatedLibraryHeader(
                    query = query,
                    isSearchActive = true,
                    onSearchToggle = {},
                    onQueryChange = { query = it }
                )
            }
        }

        composeRule.onNodeWithTag(KeyboardTestTags.LIBRARY_SEARCH_FIELD)
            .assertIsDisplayed()
            .performTextInput("Geralt")

        composeRule.runOnIdle {
            assertEquals("Geralt", query)
        }

        composeRule.onNodeWithTag(KeyboardTestTags.LIBRARY_SEARCH_FIELD)
            .performImeAction()
    }

    @Test
    fun homeHeaderSearchField_acceptsInputAndInvokesSearchAction() {
        var query by mutableStateOf("")

        composeRule.setContent {
            TigerPlayerTheme {
                HomeHeader(
                    title = "Tiger Player",
                    searchQuery = query,
                    isSearchActive = true,
                    onSearchToggle = {},
                    onSearchQueryChange = { query = it },
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag(KeyboardTestTags.HOME_SEARCH_FIELD)
            .assertIsDisplayed()
            .performTextInput("Silver Sword")

        composeRule.runOnIdle {
            assertEquals("Silver Sword", query)
        }

        composeRule.onNodeWithTag(KeyboardTestTags.HOME_SEARCH_FIELD)
            .performImeAction()
    }

    @Test
    fun cloudHeaderSearchField_acceptsInputAndInvokesSearchAction() {
        var query by mutableStateOf("")

        composeRule.setContent {
            TigerPlayerTheme {
                CloudHeader(
                    query = query,
                    onQueryChange = { query = it },
                    onRefresh = {},
                    onYouTubeSearchClick = {}
                )
            }
        }

        composeRule.onNodeWithTag(KeyboardTestTags.CLOUD_SEARCH_FIELD)
            .assertIsDisplayed()
            .performTextInput("Spotify Hits")

        composeRule.runOnIdle {
            assertEquals("Spotify Hits", query)
        }

        composeRule.onNodeWithTag(KeyboardTestTags.CLOUD_SEARCH_FIELD)
            .performImeAction()
    }

    @Test
    fun statsSearchBar_acceptsInputAndInvokesSearchAction() {
        var query by mutableStateOf("")
        var sortToggled = false

        composeRule.setContent {
            TigerPlayerTheme {
                StatsSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    isAlphabeticalSort = false,
                    onToggleSort = { sortToggled = true }
                )
            }
        }

        composeRule.onNodeWithTag(KeyboardTestTags.STATS_SEARCH_FIELD)
            .assertIsDisplayed()
            .performTextInput("Top Track")

        composeRule.runOnIdle {
            assertEquals("Top Track", query)
        }

        composeRule.onNodeWithTag(KeyboardTestTags.STATS_SEARCH_FIELD)
            .performImeAction()
    }

    @Test
    fun youTubeSearchBar_acceptsInputAndInvokesSearchAction() {
        var query by mutableStateOf("")

        composeRule.setContent {
            TigerPlayerTheme {
                com.tigerplayer.ui.youtube.YouTubeSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onClearClick = { query = "" },
                    onBackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag(KeyboardTestTags.YOUTUBE_SEARCH_FIELD)
            .assertIsDisplayed()
            .performTextInput("Lofi Beats")

        composeRule.runOnIdle {
            assertEquals("Lofi Beats", query)
        }

        composeRule.onNodeWithTag(KeyboardTestTags.YOUTUBE_SEARCH_FIELD)
            .performImeAction()
    }

    @Test
    fun navidromeLoginScreen_navigatesFieldsWithNextAndSubmitsWithDone() {
        val mockViewModel = io.mockk.mockk<com.tigerplayer.ui.player.PlayerViewModel>(relaxed = true)

        composeRule.setContent {
            TigerPlayerTheme {
                com.tigerplayer.ui.library.NavidromeLoginScreen(
                    viewModel = mockViewModel,
                    onLoginSuccess = {},
                    onBackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag(KeyboardTestTags.NAVIDROME_LOGIN_CARD)
            .assertIsDisplayed()

        // 1. Enter Server URL and hit Next (ImeAction.Next)
        composeRule.onNodeWithTag(KeyboardTestTags.NAVIDROME_SERVER_URL)
            .assertIsDisplayed()
            .performTextInput("https://navidrome.local:4533")
        composeRule.onNodeWithTag(KeyboardTestTags.NAVIDROME_SERVER_URL)
            .performImeAction()

        // 2. Focus should have moved to Username; enter Username and hit Next (ImeAction.Next)
        composeRule.onNodeWithTag(KeyboardTestTags.NAVIDROME_USERNAME)
            .assertIsDisplayed()
            .performTextInput("witcher")
        composeRule.onNodeWithTag(KeyboardTestTags.NAVIDROME_USERNAME)
            .performImeAction()

        // 3. Focus should have moved to Password; enter Password and hit Done (ImeAction.Done)
        composeRule.onNodeWithTag(KeyboardTestTags.NAVIDROME_PASSWORD)
            .assertIsDisplayed()
            .performTextInput("secret123")
        composeRule.onNodeWithTag(KeyboardTestTags.NAVIDROME_PASSWORD)
            .performImeAction()

        composeRule.runOnIdle {
            io.mockk.verify {
                mockViewModel.connectToNavidrome(
                    "https://navidrome.local:4533",
                    "witcher",
                    "secret123",
                    false
                )
            }
        }
    }
}
