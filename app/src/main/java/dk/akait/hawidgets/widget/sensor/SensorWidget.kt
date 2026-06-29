package dk.akait.hawidgets.widget.sensor

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.EntityStateEntity
import dk.akait.hawidgets.data.db.EntityWidgetEntity
import androidx.glance.action.actionParametersOf
import dk.akait.hawidgets.widget.common.RefreshEntityAction
import dk.akait.hawidgets.widget.common.STALE_THRESHOLD_MS
import dk.akait.hawidgets.widget.common.UnconfiguredWidgetContent
import dk.akait.hawidgets.widget.common.WidgetCompactLayout
import dk.akait.hawidgets.widget.common.WidgetWideLayout
import dk.akait.hawidgets.widget.common.friendlyNameFromJson
import dk.akait.hawidgets.worker.SyncWorker
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class SensorWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(56.dp, 56.dp), DpSize(110.dp, 56.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val db = AppDatabase.get(context)
        val initialCfg = db.entityWidgetDao().get(appWidgetId)
        val initialState = initialCfg?.let { db.entityStateDao().get(it.entityId) }
        provideContent {
            val viewState by db.entityWidgetDao()
                .observe(appWidgetId)
                .flatMapLatest { cfg ->
                    if (cfg == null) flowOf(null to null)
                    else db.entityStateDao().observe(cfg.entityId).map { state -> cfg to state }
                }
                .collectAsState(initial = initialCfg to initialState)
            val (cfg, state) = viewState
            GlanceTheme {
                val isWide = LocalSize.current.width >= 110.dp
                if (cfg == null) {
                    UnconfiguredWidgetContent(context, appWidgetId, SensorWidgetConfigActivity::class.java, R.drawable.ic_sensor)
                } else {
                    SensorContent(cfg, state, isWide)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SensorContent(
    config: EntityWidgetEntity,
    state: EntityStateEntity?,
    isWide: Boolean,
) {
    val isUnavailable = state?.state == "unavailable"
    val isStale = state == null || (System.currentTimeMillis() - state.lastUpdated) > STALE_THRESHOLD_MS

    val contentColor = if (isUnavailable) GlanceTheme.colors.onErrorContainer
                       else GlanceTheme.colors.onSurfaceVariant
    val bgColor = if (isUnavailable) GlanceTheme.colors.errorContainer
                  else GlanceTheme.colors.surfaceVariant

    val attrs = state?.let { parseSensorAttrs(it.attributesJson) }
    val label = config.label.ifEmpty { attrs?.friendlyName ?: config.entityId }
    val statusBase = when {
        state == null -> "Henter…"
        isUnavailable -> "Utilgængelig"
        else -> buildSensorValue(state.state, attrs?.unit)
    }
    val statusText = if (isStale && state != null) "$statusBase ~" else statusBase
    val iconRes = iconForDeviceClass(attrs?.deviceClass)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(16.dp)
            .clickable(actionRunCallback<RefreshEntityAction>(
                actionParametersOf(RefreshEntityAction.entityIdKey to config.entityId)
            )),
        contentAlignment = Alignment.Center,
    ) {
        if (isWide) WidgetWideLayout(iconRes, label, statusText, contentColor)
        else WidgetCompactLayout(iconRes, label, statusText, contentColor)
    }
}

private data class SensorAttrs(val unit: String?, val friendlyName: String?, val deviceClass: String?)

private fun parseSensorAttrs(attributesJson: String): SensorAttrs =
    try {
        val obj = JSONObject(attributesJson)
        SensorAttrs(
            unit = obj.optString("unit_of_measurement").ifEmpty { null },
            friendlyName = obj.optString("friendly_name").ifEmpty { null },
            deviceClass = obj.optString("device_class").ifEmpty { null },
        )
    } catch (_: Exception) { SensorAttrs(null, null, null) }

private fun iconForDeviceClass(deviceClass: String?): Int = when (deviceClass) {
    "temperature", "apparent_temperature" -> R.drawable.ic_thermometer
    "humidity", "moisture" -> R.drawable.ic_humidity
    "power", "energy", "current", "voltage", "apparent_power",
    "reactive_power", "frequency", "battery" -> R.drawable.ic_power
    else -> R.drawable.ic_sensor
}

private fun buildSensorValue(state: String, unit: String?): String =
    if (unit != null) "$state $unit" else state

class SensorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SensorWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
