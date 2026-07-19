package dk.rtr.hawidgets.data

import dk.rtr.hawidgets.data.db.MultiWidgetChipEntity
import dk.rtr.hawidgets.data.db.MultiWidgetSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettleBurstTest {

    private fun slot(
        widget: Int,
        index: Int,
        display: String?,
        action: String? = display,
    ) = MultiWidgetSlotEntity(
        appWidgetId = widget,
        slotIndex = index,
        displayEntityId = display,
        displayDomain = display?.substringBefore('.'),
        actionEntityId = action,
        actionDomain = action?.substringBefore('.'),
        action = if (action == null) null else "TOGGLE",
        label = "",
    )

    private fun chip(
        widget: Int,
        slotIndex: Int,
        chipIndex: Int,
        display: String,
        action: String = display,
    ) = MultiWidgetChipEntity(
        appWidgetId = widget,
        slotIndex = slotIndex,
        chipIndex = chipIndex,
        displayEntityId = display,
        displayDomain = display.substringBefore('.'),
        actionEntityId = action,
        actionDomain = action.substringBefore('.'),
        action = "TOGGLE",
    )

    @Test fun alwaysIncludesActedEntityEvenWhenNotInAnyRow() {
        val result = entityIdsInSameRows("climate.spa", emptyList(), emptyList())
        assertEquals(setOf("climate.spa"), result)
    }

    @Test fun includesSlotDisplayAndActionOfMatchingRow() {
        val slots = listOf(slot(1, 0, display = "sensor.spa_temp", action = "climate.spa"))
        // Handling sker på action-målet; visnings-entiteten i samme række skal med.
        val result = entityIdsInSameRows("climate.spa", slots, emptyList())
        assertEquals(setOf("climate.spa", "sensor.spa_temp"), result)
    }

    @Test fun includesChipsOfMatchingRow() {
        val slots = listOf(slot(1, 0, display = "climate.spa"))
        val chips = listOf(
            chip(1, 0, 0, display = "sensor.spa_power"),
            chip(1, 0, 1, display = "switch.spa_pump"),
        )
        val result = entityIdsInSameRows("climate.spa", slots, chips)
        assertEquals(
            setOf("climate.spa", "sensor.spa_power", "switch.spa_pump"),
            result,
        )
    }

    @Test fun matchesRowWhenActedEntityIsAChip() {
        // Trykket kom fra en chip → hele dens række (hoved-slot + søster-chips) skal med.
        val slots = listOf(slot(1, 0, display = "light.living"))
        val chips = listOf(
            chip(1, 0, 0, display = "cover.velux"),
            chip(1, 0, 1, display = "sensor.lux"),
        )
        val result = entityIdsInSameRows("cover.velux", slots, chips)
        assertEquals(
            setOf("cover.velux", "light.living", "sensor.lux"),
            result,
        )
    }

    @Test fun doesNotIncludeOtherRowsInSameWidget() {
        val slots = listOf(
            slot(1, 0, display = "climate.spa"),
            slot(1, 1, display = "light.bedroom"),
        )
        val result = entityIdsInSameRows("climate.spa", slots, emptyList())
        assertTrue("light.bedroom" !in result)
        assertEquals(setOf("climate.spa"), result)
    }

    @Test fun coversSameEntityInMultipleWidgets() {
        val slots = listOf(
            slot(1, 0, display = "climate.spa", action = "climate.spa"),
            slot(2, 0, display = "sensor.other", action = "climate.spa"),
        )
        val result = entityIdsInSameRows("climate.spa", slots, emptyList())
        assertEquals(setOf("climate.spa", "sensor.other"), result)
    }

    @Test fun chipsOnlyRowNullSlotEntitiesAreIgnored() {
        // Chips-only række: slot-felterne er null. Ingen NPE, kun chip-entiteterne med.
        val slots = listOf(slot(1, 0, display = null, action = null))
        val chips = listOf(
            chip(1, 0, 0, display = "switch.a"),
            chip(1, 0, 1, display = "switch.b"),
        )
        val result = entityIdsInSameRows("switch.a", slots, chips)
        assertEquals(setOf("switch.a", "switch.b"), result)
    }

    @Test fun pollPlanIsWithinTwoMinutesAndAscendingCumulative() {
        var cumulative = 0L
        for (d in SETTLE_BURST_DELAYS_MS) {
            assertTrue("hvert step-delay er positivt", d > 0)
            cumulative += d
        }
        // Sidste poll skal ligge inden for ~2 min (Velux < 1 min + spa-opstart dækkes).
        assertTrue("samlet plan ≤ 120 sek", cumulative <= 120_000)
        assertTrue("planen dækker mindst 60 sek", cumulative >= 60_000)
    }
}
