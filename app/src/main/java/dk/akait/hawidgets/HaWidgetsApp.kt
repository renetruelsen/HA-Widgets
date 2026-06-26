package dk.akait.hawidgets

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.worker.SyncWorker

/**
 * Pre-warms the WebView/Chromium provider so the first real dashboard open is
 * fast. This loads the native library off the critical path; combined with the
 * WebView HTTP cache and HA's service worker, reopening a dashboard is near-instant.
 * No persistent connection is kept (battery-friendly per project constraints).
 */
class HaWidgetsApp : Application() {
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
