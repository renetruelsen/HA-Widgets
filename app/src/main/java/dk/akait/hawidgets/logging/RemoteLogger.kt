package dk.akait.hawidgets.logging

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import dk.akait.hawidgets.BuildConfig
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Sender crash-/fejl-logs til rtr.dk's Log Collector (docs/ha-widgets-logging.md). Aktiv i
 * BEGGE build-typer (debug og release) — se docs/superpowers/specs/2026-07-12-remote-error-
 * logging-design.md for begrundelsen.
 */
object RemoteLogger {
    private const val TAG = "RemoteLogger"
    private const val ENDPOINT = "https://rtr.dk/api/logs"
    private const val THROTTLE_MS = 30_000L

    private val buffer = LogBuffer()
    @Volatile private var deviceLineAdded = false
    @Volatile private var lastFlushAt = 0L

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun i(tag: String, message: String) {
        buffer.add('I', tag, message)
    }

    fun w(tag: String, message: String) {
        buffer.add('W', tag, message)
        flush()
    }

    fun e(tag: String, message: String) {
        buffer.add('E', tag, message)
        flush()
    }

    /** Kaldes tidligt (HaWidgetsApp.onCreate) — tilføjer enheds-info-linjen én gang pr. proces. */
    fun ensureDeviceLine(context: Context) {
        if (deviceLineAdded) return
        deviceLineAdded = true
        val launcher = currentLauncherPackage(context)
        buffer.add(
            'I', "DEVICE",
            "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} " +
                "(API ${Build.VERSION.SDK_INT}), launcher=$launcher"
        )
    }

    private fun currentLauncherPackage(context: Context): String {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName ?: "unknown"
    }

    /**
     * Uploader bufferens indhold. [force] ignorerer 30s-throttlen (bruges af crash-handleren og
     * "Send log nu"). [configLines] tilføjes til bufferen lige før upload (crash + manuel send —
     * IKKE den rutinemæssige, throttlede auto-flush fra [w]/[e]). Blokerende netværkskald — kald
     * fra en baggrundstråd (crash-handleren) eller wrap i `Dispatchers.IO` fra en coroutine.
     * Returnerer true ved 2xx, false ellers (inkl. tomt token/tom buffer/netværksfejl/429).
     */
    fun flush(force: Boolean = false, configLines: List<String> = emptyList()): Boolean {
        if (BuildConfig.LOG_UPLOAD_TOKEN.isBlank()) {
            Log.d(TAG, "No LOG_UPLOAD_TOKEN configured — skipping upload")
            return false
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastFlushAt < THROTTLE_MS) return false
        if (buffer.isEmpty() && configLines.isEmpty()) return false
        lastFlushAt = now

        configLines.forEach(buffer::addRaw)
        val body = buffer.body()
        return try {
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer ${BuildConfig.LOG_UPLOAD_TOKEN}")
                .header("X-App-Id", "ha-widgets")
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .header("X-App-Platform", "android")
                .header("User-Agent", "HAWidgets-Android/${BuildConfig.VERSION_NAME}")
                .post(body.toRequestBody("text/plain; charset=utf-8".toMediaType()))
                .build()
            http.newCall(request).execute().use { it.isSuccessful }
        } catch (ex: Exception) {
            Log.w(TAG, "Log upload failed: ${ex.message}")
            false
        }
    }

    /**
     * Installerer en global uncaught-exception-handler: skriver en E-linje + stacktrace,
     * tilføjer widget-config-dumpet, og forsøger en FORCED, blokerende upload FØR den
     * oprindelige handler kaldes videre — så systemets crash-dialog/øvrig crash-reporting
     * sker helt uændret bagefter.
     */
    fun installCrashHandler(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                buffer.add('E', "CRASH", throwable.toString())
                buffer.addRaw(Log.getStackTraceString(throwable))
                val configLines = runBlocking { collectWidgetConfigDump(appContext) }
                flush(force = true, configLines = configLines)
            } catch (_: Exception) {
                // Logging må aldrig forstyrre den rigtige crash-håndtering.
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }
}
