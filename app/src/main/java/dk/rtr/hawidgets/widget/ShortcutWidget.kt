package dk.rtr.hawidgets.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
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
import dk.rtr.hawidgets.R
import dk.rtr.hawidgets.data.WidgetConfig
import dk.rtr.hawidgets.data.WidgetConfigStore
import dk.rtr.hawidgets.web.WebViewActivity
import dk.rtr.hawidgets.widget.common.UnconfiguredWidgetContent
import dk.rtr.hawidgets.widget.common.WidgetGlanceTheme
import dk.rtr.hawidgets.widget.common.WidgetCompactLayout
import dk.rtr.hawidgets.widget.common.WidgetWideLayout

/**
 * A home-screen tile that opens a configured HA dashboard on tap. If not yet configured
 * (e.g. pinned via the in-app button), tapping opens the config screen.
 *
 * Structurally identical to the entity widgets (light/switch/climate etc.): same
 * SizeMode.Responsive buckets, same compact/wide layout composables. Kept as its own
 * design (v0.2.15/v0.2.16) rather than that made it continuously scale — it looked
 * different from every other widget on the home screen (no size cap, filled arbitrarily
 * large boxes) instead of matching the family's fixed compact/wide look.
 */
class ShortcutWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(56.dp, 56.dp),   // 1×1
            DpSize(110.dp, 56.dp),  // 2×1
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val store = WidgetConfigStore.get(context)
        val initialConfig = store.get(appWidgetId)

        provideContent {
            // Reaktivt: observe() holder Glance-sessionen i live og rekomponerer når config-
            // activity'en gemmer (saveAndFinish). Uden dette viste en frisk-placeret genvej "Opsæt",
            // fordi provideGlance kørte med null config under placeringen og WidgetConfigStore
            // (SharedPreferences) ikke er reaktiv — samme fix-mønster som entity-widgets' Room-Flow.
            val config by store.observe(appWidgetId).collectAsState(initial = initialConfig)

            // Både den ukonfigurerede "Opsæt"-visning og den konfigurerede genvej-tile bruger
            // det globale widget-farvetema (GlanceTheme.colors via WidgetGlanceTheme) — genvejen
            // er altid "tændt" og bruger derfor primary/onPrimary som entity-widgetsenes aktive look.
            WidgetGlanceTheme(context) {
                val isWide = LocalSize.current.width >= 110.dp
                val cfg = config
                if (cfg == null) {
                    UnconfiguredWidgetContent(context, appWidgetId, ShortcutWidgetConfigActivity::class.java, R.drawable.ic_dashboard)
                } else {
                    ShortcutContent(context, appWidgetId, cfg, isWide)
                }
            }
        }
    }
}

@Composable
private fun ShortcutContent(
    context: Context,
    appWidgetId: Int,
    config: WidgetConfig,
    isWide: Boolean,
) {
    // Always route via WebViewActivity. It checks config/connection on launch
    // and redirects to ShortcutWidgetConfigActivity if unconfigured. This avoids
    // a race where the Glance composition hasn't updated yet after first placement
    // and the launcher falls back to reopening the configure activity.
    val intent = Intent(context, WebViewActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("hawidgets://dashboard/$appWidgetId")
        putExtra(WebViewActivity.EXTRA_APPWIDGET_ID, appWidgetId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val contentColor = GlanceTheme.colors.onPrimary

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.primary)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center,
    ) {
        if (isWide) {
            WidgetWideLayout(R.drawable.ic_dashboard, config.title, "", contentColor)
        } else {
            WidgetCompactLayout(R.drawable.ic_dashboard, config.title, "", contentColor)
        }
    }
}

class ShortcutWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShortcutWidget()

    // Soft-delete: stempl fjernede genvej-configs (prefs er synkron, ingen coroutine nødvendig).
    // reconcileWidgets hard-sletter efter grace-perioden.
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetConfigStore.get(context)
        val now = System.currentTimeMillis()
        appWidgetIds.forEach { store.markRemoved(it, now) }
    }
}
