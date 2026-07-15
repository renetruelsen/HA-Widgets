package dk.akait.hawidgets.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Keystore-backed secure storage for the HA connection.
 *
 * The long-lived access token is the crown jewel: it is stored only here, in
 * EncryptedSharedPreferences with an AndroidKeyStore-backed master key. It is
 * NEVER written to WebView storage — the WebView only receives it in memory via
 * the external-auth JS bridge.
 */
/** (tema-tilstand, farvetema)-par — de to SecureStore-værdier der tilsammen bestemmer widget-farver. */
data class ThemeSettings(val themeMode: String, val colorTheme: String)

class SecureStore private constructor(private val prefs: SharedPreferences) {

    var baseUrl: String?
        get() = prefs.getString(KEY_BASE_URL, null)
        set(value) = prefs.edit().putString(KEY_BASE_URL, value?.trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    /**
     * Global tema-valg der overstyrer systemets lys/mørk-indstilling i HELE app-UI'et og i
     * ALLE Glance-widgets (IKKE WebView-dashboardet — det styres af HA-serveren). Gyldige
     * værdier: "light" | "dark" | "system". Default "system" → identisk med den historiske
     * adfærd (følg systemets nattilstand).
     */
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    /**
     * Globalt farvetema for ALLE Glance-widgets (IKKE app-UI'et — det forbliver fast blå).
     * Gyldige værdier: "blue" | "green" | "purple" | "orange" | "red" | "teal". Default
     * "blue" → identisk med den historiske (eneste) farve.
     */
    var widgetColorTheme: String
        get() = prefs.getString(KEY_WIDGET_COLOR_THEME, COLOR_BLUE) ?: COLOR_BLUE
        set(value) = prefs.edit().putString(KEY_WIDGET_COLOR_THEME, value).apply()

    /**
     * Global baggrunds-pull-frekvens i minutter for ALLE widgets ([dk.akait.hawidgets.worker.SyncWorker]).
     * [SYNC_MANUAL] (0) = ingen periodisk pull; widgets opdaterer kun når man trykker på dem. Ellers er
     * værdien det periodiske interval i minutter (WorkManager-gulv er 15 min — alle presets ≥ 15).
     * Default [SYNC_DEFAULT_MINUTES] (30). Ikke-reaktiv: et skift skal følges op af
     * `SyncWorker.schedule(context)` for at træde i kraft (samme mønster som tema, ADR-5).
     */
    var syncIntervalMinutes: Int
        get() = prefs.getInt(KEY_SYNC_INTERVAL, SYNC_DEFAULT_MINUTES)
        set(value) = prefs.edit().putInt(KEY_SYNC_INTERVAL, value).apply()

    /** Sat af [dk.akait.hawidgets.logging.RemoteLogger.installCrashHandler] hvis forrige proces
     * crashede — throwable.toString(), null = intet ventende. Læses af `MainActivity` ved næste
     * app-åbning for at auto-åbne rapport-dialogen. Ryddes af [clearPendingCrash]. */
    var pendingCrashSummary: String?
        get() = prefs.getString(KEY_PENDING_CRASH_SUMMARY, null)
        set(value) = prefs.edit().putString(KEY_PENDING_CRASH_SUMMARY, value).apply()

    /** Buffer-øjebliksbilledet (newline-joinet) fra crash-tidspunktet — genindsættes i den nye
     * proces' `RemoteLogger` via `restorePersistedLines` (se `HaWidgetsApp`), så et efterfølgende
     * flush faktisk indeholder selve crashet (den in-memory buffer overlever ikke genstart). */
    var pendingCrashLog: String?
        get() = prefs.getString(KEY_PENDING_CRASH_LOG, null)
        set(value) = prefs.edit().putString(KEY_PENDING_CRASH_LOG, value).apply()

    /** Rydder begge crash-pending-felter — kaldes når rapport-dialogen er blevet vist og
     * afsluttet (Cancel eller Send, uanset udfald): "spørg kun én gang pr. crash". */
    fun clearPendingCrash() {
        prefs.edit().remove(KEY_PENDING_CRASH_SUMMARY).remove(KEY_PENDING_CRASH_LOG).apply()
    }

    /**
     * Synkron, BLOKERENDE variant af [pendingCrashSummary]/[pendingCrashLog]-sætterne — kaldes
     * KUN fra [dk.akait.hawidgets.logging.RemoteLogger.installCrashHandler]. `.apply()` skriver
     * asynkront (via en baggrunds-handler); en crash-handler kan finde på at dræbe processen
     * (`Process.killProcess`) eller lade systemet gøre det meget kort efter, og der er ingen
     * garanti for at et `.apply()`-kald når at blive flushet til disk inden da. `.commit()` er
     * synkront og garanterer derfor at data ER på disk når kaldet returnerer, FØR crash-handleren
     * går videre til selve netværks-uploadet (som i sig selv kan hænge/blive dræbt uden risiko —
     * persisteringen er allerede sket).
     */
    fun persistPendingCrashSync(summary: String, log: String) {
        prefs.edit()
            .putString(KEY_PENDING_CRASH_SUMMARY, summary)
            .putString(KEY_PENDING_CRASH_LOG, log)
            .commit()
    }

    val isConfigured: Boolean
        get() = !baseUrl.isNullOrBlank() && !token.isNullOrBlank()

    /** Nuværende (tema-tilstand, farvetema) — det par der bestemmer en widgets farver. */
    fun themeSettings(): ThemeSettings = ThemeSettings(themeMode, widgetColorTheme)

    /**
     * Reaktiv strøm af [ThemeSettings]: emitterer nuværende værdi straks, og igen hver gang tema-
     * eller farvetema-nøglen ændres. Collectes af [dk.akait.hawidgets.widget.common.WidgetGlanceTheme]
     * så en Glance-session RE-KOMPONERER (og dermed re-læser farverne) når brugeren skifter tema/farve.
     *
     * Uden dette blev temaet kun læst imperativt under komposition, og `updateAll()` genfremkaldte det
     * kun hvis kompositionen tilfældigvis re-komponerede af anden grund (en Room-emission) — derfor
     * "trådte ikke altid i kraft øjeblikkeligt". Samme reaktive fix-mønster som [WidgetConfigStore.observe].
     */
    fun observeThemeSettings(): Flow<ThemeSettings> = callbackFlow {
        trySend(themeSettings())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == KEY_THEME_MODE || changedKey == KEY_WIDGET_COLOR_THEME || changedKey == null) {
                trySend(themeSettings())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setDashboardPath(appWidgetId: Int, path: String) {
        prefs.edit().putString(keyDashboard(appWidgetId), path).apply()
    }

    fun getDashboardPath(appWidgetId: Int): String? =
        prefs.getString(keyDashboard(appWidgetId), null)

    fun removeWidget(appWidgetId: Int) {
        prefs.edit().remove(keyDashboard(appWidgetId)).apply()
    }

    fun clearConnection() {
        prefs.edit().remove(KEY_BASE_URL).remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val FILE_NAME = "ha_secure_store"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_WIDGET_COLOR_THEME = "widget_color_theme"
        private const val KEY_PENDING_CRASH_SUMMARY = "pending_crash_summary"
        private const val KEY_PENDING_CRASH_LOG = "pending_crash_log"
        private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
        private fun keyDashboard(id: Int) = "dashboard_path_$id"

        /** Ingen periodisk baggrunds-pull — kun tryk-på-widget opdaterer. */
        const val SYNC_MANUAL = 0
        /** Default baggrunds-pull-interval i minutter (batterivenligt udgangspunkt). */
        const val SYNC_DEFAULT_MINUTES = 30

        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"

        const val COLOR_BLUE = "blue"
        const val COLOR_GREEN = "green"
        const val COLOR_PURPLE = "purple"
        const val COLOR_ORANGE = "orange"
        const val COLOR_RED = "red"
        const val COLOR_TEAL = "teal"

        @Volatile
        private var instance: SecureStore? = null

        fun get(context: Context): SecureStore =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        private fun create(context: Context): SecureStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return SecureStore(prefs)
        }
    }
}
