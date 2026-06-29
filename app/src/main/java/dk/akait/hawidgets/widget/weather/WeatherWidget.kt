package dk.akait.hawidgets.widget.weather

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

class WeatherWidget : GlanceAppWidget() {

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
                    UnconfiguredWidgetContent(context, appWidgetId, WeatherWidgetConfigActivity::class.java, R.drawable.ic_weather)
                } else {
                    WeatherContent(cfg, state, isWide)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WeatherContent(
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

    val attrs = state?.let { parseWeatherAttrs(it.attributesJson) }
    val label = config.label.ifEmpty { attrs?.friendlyName ?: config.entityId }
    // Compact (1×1): temp only — condition text too long to fit.
    // Wide (2×1): full "temp • condition" status.
    val compactStatusBase = when {
        state == null -> "Henter…"
        isUnavailable -> "Utilgæng."
        else -> attrs?.temperature?.let { "${it.roundToInt()}${attrs.temperatureUnit}" } ?: state.state
    }
    val wideStatusBase = when {
        state == null -> "Henter…"
        isUnavailable -> "Utilgængelig"
        else -> buildWeatherStatus(state.state, attrs)
    }
    val compactStatus = if (isStale && state != null) "$compactStatusBase ~" else compactStatusBase
    val wideStatus = if (isStale && state != null) "$wideStatusBase ~" else wideStatusBase

    // Tap = manual refresh (user expects this when seeing stale data)
    val modifier = GlanceModifier
        .fillMaxSize()
        .background(bgColor)
        .cornerRadius(16.dp)
        .clickable(actionRunCallback<RefreshEntityAction>())

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isWide) WidgetWideLayout(R.drawable.ic_weather, label, wideStatus, contentColor)
        else WidgetCompactLayout(R.drawable.ic_weather, label, compactStatus, contentColor)
    }
}

private data class WeatherAttrs(
    val temperature: Double?,
    val temperatureUnit: String,
    val friendlyName: String?,
)

private fun parseWeatherAttrs(attributesJson: String): WeatherAttrs =
    try {
        val obj = JSONObject(attributesJson)
        WeatherAttrs(
            temperature = obj.optDouble("temperature").takeIf { !it.isNaN() },
            temperatureUnit = obj.optString("temperature_unit").ifEmpty { "°C" },
            friendlyName = obj.optString("friendly_name").ifEmpty { null },
        )
    } catch (_: Exception) { WeatherAttrs(null, "°C", null) }

private fun buildWeatherStatus(condition: String, attrs: WeatherAttrs?): String {
    val condText = conditionLabel(condition)
    val tempText = attrs?.temperature?.let { "${it.roundToInt()}${attrs.temperatureUnit}" }
    return if (tempText != null) "$tempText • $condText" else condText
}

private fun conditionLabel(condition: String): String = when (condition) {
    "sunny", "clear-night" -> "Solrigt"
    "partlycloudy" -> "Delvist skyet"
    "cloudy" -> "Skyet"
    "rainy" -> "Regn"
    "pouring" -> "Kraftig regn"
    "snowy" -> "Sne"
    "snowy-rainy" -> "Slud"
    "hail" -> "Hagl"
    "lightning" -> "Torden"
    "lightning-rainy" -> "Tordenbyge"
    "windy" -> "Blæsende"
    "windy-variant" -> "Blæsende"
    "fog" -> "Tåge"
    "exceptional" -> "Ekstremt"
    else -> condition
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
