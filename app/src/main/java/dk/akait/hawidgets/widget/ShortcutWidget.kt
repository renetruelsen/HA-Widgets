package dk.akait.hawidgets.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.WidgetConfigStore
import dk.akait.hawidgets.web.WebViewActivity


/**
 * A 1x1 home-screen tile that opens a configured HA dashboard on tap. If not yet
 * configured (e.g. pinned via the in-app button), tapping opens the config screen.
 */
class ShortcutWidget : GlanceAppWidget() {

    // Exact mode: LocalSize.current reflects the actual allocated cell dimensions,
    // allowing us to draw a square tile even when the launcher cell is not square.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = WidgetConfigStore.get(context).get(appWidgetId)

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

        provideContent {
            // One UI cells are not square. Use the smaller dimension so the tile is always
            // square and the same width as the neighboring entity widgets (which fill their cell).
            val cellSize = LocalSize.current
            val side = min(cellSize.width, cellSize.height)
            val title = config?.title

            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(side)
                        .background(Color(0xFF03A9F4))
                        .cornerRadius(16.dp)
                        .clickable(actionStartActivity(intent)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_dashboard),
                            contentDescription = title ?: context.getString(R.string.open_dashboard),
                            modifier = GlanceModifier.size(28.dp)
                        )
                        if (title != null) {
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = title,
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

class ShortcutWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShortcutWidget()
}
