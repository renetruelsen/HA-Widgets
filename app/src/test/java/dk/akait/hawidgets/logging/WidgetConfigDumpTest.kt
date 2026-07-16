package dk.akait.hawidgets.logging

import dk.akait.hawidgets.data.DisplayMode
import dk.akait.hawidgets.data.WidgetConfig
import dk.akait.hawidgets.data.db.MultiWidgetChipEntity
import dk.akait.hawidgets.data.db.MultiWidgetEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetConfigDumpTest {

    @Test
    fun dumpsShortcutsAndMultiWidgetsWithSlotsAndSecondaryChips() {
        val shortcuts = mapOf(
            12 to WidgetConfig(dashboardPath = "lovelace-hjem", title = "Hjem", displayMode = DisplayMode.FULLSCREEN)
        )
        val widgets = listOf(MultiWidgetEntity(appWidgetId = 20))
        val mainSlot = MultiWidgetSlotEntity(
            appWidgetId = 20,
            slotIndex = 0,
            displayEntityId = "light.hue_stuelampe",
            displayDomain = "light",
            actionEntityId = "light.hue_stuelampe",
            actionDomain = "light",
            action = "TOGGLE",
            label = "",
            confirmAction = true,
            showIcon = true,
        )
        val chip = MultiWidgetChipEntity(
            appWidgetId = 20,
            slotIndex = 0,
            chipIndex = 0,
            displayEntityId = "sensor.temp",
            displayDomain = "sensor",
            actionEntityId = "sensor.temp",
            actionDomain = "sensor",
            action = "NONE",
            showValue = true,
            label = "Temp",
        )
        val slotsByWidget = mapOf(20 to listOf(mainSlot))
        val chipsByWidget = mapOf(20 to listOf(chip))

        val lines = formatWidgetConfigDump(shortcuts, widgets, slotsByWidget, chipsByWidget)

        assertEquals(
            listOf(
                "I [CONFIG] shortcut widget=12 dashboard=lovelace-hjem",
                "I [CONFIG] multi widget=20 slots=1",
                "I [CONFIG]   slot0 display=light.hue_stuelampe domain=light action=TOGGLE " +
                    "target=light.hue_stuelampe confirm=true showIcon=true",
                "I [CONFIG]   slot0.chip1 display=sensor.temp action=NONE showValue=true label=\"Temp\"",
            ),
            lines
        )
    }

    @Test
    fun emptyWidgetsAndShortcutsProduceEmptyDump() {
        assertEquals(emptyList<String>(), formatWidgetConfigDump(emptyMap(), emptyList(), emptyMap(), emptyMap()))
    }
}
