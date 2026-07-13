# Settings Redesign + Error Report Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the Settings sheet's "doesn't open fully" bug, reorganize it into two sections (Appearance / Troubleshooting), and replace the one-tap "Send log now" button with a rich "Report a problem" dialog (optional note, Copy log / Cancel / Send report, non-dismissible sending state, 4-state Snackbar result) that can also auto-open once after an app crash.

**Architecture:** `RemoteLogger.flush()`'s return type widens from `Boolean` to a sealed `UploadResult` (Success/NotConfigured/NetworkError/Throttled/ServerRejected) so the UI can show a precise result. A new `SecureStore`-backed pending-crash flag + persisted log snapshot survives process restart (the in-memory `LogBuffer` does not), letting a crash's context reach the dialog on next launch via a new `RemoteLogger.restorePersistedLines()`. A new standalone `ReportProblemDialog` composable (in the `logging` package, next to `RemoteLogger`) owns the note-field/sending/result state machine and is mounted once in `OnboardingScreen`, driven by either the Settings-sheet row or the crash-pending flag. `MainActivity`'s `SettingsSheet` gets two `SectionHeader`s, `skipPartiallyExpanded`, `verticalScroll`, and 48dp row minimums.

**Tech Stack:** Kotlin, Jetpack Compose Material3 (existing `AlertDialog`/`ModalBottomSheet`/`Snackbar`), OkHttp 4.12.0 (existing), JUnit4 (existing test setup).

## Global Constraints

- Settings sheet: `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, outer `Column` gets `Modifier.verticalScroll(rememberScrollState())`, every clickable row gets `Modifier.heightIn(min = 48.dp)`.
- Two sections, in this order: **Appearance** (Theme, Widget color, Language) then **Troubleshooting** (Battery optimization, Report a problem). No divider between rows within a section; one divider before each section header.
- "Send log now" is fully replaced (not kept alongside) by "Report a problem" — approved by user, no dual entry points.
- Report dialog: optional note field, max 300 characters / 3 lines, **never** blocks the Send button (empty note is valid). Three actions: Copy log (network-free, does not close the dialog) / Cancel / Send report (primary).
- Sending state: non-dismissible (`onDismissRequest = {}`), no buttons, spinner + text only, ~15 second timeout (`withTimeoutOrNull(15_000)`), timeout maps to `NetworkError`.
- Result feedback: Snackbar (not Toast), 4 user-facing states — Success / NotConfigured (informational, not alarming) / NetworkError (with a "Retry" Snackbar action) / ServerRejected. `RemoteLogger.flush()` is always called with `force = true` from this dialog.
- Auto-trigger scope: **crash only** (detected via a persisted flag surviving process restart). No auto-trigger for HA-connection failures or empty entity-picker results — explicitly out of scope this round.
- Ask-once-per-crash: the pending-crash flag is cleared as soon as the dialog is dismissed (Cancel or after Send, regardless of success/failure) — never re-shown for the same crash.
- User note is sent as a plain log line, not a structured field: `formatLogLine('I', "USER_NOTE", note.trim())`, appended to `configLines` only when the note is non-blank (matches the server's plain-text-body contract).
- Spec: [`docs/superpowers/specs/2026-07-13-settings-redesign-error-report-design.md`](../specs/2026-07-13-settings-redesign-error-report-design.md).

---

### Task 1: SecureStore — pending-crash persistence fields

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/data/SecureStore.kt`

