package dk.akait.hawidgets.widget.light

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import dk.akait.hawidgets.worker.SyncWorker
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.appwidget.SizeMode
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.EntityRepository
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.EntityStateEntity
import dk.akait.hawidgets.data.db.EntityWidgetEntity
import org.json.JSONObject

private const val STALE_THRESHOLD_MS = 15 * 60 * 1000L

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
        val widgetCfg = db.entityWidgetDao().get(appWidgetId)
        val entityState = widgetCfg?.let { db.entityStateDao().get(it.entityId) }

        provideContent {
            GlanceTheme {
                val isWide = LocalSize.current.width >= 110.dp
                if (widgetCfg == null) {
                    UnconfiguredContent(context, appWidgetId, isWide)
                } else {
                    LightContent(context, appWidgetId, widgetCfg, entityState, isWide)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun UnconfiguredContent(context: Context, appWidgetId: Int, isWide: Boolean) {
    val intent = Intent(context, LightWidgetConfigActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                provider = ImageProvider(R.drawable.ic_lightbulb),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            )
            Text(
                text = "Opsæt",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            )
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
        isOn -> GlanceTheme.colors.primaryContainer
        else -> GlanceTheme.colors.surfaceVariant
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        isOn -> GlanceTheme.colors.onPrimaryContainer
        else -> GlanceTheme.colors.onSurfaceVariant
    }

    val attrs = state?.let { parseLightAttrs(it.attributesJson) }
    val label = config.label.ifEmpty { attrs?.friendlyName ?: config.entityId }
    val statusText = buildStatusText(state, attrs, isStale)

    val baseModifier = GlanceModifier
        .fillMaxSize()
        .background(bgColor)
        .cornerRadius(16.dp)

    // Tap tilladt så længe state er kendt (også stale): trykket sender en frisk kommando,
    // som enten lykkes (frisk state) eller fejler (forbliver stale). Kun helt ukendt/
    // unavailable blokerer — ellers kan en widget der engang fejlede aldrig selv-hele.
    val modifier = if (state != null && !isUnavailable) {
        baseModifier.clickable(
            actionRunCallback<ToggleLightAction>(
                actionParametersOf(ToggleLightAction.entityIdKey to config.entityId)
            )
        )
    } else baseModifier

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isWide) {
            WideLayout(label, statusText, contentColor)
        } else {
            CompactLayout(label, statusText, contentColor)
        }
    }
}

@androidx.compose.runtime.Composable
private fun CompactLayout(label: String, statusText: String, contentColor: androidx.glance.unit.ColorProvider) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_lightbulb),
            contentDescription = label,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
        Text(
            text = label,
            style = TextStyle(color = contentColor, fontSize = 10.sp),
            maxLines = 1,
        )
        Text(
            text = statusText,
            style = TextStyle(color = contentColor, fontSize = 10.sp),
            maxLines = 1,
        )
    }
}

@androidx.compose.runtime.Composable
private fun WideLayout(label: String, statusText: String, contentColor: androidx.glance.unit.ColorProvider) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_lightbulb),
            contentDescription = label,
            modifier = GlanceModifier.size(28.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = label,
                style = TextStyle(
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = statusText,
                style = TextStyle(color = contentColor, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

private fun buildStatusText(
    state: EntityStateEntity?,
    attrs: LightAttrs?,
    isStale: Boolean,
): String {
    val base = when {
        state == null -> "…"
        state.state == "unavailable" -> "Utilgængelig"
        state.state == "on" && attrs?.brightnessPercent != null -> "${attrs.brightnessPercent}%"
        state.state == "on" -> "Tændt"
        else -> "Slukket"
    }
    return if (isStale && state != null) "$base ~" else base
}

private data class LightAttrs(val brightnessPercent: Int?, val friendlyName: String?)

private fun parseLightAttrs(attributesJson: String): LightAttrs {
    return try {
        val attrs = JSONObject(attributesJson)
        val b = attrs.optInt("brightness", -1).takeIf { it >= 0 }
        LightAttrs(
            brightnessPercent = b?.let { (it * 100 / 255).coerceIn(0, 100) },
            friendlyName = attrs.optString("friendly_name").ifEmpty { null },
        )
    } catch (_: Exception) {
        LightAttrs(null, null)
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
        // turn_on/turn_off (ikke toggle): resultatet er kendt → optimistisk state ER sandheden,
        // ingen confirm-poll nødvendig. Ét hurtigt kald, godt under broadcast-ANR-grænsen.
        val service = if (target == "on") "turn_on" else "turn_off"

        // Tryk vækker appen straks (pålideligt, modsat baggrunds-WorkManager).
        // Optimistisk lokalt + kort netværkskald direkte her i broadcast-vinduet.
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

    // Kaldt af systemet efter config-RESULT_OK og ved eventuelle system-udløste opdateringer.
    // super.onUpdate → Glance updateAll → provideGlance (læser Room, viser config/"…").
    // SyncWorker.runNow henter state fra HA og fan-outer til widget — bypasser Glance's
    // interne async scheduler der kan forsinkes 30-60 sek på Nova/Samsung.
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
