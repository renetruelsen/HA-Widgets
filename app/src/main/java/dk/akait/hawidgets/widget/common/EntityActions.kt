package dk.akait.hawidgets.widget.common

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dk.akait.hawidgets.data.EntityRepository
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.worker.SyncWorker

/** Generic toggle on↔off — for switch and any other on/off domain (not light, light uses its own). */
class ToggleEntityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entityId = parameters[entityIdKey] ?: return
        val domain = parameters[domainKey] ?: return
        val current = AppDatabase.get(context).entityStateDao().get(entityId) ?: return
        val target = if (current.state == "on") "off" else "on"
        val service = if (target == "on") "turn_on" else "turn_off"
        EntityRepository.command(context, domain, service, entityId, target, current.state)
    }

    companion object {
        val entityIdKey = ActionParameters.Key<String>("entityId")
        val domainKey = ActionParameters.Key<String>("domain")
    }
}

/** Tap = immediate sync. For read-only widgets (sensor, binary_sensor, weather, climate). */
class RefreshEntityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SyncWorker.runNow(context)
    }
}

/**
 * Trigger entity — for scene (turn_on), script (turn_on), automation (trigger).
 * [targetStateKey] is optional: if supplied, applied optimistically; else current state kept.
 */
class TriggerEntityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entityId = parameters[entityIdKey] ?: return
        val domain = parameters[domainKey] ?: return
        val service = parameters[serviceKey] ?: return
        val targetState = parameters[targetStateKey]
        val current = AppDatabase.get(context).entityStateDao().get(entityId)
        EntityRepository.command(
            context = context,
            domain = domain,
            service = service,
            entityId = entityId,
            targetState = targetState ?: current?.state ?: "on",
            fromState = current?.state,
        )
    }

    companion object {
        val entityIdKey = ActionParameters.Key<String>("entityId")
        val domainKey = ActionParameters.Key<String>("domain")
        val serviceKey = ActionParameters.Key<String>("service")
        val targetStateKey = ActionParameters.Key<String>("targetState")
    }
}
