package dk.akait.hawidgets

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Pre-warms the WebView/Chromium provider so the first real dashboard open is
 * fast. This loads the native library off the critical path; combined with the
 * WebView HTTP cache and HA's service worker, reopening a dashboard is near-instant.
 * No persistent connection is kept (battery-friendly per project constraints).
 */
class HaWidgetsApp : Application() {

    /**
     * Applikations-levetids-scope til fire-and-forget-arbejde der SKAL overleve at en activity
     * genskabes (fx tema-skift kalder `recreate()`). Widget-opdateringer efter et tema-/farvevalg
     * blev tidligere launchet på settings-sheet'ens `rememberCoroutineScope()` og kunne blive
     * annulleret af det efterfølgende `recreate()`, så kun nogle widgets nåede at gen-tegne.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Handler(Looper.getMainLooper()).post {
            runCatching { WebView(this).destroy() }
        }
        if (SecureStore.get(this).isConfigured) {
            SyncWorker.schedule(this)
        }
    }
}
