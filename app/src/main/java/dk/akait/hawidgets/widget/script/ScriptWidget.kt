package dk.akait.hawidgets.widget.script

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
import androidx.glance.action.actionParametersOf
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
import dk.akait.hawidgets.widget.common.STALE_THRESHOLD_MS
import dk.akait.hawidgets.widget.common.TriggerEntityAction
import dk.akait.hawidgets.widget.common.UnconfiguredWidgetContent
import dk.akait.hawidgets.widget.common.WidgetGlanceTheme
import dk.akait.hawidgets.widget.common.WidgetCompactLayout
import dk.akait.hawidgets.widget.common.WidgetWideLayout
import dk.akait.hawidgets.widget.common.friendlyNameFromJson
import dk.akait.hawidgets.worker.SyncWorker
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ScriptWidget : GlanceAppWidget() {

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
                    UnconfiguredWidgetContent(context, appWidgetId, ScriptWidgetConfigActivity::class.java, R.drawable.ic_script)
                } else {
                    ScriptContent(context, cfg, state, isWide)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ScriptContent(
    context: Context,
    config: EntityWidgetEntity,
    state: EntityStateEntity?,
    isWide: Boolean,
) {
    val isRunning = state?.state == "on"
    val isUnavailable = state?.state == "unavailable"
    val isStale = state == null || (System.currentTimeMillis() - state.lastUpdated) > STALE_THRESHOLD_MS

    // Running: surfaceVariant (not primaryContainer) — disabled tap should NOT look active/inviting.
    val bgColor = when {
        isUnavailable -> GlanceTheme.colors.errorContainer
        else -> GlanceTheme.colors.surfaceVariant
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        else -> GlanceTheme.colors.onSurfaceVariant
    }

    val friendlyName = state?.let { friendlyNameFromJson(it.attributesJson) }
    val label = config.label.ifEmpty { friendlyName ?: config.entityId }
    val statusBase = when {
        state == null -> context.getString(R.string.state_loading_status)
        isUnavailable -> context.getString(R.string.state_unavailable)
        isRunning -> context.getString(R.string.state_running_ellipsis)
        else -> context.getString(R.string.state_ready)
    }
    val statusText = if (isStale && state != null) "$statusBase ~" else statusBase

    // Don't allow re-triggering while already running — avoids unintended queued runs.
    val baseModifier = GlanceModifier.fillMaxSize().background(bgColor).cornerRadius(16.dp)
    val modifier = if (state != null && !isUnavailable && !isRunning) {
        baseModifier.clickable(
            actionRunCallback<TriggerEntityAction>(
                actionParametersOf(
                    TriggerEntityAction.entityIdKey to config.entityId,
                    TriggerEntityAction.domainKey to "script",
                    TriggerEntityAction.serviceKey to "turn_on",
                    TriggerEntityAction.targetStateKey to "on",
                )
            )
        )
    } else baseModifier

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isWide) WidgetWideLayout(R.drawable.ic_script, label, statusText, contentColor)
        else WidgetCompactLayout(R.drawable.ic_script, label, statusText, contentColor)
    }
}

class ScriptWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScriptWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
