package dk.akait.hawidgets.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed secure storage for the HA connection.
 *
 * The long-lived access token is the crown jewel: it is stored only here, in
 * EncryptedSharedPreferences with an AndroidKeyStore-backed master key. It is
 * NEVER written to WebView storage — the WebView only receives it in memory via
 * the external-auth JS bridge.
 */
class SecureStore private constructor(private val prefs: SharedPreferences) {

    var baseUrl: String?
        get() = prefs.getString(KEY_BASE_URL, null)
        set(value) = prefs.edit().putString(KEY_BASE_URL, value?.trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    val isConfigured: Boolean
        get() = !baseUrl.isNullOrBlank() && !token.isNullOrBlank()

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
        private fun keyDashboard(id: Int) = "dashboard_path_$id"

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
