package dk.akait.hawidgets.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.unit.ColorProvider
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.WidgetConfig
import dk.akait.hawidgets.data.WidgetConfigStore
import dk.akait.hawidgets.web.WebViewActivity
import dk.akait.hawidgets.widget.common.UnconfiguredWidgetContent
import dk.akait.hawidgets.widget.common.WidgetGlanceTheme
import dk.akait.hawidgets.widget.common.WidgetCompactLayout
import dk.akait.hawidgets.widget.common.WidgetWideLayout

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
        val config = WidgetConfigStore.get(context).get(appWidgetId)

        provideContent {
            // Kun den ukonfigurerede "Opsæt"-visning bruger tema-farver (GlanceTheme.colors
            // via UnconfiguredWidgetContent). Den konfigurerede genvej-tile er bevidst altid
            // brand-blå (uafhængig af tema) — se ShortcutContent.
            WidgetGlanceTheme(context) {
                val isWide = LocalSize.current.width >= 110.dp
                if (config == null) {
                    UnconfiguredWidgetContent(context, appWidgetId, ShortcutWidgetConfigActivity::class.java, R.drawable.ic_dashboard)
                } else {
                    ShortcutContent(context, appWidgetId, config, isWide)
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

    val contentColor = ColorProvider(Color.White)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF03A9F4))
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
}
