package com.example.tigerplayer.ui.player

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.prism.PrismUiState
import com.example.tigerplayer.ui.prism.PrismViewModel
import com.example.tigerplayer.ui.theme.TigerPlayerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FullPlayerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playPause_click_togglesState_and_neonThemeRendersWithoutCrash() {
        val playerViewModel = mockk<PlayerViewModel>(relaxed = true)
        val prismViewModel = mockk<PrismViewModel>(relaxed = true)

        val sampleTrack = AudioTrack(
            id = "test-track",
            title = "Neon Pilot",
            artist = "Tiger QA",
            album = "RC Flight",
            uri = Uri.parse("content://media/external/audio/media/1"),
            artworkUri = Uri.parse("content://media/external/audio/albumart/1"),
            durationMs = 180_000L,
            mimeType = "audio/mpeg",
            isLocal = true,
            bitrate = 320_000,
            sampleRate = 44_100,
            trackNumber = 1,
            path = "/music/neon.mp3"
        )

        val uiStateFlow = MutableStateFlow(
            PlayerUiState(
                currentTrack = sampleTrack,
                tracks = listOf(sampleTrack),
                queue = listOf(sampleTrack),
                isPlaying = false,
                currentWaveform = List(72) { 0.3f }
            )
        )
        val prismStateFlow = MutableStateFlow(PrismUiState())

        every { playerViewModel.uiState } returns uiStateFlow
        every { prismViewModel.uiState } returns prismStateFlow
        every { playerViewModel.togglePlayPause() } answers {
            uiStateFlow.value = uiStateFlow.value.copy(isPlaying = !uiStateFlow.value.isPlaying)
        }

        composeRule.setContent {
            TigerPlayerTheme {
                FullPlayerScreen(
                    viewModel = playerViewModel,
                    prismViewModel = prismViewModel,
                    onCollapse = {},
                    onOpenQueueScreen = {},
                    onNavigateToAlbum = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Play/Pause").assertIsDisplayed().performClick()

        verify(exactly = 1) { playerViewModel.togglePlayPause() }
        assertTrue(uiStateFlow.value.isPlaying)
    }

    @Test
    fun sonicPrism_visualMode_transition_togglesPrismEnableBinding() {
        val playerViewModel = mockk<PlayerViewModel>(relaxed = true)
        val prismViewModel = mockk<PrismViewModel>(relaxed = true)

        val sampleTrack = AudioTrack(
            id = "prism-track",
            title = "Stem Field",
            artist = "Tiger QA",
            album = "Prism Trials",
            uri = Uri.parse("content://media/external/audio/media/2"),
            artworkUri = Uri.parse("content://media/external/audio/albumart/2"),
            durationMs = 200_000L,
            mimeType = "audio/mpeg",
            isLocal = true,
            bitrate = 320_000,
            sampleRate = 48_000,
            trackNumber = 2,
            path = "/music/prism.mp3"
        )

        val uiStateFlow = MutableStateFlow(
            PlayerUiState(
                currentTrack = sampleTrack,
                tracks = listOf(sampleTrack),
                queue = listOf(sampleTrack),
                visualMode = PlayerVisualMode.SONIC_PRISM,
                mainViewState = MainViewState.ARTWORK
            )
        )
        val prismStateFlow = MutableStateFlow(PrismUiState())

        every { playerViewModel.uiState } returns uiStateFlow
        every { prismViewModel.uiState } returns prismStateFlow

        composeRule.setContent {
            TigerPlayerTheme {
                FullPlayerScreen(
                    viewModel = playerViewModel,
                    prismViewModel = prismViewModel,
                    onCollapse = {},
                    onOpenQueueScreen = {},
                    onNavigateToAlbum = {}
                )
            }
        }

        composeRule.waitForIdle()
        verify(atLeast = 1) { prismViewModel.setPrismEnabled(true) }

        composeRule.runOnIdle {
            uiStateFlow.value = uiStateFlow.value.copy(visualMode = PlayerVisualMode.ARTWORK)
        }
        composeRule.waitForIdle()

        verify(atLeast = 1) { prismViewModel.setPrismEnabled(false) }
        assertEquals(PlayerVisualMode.ARTWORK, uiStateFlow.value.visualMode)
    }
}



