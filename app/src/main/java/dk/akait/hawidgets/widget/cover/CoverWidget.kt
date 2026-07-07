package dk.akait.hawidgets.widget.cover

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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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

class CoverWidget : GlanceAppWidget() {

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
                    UnconfiguredWidgetContent(context, appWidgetId, CoverWidgetConfigActivity::class.java, R.drawable.ic_cover)
                } else {
                    CoverContent(context, cfg, state, isWide)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun CoverContent(
    context: Context,
    config: EntityWidgetEntity,
    state: EntityStateEntity?,
    isWide: Boolean,
) {
    val coverState = state?.state ?: ""
    val isOpen = coverState in listOf("open", "opening")
    val isUnavailable = coverState == "unavailable"
    val isStale = state == null || (System.currentTimeMillis() - state.lastUpdated) > STALE_THRESHOLD_MS

    val bgColor = when {
        isUnavailable -> GlanceTheme.colors.errorContainer
        isOpen -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.surfaceVariant
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        isOpen -> GlanceTheme.colors.onPrimary
        else -> GlanceTheme.colors.onSurfaceVariant
    }

    val attrs = state?.let { parseCoverAttrs(it.attributesJson) }
    val label = config.label.ifEmpty { attrs?.friendlyName ?: config.entityId }
    val statusBase = when {
        state == null -> context.getString(R.string.state_loading)
        isUnavailable -> context.getString(R.string.state_unavailable)
        coverState == "opening" -> context.getString(R.string.state_opening)
        coverState == "closing" -> context.getString(R.string.state_closing)
        attrs?.position != null -> "${attrs.position}%"
        coverState == "open" -> context.getString(R.string.state_open)
        else -> context.getString(R.string.state_closed)
    }
    val statusText = if (isStale && state != null) "$statusBase ~" else statusBase

    val controlIntent = Intent(context, RangeControlActivity::class.java).apply {
        putExtra(RangeControlActivity.EXTRA_ENTITY_ID, config.entityId)
        putExtra(RangeControlActivity.EXTRA_LABEL, label)
        putExtra(RangeControlActivity.EXTRA_DOMAIN, "cover")
        putExtra(RangeControlActivity.EXTRA_CURRENT_VALUE, attrs?.position ?: if (isOpen) 100 else 0)
        putExtra(RangeControlActivity.EXTRA_IS_ON, isOpen)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val modifier = if (state != null && !isUnavailable) {
        GlanceModifier.fillMaxSize().background(bgColor).cornerRadius(16.dp)
            .clickable(actionStartActivity(controlIntent))
    } else {
        GlanceModifier.fillMaxSize().background(bgColor).cornerRadius(16.dp)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isWide) WidgetWideLayout(R.drawable.ic_cover, label, statusText, contentColor)
        else WidgetCompactLayout(R.drawable.ic_cover, label, statusText, contentColor)
    }
}

private data class CoverAttrs(val position: Int?, val friendlyName: String?)

private fun parseCoverAttrs(attributesJson: String): CoverAttrs =
    try {
        val obj = JSONObject(attributesJson)
        CoverAttrs(
            position = obj.optInt("current_position", -1).takeIf { it >= 0 },
            friendlyName = obj.optString("friendly_name").ifEmpty { null },
        )
    } catch (_: Exception) { CoverAttrs(null, null) }

class CoverWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CoverWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
