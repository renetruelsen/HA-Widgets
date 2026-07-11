package dk.akait.hawidgets.widget.multientity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetSlotStyleTest {

    private fun row(active: Boolean = false, heating: Boolean = false, unavailable: Boolean = false) =
        resolveStyle(isChip = false, isActive = active, isToggle = false, heating = heating, unavailable = unavailable)

    private fun chip(active: Boolean = false, toggle: Boolean = false, heating: Boolean = false, unavailable: Boolean = false) =
        resolveStyle(isChip = true, isActive = active, isToggle = toggle, heating = heating, unavailable = unavailable)

    // --- HOVED-RÆKKE (row) ---
    @Test fun rowActiveColorsIconAndStatusKeepsLabelNeutral() {
        val t = row(active = true)
        assertEquals(ColorRole.ROW_BG, t.bg)
        assertEquals(ColorRole.PRIMARY, t.icon)
        assertEquals(ColorRole.NEUTRAL, t.label)
        assertEquals(ColorRole.PRIMARY, t.status)
        assertFalse(t.showBorder)
    }

    @Test fun rowInactiveIsAllNeutral() {
        val t = row(active = false)
        assertEquals(ColorRole.ROW_BG, t.bg)
        assertEquals(ColorRole.NEUTRAL, t.icon)
        assertEquals(ColorRole.NEUTRAL, t.label)
        assertEquals(ColorRole.NEUTRAL, t.status)
    }

    @Test fun rowHeatingIsFullOrange() {
        val t = row(active = true, heating = true)
        assertEquals(ColorRole.HEATING_BG, t.bg)
        assertEquals(ColorRole.ON_HEATING, t.icon)
        assertEquals(ColorRole.ON_HEATING, t.label)
        assertEquals(ColorRole.ON_HEATING, t.status)
    }

    @Test fun rowUnavailableIsFadedOnNeutralBg() {
        val t = row(unavailable = true)
        assertEquals(ColorRole.ROW_BG, t.bg)
        assertEquals(ColorRole.FADED, t.icon)
        assertEquals(ColorRole.FADED, t.label)
        assertEquals(ColorRole.FADED, t.status)
    }

    // --- CHIP ---
    @Test fun chipToggleActiveIsFullPrimary() {
        val t = chip(active = true, toggle = true)
        assertEquals(ColorRole.PRIMARY, t.bg)
        assertEquals(ColorRole.ON_PRIMARY, t.icon)
        assertEquals(ColorRole.ON_PRIMARY, t.label)
        assertEquals(ColorRole.ON_PRIMARY, t.status)
    }

    @Test fun chipToggleOffIsDimOnChipBg() {
        val t = chip(active = false, toggle = true)
        assertEquals(ColorRole.CHIP_BG, t.bg)
        assertEquals(ColorRole.CHIP_DIM, t.icon)
        assertEquals(ColorRole.CHIP_DIM, t.label)
        assertEquals(ColorRole.CHIP_DIM, t.status)
    }

    @Test fun chipActiveNonToggleColorsIconAndStatusDimsLabel() {
        val t = chip(active = true, toggle = false)
        assertEquals(ColorRole.CHIP_BG, t.bg)
        assertEquals(ColorRole.PRIMARY, t.icon)
        assertEquals(ColorRole.CHIP_DIM, t.label)
        assertEquals(ColorRole.PRIMARY, t.status)
    }

    @Test fun chipInfoIsAllDimOnChipBg() {
        val t = chip(active = false, toggle = false)
        assertEquals(ColorRole.CHIP_BG, t.bg)
        assertEquals(ColorRole.CHIP_DIM, t.icon)
    }

    @Test fun chipHeatingIsFullOrange() {
        val t = chip(active = true, heating = true)
        assertEquals(ColorRole.HEATING_BG, t.bg)
        assertEquals(ColorRole.ON_HEATING, t.status)
    }

    @Test fun chipUnavailableIsFadedOnChipBg() {
        val t = chip(unavailable = true)
        assertEquals(ColorRole.CHIP_BG, t.bg)
        assertEquals(ColorRole.FADED, t.icon)
    }

    // --- PRIORITET ---
    @Test fun unavailableWinsOverActive() {
        val t = chip(active = true, toggle = true, unavailable = true)
        assertEquals(ColorRole.FADED, t.icon)
    }

    @Test fun heatingWinsOverActive() {
        val t = row(active = true, heating = true)
        assertEquals(ColorRole.HEATING_BG, t.bg)
    }

    @Test fun borderIsAlwaysFalseInThisVersion() {
        assertFalse(row(active = true).showBorder)
        assertFalse(chip(active = true, toggle = true).showBorder)
        assertFalse(chip(unavailable = true).showBorder)
    }
}