**Interfaces:**
- Produces: `var pendingCrashSummary: String?`, `var pendingCrashLog: String?`, `fun clearPendingCrash()` on `SecureStore`. Consumed by Task 3 (`RemoteLogger.installCrashHandler`), Task 4 (`HaWidgetsApp.onCreate`), and Task 7 (`MainActivity`'s crash-auto-trigger read + dialog-dismiss clear).

No automated test — `SecureStore` wraps `EncryptedSharedPreferences` (AndroidKeyStore), which has no JVM-unit-testable double in this project (same reason the rest of `SecureStore` has no unit tests today). Verified in Task 8's emulator QA.

- [ ] **Step 1: Add the two fields and the clear helper**

Modify `app/src/main/java/dk/akait/hawidgets/data/SecureStore.kt` — add these members inside `class SecureStore`, right after the existing `widgetColorTheme` var (before `val isConfigured`):

```kotlin
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
```

Add the two new keys inside `companion object`, alongside the existing `KEY_*` constants:

```kotlin
        private const val KEY_PENDING_CRASH_SUMMARY = "pending_crash_summary"
        private const val KEY_PENDING_CRASH_LOG = "pending_crash_log"
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/data/SecureStore.kt
git commit -m "feat: add pending-crash persistence fields to SecureStore"
```

---

### Task 2: LogBuffer — bounded snapshot for "Copy log"

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/logging/LogBuffer.kt`
- Modify: `app/src/test/java/dk/akait/hawidgets/logging/LogBufferTest.kt`

**Interfaces:**
- Produces: `fun snapshot(lastN: Int): List<String>` overload on `LogBuffer` (existing no-arg `snapshot()` unchanged). Consumed by Task 3's `RemoteLogger.recentLines(n)`.

- [ ] **Step 1: Write the failing tests**

Add to `app/src/test/java/dk/akait/hawidgets/logging/LogBufferTest.kt`, inside `class LogBufferTest` (anywhere alongside the other `@Test` methods):

```kotlin
    @Test
    fun snapshotWithLimitReturnsOnlyLastNLines() {
        val buffer = LogBuffer(maxLines = 10)
        repeat(5) { i -> buffer.add('I', "T", "line$i", ts) }
        val last3 = buffer.snapshot(3)
        assertEquals(3, last3.size)
        assertTrue(last3[0].endsWith("line2"))
        assertTrue(last3[2].endsWith("line4"))
    }

    @Test
    fun snapshotWithLimitLargerThanSizeReturnsAll() {
        val buffer = LogBuffer(maxLines = 10)
        buffer.add('I', "A", "one", ts)
        assertEquals(1, buffer.snapshot(30).size)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "dk.akait.hawidgets.logging.LogBufferTest"`
Expected: FAIL — compilation error, `snapshot(Int)` overload not defined.

- [ ] **Step 3: Add the overload**

Modify `app/src/main/java/dk/akait/hawidgets/logging/LogBuffer.kt` — add directly below the existing `snapshot()`:

```kotlin
    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    /** Seneste [lastN] linjer (nyeste sidst) — bruges af "Copy log", uden at klippe hele bufferen. */
    @Synchronized
    fun snapshot(lastN: Int): List<String> = lines.toList().takeLast(lastN)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "dk.akait.hawidgets.logging.LogBufferTest"`
Expected: PASS (10 tests green — 8 existing + 2 new)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/logging/LogBuffer.kt app/src/test/java/dk/akait/hawidgets/logging/LogBufferTest.kt
git commit -m "feat: add bounded LogBuffer snapshot for Copy log"
```

---

### Task 3: RemoteLogger — UploadResult, recentLines, restorePersistedLines, crash persistence

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/logging/RemoteLogger.kt`
- Modify: `app/src/main/java/dk/akait/hawidgets/MainActivity.kt` (one-line temporary compatibility patch to the existing `sendLogNow` — fully deleted in Task 7)

**Interfaces:**
- Consumes: `SecureStore.pendingCrashSummary`/`pendingCrashLog` (Task 1), `LogBuffer.snapshot(lastN: Int)` (Task 2).
- Produces: `sealed class RemoteLogger.UploadResult` with objects `Success`, `NotConfigured`, `NetworkError`, `Throttled`, and `data class ServerRejected(val code: Int)`; `fun flush(force: Boolean = false, configLines: List<String> = emptyList()): UploadResult` (signature change: was `Boolean`); `fun recentLines(n: Int = 30): List<String>`; `fun restorePersistedLines(lines: List<String>)`. Consumed by Task 4 (`HaWidgetsApp`), Task 6 (`ReportProblemDialog`), Task 7 (`MainActivity`'s result-message mapping).

No new automated test — `RemoteLogger` does real network I/O and Android-framework calls (`Log`, `Build`, `SecureStore`) with no test double in this project, same as before. Verified in Task 8's emulator QA (all 4 `UploadResult` branches are forced via network-toggle / bad-token / `adb shell am crash`, per the spec's Test section).

- [ ] **Step 1: Rewrite RemoteLogger.kt**

Replace the full contents of `app/src/main/java/dk/akait/hawidgets/logging/RemoteLogger.kt`:

```kotlin
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
```

- [ ] **Step 2: Patch MainActivity's existing call site (temporary compatibility — fully replaced in Task 7)**

`MainActivity.kt`'s existing `sendLogNow` function (still present until Task 7 deletes it) calls `RemoteLogger.flush(...)` and assumes it returns `Boolean`. Since Step 1 just changed that return type to `UploadResult`, patch the one line so the file keeps compiling in the meantime. Modify `app/src/main/java/dk/akait/hawidgets/MainActivity.kt`:

```kotlin
        val ok = withContext(Dispatchers.IO) { RemoteLogger.flush(force = true, configLines = configLines) }
        withContext(Dispatchers.Main) { onResult(ok) }
```

becomes:

```kotlin
        val result = withContext(Dispatchers.IO) { RemoteLogger.flush(force = true, configLines = configLines) }
        withContext(Dispatchers.Main) { onResult(result is RemoteLogger.UploadResult.Success) }
```

(This whole `sendLogNow` function is deleted outright in Task 7 Step 5 — this patch only keeps the tree compiling between Task 3 and Task 7.)

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/logging/RemoteLogger.kt app/src/main/java/dk/akait/hawidgets/MainActivity.kt
git commit -m "feat: widen RemoteLogger.flush() to UploadResult, add recentLines/restorePersistedLines"
```

---

### Task 4: Wire crash-line restoration into HaWidgetsApp

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/HaWidgetsApp.kt`

**Interfaces:**
- Consumes: `SecureStore.pendingCrashLog` (Task 1), `RemoteLogger.restorePersistedLines(lines: List<String>)` (Task 3).

No new automated test — framework wiring, verified in Task 8's QA (matches existing convention for this file).

- [ ] **Step 1: Restore persisted crash lines at startup**

Modify `app/src/main/java/dk/akait/hawidgets/HaWidgetsApp.kt`:

```kotlin
package dk.akait.hawidgets

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.logging.RemoteLogger
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
        RemoteLogger.ensureDeviceLine(this)
        SecureStore.get(this).pendingCrashLog?.let { persisted ->
            RemoteLogger.restorePersistedLines(persisted.split("\n"))
        }
        RemoteLogger.installCrashHandler(this)
        Handler(Looper.getMainLooper()).post {
            runCatching { WebView(this).destroy() }
        }
        if (SecureStore.get(this).isConfigured) {
            SyncWorker.schedule(this)
        }
    }
}
```

(Only the 3-line `pendingCrashLog?.let { ... }` block is new — the rest of the file is unchanged.)

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (aside from the known Task 7 dependency noted in Task 3 Step 2)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/HaWidgetsApp.kt
git commit -m "feat: restore persisted crash log lines at app startup"
```

