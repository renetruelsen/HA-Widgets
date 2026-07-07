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
import dk.akait.hawidgets.widget.common.WidgetGlanceTheme
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
            WidgetGlanceTheme(context) {
                val isWide = LocalSize.current.width >= 110.dp
                if (cfg == null) {
                    UnconfiguredWidgetContent(context, appWidgetId, BinarySensorWidgetConfigActivity::class.java, R.drawable.ic_binary_sensor)
                } else {
                    BinarySensorContent(context, cfg, state, isWide)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun BinarySensorContent(
    context: Context,
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
        isActive -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.surfaceVariant
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        isActive && isAlertClass -> GlanceTheme.colors.onErrorContainer
        isActive -> GlanceTheme.colors.onPrimary
        else -> GlanceTheme.colors.onSurfaceVariant
    }

    val label = config.label.ifEmpty { attrs?.friendlyName ?: config.entityId }
    val statusBase = when {
        state == null -> context.getString(R.string.state_loading)
        isUnavailable -> context.getString(R.string.state_unavailable)
        else -> stateLabel(context, attrs?.deviceClass, isActive)
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

private fun stateLabel(context: Context, deviceClass: String?, isActive: Boolean): String {
    fun s(resId: Int) = context.getString(resId)
    return when (deviceClass) {
        "motion", "occupancy", "presence" -> s(if (isActive) R.string.binary_motion else R.string.binary_clear)
        "door", "garage_door", "lock" -> s(if (isActive) R.string.state_open else R.string.state_closed)
        "window" -> s(if (isActive) R.string.state_open else R.string.state_closed)
        "battery" -> s(if (isActive) R.string.binary_low_battery else R.string.binary_ok)
        "battery_charging" -> s(if (isActive) R.string.binary_charging else R.string.binary_not_charging)
        "cold" -> s(if (isActive) R.string.binary_cold else R.string.binary_ok)
        "connectivity" -> s(if (isActive) R.string.binary_connected else R.string.binary_not_connected)
        "heat" -> s(if (isActive) R.string.binary_hot else R.string.binary_ok)
        "light" -> s(if (isActive) R.string.binary_light else R.string.binary_dark)
        "moisture", "rain" -> s(if (isActive) R.string.binary_wet else R.string.binary_dry)
        "plug", "power" -> s(if (isActive) R.string.state_active else R.string.state_inactive)
        "running" -> s(if (isActive) R.string.state_running else R.string.binary_stopped)
        "smoke", "gas", "carbon_monoxide", "carbon_dioxide" -> s(if (isActive) R.string.binary_alarm else R.string.binary_ok)
        "sound", "vibration" -> s(if (isActive) R.string.binary_detected else R.string.binary_clear)
        "tamper", "problem", "safety" -> s(if (isActive) R.string.binary_problem else R.string.binary_ok)
        else -> s(if (isActive) R.string.state_active else R.string.state_inactive)
    }
}

class BinarySensorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BinarySensorWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
