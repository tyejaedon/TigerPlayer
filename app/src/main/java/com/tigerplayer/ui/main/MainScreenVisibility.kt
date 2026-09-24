package com.tigerplayer.ui.main

import com.tigerplayer.navigation.Screen

fun shouldShowBottomNavigation(
    currentRoute: String?,
    isImmersiveOverlayVisible: Boolean
): Boolean {
    return !isImmersiveOverlayVisible && currentRoute != Screen.SonicPrism.route
}