---

### Task 5: Strings — sections, Report a problem, remove Send log now (3 languages)

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (English, default)
- Modify: `app/src/main/res/values-da/strings.xml` (Danish)
- Modify: `app/src/main/res/values-sv/strings.xml` (Swedish)

**Interfaces:**
- Produces string resources consumed by Task 6 (`ReportProblemDialog`) and Task 7 (`MainActivity`).

- [ ] **Step 1: English strings**

Modify `app/src/main/res/values/strings.xml` — replace the existing `<!-- Remote error logging -->` block (5 lines: `log_send_title` through `log_send_failed`) with:

```xml
    <!-- Remote error logging: Report a problem dialog -->
    <string name="report_problem_row_title">Report a problem</string>
    <string name="report_problem_row_subtitle">Send logs and an optional note to the developer</string>
    <string name="report_problem_button">Report</string>
    <string name="report_problem_title">Report a problem</string>
    <string name="report_problem_crash_title">App closed unexpectedly</string>
    <string name="report_problem_body">Send the most recent logs to the developer. Describe what happened, if you like (optional).</string>
    <string name="report_problem_crash_body">The app closed unexpectedly last time (%1$s). Send logs to help fix it? Add details, if you like (optional).</string>
    <string name="report_problem_note_label">What happened? (optional)</string>
    <string name="copy_log">Copy log</string>
    <string name="sending_in_progress">Sending report…</string>
    <string name="report_problem_button_send">Send report</string>
    <string name="report_problem_success">Report sent</string>
    <string name="report_problem_not_configured">Reporting isn\'t set up on this build</string>
    <string name="report_problem_network_error">Network error — couldn\'t send</string>
    <string name="report_problem_server_rejected">Server rejected the report</string>
    <string name="retry_action">Retry</string>
    <string name="copied_to_clipboard">Copied to clipboard</string>
```

