package com.tigerplayer.ui.main

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import android.net.Uri
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.engine.CoverScreenStatsSnapshot
import com.tigerplayer.ui.dashboard.CurationFeedMeta
import com.tigerplayer.ui.dashboard.DashboardViewModel
import com.tigerplayer.engine.CoverScreenStatsSnapshot
import com.tigerplayer.ui.coverscreen.CoverScreenTestTags
import com.tigerplayer.ui.coverscreen.CoverScreenWindowState
import com.tigerplayer.ui.player.PlayerUiState
import com.tigerplayer.ui.player.PlayerViewModel
import com.tigerplayer.ui.theme.TigerPlayerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * Regression coverage for issue #120 (CoverScreenMiniHub was fully implemented but never
 * mounted) and issue #127 itself: locks in that MainScreen actually composes
 * CoverScreenMiniHub - and not the standard full-shell bottom NavigationBar - when
 * CoverScreenWindowState.isCoverScreen is true.
 *
 * The cover-screen tests pass mocked player and dashboard ViewModels, so MainScreen returns before
 * creating the Home and Prism ViewModels and can run without Hilt or a NavHost. The non-cover-screen
 * shell is not exercised here since it requires those Hilt-scoped ViewModels.
 * [MainScreen.windowStateOverride] is a test-only seam: rememberCoverScreenWindowState() needs a
 * real Activity/display to produce a genuine cover-screen Configuration, which this test can't
 * otherwise simulate.
 */
class CoverScreenMountingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mockPlayerViewModel(): PlayerViewModel {
        val playerViewModel = mockk<PlayerViewModel>(relaxed = true)
        every { playerViewModel.uiState } returns MutableStateFlow(PlayerUiState())
        every { playerViewModel.coverScreenStats } returns MutableStateFlow(CoverScreenStatsSnapshot())
        every { playerViewModel.sonicFootprintOpenRequest } returns MutableStateFlow(false)
        return playerViewModel
    }

    private fun mockDashboardViewModel(
        daylistTracks: List<AudioTrack> = emptyList(),
        discoveryWeeklyTracks: List<AudioTrack> = emptyList()
    ): DashboardViewModel {
        val dashboardViewModel = mockk<DashboardViewModel>(relaxed = true)
        every { dashboardViewModel.daylistTracks } returns MutableStateFlow(daylistTracks)
        every { dashboardViewModel.discoveryWeeklyTracks } returns
            MutableStateFlow(discoveryWeeklyTracks)
        every { dashboardViewModel.daylistMeta } returns
            MutableStateFlow(CurationFeedMeta(isLoading = false))
        every { dashboardViewModel.discoveryMeta } returns
            MutableStateFlow(CurationFeedMeta(isLoading = false))
        return dashboardViewModel
    }

    private fun setCoverScreenMainScreenContent(
        dashboardViewModel: DashboardViewModel = mockDashboardViewModel(),
        playerViewModel: PlayerViewModel = mockPlayerViewModel()
    ) {
        composeRule.setContent {
            TigerPlayerTheme {
                MainScreen(
                    playerViewModel = playerViewModel,
                    dashboardViewModel = dashboardViewModel,
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
        composeRule.onNodeWithTag(CoverScreenTestTags.DISCOVERY_VIEW).assertExists()
        composeRule.onNodeWithTag(CoverScreenTestTags.DISCOVERY_EMPTY_STATE).assertExists()
    }

    @Test
    fun standard_bottom_navigation_bar_is_not_composed_when_cover_state_is_true() {
        setCoverScreenMainScreenContent()

        composeRule.onNodeWithTag(MainScreenTestTags.BOTTOM_NAVIGATION_BAR).assertDoesNotExist()
    }

    @Test
    fun coverScreenMiniHub_usesTheSharedDashboardRecommendations() {
        val daylistTrack = track("daylist")
        setCoverScreenMainScreenContent(
            mockDashboardViewModel(daylistTracks = listOf(daylistTrack))
        )

        composeRule.onNodeWithTag(
            CoverScreenTestTags.discoveryRecommendation(daylistTrack.id)
        ).assertExists()
    }

    @Test
    fun swipingRecommendationsDoesNotSkipThePlayingTrack() {
        val playerViewModel = mockPlayerViewModel()
        val daylistTrack = track("daylist")
        setCoverScreenMainScreenContent(
            dashboardViewModel = mockDashboardViewModel(daylistTracks = listOf(daylistTrack)),
            playerViewModel = playerViewModel
        )

        composeRule.onNodeWithTag(CoverScreenTestTags.DISCOVERY_LIST)
            .performTouchInput { swipeLeft() }
        composeRule.runOnIdle {
            verify(exactly = 0) { playerViewModel.skipToNext() }
        }
    }

    private fun track(id: String) = AudioTrack(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        uri = Uri.parse("content://tracks/$id"),
        artworkUri = Uri.EMPTY,
        durationMs = 60_000L,
        mimeType = "audio/mpeg"
    )
}
