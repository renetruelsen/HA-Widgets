package dk.akait.hawidgets.widget.climate

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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
import dk.akait.hawidgets.widget.common.RangeControlActivity
import dk.akait.hawidgets.widget.common.RefreshEntityAction
import dk.akait.hawidgets.widget.common.STALE_THRESHOLD_MS
import dk.akait.hawidgets.widget.common.UnconfiguredWidgetContent
import dk.akait.hawidgets.widget.common.WidgetCompactLayout
import dk.akait.hawidgets.widget.common.WidgetWideLayout
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
                    UnconfiguredWidgetContent(context, appWidgetId, ClimateWidgetConfigActivity::class.java, R.drawable.ic_climate)
                } else {
                    ClimateContent(context, cfg, state, isWide)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ClimateContent(
    context: Context,
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
        else -> GlanceTheme.colors.primary
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        isOff -> GlanceTheme.colors.onSurfaceVariant
        else -> GlanceTheme.colors.onPrimary
    }

    val attrs = state?.let { parseClimateAttrs(it.attributesJson) }
    val entityName = config.label.ifEmpty { attrs?.friendlyName ?: config.entityId }

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

    // Compact status includes current temperature when available
    val compactStatus = when {
        state == null -> "Henter…"
        isUnavailable -> "Utilgængelig"
        else -> {
            val curTemp = attrs?.currentTemp?.roundToInt()
            if (curTemp != null) "$curTemp° $modeStatus" else modeStatus
        }
    }
    val compactStatusStale = if (isStale && state != null) "$compactStatus ~" else compactStatus

    val baseModifier = GlanceModifier
        .fillMaxSize()
        .background(bgColor)
        .cornerRadius(16.dp)

    // Wide: tap opens temperature setpoint slider.
    val controlIntent = Intent(context, RangeControlActivity::class.java).apply {
        putExtra(RangeControlActivity.EXTRA_ENTITY_ID, config.entityId)
        putExtra(RangeControlActivity.EXTRA_LABEL, entityName)
        putExtra(RangeControlActivity.EXTRA_DOMAIN, "climate")
        putExtra(RangeControlActivity.EXTRA_CURRENT_VALUE, attrs?.targetTemp?.roundToInt() ?: 20)
        putExtra(RangeControlActivity.EXTRA_IS_ON, !isOff && !isUnavailable)
        putExtra(RangeControlActivity.EXTRA_MIN_VALUE, attrs?.minTemp?.roundToInt() ?: 16)
        putExtra(RangeControlActivity.EXTRA_MAX_VALUE, attrs?.maxTemp?.roundToInt() ?: 30)
        attrs?.currentTemp?.roundToInt()?.let { putExtra(RangeControlActivity.EXTRA_ACTUAL_TEMP, it) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val wideModifier = if (state != null && !isUnavailable) {
        baseModifier.clickable(actionStartActivity(controlIntent))
    } else baseModifier

    // Compact: tap refreshes this entity only.
    val compactModifier = if (state != null && !isUnavailable) {
        baseModifier.clickable(
            actionRunCallback<RefreshEntityAction>(
                actionParametersOf(RefreshEntityAction.entityIdKey to config.entityId)
            )
        )
    } else baseModifier

    Box(
        modifier = if (isWide) wideModifier else compactModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (isWide) WidgetWideLayout(R.drawable.ic_climate, tempLabel, modeStatusStale, contentColor)
        else WidgetCompactLayout(R.drawable.ic_climate, entityName, compactStatusStale, contentColor)
    }
}

private data class ClimateAttrs(
    val currentTemp: Double?,
    val targetTemp: Double?,
    val minTemp: Double?,
    val maxTemp: Double?,
    val unit: String,
    val friendlyName: String?,
)

private fun parseClimateAttrs(attributesJson: String): ClimateAttrs =
    try {
        val obj = JSONObject(attributesJson)
        ClimateAttrs(
            currentTemp = obj.optDouble("current_temperature").takeIf { !it.isNaN() },
            targetTemp = obj.optDouble("temperature").takeIf { !it.isNaN() },
            minTemp = obj.optDouble("min_temp").takeIf { !it.isNaN() },
            maxTemp = obj.optDouble("max_temp").takeIf { !it.isNaN() },
            unit = obj.optString("temperature_unit").ifEmpty { "°C" },
            friendlyName = obj.optString("friendly_name").ifEmpty { null },
        )
    } catch (_: Exception) { ClimateAttrs(null, null, null, null, "°C", null) }

/** Temperature label for wide layout: "21° / 22°C" (current / setpoint). */
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