Also insert two new section-header strings right after the existing `<string name="section_settings_title">Settings</string>` line:

```xml
    <string name="section_appearance">Appearance</string>
    <string name="section_troubleshooting">Troubleshooting</string>
```

- [ ] **Step 2: Danish strings**

Modify `app/src/main/res/values-da/strings.xml` — replace the same `<!-- Remote error logging -->` block:

```xml
    <!-- Remote error logging: Report a problem dialog -->
    <string name="report_problem_row_title">Rapportér et problem</string>
    <string name="report_problem_row_subtitle">Send logs og en valgfri note til udvikleren</string>
    <string name="report_problem_button">Rapportér</string>
    <string name="report_problem_title">Rapportér et problem</string>
    <string name="report_problem_crash_title">Appen lukkede uventet</string>
    <string name="report_problem_body">Send de seneste logs til udvikleren. Beskriv gerne hvad der skete (valgfrit).</string>
    <string name="report_problem_crash_body">Appen lukkede uventet sidste gang (%1$s). Send logs så det kan undersøges? Tilføj gerne detaljer (valgfrit).</string>
    <string name="report_problem_note_label">Hvad skete der? (valgfrit)</string>
    <string name="copy_log">Kopiér log</string>
    <string name="sending_in_progress">Sender rapport…</string>
    <string name="report_problem_button_send">Send rapport</string>
    <string name="report_problem_success">Rapport sendt</string>
    <string name="report_problem_not_configured">Rapportering er ikke opsat i denne build</string>
    <string name="report_problem_network_error">Netværksfejl — kunne ikke sende</string>
    <string name="report_problem_server_rejected">Serveren afviste rapporten</string>
    <string name="retry_action">Prøv igen</string>
    <string name="copied_to_clipboard">Kopieret til udklipsholder</string>
```

And the two section headers after `section_settings_title`:

```xml
    <string name="section_appearance">Udseende</string>
    <string name="section_troubleshooting">Fejlfinding</string>
```

- [ ] **Step 3: Swedish strings**

Modify `app/src/main/res/values-sv/strings.xml` — replace the same `<!-- Remote error logging -->` block:

```xml
    <!-- Remote error logging: Report a problem dialog -->
    <string name="report_problem_row_title">Rapportera ett problem</string>
    <string name="report_problem_row_subtitle">Skicka loggar och en valfri notering till utvecklaren</string>
    <string name="report_problem_button">Rapportera</string>
    <string name="report_problem_title">Rapportera ett problem</string>
    <string name="report_problem_crash_title">Appen stängdes oväntat</string>
    <string name="report_problem_body">Skicka de senaste loggarna till utvecklaren. Beskriv gärna vad som hände (valfritt).</string>
    <string name="report_problem_crash_body">Appen stängdes oväntat förra gången (%1$s). Skicka loggar för att hjälpa till att åtgärda det? Lägg gärna till detaljer (valfritt).</string>
    <string name="report_problem_note_label">Vad hände? (valfritt)</string>
    <string name="copy_log">Kopiera logg</string>
    <string name="sending_in_progress">Skickar rapport…</string>
    <string name="report_problem_button_send">Skicka rapport</string>
    <string name="report_problem_success">Rapport skickad</string>
    <string name="report_problem_not_configured">Rapportering är inte konfigurerad i den här byggen</string>
    <string name="report_problem_network_error">Nätverksfel — kunde inte skicka</string>
    <string name="report_problem_server_rejected">Servern avvisade rapporten</string>
    <string name="retry_action">Försök igen</string>
    <string name="copied_to_clipboard">Kopierat till urklipp</string>
```

