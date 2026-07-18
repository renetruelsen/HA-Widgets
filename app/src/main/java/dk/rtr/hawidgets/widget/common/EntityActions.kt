package dk.rtr.hawidgets.widget.common

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dk.rtr.hawidgets.data.EntityRepository
import dk.rtr.hawidgets.data.db.AppDatabase
import dk.rtr.hawidgets.worker.SyncWorker

/** Generisk toggle — domain-bevidst service-mapping. Bruges af switch, lock, cover, automation osv. */
class ToggleEntityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entityId = parameters[entityIdKey] ?: return
        val domain = parameters[domainKey] ?: return
        val current = AppDatabase.get(context).entityStateDao().get(entityId) ?: return
        val (targetState, service) = when (domain) {
            "lock" -> if (current.state == "locked") "unlocked" to "unlock" else "locked" to "lock"
            "cover" -> if (current.state == "open") "closed" to "close_cover" else "open" to "open_cover"
            else -> if (current.state == "on") "off" to "turn_off" else "on" to "turn_on"
        }
        EntityRepository.command(context, domain, service, entityId, targetState, current.state)
    }

    companion object {
        val entityIdKey = ActionParameters.Key<String>("entityId")
        val domainKey = ActionParameters.Key<String>("domain")
    }
}

/** Tap = refresh one entity. Pass entityId to avoid refreshing all entities. */
class RefreshEntityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entityId = parameters[entityIdKey]
        if (entityId != null) {
            EntityRepository.refresh(context, entityId)
        } else {
            SyncWorker.runNow(context)
        }
    }

    companion object {
        val entityIdKey = ActionParameters.Key<String>("entityId")
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
