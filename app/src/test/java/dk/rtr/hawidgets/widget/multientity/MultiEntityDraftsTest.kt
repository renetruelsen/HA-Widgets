package dk.rtr.hawidgets.widget.multientity

import dk.rtr.hawidgets.data.HaApiClient
import dk.rtr.hawidgets.data.db.MultiSlotWithChips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiEntityDraftsTest {

    private fun entity(id: String, name: String = id) = HaApiClient.EntityBrief(id, name, "on")

    @Test fun toSlotWithChipsReturnsNullWhenEmpty() {
        val draft = SlotDraft()
        assertNull(draft.toSlotWithChips(appWidgetId = 1, slotIndex = 0))
    }

    @Test fun toSlotWithChipsWithOnlyChipsIsValidChipsOnlyRow() {
        val chip = SecondarySlotDraft(entity("lock.front_door"), entity("lock.front_door"), "TOGGLE")
        val draft = SlotDraft(secondaryEntities = listOf(chip))
        val row = draft.toSlotWithChips(appWidgetId = 1, slotIndex = 0)
        requireNotNull(row)
        assertNull(row.slot.displayEntityId)
        assertNull(row.slot.action)
        assertEquals(1, row.chips.size)
        assertEquals("lock.front_door", row.chips[0].displayEntityId)
        assertEquals(0, row.chips[0].chipIndex)
    }

    @Test fun toSlotWithChipsWithMainEntityKeepsAction() {
        val light = entity("light.hue_stuelampe")
        val draft = SlotDraft(displayEntity = light, actionEntity = light, action = "TOGGLE")
        val row = draft.toSlotWithChips(appWidgetId = 1, slotIndex = 0)
        requireNotNull(row)
        assertEquals("light.hue_stuelampe", row.slot.displayEntityId)
        assertEquals("TOGGLE", row.slot.action)
    }

    @Test fun draftFromSlotWithChipsRoundTripsChipsOnlyRow() {
        val allEntities = listOf(entity("lock.front_door"), entity("sensor.temp"))
        val chip = SecondarySlotDraft(entity("lock.front_door"), entity("lock.front_door"), "TOGGLE")
        val original = SlotDraft(secondaryEntities = listOf(chip))
        val row = requireNotNull(original.toSlotWithChips(appWidgetId = 1, slotIndex = 2))

        val reloaded = draftFromSlotWithChips(row, allEntities)

        assertNull(reloaded.displayEntity)
        assertEquals("NONE", reloaded.action)
        assertEquals(1, reloaded.secondaryEntities.size)
        assertEquals("lock.front_door", reloaded.secondaryEntities[0].displayEntity.entityId)
    }

    @Test fun draftFromSlotWithChipsRoundTripsMainEntityRow() {
        val light = entity("light.hue_stuelampe")
        val allEntities = listOf(light)
        val original = SlotDraft(displayEntity = light, actionEntity = light, action = "TOGGLE", label = "Stue")
        val row = requireNotNull(original.toSlotWithChips(appWidgetId = 1, slotIndex = 0))

        val reloaded = draftFromSlotWithChips(row, allEntities)

        assertEquals("light.hue_stuelampe", reloaded.displayEntity?.entityId)
        assertEquals("TOGGLE", reloaded.action)
        assertEquals("Stue", reloaded.label)
    }

    @Test fun removingMainEntityFromExistingRowKeepsChipsOnSave() {
        val light = entity("light.hue_stuelampe")
        val chipEntity = entity("switch.spa")
        val chip = SecondarySlotDraft(chipEntity, chipEntity, "TOGGLE")
        // Simulerer "Fjern hoved-entitet": draft med displayEntity=null men chips bevaret.
        val draft = SlotDraft(secondaryEntities = listOf(chip))
        val row: MultiSlotWithChips = requireNotNull(draft.toSlotWithChips(appWidgetId = 5, slotIndex = 0))

        assertNull(row.slot.displayEntityId)
        assertTrue(row.chips.isNotEmpty())
    }
}
