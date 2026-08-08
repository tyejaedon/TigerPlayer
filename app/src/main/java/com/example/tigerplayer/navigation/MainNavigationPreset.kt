package com.example.tigerplayer.navigation

/**
 * Controls top-level navigation behavior for different device contexts.
 */
data class MainNavigationPreset(
    val tabs: List<BottomNavTab>,
    val startDestinationRoute: String,
    val showTabLabels: Boolean,
    val compactPlayerSheetMotion: Boolean,
    val minimizeBackdropPushback: Boolean
)

object MainNavigationPresets {
    val Default = MainNavigationPreset(
        tabs = listOf(
            BottomNavTab.Home,
            BottomNavTab.Library,
            BottomNavTab.Cloud
        ),
        startDestinationRoute = BottomNavTab.Home.route,
        showTabLabels = true,
        compactPlayerSheetMotion = false,
        minimizeBackdropPushback = false
    )

    // Prioritize thumb-friendly routing on cover displays.
    val CoverOneHand = MainNavigationPreset(
        tabs = listOf(
            BottomNavTab.Library,
            BottomNavTab.Home,
            BottomNavTab.Cloud
        ),
        startDestinationRoute = BottomNavTab.Library.route,
        showTabLabels = false,
        compactPlayerSheetMotion = true,
        minimizeBackdropPushback = true
    )
}

