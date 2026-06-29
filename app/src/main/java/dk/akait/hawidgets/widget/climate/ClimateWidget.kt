package dk.akait.hawidgets.widget.climate

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
import kotlin.math.roundToInt

class ClimateWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(56.dp, 56.dp), DpSize(110.dp, 56.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val db = AppDatabase.get(context)
        provideContent {
            val viewState by db.entityWidgetDao()
                .observe(appWidgetId)
                .flatMapLatest { cfg ->
                    if (cfg == null) flowOf(null to null)
                    else db.entityStateDao().observe(cfg.entityId).map { state -> cfg to state }
                }
                .collectAsState(initial = null to null)
            val (cfg, state) = viewState
            GlanceTheme {
                val isWide = LocalSize.current.width >= 110.dp
                if (cfg == null) {
                    UnconfiguredWidgetContent(context, appWidgetId, ClimateWidgetConfigActivity::class.java, R.drawable.ic_climate)
                } else {
                    ClimateContent(cfg, state, isWide)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ClimateContent(
    config: EntityWidgetEntity,
    state: EntityStateEntity?,
    isWide: Boolean,
) {
    val hvacMode = state?.state ?: ""
    val isOff = hvacMode == "off"
    val isUnavailable = hvacMode == "unavailable"
    val isStale = state == null || (System.currentTimeMillis() - state.lastUpdated) > STALE_THRESHOLD_MS

    val bgColor = when {
        isUnavailable -> GlanceTheme.colors.errorContainer
        isOff -> GlanceTheme.colors.surfaceVariant
        else -> GlanceTheme.colors.primaryContainer
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        isOff -> GlanceTheme.colors.onSurfaceVariant
        else -> GlanceTheme.colors.onPrimaryContainer
    }

    val attrs = state?.let { parseClimateAttrs(it.attributesJson) }
    val entityName = config.label.ifEmpty { attrs?.friendlyName ?: config.entityId }

    // Wide layout: label=temp display, status=hvac mode — avoids long single-line truncation.
    // Compact: show mode label (most actionable info at a glance).
    val tempLabel = when {
        state == null || isUnavailable -> entityName
        else -> buildTempLabel(attrs) ?: entityName
    }
    val modeStatus = when {
        state == null -> "Henter…"
        isUnavailable -> "Utilgængelig"
        else -> hvacModeLabel(hvacMode)
    }
    val modeStatusStale = if (isStale && state != null) "$modeStatus ~" else modeStatus

    // Compact (1×1): icon + mode label (e.g. "Opvarmning"). Entity name already known by user.
    val compactStatus = modeStatusStale

    // Tap = manual refresh
    val modifier = GlanceModifier
        .fillMaxSize()
        .background(bgColor)
        .cornerRadius(16.dp)
        .clickable(actionRunCallback<RefreshEntityAction>())

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isWide) WidgetWideLayout(R.drawable.ic_climate, tempLabel, modeStatusStale, contentColor)
        else WidgetCompactLayout(R.drawable.ic_climate, entityName, compactStatus, contentColor)
    }
}

private data class ClimateAttrs(
    val currentTemp: Double?,
    val targetTemp: Double?,
    val unit: String,
    val friendlyName: String?,
)

private fun parseClimateAttrs(attributesJson: String): ClimateAttrs =
    try {
        val obj = JSONObject(attributesJson)
        ClimateAttrs(
            currentTemp = obj.optDouble("current_temperature").takeIf { !it.isNaN() },
            targetTemp = obj.optDouble("temperature").takeIf { !it.isNaN() },
            unit = obj.optString("temperature_unit").ifEmpty { "°C" },
            friendlyName = obj.optString("friendly_name").ifEmpty { null },
        )
    } catch (_: Exception) { ClimateAttrs(null, null, "°C", null) }

/** Temperature label for wide layout's label slot: "21° / 22°C" (current / setpoint). */
private fun buildTempLabel(attrs: ClimateAttrs?): String? {
    if (attrs == null) return null
    val cur = attrs.currentTemp?.roundToInt()
    val tgt = attrs.targetTemp?.roundToInt()
    val u = attrs.unit
    return when {
        cur != null && tgt != null -> "$cur° / $tgt$u"
        cur != null -> "$cur$u"
        tgt != null -> "→ $tgt$u"
        else -> null
    }
}

private fun hvacModeLabel(mode: String): String = when (mode) {
    "heat" -> "Opvarmning"
    "cool" -> "Køling"
    "heat_cool" -> "Auto"
    "auto" -> "Auto"
    "dry" -> "Affugtning"
    "fan_only" -> "Ventilator"
    "off" -> "Slukket"
    else -> mode
}

class ClimateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClimateWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
