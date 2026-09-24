package com.tigerplayer.ui.main

import com.tigerplayer.navigation.Screen
import com.tigerplayer.ui.main.shouldShowBottomNavigation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenVisibilityTest {

    @Test
    fun `bottom navigation stays visible on regular routes`() {
        assertTrue(shouldShowBottomNavigation(currentRoute = "tab_home", isImmersiveOverlayVisible = false))
    }

    @Test
    fun `bottom navigation hides on sonic prism route`() {
        assertFalse(shouldShowBottomNavigation(currentRoute = Screen.SonicPrism.route, isImmersiveOverlayVisible = false))
    }

    @Test
    fun `bottom navigation hides while an immersive overlay is visible`() {
        assertFalse(shouldShowBottomNavigation(currentRoute = "tab_home", isImmersiveOverlayVisible = true))
    }
}

