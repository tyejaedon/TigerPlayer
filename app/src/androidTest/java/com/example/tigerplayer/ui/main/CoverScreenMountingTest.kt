package com.example.tigerplayer.ui.main

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.tigerplayer.ui.coverscreen.CoverScreenTestTags
import com.example.tigerplayer.ui.coverscreen.CoverScreenWindowState
import com.example.tigerplayer.ui.player.PlayerUiState
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.TigerPlayerTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * Regression coverage for issue #120 (CoverScreenMiniHub was fully implemented but never
 * mounted) and issue #127 itself: locks in that MainScreen actually composes
 * CoverScreenMiniHub - and not the standard full-shell bottom NavigationBar - when
 * CoverScreenWindowState.isCoverScreen is true.
 *
 * MainScreen's cover-screen branch returns before any hiltViewModel() call (HomeViewModel,
 * PrismViewModel), so this can run as a plain Compose test with a mocked PlayerViewModel,
 * without pulling in Hilt or a NavHost. The non-cover-screen shell is not exercised here since
 * it requires those Hilt-scoped ViewModels. [MainScreen.windowStateOverride] is a test-only
 * seam: rememberCoverScreenWindowState() needs a real Activity/display to produce a genuine
 * cover-screen Configuration, which this instrumented test can't otherwise simulate.
 */
class CoverScreenMountingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mockPlayerViewModel(): PlayerViewModel {
        val playerViewModel = mockk<PlayerViewModel>(relaxed = true)
        every { playerViewModel.uiState } returns MutableStateFlow(PlayerUiState())
        return playerViewModel
    }

    private fun setCoverScreenMainScreenContent() {
        composeRule.setContent {
            TigerPlayerTheme {
                MainScreen(
                    playerViewModel = mockPlayerViewModel(),
                    onNavigateToSpotifyPlaylist = { _, _, _ -> },
                    onNavigateToSpotifyAlbum = { _, _, _ -> },
                    onNavigateToArtist = {},
                    onNavigateToNavidromeLogin = {},
                    onNavigateToAlbum = {},
                    onNavigateToPlaylist = { _, _ -> },
                    onNavigateToDaylistDetail = {},
                    onNavigateToDiscoverWeeklyDetail = {},
                    onNavigateToSettings = {},
                    onNavigateToQueue = {},
                    windowStateOverride = CoverScreenWindowState(
                        widthDp = 372,
                        heightDp = 400,
                        isCoverScreen = true,
                        hasSeparatingHinge = false
                    )
                )
            }
        }
    }

    @Test
    fun coverScreenMiniHub_is_composed_when_cover_state_is_true() {
        setCoverScreenMainScreenContent()

        composeRule.onNodeWithTag(CoverScreenTestTags.MINI_HUB_ROOT).assertExists()
    }

    @Test
    fun standard_bottom_navigation_bar_is_not_composed_when_cover_state_is_true() {
        setCoverScreenMainScreenContent()

        composeRule.onNodeWithTag(MainScreenTestTags.BOTTOM_NAVIGATION_BAR).assertDoesNotExist()
    }
}
