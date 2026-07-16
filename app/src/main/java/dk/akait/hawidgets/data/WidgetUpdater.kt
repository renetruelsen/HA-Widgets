package dk.akait.hawidgets.data

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.widget.multientity.MultiEntityWidget

/**
 * Fan-out til hjemskærms-widgets. Kun `MultiEntityWidget` er entitets-drevet
 * (genvejen viser ingen live entitet).
 */
object WidgetUpdater {

    /** Opdatér alle multi-widgets der viser/handler på [entityId]. */
    suspend fun updateForEntity(context: Context, entityId: String) {
        val db = AppDatabase.get(context)
        if (!db.multiWidgetDao().isEntityUsed(entityId)) return

        val manager = GlanceAppWidgetManager(context)
        val multiWidget = MultiEntityWidget()
        manager.getGlanceIds(multiWidget::class.java)
            .forEach { glanceId -> runCatching { multiWidget.update(context, glanceId) } }
    }
}
