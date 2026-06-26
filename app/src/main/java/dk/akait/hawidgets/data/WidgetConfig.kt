package dk.akait.hawidgets.data

import android.content.Context
import org.json.JSONObject

enum class DisplayMode { FULLSCREEN, OVERLAY }
enum class ColorScheme { SYSTEM, DARK, LIGHT }

/** Per-widget display configuration (non-secret). */
data class WidgetConfig(
    val dashboardPath: String,   // e.g. "lovelace-hjem", or "lovelace" for default
    val title: String,
    val displayMode: DisplayMode = DisplayMode.FULLSCREEN,
    val widthPct: Int = 90,
    val heightPct: Int = 80,
    val colorScheme: ColorScheme = ColorScheme.SYSTEM,
) {
    fun toJson(): String = JSONObject()
        .put("dashboardPath", dashboardPath)
        .put("title", title)
        .put("displayMode", displayMode.name)
        .put("widthPct", widthPct)
        .put("heightPct", heightPct)
        .put("colorScheme", colorScheme.name)
        .toString()

    companion object {
        fun fromJson(s: String): WidgetConfig {
            val o = JSONObject(s)
            return WidgetConfig(
                dashboardPath = o.optString("dashboardPath", "lovelace"),
                title = o.optString("title", "Dashboard"),
                displayMode = runCatching { DisplayMode.valueOf(o.optString("displayMode")) }
                    .getOrDefault(DisplayMode.FULLSCREEN),
                widthPct = o.optInt("widthPct", 90),
                heightPct = o.optInt("heightPct", 80),
                colorScheme = runCatching { ColorScheme.valueOf(o.optString("colorScheme")) }
                    .getOrDefault(ColorScheme.SYSTEM),
            )
        }
    }
}

/** Plain (non-encrypted) store for per-widget display config. Secrets stay in [SecureStore]. */
class WidgetConfigStore private constructor(
    private val prefs: android.content.SharedPreferences,
) {
    fun save(appWidgetId: Int, config: WidgetConfig) {
        prefs.edit().putString(key(appWidgetId), config.toJson()).apply()
    }

    fun get(appWidgetId: Int): WidgetConfig? =
        prefs.getString(key(appWidgetId), null)?.let { runCatching { WidgetConfig.fromJson(it) }.getOrNull() }

    fun remove(appWidgetId: Int) {
        prefs.edit().remove(key(appWidgetId)).apply()
    }

    companion object {
        private fun key(id: Int) = "widget_$id"

        fun get(context: Context): WidgetConfigStore =
            WidgetConfigStore(
                context.applicationContext.getSharedPreferences("widget_config", Context.MODE_PRIVATE)
            )
    }
}
