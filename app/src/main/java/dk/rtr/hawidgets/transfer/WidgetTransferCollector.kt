package dk.rtr.hawidgets.transfer

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import dk.rtr.hawidgets.data.WidgetConfig
import dk.rtr.hawidgets.data.WidgetConfigStore
import dk.rtr.hawidgets.data.db.AppDatabase
import dk.rtr.hawidgets.data.db.MultiSlotWithChips
import dk.rtr.hawidgets.widget.ShortcutWidgetReceiver
import dk.rtr.hawidgets.widget.multientity.MultiEntityWidgetReceiver
import java.time.Instant

/**
 * Bygger [TransferBundle]'er fra den aktuelle widget-konfiguration (Room + [WidgetConfigStore]).
 * Parallel til [dk.rtr.hawidgets.logging.collectWidgetConfigDump], men producerer den fulde,
 * genimporterbare struktur i stedet for log-linjer.
 */

private fun nowIso(): String = Instant.now().toString()

/** Auto-udledt label til import-vælgeren — multi-widgets har ingen egen titel. */
internal fun deriveMultiLabel(slots: List<MultiSlotWithChips>): String {
    if (slots.isEmpty()) return "Multi"
    val first = slots.first().let { it.slot.label.ifBlank { it.slot.displayEntityId ?: "chips" } }
    val extra = slots.size - 1
    return if (extra > 0) "Multi: $first +$extra" else "Multi: $first"
}

internal fun deriveShortcutLabel(config: WidgetConfig): String =
    config.title.ifBlank { config.dashboardPath }

/** Sammensætter en widgets slots + chips (fra Room) til [MultiSlotWithChips]. */
internal suspend fun loadSlotsWithChips(
    db: AppDatabase,
    appWidgetId: Int,
): List<MultiSlotWithChips> {
    val dao = db.multiWidgetDao()
    val slots = dao.getSlots(appWidgetId)
    val chipsBySlot = dao.getChips(appWidgetId).groupBy { it.slotIndex }
    return slots.map { slot -> MultiSlotWithChips(slot, chipsBySlot[slot.slotIndex].orEmpty()) }
}

/** Én multi-widgets in-memory tilstand → transport-config (bruges af "Eksportér denne"). */
fun multiTransferConfig(
    slots: List<MultiSlotWithChips>,
    showRefreshIcon: Boolean,
    rowDensity: String = "NORMAL",
): TransferConfig.Multi =
    TransferConfig.Multi(
        label = deriveMultiLabel(slots),
        showRefreshIcon = showRefreshIcon,
        rowDensity = rowDensity,
        slots = slots,
    )

/** Én genvej-config → transport-config (bruges af "Eksportér denne"). */
fun shortcutTransferConfig(config: WidgetConfig): TransferConfig.Shortcut =
    TransferConfig.Shortcut(label = deriveShortcutLabel(config), config = config)

/** Pak én config ind i et bundle med tidsstempel. */
fun singleConfigBundle(config: TransferConfig): TransferBundle =
    TransferBundle(exported = nowIso(), configs = listOf(config))

/**
 * ALLE faktisk placerede widgets (multi fra Room + genveje fra [WidgetConfigStore]) → ét bundle.
 * Filtreret til widget-id'er som AppWidgetManager stadig kender — så forældreløse config-rækker
 * (fjernede widgets, endnu ikke purged af grace-perioden) IKKE havner i eksporten.
 */
suspend fun collectAllConfigs(context: Context): TransferBundle {
    val awm = AppWidgetManager.getInstance(context)
    val boundMulti = awm?.getAppWidgetIds(ComponentName(context, MultiEntityWidgetReceiver::class.java))?.toSet().orEmpty()
    val boundShortcut = awm?.getAppWidgetIds(ComponentName(context, ShortcutWidgetReceiver::class.java))?.toSet().orEmpty()

    val db = AppDatabase.get(context)
    val dao = db.multiWidgetDao()
    val multi = dao.getAll()
        .filter { it.appWidgetId in boundMulti }
        .map { widget -> multiTransferConfig(loadSlotsWithChips(db, widget.appWidgetId), widget.showRefreshIcon, widget.rowDensity) }
    val shortcuts = WidgetConfigStore.get(context).getAll()
        .filterKeys { it in boundShortcut }
        .values.map { shortcutTransferConfig(it) }
    return TransferBundle(exported = nowIso(), configs = multi + shortcuts)
}
