package dk.akait.hawidgets.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject

enum class DisplayMode { FULLSCREEN, OVERLAY }

/** Per-widget display configuration (non-secret). */
data class WidgetConfig(
    val dashboardPath: String,   // e.g. "lovelace-hjem", or "lovelace" for default
    val title: String,
    val displayMode: DisplayMode = DisplayMode.FULLSCREEN,
    val widthPct: Int = 90,
    val heightPct: Int = 80,
) {
    fun toJson(): String = JSONObject()
        .put("dashboardPath", dashboardPath)
        .put("title", title)
        .put("displayMode", displayMode.name)
        .put("widthPct", widthPct)
        .put("heightPct", heightPct)
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

    /**
     * Reaktiv strøm af config for [appWidgetId]: emitterer nuværende værdi straks, og igen hver gang
     * dens nøgle ændres i prefs. Bruges af [dk.akait.hawidgets.widget.ShortcutWidget]s `provideGlance`
     * så Glance-sessionen rekomponerer når config-activity'en gemmer — uden dette viste en frisk-
     * placeret genvej "Opsæt", fordi `provideGlance` kørte med null config under placeringen og
     * SharedPreferences (modsat Room) ikke er reaktiv. Samme fix-mønster som entity-widgets' Room-Flow.
     */
    fun observe(appWidgetId: Int): Flow<WidgetConfig?> = callbackFlow {
        val watchedKey = key(appWidgetId)
        trySend(get(appWidgetId))
        // changedKey er null når prefs ryddes (API 30+) — genlæs også der.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == watchedKey || changedKey == null) trySend(get(appWidgetId))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun remove(appWidgetId: Int) {
        prefs.edit().remove(key(appWidgetId)).apply()
    }

    /** Alle gemte genvej-konfigurationer, keyed på appWidgetId — bruges kun af log-dump'et
     * (RemoteLogger), ikke af selve widget-rendering (som bruger [get]/[observe] pr. id). */
    fun getAll(): Map<Int, WidgetConfig> =
        prefs.all.entries
            .filter { it.key.startsWith("widget_") }
            .mapNotNull { (key, value) ->
                val id = key.removePrefix("widget_").toIntOrNull() ?: return@mapNotNull null
                val config = (value as? String)?.let { runCatching { WidgetConfig.fromJson(it) }.getOrNull() }
                config?.let { id to it }
            }
            .toMap()

    companion object {
        private fun key(id: Int) = "widget_$id"

        fun get(context: Context): WidgetConfigStore =
            WidgetConfigStore(
                context.applicationContext.getSharedPreferences("widget_config", Context.MODE_PRIVATE)
            )
    }
}