And the two section headers after `section_settings_title`:

```xml
    <string name="section_appearance">Utseende</string>
    <string name="section_troubleshooting">Felsökning</string>
```

- [ ] **Step 4: Verify resources compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (resource merge happens as part of this; a missing/duplicate string name would fail the build)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-da/strings.xml app/src/main/res/values-sv/strings.xml
git commit -m "feat: add Report a problem + section-header strings, remove Send log now (3 languages)"
```

---

### Task 6: ReportProblemDialog composable

**Files:**
- Create: `app/src/main/java/dk/akait/hawidgets/logging/ReportProblemDialog.kt`

**Interfaces:**
- Consumes: `RemoteLogger.flush/recentLines/UploadResult` (Task 3), `collectWidgetConfigDump(context): List<String>` (existing), `formatLogLine(level, tag, message): String` (existing), string resources from Task 5.
- Produces: `@Composable fun ReportProblemDialog(crashSummary: String?, onDismiss: () -> Unit, onResult: (RemoteLogger.UploadResult) -> Unit)`. Consumed by Task 7 (`MainActivity`/`OnboardingScreen`).

No automated test — this is Compose UI + network orchestration, verified in Task 8's emulator QA (matches the project's established convention: no unit tests for Compose dialogs/screens, e.g. `ThemeRow`/`ColorThemeRow`/battery row).

- [ ] **Step 1: Write the dialog**

Create `app/src/main/java/dk/akait/hawidgets/logging/ReportProblemDialog.kt`:

```kotlin
package dk.akait.hawidgets.logging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_NOTE_LENGTH = 300
private const val SEND_TIMEOUT_MS = 15_000L

/**
 * Rig fejlrapport-dialog: note-felt (valgfrit, maks 300 tegn) + Copy log/Cancel/Send report,
 * efterfulgt af en ikke-lukkelig sender-dialog og et resultat i [RemoteLogger.UploadResult]-form
 * til [onResult] (controlleren viser en Snackbar). [crashSummary] null = almindelig menu-trigger;
 * ikke-null = automatisk åbnet efter et crash i forrige proces (viser en ekstra intro-linje).
 * Se docs/superpowers/specs/2026-07-13-settings-redesign-error-report-design.md.
 */
@Composable
fun ReportProblemDialog(
    crashSummary: String?,
    onDismiss: () -> Unit,
    onResult: (RemoteLogger.UploadResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    if (sending) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                    Text(stringResource(R.string.sending_in_progress))
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (crashSummary != null) stringResource(R.string.report_problem_crash_title)
                else stringResource(R.string.report_problem_title)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (crashSummary != null) stringResource(R.string.report_problem_crash_body, crashSummary)
                    else stringResource(R.string.report_problem_body)
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(MAX_NOTE_LENGTH) },
                    label = { Text(stringResource(R.string.report_problem_note_label)) },
                    supportingText = { Text("${note.length}/$MAX_NOTE_LENGTH") },
                    minLines = 3,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                sending = true
                scope.launch {
                    val result = sendReport(context, note)
                    sending = false
                    onDismiss()
                    onResult(result)
                }
            }) { Text(stringResource(R.string.report_problem_button_send)) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { copyLogToClipboard(context) } }) {
                    Text(stringResource(R.string.copy_log))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

private suspend fun copyLogToClipboard(context: Context) {
    val lines = RemoteLogger.recentLines(30) + collectWidgetConfigDump(context.applicationContext)
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("ha-widgets log", lines.joinToString("\n")))
    Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
}

