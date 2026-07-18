package dk.rtr.hawidgets.widget.multientity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSlotStyleTest {

    private fun row(active: Boolean = false, heating: Boolean = false, unavailable: Boolean = false) =
        resolveStyle(isChip = false, isActive = active, isToggle = false, heating = heating, unavailable = unavailable)

    private fun chip(
        active: Boolean = false,
        toggle: Boolean = false,
        heating: Boolean = false,
        unavailable: Boolean = false,
        rowHeating: Boolean = false,
    ) = resolveStyle(
        isChip = true, isActive = active, isToggle = toggle, heating = heating,
        unavailable = unavailable, rowHeating = rowHeating,
    )

    // --- HOVED-RÆKKE (row) ---
    @Test fun rowActiveColorsIconAndStatusKeepsLabelNeutral() {
        val t = row(active = true)
        assertEquals(ColorRole.ROW_BG, t.bg)
        assertEquals(ColorRole.PRIMARY, t.icon)
        assertEquals(ColorRole.NEUTRAL, t.label)
        assertEquals(ColorRole.PRIMARY, t.status)
        assertFalse(t.showBorder)
        assertFalse(t.strikeLabel)
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
        assertFalse(t.strikeLabel)
    }

    @Test fun rowUnavailableIsFadedAndStrikesLabelOnly() {
        val t = row(unavailable = true)
        assertEquals(ColorRole.ROW_BG, t.bg)
        assertEquals(ColorRole.FADED, t.icon)
        assertEquals(ColorRole.FADED, t.label)
        assertEquals(ColorRole.FADED, t.status)
        assertTrue(t.strikeLabel)
    }

    // --- CHIP, NORMAL RÆKKE ---
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

    @Test fun chipUnavailableIsFadedAndStrikesLabel() {
        val t = chip(unavailable = true)
        assertEquals(ColorRole.CHIP_BG, t.bg)
        assertEquals(ColorRole.FADED, t.icon)
        assertTrue(t.strikeLabel)
    }

    // --- CHIP PÅ EN VARMENDE RÆKKE (rowHeating) ---
    @Test fun chipToggleActiveOnHeatingRowIsAccentFillWhiteText() {
        val t = chip(active = true, toggle = true, rowHeating = true)
        assertEquals(ColorRole.HEATING_ACCENT, t.bg)
        assertEquals(ColorRole.ON_HEATING, t.icon)
        assertEquals(ColorRole.ON_HEATING, t.label)
        assertEquals(ColorRole.ON_HEATING, t.status)
    }

    @Test fun chipActiveNonToggleOnHeatingRowIsUniformHeatingDim() {
        // "Keep it simple": aktiv D og info ser ens ud på en varmende række — samme kombi som "Fra".
        val t = chip(active = true, toggle = false, rowHeating = true)
        assertEquals(ColorRole.CHIP_BG, t.bg)
        assertEquals(ColorRole.HEATING_DIM, t.icon)
        assertEquals(ColorRole.HEATING_DIM, t.label)
        assertEquals(ColorRole.HEATING_DIM, t.status)
    }

    @Test fun chipInfoOnHeatingRowIsUniformHeatingDim() {
        val t = chip(active = false, toggle = false, rowHeating = true)
        assertEquals(ColorRole.CHIP_BG, t.bg)
        assertEquals(ColorRole.HEATING_DIM, t.icon)
        assertEquals(ColorRole.HEATING_DIM, t.status)
    }

    @Test fun chipToggleOffOnHeatingRowIsUniformHeatingDim() {
        val t = chip(active = false, toggle = true, rowHeating = true)
        assertEquals(ColorRole.HEATING_DIM, t.icon)
        assertEquals(ColorRole.HEATING_DIM, t.status)
    }

    @Test fun chipUnavailableOnHeatingRowUsesHeatingUnavailableAndStrikes() {
        val t = chip(unavailable = true, rowHeating = true)
        assertEquals(ColorRole.CHIP_BG, t.bg)
        assertEquals(ColorRole.HEATING_UNAVAIL, t.icon)
        assertEquals(ColorRole.HEATING_UNAVAIL, t.label)
        assertEquals(ColorRole.HEATING_UNAVAIL, t.status)
        assertTrue(t.strikeLabel)
    }

    @Test fun ownHeatingWinsOverRowHeating() {
        // Chippen ER selv en climate der varmer, mens den også sidder på en varmende række →
        // egen fuld orange vinder over row-heating-dæmpningen.
        val t = chip(active = true, heating = true, rowHeating = true)
        assertEquals(ColorRole.HEATING_BG, t.bg)
        assertEquals(ColorRole.ON_HEATING, t.status)
    }

    @Test fun rowHeatingHasNoEffectOnMainRow() {
        // rowHeating er kun meningsfuldt for chips; en hoved-række har ingen "forælder" at arve fra.
        val t = resolveStyle(isChip = false, isActive = true, isToggle = false, heating = false, unavailable = false, rowHeating = true)
        assertEquals(ColorRole.PRIMARY, t.icon)
        assertEquals(ColorRole.NEUTRAL, t.label)
    }

    // --- PRIORITET ---
    @Test fun unavailableWinsOverActive() {
        val t = chip(active = true, toggle = true, unavailable = true)
        assertEquals(ColorRole.FADED, t.icon)
        assertTrue(t.strikeLabel)
    }

    @Test fun unavailableWinsOverRowHeating() {
        val t = chip(unavailable = true, rowHeating = true)
        assertEquals(ColorRole.HEATING_UNAVAIL, t.icon)
    }

    @Test fun heatingWinsOverActive() {
        val t = row(active = true, heating = true)
        assertEquals(ColorRole.HEATING_BG, t.bg)
    }

    @Test fun strikeLabelFalseWhenNotUnavailable() {
        assertFalse(row(active = true).strikeLabel)
        assertFalse(chip(active = true, toggle = true).strikeLabel)
        assertFalse(chip(active = true, rowHeating = true).strikeLabel)
        assertFalse(row(heating = true).strikeLabel)
    }

    @Test fun borderIsAlwaysFalseInThisVersion() {
        assertFalse(row(active = true).showBorder)
        assertFalse(chip(active = true, toggle = true).showBorder)
        assertFalse(chip(unavailable = true).showBorder)
        assertFalse(chip(active = true, rowHeating = true).showBorder)
    }
}
