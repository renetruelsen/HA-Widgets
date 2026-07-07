package dk.akait.hawidgets.widget.light

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.EntityRepository
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.EntityStateEntity
import dk.akait.hawidgets.data.db.EntityWidgetEntity
import dk.akait.hawidgets.widget.common.RangeControlActivity
import dk.akait.hawidgets.widget.common.STALE_THRESHOLD_MS
import dk.akait.hawidgets.widget.common.UnconfiguredWidgetContent
import dk.akait.hawidgets.widget.common.WidgetGlanceTheme
import dk.akait.hawidgets.widget.common.WidgetCompactLayout
import dk.akait.hawidgets.widget.common.WidgetWideLayout
import dk.akait.hawidgets.worker.SyncWorker
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class LightWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(56.dp, 56.dp),   // 1×1
            DpSize(110.dp, 56.dp),  // 2×1
        )
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
                    if (cfg == null) {
                        flowOf<Pair<EntityWidgetEntity?, EntityStateEntity?>>(null to null)
                    } else {
                        db.entityStateDao().observe(cfg.entityId)
                            .map { state -> cfg to state }
                    }
                }
                .collectAsState(initial = initialCfg to initialState)
            val (widgetCfg, entityState) = viewState
            Log.d("HA_WIDGET", "recompose: appWidgetId=$appWidgetId cfg=${widgetCfg?.entityId} state=${entityState?.state}")

            WidgetGlanceTheme(context) {
                val isWide = LocalSize.current.width >= 110.dp
                if (widgetCfg == null) {
                    UnconfiguredWidgetContent(context, appWidgetId, LightWidgetConfigActivity::class.java, R.drawable.ic_lightbulb)
                } else {
                    LightContent(context, appWidgetId, widgetCfg, entityState, isWide)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun LightContent(
    context: Context,
    appWidgetId: Int,
    config: EntityWidgetEntity,
    state: EntityStateEntity?,
    isWide: Boolean,
) {
    val isOn = state?.state == "on"
    val isUnavailable = state?.state == "unavailable"
    val isStale = state == null ||
        (System.currentTimeMillis() - state.lastUpdated) > STALE_THRESHOLD_MS

    val bgColor = when {
        isUnavailable -> GlanceTheme.colors.errorContainer
        isOn -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.surfaceVariant
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        isOn -> GlanceTheme.colors.onPrimary
        else -> GlanceTheme.colors.onSurfaceVariant
    }

    val attrs = state?.let { parseLightAttrs(it.attributesJson) }
    val label = config.label.ifEmpty { attrs?.friendlyName ?: config.entityId }
    val statusText = buildStatusText(context, state, attrs, isStale)

    val baseModifier = GlanceModifier
        .fillMaxSize()
        .background(bgColor)
        .cornerRadius(16.dp)

    val toggleModifier = if (state != null && !isUnavailable) {
        baseModifier.clickable(
            actionRunCallback<ToggleLightAction>(
                actionParametersOf(ToggleLightAction.entityIdKey to config.entityId)
            )
        )
    } else baseModifier

    // Dimmable lights: tap opens brightness slider on both compact and wide.
    // Non-dimmable lights: tap toggles on/off.
    val controlIntent = Intent(context, RangeControlActivity::class.java).apply {
        putExtra(RangeControlActivity.EXTRA_ENTITY_ID, config.entityId)
        putExtra(RangeControlActivity.EXTRA_LABEL, label)
        putExtra(RangeControlActivity.EXTRA_DOMAIN, "light")
        putExtra(RangeControlActivity.EXTRA_CURRENT_VALUE, attrs?.brightnessPercent ?: 100)
        putExtra(RangeControlActivity.EXTRA_IS_ON, isOn)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val dimmerModifier = if (state != null && !isUnavailable) {
        baseModifier.clickable(actionStartActivity(controlIntent))
    } else baseModifier

    val activeModifier = if (state != null && !isUnavailable) {
        if (attrs?.supportsBrightness == true) dimmerModifier else toggleModifier
    } else baseModifier

    Box(
        modifier = activeModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (isWide) {
            WidgetWideLayout(R.drawable.ic_lightbulb, label, statusText, contentColor)
        } else {
            WidgetCompactLayout(R.drawable.ic_lightbulb, label, statusText, contentColor)
        }
    }
}

private fun buildStatusText(
    context: Context,
    state: EntityStateEntity?,
    attrs: LightAttrs?,
    isStale: Boolean,
): String {
    val base = when {
        state == null -> context.getString(R.string.state_loading_status)
        state.state == "unavailable" -> context.getString(R.string.state_unavailable)
        state.state == "on" && attrs?.brightnessPercent != null -> "${attrs.brightnessPercent}%"
        state.state == "on" -> context.getString(R.string.state_on)
        else -> context.getString(R.string.state_off)
    }
    return if (isStale && state != null) "$base ~" else base
}

private data class LightAttrs(
    val brightnessPercent: Int?,
    val friendlyName: String?,
    val supportsBrightness: Boolean,
)

private fun parseLightAttrs(attributesJson: String): LightAttrs {
    return try {
        val attrs = JSONObject(attributesJson)
        val b = attrs.optInt("brightness", -1).takeIf { it >= 0 }
        val supportedFeatures = attrs.optInt("supported_features", 0)
        // Modern HA uses supported_color_modes instead of supported_features bit 1.
        // Any mode other than "onoff" means the light supports brightness control.
        val supportedColorModes = attrs.optJSONArray("supported_color_modes")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()
        val dimmableByColorMode = supportedColorModes.any { it != "onoff" && it.isNotEmpty() }
        LightAttrs(
            brightnessPercent = b?.let { (it * 100 / 255).coerceIn(0, 100) },
            friendlyName = attrs.optString("friendly_name").ifEmpty { null },
            supportsBrightness = (supportedFeatures and 1) != 0 || b != null || dimmableByColorMode,
        )
    } catch (_: Exception) {
        LightAttrs(null, null, false)
    }
}

class ToggleLightAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val entityId = parameters[entityIdKey] ?: return
        val current = AppDatabase.get(context).entityStateDao().get(entityId) ?: return
        val target = if (current.state == "on") "off" else "on"
        val service = if (target == "on") "turn_on" else "turn_off"
        EntityRepository.command(
            context = context,
            domain = "light",
            service = service,
            entityId = entityId,
            targetState = target,
            fromState = current.state,
        )
    }

    companion object {
        val entityIdKey = ActionParameters.Key<String>("entityId")
    }
}

class LightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LightWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
