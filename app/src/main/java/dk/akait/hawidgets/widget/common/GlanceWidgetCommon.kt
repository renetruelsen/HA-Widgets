package dk.akait.hawidgets.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import dk.akait.hawidgets.R
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dk.akait.hawidgets.data.db.EntityStateEntity
import org.json.JSONObject

internal const val STALE_THRESHOLD_MS = 15 * 60 * 1000L

internal fun EntityStateEntity.isStale() =
    (System.currentTimeMillis() - lastUpdated) > STALE_THRESHOLD_MS

internal fun friendlyNameFromJson(attributesJson: String): String? =
    try { JSONObject(attributesJson).optString("friendly_name").ifEmpty { null } } catch (_: Exception) { null }

internal fun unitFromJson(attributesJson: String): String? =
    try { JSONObject(attributesJson).optString("unit_of_measurement").ifEmpty { null } } catch (_: Exception) { null }

/** `hvac_action`-attributten for en climate-entitet (hvad den FAKTISK gør lige nu:
 * "heating"/"cooling"/"idle"/"off"), i modsætning til `state` (den valgte hvac_mode). Null hvis
 * entiteten ikke rapporterer den. Bruges til at farve en climate-række rød mens den varmer. */
internal fun hvacActionFromJson(attributesJson: String): String? =
    try { JSONObject(attributesJson).optString("hvac_action").ifEmpty { null } } catch (_: Exception) { null }

@Composable
fun UnconfiguredWidgetContent(
    context: Context,
    appWidgetId: Int,
    configClass: Class<*>,
    iconResId: Int,
) {
    val intent = Intent(context, configClass).apply {
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
                provider = ImageProvider(iconResId),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            )
            Text(
                text = context.getString(R.string.widget_setup_placeholder),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            )
        }
    }
}

/** 1×1 compact: icon (26dp) + label (11sp) + status (13sp, bold). Sized to read state at a glance. */
@Composable
fun WidgetCompactLayout(iconResId: Int, label: String, statusText: String, contentColor: ColorProvider) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(iconResId),
            contentDescription = label,
            modifier = GlanceModifier.size(26.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
        Text(text = label, style = TextStyle(color = contentColor, fontSize = 11.sp), maxLines = 1)
        // Skip the status line entirely when empty (e.g. ShortcutWidget has no live state) —
        // an empty Text still reserves a line of height, which biases the visible content
        // above the box's true vertical center once the column is centered as a whole.
        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                style = TextStyle(color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun WidgetWideLayout(iconResId: Int, label: String, statusText: String, contentColor: ColorProvider) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(iconResId),
            contentDescription = label,
            modifier = GlanceModifier.size(28.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = label,
                style = TextStyle(color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            // Skip the status line entirely when empty (e.g. ShortcutWidget has no live
            // state) — an empty Text still reserves a line of height, which biases the
            // visible content above the row's true vertical center.
            if (statusText.isNotEmpty()) {
                Text(text = statusText, style = TextStyle(color = contentColor, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}
