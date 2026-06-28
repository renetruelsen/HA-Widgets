package dk.akait.hawidgets.data

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.widget.light.LightWidget

/**
 * Fan-out til hjemskærms-widgets. Glance gen-læser Room (sandhedskilden) og
 * tegner korrekt. Alle widgets opdateres via updateAll der bruger
 * AppWidgetManager direkte — robust mod nyoprettede widgets uden Glance-session,
 * modsat getGlanceIdBy der kaster IllegalArgumentException og fejler stille.
 */
object WidgetUpdater {

    /** Opdatér alle widgets der viser [entityId]. */
    suspend fun updateForEntity(context: Context, entityId: String) {
        val widgets = AppDatabase.get(context).entityWidgetDao().widgetsForEntity(entityId)
        if (widgets.isEmpty()) return
        // getGlanceIds bruger AppWidgetManager (system, altid korrekt) og opretter GlanceId
        // for nye widgets — robust mod getGlanceIdBy der kaster for widgets uden session.
        val widget = LightWidget()
        GlanceAppWidgetManager(context)
            .getGlanceIds(LightWidget::class.java)
            .forEach { glanceId -> runCatching { widget.update(context, glanceId) } }
    }
}
