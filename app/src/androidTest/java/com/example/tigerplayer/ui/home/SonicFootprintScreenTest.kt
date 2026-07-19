package com.example.tigerplayer.ui.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.tigerplayer.ui.theme.TigerPlayerTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SonicFootprintScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_renders_correctly() {
        val viewModel = mockk<SonicFootprintViewModel>(relaxed = true)
        val uiState = SonicFootprintUiState(
            axisValues = emptyMap(),
            topTags = emptyList(),
            totalListeningHours = 0.0f
        )
        every { viewModel.uiState } returns MutableStateFlow(uiState)

        composeRule.setContent {
            TigerPlayerTheme {
                SonicFootprintScreen(onClose = {}, viewModel = viewModel)
            }
        }

        composeRule.onNodeWithText("0.0H", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("radar_chart").assertIsDisplayed()
    }

    @Test
    fun nonEmptyState_renders_genre_tags() {
        val viewModel = mockk<SonicFootprintViewModel>(relaxed = true)
        val uiState = SonicFootprintUiState(
            axisValues = SonicAxis.entries.associateWith { 0.5f },
            topTags = listOf("Synthwave" to 120f, "Techno" to 80f),
            totalListeningHours = 3.3f
        )
        every { viewModel.uiState } returns MutableStateFlow(uiState)

        composeRule.setContent {
            TigerPlayerTheme {
                SonicFootprintScreen(onClose = {}, viewModel = viewModel)
            }
        }

        composeRule.onNodeWithText("3.3H", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("genre_tag_Synthwave").assertIsDisplayed()
        composeRule.onNodeWithTag("genre_tag_Techno").assertIsDisplayed()
    }
}
