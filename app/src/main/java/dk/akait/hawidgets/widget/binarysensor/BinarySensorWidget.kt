package dk.akait.hawidgets.widget.binarysensor

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

class BinarySensorWidget : GlanceAppWidget() {

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
                    UnconfiguredWidgetContent(context, appWidgetId, BinarySensorWidgetConfigActivity::class.java, R.drawable.ic_binary_sensor)
                } else {
                    BinarySensorContent(cfg, state, isWide)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun BinarySensorContent(
    config: EntityWidgetEntity,
    state: EntityStateEntity?,
    isWide: Boolean,
) {
    val isActive = state?.state == "on"
    val isUnavailable = state?.state == "unavailable"
    val isStale = state == null || (System.currentTimeMillis() - state.lastUpdated) > STALE_THRESHOLD_MS

    val attrs = state?.let { parseBinaryAttrs(it.attributesJson) }

    // Alert device classes use errorContainer when active
    val isAlertClass = attrs?.deviceClass in ALERT_CLASSES
    val bgColor = when {
        isUnavailable -> GlanceTheme.colors.errorContainer
        isActive && isAlertClass -> GlanceTheme.colors.errorContainer
        isActive -> GlanceTheme.colors.primaryContainer
        else -> GlanceTheme.colors.surfaceVariant
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        isActive && isAlertClass -> GlanceTheme.colors.onErrorContainer
        isActive -> GlanceTheme.colors.onPrimaryContainer
        else -> GlanceTheme.colors.onSurfaceVariant
    }

    val label = config.label.ifEmpty { attrs?.friendlyName ?: config.entityId }
    val statusBase = when {
        state == null -> "Henter…"
        isUnavailable -> "Utilgængelig"
        else -> stateLabel(attrs?.deviceClass, isActive)
    }
    val statusText = if (isStale && state != null) "$statusBase ~" else statusBase

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
        if (isWide) WidgetWideLayout(R.drawable.ic_binary_sensor, label, statusText, contentColor)
        else WidgetCompactLayout(R.drawable.ic_binary_sensor, label, statusText, contentColor)
    }
}

private data class BinaryAttrs(val deviceClass: String?, val friendlyName: String?)

private fun parseBinaryAttrs(attributesJson: String): BinaryAttrs =
    try {
        val obj = JSONObject(attributesJson)
        BinaryAttrs(
            deviceClass = obj.optString("device_class").ifEmpty { null },
            friendlyName = obj.optString("friendly_name").ifEmpty { null },
        )
    } catch (_: Exception) { BinaryAttrs(null, null) }

private val ALERT_CLASSES = setOf("smoke", "gas", "carbon_monoxide", "carbon_dioxide", "problem", "safety", "tamper")

private fun stateLabel(deviceClass: String?, isActive: Boolean): String = when (deviceClass) {
    "motion", "occupancy", "presence" -> if (isActive) "Bevægelse" else "Ro"
    "door", "garage_door", "lock" -> if (isActive) "Åben" else "Lukket"
    "window" -> if (isActive) "Åben" else "Lukket"
    "battery" -> if (isActive) "Lav batteri" else "OK"
    "battery_charging" -> if (isActive) "Oplader" else "Oplader ikke"
    "cold" -> if (isActive) "Koldt" else "OK"
    "connectivity" -> if (isActive) "Forbundet" else "Ikke forbundet"
    "heat" -> if (isActive) "Varmt" else "OK"
    "light" -> if (isActive) "Lyst" else "Mørkt"
    "moisture", "rain" -> if (isActive) "Vådt" else "Tørt"
    "plug", "power" -> if (isActive) "Aktiv" else "Inaktiv"
    "running" -> if (isActive) "Kører" else "Stoppet"
    "smoke", "gas", "carbon_monoxide", "carbon_dioxide" -> if (isActive) "Alarm!" else "OK"
    "sound", "vibration" -> if (isActive) "Registreret" else "Ro"
    "tamper", "problem", "safety" -> if (isActive) "Problem" else "OK"
    else -> if (isActive) "Aktiv" else "Inaktiv"
}

class BinarySensorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BinarySensorWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
