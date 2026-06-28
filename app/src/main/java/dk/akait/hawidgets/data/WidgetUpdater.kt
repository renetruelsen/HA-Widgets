package dk.akait.hawidgets.data

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.widget.light.LightWidget

/**
 * Fan-out til hjemskærms-widgets. Glance-widgets er ikke live-abonnenter; en
 * "opdatering" = find alle glanceIds hvis config peger på den ændrede entity og
 * kald [GlanceAppWidget.update]. Glance gen-læser så Room (sandhedskilden) og
 * tegner korrekt. Flere widgets på samme entity opdateres derfor samtidig.
 */
object WidgetUpdater {

    /** Map domæne → Glance-widget-instans. Udvides når flere widget-typer kommer. */
    private fun widgetForDomain(domain: String): GlanceAppWidget? = when (domain) {
        "light" -> LightWidget()
        else -> null
    }

    /** Opdatér netop de widgets der viser [entityId]. */
    suspend fun updateForEntity(context: Context, entityId: String) {
        val widgets = AppDatabase.get(context).entityWidgetDao().widgetsForEntity(entityId)
        if (widgets.isEmpty()) return
        val manager = GlanceAppWidgetManager(context)
        widgets.forEach { w ->
            val glance = widgetForDomain(w.domain) ?: return@forEach
            runCatching {
                val glanceId = manager.getGlanceIdBy(w.appWidgetId)
                glance.update(context, glanceId)
            }
        }
    }
}
