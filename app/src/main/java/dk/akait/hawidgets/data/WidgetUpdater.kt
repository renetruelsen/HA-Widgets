package dk.akait.hawidgets.data

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.widget.automation.AutomationWidget
import dk.akait.hawidgets.widget.binarysensor.BinarySensorWidget
import dk.akait.hawidgets.widget.climate.ClimateWidget
import dk.akait.hawidgets.widget.light.LightWidget
import dk.akait.hawidgets.widget.scene.SceneWidget
import dk.akait.hawidgets.widget.script.ScriptWidget
import dk.akait.hawidgets.widget.sensor.SensorWidget
import dk.akait.hawidgets.widget.cover.CoverWidget
import dk.akait.hawidgets.widget.switchwidget.SwitchWidget

/**
 * Fan-out til hjemskærms-widgets.
 * For hvert berørt domæne: opdatér alle widgets af den type via updateAll.
 */
object WidgetUpdater {

    private val domainWidgets: Map<String, GlanceAppWidget> = mapOf(
        "light" to LightWidget(),
        "switch" to SwitchWidget(),
        "scene" to SceneWidget(),
        "script" to ScriptWidget(),
        "automation" to AutomationWidget(),
        "sensor" to SensorWidget(),
        "binary_sensor" to BinarySensorWidget(),
        "cover" to CoverWidget(),
        "climate" to ClimateWidget(),
    )

    /** Opdatér alle widgets der viser [entityId]. */
    suspend fun updateForEntity(context: Context, entityId: String) {
        val domains = AppDatabase.get(context).entityWidgetDao()
            .widgetsForEntity(entityId).map { it.domain }.toSet()
        if (domains.isEmpty()) return

        val manager = GlanceAppWidgetManager(context)
        for (domain in domains) {
            val widget = domainWidgets[domain] ?: continue
            manager.getGlanceIds(widget::class.java)
                .forEach { glanceId -> runCatching { widget.update(context, glanceId) } }
        }
    }
}
