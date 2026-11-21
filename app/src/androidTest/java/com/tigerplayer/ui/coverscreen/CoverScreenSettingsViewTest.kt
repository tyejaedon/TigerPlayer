package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.model.MusicFolder
import com.tigerplayer.service.HapticsDebugState
import com.tigerplayer.ui.settings.ConnectedAccountsState
import com.tigerplayer.ui.settings.LibraryRescanState
import com.tigerplayer.ui.settings.SettingsViewModel
import com.tigerplayer.ui.theme.TigerPlayerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoverScreenSettingsViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createMockViewModel(
        settingsState: TigerSettingsState = TigerSettingsState(),
        connectedAccounts: ConnectedAccountsState = ConnectedAccountsState(),
        musicFolders: List<MusicFolder> = emptyList()
    ): SettingsViewModel {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.settingsState } returns MutableStateFlow(settingsState)
        every { vm.libraryRescanState } returns MutableStateFlow(LibraryRescanState())
        every { vm.connectedAccountsState } returns MutableStateFlow(connectedAccounts)
        every { vm.spotifyAuthError } returns MutableStateFlow(null)
        every { vm.musicFolders } returns MutableStateFlow(musicFolders)
        every { vm.hapticsDebugState } returns MutableStateFlow(HapticsDebugState())
        every { vm.backupEvent } returns MutableStateFlow(null)
        return vm
    }

    @Test
    fun settingsRendersTopBarAndInvokesCloseAndReset() {
        var closed = false
        val vm = createMockViewModel()

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenSettingsView(
                    viewModel = vm,
                    onOpenEqualizer = null,
                    onClose = { closed = true }
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_VIEW).assertIsDisplayed()
        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_BACK).assertIsDisplayed().performClick()
        assertTrue(closed)

        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_RESET).assertIsDisplayed().performClick()
        verify(atLeast = 1) { vm.resetToDefaults() }
    }

    @Test
    fun equalizerRowRendersWhenCallbackProvidedAndTriggersAction() {
        var equalizerOpened = false
        val vm = createMockViewModel()

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenSettingsView(
                    viewModel = vm,
                    onOpenEqualizer = { equalizerOpened = true },
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_EQUALIZER)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertTrue(equalizerOpened)
    }

    @Test
    fun onlineArtworkAndLyricsSwitchesAreAccessible() {
        val vm = createMockViewModel()

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenSettingsView(
                    viewModel = vm,
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_ONLINE_ARTWORK)
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_AUTO_LYRICS)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun musicFoldersAndBackupRestoreSectionsRender() {
        val vm = createMockViewModel(
            musicFolders = listOf(
                MusicFolder(
                    uriString = "content://music/custom",
                    displayName = "Custom Music",
                    path = "/storage/emulated/0/Music/Custom",
                    isExcluded = false,
                    addedAt = 1000L
                )
            )
        )

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenSettingsView(
                    viewModel = vm,
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_ADD_FOLDER)
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_EXCLUDE_FOLDER)
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag(CoverScreenTestTags.removeFolder("content://music/custom"))
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_EXPORT_BACKUP)
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_RESTORE_STRATEGY_MERGE)
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_RESTORE_STRATEGY_REPLACE)
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_IMPORT_BACKUP)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun accountDisconnectShowsConfirmationDialogBeforeLogout() {
        val vm = createMockViewModel(
            connectedAccounts = ConnectedAccountsState(isSpotifyConnected = true)
        )

        composeRule.setContent {
            TigerPlayerTheme {
                CoverScreenSettingsView(
                    viewModel = vm,
                    onClose = {}
                )
            }
        }

        // Click disconnect button
        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_SPOTIFY_DISCONNECT)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        // Confirmation dialog must appear
        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_LOGOUT_CONFIRM_DIALOG)
            .assertIsDisplayed()

        // Confirm logout
        composeRule.onNodeWithTag(CoverScreenTestTags.SETTINGS_LOGOUT_CONFIRM)
            .performClick()

        verify(exactly = 1) { vm.logoutSpotify() }
    }
}
