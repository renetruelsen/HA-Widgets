package dk.akait.hawidgets.web

import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * Implements Home Assistant's external authentication contract for the WebView.
 *
 * Two bridge versions are supported simultaneously:
 *
 * **Legacy `externalApp`** (via `addJavascriptInterface`): available to all frames.
 * Used as fallback when the WebView doesn't support WEB_MESSAGE_LISTENER or HA < 2023.4.
 *
 * **Modern `externalAppV2`** (via `WebViewCompat.addWebMessageListener`): origin-validated,
 * main-frame only. Registered in [WebViewActivity] when the feature is available.
 * Closes CVE-2023-41898: cross-origin iframes can no longer call getExternalAuth.
 * HA frontend picks V2 over legacy when `window.externalAppV2` is present.
 *
 * The token lives only in native memory and is handed to the page in-memory.
 * It is never persisted to WebView storage.
 *
 * During bootstrap HA sends `config/get` over the external bus and BLOCKS on a reply.
 * [handleExternalBus] handles that handshake; all capabilities are reported unavailable.
 */
class ExternalAuthBridge(
    private val token: String,
    private val runJs: (String) -> Unit,
) {

    // ── Legacy externalApp (addJavascriptInterface) ──────────────────────────

    @JavascriptInterface
    fun getExternalAuth(payload: String) = handleGetExternalAuth(payload)

    @JavascriptInterface
    fun revokeExternalAuth(payload: String) = handleRevokeExternalAuth(payload)

    @JavascriptInterface
    fun externalBus(message: String): Boolean {
        handleExternalBus(message)
        return true
    }

    // ── Modern externalAppV2 (WebViewCompat.addWebMessageListener) ───────────

    /**
     * Entry point for messages from `window.externalAppV2.postMessage(json)`.
     * The envelope format is `{"type": "<method>", "payload": {...}}`.
     */
    fun dispatchV2(rawJson: String) {
        Log.d(TAG, "externalAppV2 raw=$rawJson")
        try {
            val envelope = JSONObject(rawJson)
            val payloadJson = envelope.optJSONObject("payload")?.toString() ?: "{}"
            when (envelope.optString("type")) {
                "getExternalAuth"    -> handleGetExternalAuth(payloadJson)
                "revokeExternalAuth" -> handleRevokeExternalAuth(payloadJson)
                "externalBus"        -> handleExternalBus(payloadJson)
                else -> Log.d(TAG, "externalAppV2 unknown type: ${envelope.optString("type")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "externalAppV2 parse error: ${e.message}")
        }
    }

    // ── Internal handlers (shared by both bridge versions) ───────────────────

    private fun handleGetExternalAuth(payloadJson: String) {
        Log.d(TAG, "getExternalAuth")
        val callback = parseCallback(payloadJson, "externalAuthSetToken")
        val tokens = JSONObject()
            .put("access_token", token)
            .put("expires_in", LONG_EXPIRES_SECONDS)
        runJs("window['$callback'](true, $tokens);")
    }

    private fun handleRevokeExternalAuth(payloadJson: String) {
        Log.d(TAG, "revokeExternalAuth")
        val callback = parseCallback(payloadJson, "externalAuthRevokeToken")
        runJs("window['$callback'](true);")
    }

    private fun handleExternalBus(messageJson: String) {
        Log.d(TAG, "externalBus message=$messageJson")
        try {
            val msg = JSONObject(messageJson)
            when (msg.optString("type")) {
                "config/get" -> replyExternalConfig(msg.optInt("id", -1))
                // Other bus commands (theme-update, haptic, matter, …) ignored in M1.
            }
        } catch (e: Exception) {
            Log.e(TAG, "externalBus parse error: ${e.message}")
        }
    }

    /** Answers the frontend's `config/get` so bootstrap can proceed. */
    private fun replyExternalConfig(id: Int) {
        val config = JSONObject()
            .put("hasSettingsScreen", false)
            .put("hasSidebar", false)
            .put("canWriteTag", false)
            .put("hasExoPlayer", false)
            .put("canCommissionMatter", false)
            .put("canImportThreadCredentials", false)
            .put("canTransferThreadCredentialsToKeychain", false)
            .put("hasAssist", false)
            .put("hasBarCodeScanner", 0)
            .put("canSetupImprov", false)
            .put("downloadFileSupported", false)
        val response = JSONObject()
            .put("id", id)
            .put("type", "result")
            .put("success", true)
            .put("result", config)
        // The frontend's window.externalBus expects an object, not a string.
        runJs("window.externalBus($response);")
    }

    private fun parseCallback(payload: String, fallback: String): String =
        try {
            JSONObject(payload).optString("callback", fallback)
        } catch (_: Exception) {
            fallback
        }

    companion object {
        private const val TAG = "HAWeb"

        // LLT effectively does not expire; report a long lifetime so the frontend
        // rarely re-requests it. Each re-request simply returns the same token.
        private const val LONG_EXPIRES_SECONDS = 315_360_000L // ~10 years
    }
}
