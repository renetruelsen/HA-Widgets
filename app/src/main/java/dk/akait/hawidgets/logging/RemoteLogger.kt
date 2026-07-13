package dk.akait.hawidgets.logging

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import dk.akait.hawidgets.BuildConfig
import dk.akait.hawidgets.data.SecureStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

    /** Udfald af et [flush]-forsøg — se docs/superpowers/specs/2026-07-13-settings-redesign-
     * error-report-design.md §C. "Report a problem"-dialogen viser 4 forskellige resultat-
     * beskeder fra dette; eksisterende fire-and-forget-kaldesteder ([w]/[e]/crash-handleren)
     * ignorerer fortsat returværdien. */
    sealed class UploadResult {
        data object Success : UploadResult()
        data object NotConfigured : UploadResult()
        data object NetworkError : UploadResult()

        /** Kun ramt af den interne, ikke-forced auto-flush fra [w]/[e] — aldrig af
         * "Report a problem"-dialogen (sender altid `force = true`). */
        data object Throttled : UploadResult()
        data class ServerRejected(val code: Int) : UploadResult()
    }

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
     * True hvis [BuildConfig.LOG_UPLOAD_TOKEN] er sat — dvs. om featuren overhovedet er
     * konfigureret på denne build.
     */
    fun isConfigured(): Boolean = BuildConfig.LOG_UPLOAD_TOKEN.isNotBlank()

    /** Seneste [n] log-linjer, uden at røre throttle/lastFlushAt — bruges af "Copy log"
     * (netværksfri, kopierer kun til udklipsholder). */
    fun recentLines(n: Int = 30): List<String> = buffer.snapshot(n)

    /**
     * Genindsætter log-linjer fra en TIDLIGERE (crashet) proces' buffer i DENNE proces' friske
     * buffer — [LogBuffer] er kun i hukommelsen og overlever ikke en proces-genstart alene, så
     * uden dette ville et [flush] fra crash-rapport-dialogen mangle selve crash-konteksten.
     * Kaldes fra `HaWidgetsApp.onCreate`, efter [ensureDeviceLine].
     */
    fun restorePersistedLines(lines: List<String>) {
        lines.forEach(buffer::addRaw)
    }

    /**
     * Uploader bufferens indhold. [force] ignorerer 30s-throttlen (bruges af crash-handleren og
     * "Report a problem"-dialogen). [configLines] tilføjes til bufferen lige før upload (crash +
     * manuel send — IKKE den rutinemæssige, throttlede auto-flush fra [w]/[e]). Blokerende
     * netværkskald — kald fra en baggrundstråd (crash-handleren) eller wrap i `Dispatchers.IO`
     * fra en coroutine.
     */
    fun flush(force: Boolean = false, configLines: List<String> = emptyList()): UploadResult {
        if (BuildConfig.LOG_UPLOAD_TOKEN.isBlank()) {
            Log.d(TAG, "No LOG_UPLOAD_TOKEN configured — skipping upload")
            return UploadResult.NotConfigured
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastFlushAt < THROTTLE_MS) return UploadResult.Throttled
        // Uden enheds-/config-linjer er bufferen aldrig reelt tom i praksis (ensureDeviceLine
        // kører altid ved appstart) — denne gren er et sikkerhedsnet mod en tom POST-body
        // (serveren svarer 400 på det, jf. docs/ha-widgets-logging.md).
        if (buffer.isEmpty() && configLines.isEmpty()) return UploadResult.Throttled
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
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) UploadResult.Success else UploadResult.ServerRejected(response.code)
            }
        } catch (ex: Exception) {
            Log.w(TAG, "Log upload failed: ${ex.message}")
            UploadResult.NetworkError
        }
    }

    /**
     * Installerer en global uncaught-exception-handler: skriver en E-linje + stacktrace,
     * PERSISTERER buffer-øjebliksbilledet til [SecureStore] (så "Report a problem"-dialogen kan
     * tilbyde at sende det ved næste app-åbning, selv efter en proces-genstart), tilføjer
     * widget-config-dumpet, og forsøger en FORCED, blokerende upload FØR den oprindelige handler
     * kaldes videre — så systemets crash-dialog/øvrig crash-reporting sker helt uændret bagefter.
     */
    fun installCrashHandler(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                buffer.add('E', "CRASH", throwable.toString())
                buffer.addRaw(Log.getStackTraceString(throwable))
                try {
                    val store = SecureStore.get(appContext)
                    store.pendingCrashSummary = throwable.toString()
                    store.pendingCrashLog = buffer.body()
                } catch (_: Throwable) {
                    // Persistering er best-effort — må ikke forhindre selve upload-forsøget nedenfor.
                }
                val configLines = runBlocking {
                    withTimeoutOrNull(2000) { collectWidgetConfigDump(appContext) } ?: emptyList()
                }
                flush(force = true, configLines = configLines)
            } catch (_: Throwable) {
                // Logging må aldrig forstyrre den rigtige crash-håndtering — inkl. Error
                // (fx StackOverflowError/OutOfMemoryError), da throwable selv kan være sådan én.
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
