package com.tigerplayer.ui.coverscreen

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverScreenPlaybackControlsTest {

    @Test
    fun `identifies responsive button size on narrow cover screen (~260dp width)`() {
        // Samsung Galaxy Z Flip4/5/6 cover screen: ~260dp width.
        // Horizontal padding of 16dp on each side leaves 228dp available width.
        val availableWidth = 228.dp
        val buttonSize = calculateCoverControlButtonSize(availableWidth)
        val iconSize = calculateCoverControlIconSize(buttonSize)

        assertEquals(40.dp, buttonSize)
        assertEquals(20.dp, iconSize)

        // 5 buttons must fit completely without overflowing available width
        val totalButtonsWidth = buttonSize * 5
        assertTrue(totalButtonsWidth <= availableWidth)
        // Ensure there is positive margin remaining between buttons
        assertTrue(availableWidth - totalButtonsWidth >= 20.dp)
    }

    @Test
    fun `identifies responsive button size on Motorola Razr 2022 cover screen (~246dp width)`() {
        // Motorola Razr 2022 cover screen: ~246dp width.
        // Horizontal padding of 16dp on each side leaves 214dp available width.
        val availableWidth = 214.dp
        val buttonSize = calculateCoverControlButtonSize(availableWidth)
        val iconSize = calculateCoverControlIconSize(buttonSize)

        assertEquals(40.dp, buttonSize)
        assertEquals(20.dp, iconSize)
        assertTrue(buttonSize * 5 <= availableWidth)
    }

    @Test
    fun `identifies responsive button size on minimum cover screen band (200dp width)`() {
        // Minimum compact screen supported by isCoverScreenHeuristic is 200dp.
        // With 16dp padding on each side, available width is 168dp (33.6dp per slot).
        val availableWidth = 168.dp
        val buttonSize = calculateCoverControlButtonSize(availableWidth)
        val iconSize = calculateCoverControlIconSize(buttonSize)

        assertTrue(buttonSize <= 34.dp)
        assertTrue(iconSize in 18.dp..20.dp)
        assertTrue(buttonSize * 5 <= availableWidth)
    }

    @Test
    fun `button size scales up to standard 48dp on larger cover panels`() {
        // Motorola Razr+ / Razr 50 Ultra (~409dp width, leaves 377dp available with padding)
        val razrPlusAvailable = 377.dp
        val razrPlusButtonSize = calculateCoverControlButtonSize(razrPlusAvailable)
        assertEquals(48.dp, razrPlusButtonSize)
        assertEquals(24.dp, calculateCoverControlIconSize(razrPlusButtonSize))

        // Samsung Z Flip5/6 (~306dp width, leaves 274dp available with padding)
        val flip5Available = 274.dp
        val flip5ButtonSize = calculateCoverControlButtonSize(flip5Available)
        assertEquals(48.dp, flip5ButtonSize)
        assertEquals(24.dp, calculateCoverControlIconSize(flip5ButtonSize))
    }

    @Test
    fun `button size scales correctly in flex mode without horizontal padding`() {
        // In Flex mode, the control column spans the full width without horizontal 16dp padding.
        // On a 260dp width device: slotWidth is 52dp -> 48dp button size.
        val flexNarrow = 260.dp
        assertEquals(48.dp, calculateCoverControlButtonSize(flexNarrow))
        assertEquals(24.dp, calculateCoverControlIconSize(48.dp))

        // On a standard 360dp width device in flex mode:
        val flexStandard = 360.dp
        assertEquals(48.dp, calculateCoverControlButtonSize(flexStandard))
        assertEquals(24.dp, calculateCoverControlIconSize(48.dp))
    }

    @Test
    fun `handles invalid or infinite dimensions safely`() {
        assertEquals(48.dp, calculateCoverControlButtonSize(0.dp))
        assertEquals(48.dp, calculateCoverControlButtonSize((-20).dp))
        assertEquals(48.dp, calculateCoverControlButtonSize(Dp.Infinity))
    }
}
