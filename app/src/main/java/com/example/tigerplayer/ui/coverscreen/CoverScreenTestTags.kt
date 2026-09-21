package com.tigerplayer.ui.coverscreen

/**
 * Centralized Compose test tags for the cover-screen mini hub, following the [PrismTestTags]
 * convention: tests address nodes by tag rather than by localized display strings.
 */
object CoverScreenTestTags {
    /** Root node of [CoverScreenMiniHub]; also applied by [MainScreen] where it is mounted. */
    const val MINI_HUB_ROOT = "cover_screen_mini_hub_root"
}