private suspend fun sendReport(context: Context, note: String): RemoteLogger.UploadResult {
    val configLines = buildList {
        if (note.isNotBlank()) add(formatLogLine('I', "USER_NOTE", note.trim()))
        addAll(collectWidgetConfigDump(context.applicationContext))
    }
    return withTimeoutOrNull(SEND_TIMEOUT_MS) {
        withContext(Dispatchers.IO) { RemoteLogger.flush(force = true, configLines = configLines) }
    } ?: RemoteLogger.UploadResult.NetworkError
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/logging/ReportProblemDialog.kt
git commit -m "feat: add ReportProblemDialog (note field, copy log, sending state, result)"
```

---

### Task 7: MainActivity — sectioned Settings sheet + wire ReportProblemDialog + Snackbar

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/MainActivity.kt`

**Interfaces:**
- Consumes: `SecureStore.pendingCrashSummary`/`clearPendingCrash()` (Task 1), `RemoteLogger.UploadResult` (Task 3), string resources (Task 5), `ReportProblemDialog` (Task 6).
- Removes: the old `sendLogNow` private function and its `Toast`/`Dispatchers`/`withContext`/`collectWidgetConfigDump` usages (superseded by `ReportProblemDialog`'s internal `sendReport`).

No automated test — Compose UI wiring, verified in Task 8's emulator + device QA (matches this project's established convention).

- [ ] **Step 1: Update imports**

In `app/src/main/java/dk/akait/hawidgets/MainActivity.kt`, remove these four now-unused imports:

```kotlin
import dk.akait.hawidgets.logging.collectWidgetConfigDump
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

Add these in their place (keep `dk.akait.hawidgets.logging.RemoteLogger` and `androidx.compose.material.icons.filled.BugReport`, which are still used):

```kotlin
import dk.akait.hawidgets.logging.ReportProblemDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
```

- [ ] **Step 2: Add crash-trigger state, ReportProblemDialog mount, and Snackbar host to OnboardingScreen**

Modify the top of `OnboardingScreen` (right after the existing `var showTokenHelp by remember { mutableStateOf(false) }` line):

```kotlin
    var showTokenHelp by remember { mutableStateOf(false) }
    var reportCrashSummary by remember { mutableStateOf(store.pendingCrashSummary) }
    var showReportDialog by remember { mutableStateOf(reportCrashSummary != null) }
    val snackbarHostState = remember { SnackbarHostState() }
```

Modify the `SettingsSheet` call site — add the new `onReportProblem` parameter:

```kotlin
    if (showSettings) {
        SettingsSheet(
            context = context,
            pm = pm,
            connected = connected,
            onDismiss = { showSettings = false },
            onReportProblem = {
                showSettings = false
                reportCrashSummary = null
                showReportDialog = true
            }
        )
    }
```

Add the dialog mount right after that `if (showSettings) { ... }` block:

```kotlin
    if (showReportDialog) {
        ReportProblemDialog(
            crashSummary = reportCrashSummary,
            onDismiss = {
                showReportDialog = false
                if (reportCrashSummary != null) {
                    store.clearPendingCrash()
                    reportCrashSummary = null
                }
            },
            onResult = { result ->
                scope.launch {
                    val message = reportResultMessage(context, result)
                    if (result is RemoteLogger.UploadResult.NetworkError) {
                        val action = snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = context.getString(R.string.retry_action),
                            duration = SnackbarDuration.Long
                        )
                        if (action == SnackbarResult.ActionPerformed) {
                            showReportDialog = true
                        }
                    } else {
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }
        )
    }
```

Modify the `Scaffold` call to host the Snackbar:

```kotlin
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
```

(This replaces the bare `Scaffold { innerPadding ->` line — the rest of the `Scaffold` body is unchanged.)

- [ ] **Step 3: Restructure SettingsSheet into two sections**

Replace the full `SettingsSheet` function (from `@OptIn(ExperimentalMaterial3Api::class)` / `private fun SettingsSheet(...)` through its closing brace) with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    context: android.content.Context,
    pm: PowerManager,
    connected: Boolean,
    onDismiss: () -> Unit,
    onReportProblem: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.section_settings_title), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close_settings))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.section_appearance))

            val store = remember { SecureStore.get(context) }
            var themeMode by remember { mutableStateOf(store.themeMode) }
            ThemeRow(currentMode = themeMode) { mode ->
                store.themeMode = mode
                themeMode = mode
                // Widgets læser IKKE Room for tema-valget (det bor i SecureStore), så en
                // reaktiv Flow kan ikke drive gen-render. Tving derfor hver placeret widget
                // til at gen-tegne via updateAll() (ADR-5).
                updateAllWidgets(context)
                // App-UI'et gen-læser themeMode i HaWidgetsTheme ved recomposition —
                // recreate() sikrer at HELE activity-træet (inkl. denne sheet) skifter tema.
                (context as? android.app.Activity)?.recreate()
            }

            var colorTheme by remember { mutableStateOf(store.widgetColorTheme) }
            ColorThemeRow(currentTheme = colorTheme) { theme ->
                store.widgetColorTheme = theme
                colorTheme = theme
                // Samme begrundelse som ThemeRow/LanguageRow (ADR-5): widgets observerer ikke
                // SecureStore reaktivt, så en eksplicit updateAll() er nødvendig. Ingen recreate()
                // her — farvetemaet påvirker KUN widgets, ikke app-UI'et (jf. spec).
                updateAllWidgets(context)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var currentTag by remember { mutableStateOf(currentLanguageTag(context)) }
                LanguageRow(currentTag = currentTag) { tag ->
                    setAppLocale(context, tag)
                    currentTag = tag
                    // Widgets gen-læser ikke locale reaktivt (samme begrundelse som tema, ADR-5) —
                    // uden dette ville placerede widgets først skifte sprog ved næste periodiske sync.
                    updateAllWidgets(context)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.section_troubleshooting))

            val batteryExempted = remember(connected) {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.BatteryFull, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.battery_manage), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (batteryExempted) stringResource(R.string.battery_status_exempt)
                        else stringResource(R.string.battery_status_restricted),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (batteryExempted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }) { Text(stringResource(R.string.settings_open)) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.report_problem_row_title), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.report_problem_row_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onReportProblem) { Text(stringResource(R.string.report_problem_button)) }
            }
        }
    }
}
```

- [ ] **Step 4: Add SectionHeader composable**

Modify `app/src/main/java/dk/akait/hawidgets/MainActivity.kt` — add this new composable right after the existing `SectionLabel` function (which has an icon; `SectionHeader` is the plain text-only variant used inside the Settings sheet):

```kotlin
@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}
```

- [ ] **Step 5: Remove the old sendLogNow function, add reportResultMessage**

Replace the old `sendLogNow` function (the last function in the file, right after `updateAllWidgets`) with:

```kotlin
/** Oversætter et [RemoteLogger.UploadResult] til den lokaliserede besked vist i Snackbaren efter
 * "Report a problem". [RemoteLogger.UploadResult.Throttled] optræder aldrig herfra i praksis
 * (rapport-dialogen sender altid med `force = true`) — mappet til samme tekst som netværksfejl
 * som et harmløst fallback for en udtømmende `when`. */
private fun reportResultMessage(context: android.content.Context, result: RemoteLogger.UploadResult): String =
    when (result) {
        is RemoteLogger.UploadResult.Success -> context.getString(R.string.report_problem_success)
        is RemoteLogger.UploadResult.NotConfigured -> context.getString(R.string.report_problem_not_configured)
        is RemoteLogger.UploadResult.NetworkError -> context.getString(R.string.report_problem_network_error)
        is RemoteLogger.UploadResult.Throttled -> context.getString(R.string.report_problem_network_error)
        is RemoteLogger.UploadResult.ServerRejected -> context.getString(R.string.report_problem_server_rejected)
    }
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green (existing suite + Task 2's 2 new `LogBufferTest` cases).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/MainActivity.kt
git commit -m "feat: sectioned Settings sheet, wire ReportProblemDialog + Snackbar, fix scroll/expand bug"
```

---

### Task 8: Version bump, CLAUDE.md, full build, emulator + device QA

**Files:**
- Modify: `app/build.gradle.kts` (version bump)
- Modify: `CLAUDE.md` (status entry + known-quirks note)

**Interfaces:** none new — integration verification of Tasks 1–7.

- [ ] **Step 1: Bump version**

Modify `app/build.gradle.kts`:

```kotlin
        versionCode = 78
        versionName = "0.2.78"
```

- [ ] **Step 2: Full unit test run**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, every test green.

- [ ] **Step 3: Full debug build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, `app/build/outputs/apk/debug/app-debug.apk` produced.

- [ ] **Step 4: Emulator QA (pixel_test)**

Install: `<SDK>/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`

Verify, in order:
1. App launches without crash. Open Settings — confirm it always opens fully expanded (no partial-drag state), shows two section headers ("Appearance"/"Troubleshooting" or the active language's translation), and every row (including "Report a problem" at the bottom) is visible/reachable without needing to discover a hidden drag gesture.
2. Force an artificially tall sheet: enable Android's largest "Font size" accessibility setting, reopen Settings, confirm the sheet scrolls (via `verticalScroll`) instead of clipping content.
3. Tap "Report a problem" → confirm the note-field dialog opens (Settings sheet closes first), type near the 300-char boundary and confirm the counter and hard cap. Tap "Copy log" → confirm a Toast confirmation and that pasting elsewhere shows recent log lines + widget config. Confirm the dialog stays open after Copy log.
4. Tap "Send report" with `LOG_UPLOAD_TOKEN` unset in `local.properties` (default fresh-checkout state) → confirm the Snackbar shows the "not configured" message (not alarming/error-styled) with no ~15s wait.
5. Set a real `LOG_UPLOAD_TOKEN`, rebuild/reinstall. Turn off the emulator's network, tap "Report a problem" → "Send report" → confirm the non-dismissible spinner appears, then (after timeout or immediate `IOException`) the Snackbar shows the network-error message with a "Retry" action; tapping Retry reopens the dialog.
6. Restore network, repeat Send report → confirm success Snackbar, and (via logcat or the rtr.dk admin panel if reachable) that the uploaded body includes the `I [USER_NOTE] ...` line (if a note was typed) and the `I [CONFIG] ...` widget-config section.
7. Trigger a real crash: `adb shell am crash dk.akait.hawidgets`. Reopen the app — confirm the `ReportProblemDialog` opens automatically with the crash-variant title/body (mentioning the exception). Tap Cancel; reopen the app again — confirm it does NOT reopen automatically a second time (ask-once verified). Trigger another crash, this time tap "Send report" — confirm the uploaded body contains the `E [CRASH] ...` line and stacktrace from the crashed process (verifies `restorePersistedLines` actually carried the old process' buffer into the new one).

- [ ] **Step 5: Device QA (Galaxy S23)**

Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (reinstall, never uninstall).

Repeat Step 4 items 1, 3, 4, and 6 at minimum — item 1 (the original bug report: sheet not opening fully) is the most important to confirm on the real device that reported it.

- [ ] **Step 6: Update CLAUDE.md**

Add a new entry under the `### M2 — Native entity-widgets` status section (after the most recent `v0.2.77` entry, following the file's existing chronological format) documenting:
- Two UX reviewers (info-architecture + interaction/accessibility) were dispatched against an emulator screenshot + code, both mockups (section layout, report-dialog flow) approved by the user unchanged.
- The Settings-sheet bugfix (`skipPartiallyExpanded`, `verticalScroll`, 48dp row minimums) and its likely root cause on the S23.
- The two-section restructure (Appearance/Troubleshooting) and the "Send log now" → "Report a problem" replacement.
- `RemoteLogger.flush()`'s `Boolean` → `UploadResult` sealed-class change.
- The crash-persistence mechanism (`SecureStore.pendingCrashSummary/pendingCrashLog` + `RemoteLogger.restorePersistedLines`) and the "ask once per crash" behavior.
- QA results from Steps 4–5.

Also add one line to the "Kendte quirks / beslutninger" section:

```markdown
- **Settings-arket åbner altid fuldt udfoldet:** `rememberModalBottomSheetState(skipPartiallyExpanded = true)`
  siden v0.2.78 — roden til at "Send log now"/"Report a problem"-rækken kunne være unåelig var
  standardadfærdens delvist-udfoldede starttilstand kombineret med en Column uden `verticalScroll`.
```

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts CLAUDE.md
git commit -m "chore: bump v0.2.78 (settings redesign + Report a problem dialog)"
```

---

## Post-plan note

Do not push to the remote until the user explicitly asks — this project's convention is commit locally after QA passes, push only on request.
