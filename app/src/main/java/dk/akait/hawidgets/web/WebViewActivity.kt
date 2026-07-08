package dk.akait.hawidgets.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dk.akait.hawidgets.data.DisplayMode
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.data.WidgetConfig
import dk.akait.hawidgets.data.WidgetConfigStore

/**
 * Shows a live HA dashboard in a WebView, either fullscreen or as a sized overlay.
 * Authentication is provided to the page via [ExternalAuthBridge]; the token is
 * never stored in the WebView. The HA header/sidebar are hidden via [KioskScript].
 * A native loading overlay is shown until KioskScript signals the dashboard is ready.
 */
class WebViewActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: View

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = SecureStore.get(this)
        val baseUrl = store.baseUrl
        val token = store.token
        if (baseUrl.isNullOrBlank() || token.isNullOrBlank()) {
            Toast.makeText(this, "HA er ikke forbundet endnu.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)
        val config = resolveConfig()

        // If launched from a widget tap but the widget has no saved config yet,
        // redirect to the configure activity instead of showing a broken dashboard.
        if (appWidgetId != -1 && WidgetConfigStore.get(this).get(appWidgetId) == null) {
            startActivity(
                Intent(this, dk.akait.hawidgets.widget.ShortcutWidgetConfigActivity::class.java).apply {
                    putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            finish()
            return
        }

        val url = buildUrl(baseUrl, config.dashboardPath)
        val navigateToPath = intent.getStringExtra(EXTRA_NAVIGATE_PATH)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            setBackgroundColor(Color.WHITE)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(KioskScript.JS, null)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    Log.e(TAG, "onReceivedError ${request?.url} -> ${error?.errorCode} ${error?.description}")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                    Log.d(TAG, "console[${cm.messageLevel()}] ${cm.message()}")
                    return true
                }
            }
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onDashboardReady() = runOnUiThread {
                    hideLoadingOverlay()
                    // Klient-side SPA-navigation til f.eks. /history i stedet for en frisk
                    // top-level sidenavigation dertil — HA's service worker precacher kun
                    // kendte ruter (fx /lovelace), og en direkte navigation til en ikke-
                    // precachet rute (som /history) hænger uendeligt i SW'ens fallback-fetch.
                    // Samme mekanisme HA's egen frontend bruger internt (history.pushState +
                    // 'location-changed'), så det er reelt identisk med et sidebar-klik.
                    navigateToPath?.let { path ->
                        val escaped = path.replace("\\", "\\\\").replace("'", "\\'")
                        webView.evaluateJavascript(
                            "history.pushState(null,'','$escaped');" +
                                "window.dispatchEvent(new CustomEvent('location-changed',{detail:{replace:false}}));",
                            null,
                        )
                    }
                }
            }, "haWidgetsNative")
        }

        // Single bridge instance shared by both externalApp (legacy) and externalAppV2 (secure).
        val bridge = ExternalAuthBridge(token) { js -> webView.post { webView.evaluateJavascript(js, null) } }
        webView.addJavascriptInterface(bridge, "externalApp")

        // Register externalAppV2 — origin-validated, main-frame only.
        // HA frontend prefers this over the legacy externalApp bridge when present.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            val parsedBase = Uri.parse(baseUrl)
            val port = if (parsedBase.port != -1) ":${parsedBase.port}" else ""
            val origin = "${parsedBase.scheme}://${parsedBase.host}$port"
            WebViewCompat.addWebMessageListener(
                webView, "externalAppV2", setOf(origin),
                object : WebViewCompat.WebMessageListener {
                    override fun onPostMessage(
                        view: WebView,
                        message: WebMessageCompat,
                        sourceOrigin: Uri,
                        isMainFrame: Boolean,
                        replyProxy: JavaScriptReplyProxy,
                    ) {
                        if (!isMainFrame) return
                        bridge.dispatchV2(message.data ?: return)
                    }
                }
            )
        }

        val root = buildRoot(config)
        setContentView(root)
        webView.loadUrl(url)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun hideLoadingOverlay() {
        if (!::loadingOverlay.isInitialized) return
        loadingOverlay.animate().alpha(0f).setDuration(250).withEndAction {
            loadingOverlay.visibility = View.GONE
        }.start()
    }

    private fun buildRoot(config: WidgetConfig): View {
        val root = FrameLayout(this)

        val container: FrameLayout = if (config.displayMode == DisplayMode.OVERLAY) {
            root.setBackgroundColor(0x99000000.toInt())
            root.setOnClickListener { finish() }
            val metrics = resources.displayMetrics
            val w = metrics.widthPixels * config.widthPct / 100
            val h = metrics.heightPixels * config.heightPct / 100
            FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(16f)
                }
                clipToOutline = true
                isClickable = true
                layoutParams = FrameLayout.LayoutParams(w, h, Gravity.CENTER)
            }
        } else {
            root.setBackgroundColor(Color.WHITE)
            FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }

        container.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        container.addView(buildCloseButton())

        // Native loading overlay — shown until KioskScript signals dashboard ready.
        loadingOverlay = buildLoadingOverlay()
        container.addView(
            loadingOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(container)
        return root
    }

    private fun buildLoadingOverlay(): View {
        val spinner = ProgressBar(this, null, android.R.attr.progressBarStyle).apply {
            isIndeterminate = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        return FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(spinner)
        }
    }

    private fun buildCloseButton(): Button = Button(this).apply {
        text = "✕"
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(0xAA000000.toInt())
            shape = GradientDrawable.OVAL
        }
        val size = dp(40f).toInt()
        layoutParams = FrameLayout.LayoutParams(size, size, Gravity.END or Gravity.TOP).apply {
            val m = dp(8f).toInt()
            setMargins(0, m, m, 0)
        }
        setOnClickListener { finish() }
    }

    private fun resolveConfig(): WidgetConfig {
        val appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)
        if (appWidgetId != -1) {
            WidgetConfigStore.get(this).get(appWidgetId)?.let { return it }
        }
        val path = intent.getStringExtra(EXTRA_DASHBOARD_PATH)?.trim('/').orEmpty()
        val mode = runCatching {
            DisplayMode.valueOf(intent.getStringExtra(EXTRA_DISPLAY_MODE).orEmpty())
        }.getOrDefault(DisplayMode.FULLSCREEN)
        return WidgetConfig(
            dashboardPath = path.ifBlank { "lovelace" },
            title = "Dashboard",
            displayMode = mode,
        )
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    override fun onDestroy() {
        if (this::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HAWeb"
        const val EXTRA_DASHBOARD_PATH = "dashboard_path"
        const val EXTRA_DISPLAY_MODE = "display_mode"
        const val EXTRA_APPWIDGET_ID = "appwidget_id"
        /** Klient-side SPA-sti (fx "/history?entity_id=X") at navigere til, EFTER
         * [EXTRA_DASHBOARD_PATH] er loadet og klar — se [onDashboardReady]-kommentaren. */
        const val EXTRA_NAVIGATE_PATH = "navigate_path"

        private fun buildUrl(baseUrl: String, path: String): String {
            val base = baseUrl.trimEnd('/')
            val target = path.trim('/').ifBlank { "lovelace" }
            val separator = if ('?' in target) '&' else '?'
            return "$base/$target${separator}external_auth=1&kiosk"
        }
    }
}
