package dk.rtr.hawidgets.logging

import android.content.Context
import dk.rtr.hawidgets.data.WidgetConfig
import dk.rtr.hawidgets.data.WidgetConfigStore
import dk.rtr.hawidgets.data.db.AppDatabase
import dk.rtr.hawidgets.data.db.MultiWidgetChipEntity
import dk.rtr.hawidgets.data.db.MultiWidgetEntity
import dk.rtr.hawidgets.data.db.MultiWidgetSlotEntity

/**
 * Ren serialisering af den aktuelle widget-konfiguration til log-linjer — bruges ved crash og
 * "Send log nu" (ikke ved rutine-W-flush), så en fejlrapport kan bruges til at genskabe et
 * widget-setup. Kun entity-ID'er/domæner/handlinger — ingen HA-URL/token.
 */
fun formatWidgetConfigDump(
    shortcuts: Map<Int, WidgetConfig>,
    widgets: List<MultiWidgetEntity>,
    slotsByWidget: Map<Int, List<MultiWidgetSlotEntity>>,
    chipsByWidget: Map<Int, List<MultiWidgetChipEntity>>,
): List<String> = buildList {
    shortcuts.toSortedMap().forEach { (id, cfg) ->
        add("I [CONFIG] shortcut widget=$id dashboard=${cfg.dashboardPath}")
    }
    widgets.sortedBy { it.appWidgetId }.forEach { widget ->
        val slots = slotsByWidget[widget.appWidgetId].orEmpty()
        val chipsBySlot = chipsByWidget[widget.appWidgetId].orEmpty().groupBy { it.slotIndex }
        add("I [CONFIG] multi widget=${widget.appWidgetId} slots=${slots.size}")
        slots.forEach { slot ->
            add(
                "I [CONFIG]   slot${slot.slotIndex} display=${slot.displayEntityId ?: "(none)"} domain=${slot.displayDomain} " +
                    "action=${slot.action} target=${slot.actionEntityId} confirm=${slot.confirmAction} " +
                    "showIcon=${slot.showIcon}"
            )
            chipsBySlot[slot.slotIndex].orEmpty().sortedBy { it.chipIndex }.forEach { chip ->
                val labelPart = chip.label?.takeIf { it.isNotBlank() }?.let { " label=\"$it\"" } ?: ""
                add(
                    "I [CONFIG]   slot${slot.slotIndex}.chip${chip.chipIndex + 1} display=${chip.displayEntityId} " +
                        "action=${chip.action} showValue=${chip.showValueOrDefault()}$labelPart"
                )
            }
        }
    }
}

/** Henter den aktuelle widget-konfiguration fra Room + SharedPreferences og serialiserer den. */
suspend fun collectWidgetConfigDump(context: Context): List<String> {
    val multiDao = AppDatabase.get(context).multiWidgetDao()
    val widgets = multiDao.getAll()
    val slotsByWidget = widgets.associate { it.appWidgetId to multiDao.getSlots(it.appWidgetId) }
    val chipsByWidget = widgets.associate { it.appWidgetId to multiDao.getChips(it.appWidgetId) }
    val shortcuts = WidgetConfigStore.get(context).getAll()
    return formatWidgetConfigDump(shortcuts, widgets, slotsByWidget, chipsByWidget)
}

private fun MultiWidgetChipEntity.showValueOrDefault(): Boolean =
    showValue ?: dk.rtr.hawidgets.widget.common.defaultShowValueFor(action)
