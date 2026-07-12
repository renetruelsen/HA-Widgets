package dk.akait.hawidgets.logging

import android.content.Context
import dk.akait.hawidgets.data.WidgetConfig
import dk.akait.hawidgets.data.WidgetConfigStore
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.MultiWidgetEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.multientity.secondaryColumns
import dk.akait.hawidgets.widget.multientity.showValueOrDefault

/**
 * Ren serialisering af den aktuelle widget-konfiguration til log-linjer — bruges ved crash og
 * "Send log nu" (ikke ved rutine-W-flush), så en fejlrapport kan bruges til at genskabe et
 * widget-setup. Kun entity-ID'er/domæner/handlinger — ingen HA-URL/token.
 */
fun formatWidgetConfigDump(
    shortcuts: Map<Int, WidgetConfig>,
    widgets: List<MultiWidgetEntity>,
    slotsByWidget: Map<Int, List<MultiWidgetSlotEntity>>,
): List<String> = buildList {
    shortcuts.toSortedMap().forEach { (id, cfg) ->
        add("I [CONFIG] shortcut widget=$id dashboard=${cfg.dashboardPath}")
    }
    widgets.sortedBy { it.appWidgetId }.forEach { widget ->
        val slots = slotsByWidget[widget.appWidgetId].orEmpty()
        add("I [CONFIG] multi widget=${widget.appWidgetId} slots=${slots.size}")
        slots.forEach { slot ->
            add(
                "I [CONFIG]   slot${slot.slotIndex} display=${slot.displayEntityId} domain=${slot.displayDomain} " +
                    "action=${slot.action} target=${slot.actionEntityId} confirm=${slot.confirmAction} " +
                    "showIcon=${slot.showIcon}"
            )
            slot.secondaryColumns().forEachIndexed { index, secondary ->
                val displayId = secondary.displayEntityId ?: return@forEachIndexed
                val labelPart = secondary.label?.takeIf { it.isNotBlank() }?.let { " label=\"$it\"" } ?: ""
                add(
                    "I [CONFIG]   slot${slot.slotIndex}.secondary${index + 1} display=$displayId " +
                        "action=${secondary.action ?: "NONE"} showValue=${secondary.showValueOrDefault()}$labelPart"
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
    val shortcuts = WidgetConfigStore.get(context).getAll()
    return formatWidgetConfigDump(shortcuts, widgets, slotsByWidget)
}
