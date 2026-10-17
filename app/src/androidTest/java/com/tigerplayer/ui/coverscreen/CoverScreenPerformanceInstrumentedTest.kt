package com.tigerplayer.ui.coverscreen

import android.net.Uri
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.engine.CoverScreenStatsSnapshot
import com.tigerplayer.ui.dashboard.CurationFeedMeta
import com.tigerplayer.ui.dashboard.DashboardViewModel
import com.tigerplayer.ui.player.PlayerUiState
import com.tigerplayer.ui.player.PlayerViewModel
import com.tigerplayer.ui.theme.TigerPlayerTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class CoverScreenPerformanceInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleTrack(id: String = "perf-1") = AudioTrack(
        id = id,
        title = "Performance Beat",
        artist = "Sonic Engine",
        album = "Battery Low",
        uri = Uri.parse("content://test/$id"),
        artworkUri = Uri.EMPTY,
        durationMs = 210_000L,
        mimeType = "audio/mpeg"
    )

    private fun mockPlayerViewModel(isPlaying: Boolean = true): PlayerViewModel {
        val playerViewModel = mockk<PlayerViewModel>(relaxed = true)
        val uiState = PlayerUiState(
            isPlaying = isPlaying,
            currentTrack = sampleTrack(),
            currentWaveform = List(64) { 0.4f },
            queue = listOf(sampleTrack())
        )
        every { playerViewModel.uiState } returns MutableStateFlow(uiState)
        every { playerViewModel.coverScreenStats } returns MutableStateFlow(CoverScreenStatsSnapshot())
        every { playerViewModel.sonicFootprintOpenRequest } returns MutableStateFlow(false)
        return playerViewModel
    }

    private fun mockDashboardViewModel(): DashboardViewModel {
        val dashboardViewModel = mockk<DashboardViewModel>(relaxed = true)
        every { dashboardViewModel.daylistTracks } returns MutableStateFlow(emptyList())
        every { dashboardViewModel.discoveryWeeklyTracks } returns MutableStateFlow(emptyList())
        every { dashboardViewModel.daylistMeta } returns MutableStateFlow(CurationFeedMeta())
        every { dashboardViewModel.discoveryMeta } returns MutableStateFlow(CurationFeedMeta())
        return dashboardViewModel
    }

    private val defaultWindowState = CoverScreenWindowState(
        widthDp = 300,
        heightDp = 300,
        isCoverScreen = true,
        hasSeparatingHinge = false
    )

    @Test
    fun microWaveformIsDisplayedInDefaultState() {
        val playerViewModel = mockPlayerViewModel(isPlaying = true)
        val dashboardViewModel = mockDashboardViewModel()

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenMiniHub(
                    playerViewModel = playerViewModel,
                    dashboardViewModel = dashboardViewModel,
                    windowState = defaultWindowState
                )
            }
        }

        // In normal resting state, waveform is rendered and visible
        composeRule.onNodeWithTag(CoverScreenTestTags.MICRO_WAVEFORM).assertIsDisplayed()
    }

    @Test
    fun microWaveformIsSuppressedWhenLibraryOverlayIsOpened() {
        val playerViewModel = mockPlayerViewModel(isPlaying = true)
        val dashboardViewModel = mockDashboardViewModel()

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenMiniHub(
                    playerViewModel = playerViewModel,
                    dashboardViewModel = dashboardViewModel,
                    windowState = defaultWindowState
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.MICRO_WAVEFORM).assertIsDisplayed()

        // Open Library overlay
        composeRule.onNodeWithTag(CoverScreenTestTags.LIBRARY_OPEN).performClick()

        // Library menu is displayed; heavy canvas waveform is suppressed to save GPU/CPU cycles
        composeRule.onNodeWithTag(CoverScreenTestTags.LIBRARY_MENU).assertIsDisplayed()
        composeRule.onNodeWithTag(CoverScreenTestTags.MICRO_WAVEFORM).assertDoesNotExist()
    }

    @Test
    fun microWaveformIsSuppressedWhenPlaybackSettingsOverlayIsOpened() {
        val playerViewModel = mockPlayerViewModel(isPlaying = true)
        val dashboardViewModel = mockDashboardViewModel()

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenMiniHub(
                    playerViewModel = playerViewModel,
                    dashboardViewModel = dashboardViewModel,
                    windowState = defaultWindowState
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.MICRO_WAVEFORM).assertIsDisplayed()

        // Long-press play/pause button to open playback settings overlay
        composeRule.onNodeWithTag(CoverScreenTestTags.PLAYBACK_SETTINGS_OPEN)
            .performTouchInput { longClick() }

        // Playback settings panel is displayed; waveform is suppressed
        composeRule.onNodeWithTag(CoverScreenTestTags.PLAYBACK_SETTINGS_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(CoverScreenTestTags.MICRO_WAVEFORM).assertDoesNotExist()
    }

    @Test
    fun coverMicroWaveformRendersResilientlyWhenPausedOrInactive() {
        val amplitudes = listOf(0.1f, 0.5f, 0.8f, 0.3f)

        composeRule.setContent {
            TigerPlayerTheme {
                CoverMicroWaveform(
                    amplitudes = amplitudes,
                    bass = 0.5f,
                    energy = 0.7f,
                    isPlaying = false,
                    isRenderingActive = false
                )
            }
        }

        // Waveform renders static frozen baseline smoothly without infinite transitions or crashing
        composeRule.waitForIdle()
    }
}
