package dk.akait.hawidgets.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.widget.ShortcutWidgetReceiver
import dk.akait.hawidgets.widget.multientity.MultiEntityWidgetReceiver

/** Grace-periode før en forældreløs (ubundet) widget-config hard-slettes: 30 dage. */
const val WIDGET_ORPHAN_GRACE_MILLIS = 30L * 24 * 60 * 60 * 1000

/** Hvad der skal ske med én config ved reconcile. */
enum class ReconcileAction { STAMP, CLEAR, PURGE, NONE }

/**
 * Ren beslutning (unit-testbar) for én widget-config ud fra om den er bundet af AppWidgetManager
 * og dens nuværende soft-delete-tidsstempel:
 *  - bundet + stemplet  → CLEAR  (gen-bundet, fx id-genbrug — annullér sletning)
 *  - bundet             → NONE
 *  - ubundet + ustemplet → STAMP (netop opdaget fjernet — start grace-uret)
 *  - ubundet + udløbet   → PURGE (30 dage forbi — hard-slet)
 *  - ubundet + i vindue  → NONE
 *
 * Fordi ubundet-uden-stempel kun STAMPER (aldrig sletter), er et falsk "ubundet" i boot-vinduet
 * harmløst: næste sweep (widget nu bundet) CLEARer stemplet igen. Intet hard-slettes før 30 dages
 * sammenhængende fravær.
 */
fun reconcileDecision(bound: Boolean, removedAt: Long?, now: Long, graceMillis: Long): ReconcileAction =
    if (bound) {
        if (removedAt != null) ReconcileAction.CLEAR else ReconcileAction.NONE
    } else {
        when {
            removedAt == null -> ReconcileAction.STAMP
            now - removedAt > graceMillis -> ReconcileAction.PURGE
            else -> ReconcileAction.NONE
        }
    }

/**
 * Bringer gemte widget-configs (Room multi + genvej-prefs) i overensstemmelse med de widgets der
 * faktisk er bundet af AppWidgetManager. Stempler forældreløse, rydder stempel på gen-bundne, og
 * hard-sletter dem der har været fjernet længere end [WIDGET_ORPHAN_GRACE_MILLIS]. Kaldes periodisk
 * fra [dk.akait.hawidgets.worker.SyncWorker]; er idempotent og sikker at køre når som helst.
 */
suspend fun reconcileWidgets(context: Context, now: Long = System.currentTimeMillis()) {
    val awm = AppWidgetManager.getInstance(context) ?: return
    val boundMulti = awm.getAppWidgetIds(ComponentName(context, MultiEntityWidgetReceiver::class.java)).toSet()
    val boundShortcut = awm.getAppWidgetIds(ComponentName(context, ShortcutWidgetReceiver::class.java)).toSet()

    val dao = AppDatabase.get(context).multiWidgetDao()
    dao.getAll().forEach { widget ->
        when (reconcileDecision(widget.appWidgetId in boundMulti, widget.removedAt, now, WIDGET_ORPHAN_GRACE_MILLIS)) {
            ReconcileAction.STAMP -> dao.upsert(widget.copy(removedAt = now))
            ReconcileAction.CLEAR -> dao.upsert(widget.copy(removedAt = null))
            ReconcileAction.PURGE -> {
                dao.deleteAllChips(widget.appWidgetId)
                dao.deleteAllSlots(widget.appWidgetId)
                dao.delete(widget.appWidgetId)
            }
            ReconcileAction.NONE -> {}
        }
    }

    val store = WidgetConfigStore.get(context)
    store.getAll().keys.forEach { id ->
        when (reconcileDecision(id in boundShortcut, store.removedAt(id), now, WIDGET_ORPHAN_GRACE_MILLIS)) {
            ReconcileAction.STAMP -> store.markRemoved(id, now)
            ReconcileAction.CLEAR -> store.clearRemoved(id)
            ReconcileAction.PURGE -> {
                store.remove(id)
                store.clearRemoved(id)
            }
            ReconcileAction.NONE -> {}
        }
    }
}
