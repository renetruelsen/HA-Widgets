# Remote Error Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send crash logs, Home Assistant connectivity/API errors, and a manual "send diagnostics" flow from the HA-Widgets Android app to the rtr.dk Log Collector, so bugs from real installations can be debugged remotely before Play Store release.

**Architecture:** A new `dk.akait.hawidgets.logging` package holds a pure, unit-tested ring buffer (`LogBuffer`) and line formatter, a pure widget-config-dump formatter (`formatWidgetConfigDump`), and a singleton `RemoteLogger` object that owns the OkHttp upload call (blocking, matching `HaApiClient`'s style but synchronous so it can run from a crash handler). `HaApiClient` calls `RemoteLogger.w(...)` on its existing error paths. `HaWidgetsApp` installs a global uncaught-exception handler. A new row in `MainActivity`'s settings sheet triggers a manual, forced flush.

**Tech Stack:** Kotlin, OkHttp 4.12.0 (already a dependency), Room 2.7.0 (already a dependency), JUnit4 (existing test setup, no new test framework).

## Global Constraints

- Upload token (`LOG_UPLOAD_TOKEN`) must live in `local.properties` (git-ignored) → `BuildConfig` field, default `""` if unset. Never hardcoded in a git-tracked file — this exact mistake (a real HA token in `build.gradle.kts`) previously leaked to the public GitHub repo (v0.2.45) and was removed for that reason.
- Endpoint: `POST https://rtr.dk/api/logs`, `Content-Type: text/plain; charset=utf-8`, body = log lines joined by `\n`.
- Required headers: `Authorization: Bearer <token>`, `X-App-Id: ha-widgets`, `X-App-Version: <BuildConfig.VERSION_NAME>`, `X-App-Platform: android`, and an explicit `User-Agent` (OkHttp does not set a useful default one; rtr.dk's WAF blocks missing/generic UAs with HTTP 455).
- Line format: `<ISO-8601 UTC with millis> <LEVEL> [<TAG>] <message>` — exactly one space around the level letter and around `[TAG]`. Only a line containing the literal substring `" E ["` triggers a "Fejl" status + email server-side, so level choice per call site matters (HA connectivity errors are `W`, not `E` — see Task 5).
- Ring buffer cap: 300 lines (oldest evicted first).
- Automatic (non-forced) flush throttle: 30 seconds.
- Logging is active in **both** build types (debug and release) — no `BuildConfig.DEBUG` gating.
- Widget-config dump (entity IDs/domains/actions only, no HA URL/token) is included **only** on crash-flush and manual-send-flush, never on the routine throttled auto-flush.
- No screenshot attachment (explicitly out of scope — decided during brainstorming).
- Spec: [`docs/superpowers/specs/2026-07-12-remote-error-logging-design.md`](../specs/2026-07-12-remote-error-logging-design.md). Integration reference: [`docs/ha-widgets-logging.md`](../../ha-widgets-logging.md).

---

### Task 1: LogBuffer — pure ring buffer + line formatting

**Files:**
- Create: `app/src/main/java/dk/akait/hawidgets/logging/LogBuffer.kt`
- Test: `app/src/test/java/dk/akait/hawidgets/logging/LogBufferTest.kt`

**Interfaces:**
- Produces: `class LogBuffer(maxLines: Int = 300)` with methods `add(level: Char, tag: String, message: String, timestamp: Instant = Instant.now())`, `addRaw(line: String)`, `snapshot(): List<String>`, `body(): String`, `isEmpty(): Boolean`, `clear()`.
- Produces: top-level `fun formatLogLine(level: Char, tag: String, message: String, timestamp: Instant = Instant.now()): String`.
- Consumes: nothing (pure Kotlin, `java.time.Instant`/`DateTimeFormatter` only — no Android framework dependency, safe to unit test on the local JVM).

- [ ] **Step 1: Write the failing test**

```kotlin
package dk.akait.hawidgets.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LogBufferTest {

    private val ts = Instant.parse("2026-07-12T10:00:00.000Z")

    @Test
    fun formatLogLineProducesExactShape() {
        val line = formatLogLine('E', "HA", "SocketException: Connection reset", ts)
        assertEquals("2026-07-12T10:00:00.000Z E [HA] SocketException: Connection reset", line)
    }

    @Test
    fun formatLogLinePreservesMillis() {
        val withMillis = Instant.parse("2026-07-12T10:00:04.512Z")
        val line = formatLogLine('W', "HA", "Retry 2/3 GET /api/states", withMillis)
        assertEquals("2026-07-12T10:00:04.512Z W [HA] Retry 2/3 GET /api/states", line)
    }

    @Test
    fun addAppendsFormattedLine() {
        val buffer = LogBuffer(maxLines = 10)
        buffer.add('I', "BOOT", "Widget host ready", ts)
        assertEquals(
            listOf("2026-07-12T10:00:00.000Z I [BOOT] Widget host ready"),
            buffer.snapshot()
        )
    }

    @Test
    fun ringBufferEvictsOldestWhenOverCap() {
        val buffer = LogBuffer(maxLines = 3)
        repeat(5) { i -> buffer.add('I', "T", "line$i", ts) }
        val snapshot = buffer.snapshot()
        assertEquals(3, snapshot.size)
        assertTrue(snapshot[0].endsWith("line2"))
        assertTrue(snapshot[2].endsWith("line4"))
    }

    @Test
    fun addRawAppendsUnformattedLine() {
        val buffer = LogBuffer(maxLines = 10)
        buffer.addRaw("    #0 _HttpClient.send (package:x)")
        assertEquals(listOf("    #0 _HttpClient.send (package:x)"), buffer.snapshot())
    }

    @Test
    fun bodyJoinsLinesWithNewline() {
        val buffer = LogBuffer(maxLines = 10)
        buffer.add('I', "A", "one", ts)
        buffer.add('I', "B", "two", ts)
        assertEquals(
            "2026-07-12T10:00:00.000Z I [A] one\n2026-07-12T10:00:00.000Z I [B] two",
            buffer.body()
        )
    }

    @Test
    fun isEmptyReflectsBufferState() {
        val buffer = LogBuffer(maxLines = 10)
        assertTrue(buffer.isEmpty())
        buffer.add('I', "A", "one", ts)
        assertTrue(!buffer.isEmpty())
    }

    @Test
    fun clearEmptiesBuffer() {
        val buffer = LogBuffer(maxLines = 10)
        buffer.add('I', "A", "one", ts)
        buffer.clear()
        assertTrue(buffer.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "dk.akait.hawidgets.logging.LogBufferTest"`
Expected: FAIL — compilation error, `LogBuffer`/`formatLogLine` not defined.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package dk.akait.hawidgets.logging

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/**
 * Ren linjeformatering til rtr.dk Log Collector (docs/ha-widgets-logging.md):
 * `<ISO-8601 UTC med millisekunder> <NIVEAU> [<TAG>] <besked>`. Kun linjer med literalt
 * " E [" udløser Fejl-status/mail server-side, så niveau-valg pr. kaldested er vigtigt
 * (se HaApiClient — forbindelsesfejl er bevidst W, ikke E).
 */
fun formatLogLine(level: Char, tag: String, message: String, timestamp: Instant = Instant.now()): String =
    "${TIMESTAMP_FORMATTER.format(timestamp)} $level [$tag] $message"

/**
 * Ring-buffer af log-linjer der skal uploades. Cap på [maxLines] — ældste linje falder ud
 * først. Thread-safe (kaldes både fra UI/IO-coroutines og fra en crashende tråd).
 */
class LogBuffer(private val maxLines: Int = 300) {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(level: Char, tag: String, message: String, timestamp: Instant = Instant.now()) {
        addRaw(formatLogLine(level, tag, message, timestamp))
    }

    /** Tilføjer en linje uden formatering — bruges til fri tekst som stacktraces. */
    @Synchronized
    fun addRaw(line: String) {
        lines.addLast(line)
        while (lines.size > maxLines) lines.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun body(): String = lines.joinToString("\n")

    @Synchronized
    fun isEmpty(): Boolean = lines.isEmpty()

    @Synchronized
    fun clear() = lines.clear()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "dk.akait.hawidgets.logging.LogBufferTest"`
Expected: PASS (8 tests green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/logging/LogBuffer.kt app/src/test/java/dk/akait/hawidgets/logging/LogBufferTest.kt
git commit -m "feat: add LogBuffer ring buffer for remote logging"
```

---

### Task 2: Widget-config dump (pure formatter + Room/prefs query)

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/data/db/MultiWidgetDao.kt` (add `getAll()`)
- Modify: `app/src/main/java/dk/akait/hawidgets/data/WidgetConfig.kt` (add `WidgetConfigStore.getAll()`)
- Create: `app/src/main/java/dk/akait/hawidgets/logging/WidgetConfigDump.kt`
- Test: `app/src/test/java/dk/akait/hawidgets/logging/WidgetConfigDumpTest.kt`

**Interfaces:**
- Consumes: `MultiWidgetEntity`, `MultiWidgetSlotEntity`, `MultiWidgetSlotEntity.secondaryColumns()` (existing, `internal`, in `dk.akait.hawidgets.widget.multientity`), `SecondaryColumns.showValueOrDefault()` (existing, same package), `WidgetConfig` (existing).
- Produces: pure `fun formatWidgetConfigDump(shortcuts: Map<Int, WidgetConfig>, widgets: List<MultiWidgetEntity>, slotsByWidget: Map<Int, List<MultiWidgetSlotEntity>>): List<String>` and `suspend fun collectWidgetConfigDump(context: Context): List<String>` — Task 3's crash handler and Task 6's manual-send button call the latter.
- This task does **not** depend on `RemoteLogger` (Task 3) — it only touches Room/`SharedPreferences` and produces plain strings, so it can be built and tested standalone first.

- [ ] **Step 1: Add DAO/store read-all methods**

Modify `app/src/main/java/dk/akait/hawidgets/data/db/MultiWidgetDao.kt` — add inside the `@Dao interface MultiWidgetDao` block (anywhere alongside the other `@Query` methods):

```kotlin
    @Query("SELECT * FROM multi_widget")
    suspend fun getAll(): List<MultiWidgetEntity>
```

Modify `app/src/main/java/dk/akait/hawidgets/data/WidgetConfig.kt` — inside `class WidgetConfigStore`, add:

```kotlin
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
```

- [ ] **Step 2: Write the failing test for the pure formatter**

```kotlin
package dk.akait.hawidgets.logging

import dk.akait.hawidgets.data.DisplayMode
import dk.akait.hawidgets.data.WidgetConfig
import dk.akait.hawidgets.data.db.MultiWidgetEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetConfigDumpTest {

    @Test
    fun dumpsShortcutsAndMultiWidgetsWithSlotsAndSecondaryChips() {
        val shortcuts = mapOf(
            12 to WidgetConfig(dashboardPath = "lovelace-hjem", title = "Hjem", displayMode = DisplayMode.FULLSCREEN)
        )
        val widgets = listOf(MultiWidgetEntity(appWidgetId = 20))
        val mainSlot = MultiWidgetSlotEntity(
            appWidgetId = 20,
            slotIndex = 0,
            displayEntityId = "light.hue_stuelampe",
            displayDomain = "light",
            actionEntityId = "light.hue_stuelampe",
            actionDomain = "light",
            action = "TOGGLE",
            label = "",
            confirmAction = true,
            showIcon = true,
            secondary1DisplayEntityId = "sensor.temp",
            secondary1DisplayDomain = "sensor",
            secondary1ActionEntityId = "sensor.temp",
            secondary1ActionDomain = "sensor",
            secondary1Action = "NONE",
            secondary1ShowValue = true,
            secondary1Label = "Temp",
        )
        val slotsByWidget = mapOf(20 to listOf(mainSlot))

        val lines = formatWidgetConfigDump(shortcuts, widgets, slotsByWidget)

        assertEquals(
            listOf(
                "I [CONFIG] shortcut widget=12 dashboard=lovelace-hjem",
                "I [CONFIG] multi widget=20 slots=1",
                "I [CONFIG]   slot0 display=light.hue_stuelampe domain=light action=TOGGLE " +
                    "target=light.hue_stuelampe confirm=true showIcon=true",
                "I [CONFIG]   slot0.secondary1 display=sensor.temp action=NONE showValue=true label=\"Temp\"",
            ),
            lines
        )
    }

    @Test
    fun emptyWidgetsAndShortcutsProduceEmptyDump() {
        assertEquals(emptyList<String>(), formatWidgetConfigDump(emptyMap(), emptyList(), emptyMap()))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "dk.akait.hawidgets.logging.WidgetConfigDumpTest"`
Expected: FAIL — `formatWidgetConfigDump`/`collectWidgetConfigDump` not defined.

- [ ] **Step 4: Write the implementation**

```kotlin
package dk.akait.hawidgets.logging

import android.content.Context
import dk.akait.hawidgets.data.WidgetConfig
import dk.akait.hawidgets.data.WidgetConfigStore
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.MultiWidgetEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.multientity.secondaryColumns
import dk.akait.hawidgets.widget.multientity.showValueOrDefault

/**
 * Ren serialisering af den aktuelle widget-konfiguration til log-linjer — bruges ved crash og
 * "Send log nu" (ikke ved rutine-W-flush), så en fejlrapport kan bruges til at genskabe et
 * widget-setup. Kun entity-ID'er/domæner/handlinger — ingen HA-URL/token.
 */
fun formatWidgetConfigDump(
    shortcuts: Map<Int, WidgetConfig>,
    widgets: List<MultiWidgetEntity>,
    slotsByWidget: Map<Int, List<MultiWidgetSlotEntity>>,
): List<String> = buildList {
    shortcuts.toSortedMap().forEach { (id, cfg) ->
        add("I [CONFIG] shortcut widget=$id dashboard=${cfg.dashboardPath}")
    }
    widgets.sortedBy { it.appWidgetId }.forEach { widget ->
        val slots = slotsByWidget[widget.appWidgetId].orEmpty()
        add("I [CONFIG] multi widget=${widget.appWidgetId} slots=${slots.size}")
        slots.forEach { slot ->
            add(
                "I [CONFIG]   slot${slot.slotIndex} display=${slot.displayEntityId} domain=${slot.displayDomain} " +
                    "action=${slot.action} target=${slot.actionEntityId} confirm=${slot.confirmAction} " +
                    "showIcon=${slot.showIcon}"
            )
            slot.secondaryColumns().forEachIndexed { index, secondary ->
                val displayId = secondary.displayEntityId ?: return@forEachIndexed
                val labelPart = secondary.label?.takeIf { it.isNotBlank() }?.let { " label=\"$it\"" } ?: ""
                add(
                    "I [CONFIG]   slot${slot.slotIndex}.secondary${index + 1} display=$displayId " +
                        "action=${secondary.action ?: "NONE"} showValue=${secondary.showValueOrDefault()}$labelPart"
                )
            }
        }
    }
}

/** Henter den aktuelle widget-konfiguration fra Room + SharedPreferences og serialiserer den. */
suspend fun collectWidgetConfigDump(context: Context): List<String> {
    val multiDao = AppDatabase.get(context).multiWidgetDao()
    val widgets = multiDao.getAll()
    val slotsByWidget = widgets.associate { it.appWidgetId to multiDao.getSlots(it.appWidgetId) }
    val shortcuts = WidgetConfigStore.get(context).getAll()
    return formatWidgetConfigDump(shortcuts, widgets, slotsByWidget)
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "dk.akait.hawidgets.logging.WidgetConfigDumpTest"`
Expected: PASS (2 tests green)

- [ ] **Step 6: Run the full unit test suite to catch regressions**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all existing + new tests green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/data/db/MultiWidgetDao.kt app/src/main/java/dk/akait/hawidgets/data/WidgetConfig.kt app/src/main/java/dk/akait/hawidgets/logging/WidgetConfigDump.kt app/src/test/java/dk/akait/hawidgets/logging/WidgetConfigDumpTest.kt
git commit -m "feat: add widget-config dump for crash/manual-send log context"
```

---

### Task 3: RemoteLogger — token config, device-info line, throttled/forced flush, crash handler

**Files:**
- Modify: `app/build.gradle.kts` (read `local.properties`, add `buildConfigField`)
- Create: `app/src/main/java/dk/akait/hawidgets/logging/RemoteLogger.kt`

**Interfaces:**
- Consumes: `LogBuffer`/`formatLogLine` (Task 1), `collectWidgetConfigDump` (Task 2), `BuildConfig.LOG_UPLOAD_TOKEN`/`BuildConfig.VERSION_NAME` (this task's gradle change).
- Produces: `RemoteLogger.i(tag: String, message: String)`, `RemoteLogger.w(tag: String, message: String)`, `RemoteLogger.e(tag: String, message: String)` (all record to the buffer; `w`/`e` also attempt a throttled auto-flush), `RemoteLogger.ensureDeviceLine(context: Context)`, `RemoteLogger.flush(force: Boolean = false, configLines: List<String> = emptyList()): Boolean` (blocking — callers on a coroutine must wrap in `Dispatchers.IO`), `RemoteLogger.installCrashHandler(context: Context)`. These are used by Task 4 (crash wiring), Task 5 (`HaApiClient`), and Task 6 (settings UI).

**No unit tests in this task** — `RemoteLogger` does real network I/O and reads `android.os.Build`/`PackageManager`, which are Android-framework calls with no test double in this project (same reason `HaApiClient` has no unit tests). Verified manually in Task 7's QA pass instead.

- [ ] **Step 1: Add local.properties-backed BuildConfig field**

Modify `app/build.gradle.kts` — add the import and property-loading at the top of the file, and reference it inside `defaultConfig`:

```kotlin
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "dk.akait.hawidgets"
    compileSdk = 35

    defaultConfig {
        applicationId = "dk.akait.hawidgets"
        minSdk = 26
        targetSdk = 35
        versionCode = 76
        versionName = "0.2.76"
        buildConfigField(
            "String",
            "LOG_UPLOAD_TOKEN",
            "\"${localProperties.getProperty("LOG_UPLOAD_TOKEN", "")}\""
        )
    }
    ...
```

(Leave the rest of the file — `buildTypes`, `compileOptions`, `dependencies`, etc. — unchanged. `versionCode`/`versionName` get bumped in Task 7, not here.)

- [ ] **Step 2: Verify the gradle change compiles**

Run: `./gradlew :app:generateDebugBuildConfig`
Expected: BUILD SUCCESSFUL, and `app/build/generated/source/buildConfig/debug/dk/akait/hawidgets/BuildConfig.java` contains `public static final String LOG_UPLOAD_TOKEN = "";` (empty, since `local.properties` has no such key yet — that's expected; the token is added locally by whoever builds a release meant to actually reach rtr.dk).

- [ ] **Step 3: Write RemoteLogger**

```kotlin
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
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/dk/akait/hawidgets/logging/RemoteLogger.kt
git commit -m "feat: add RemoteLogger with local.properties-backed upload token"
```

---

### Task 4: Wire the crash handler into HaWidgetsApp

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/HaWidgetsApp.kt`

**Interfaces:**
- Consumes: `RemoteLogger.ensureDeviceLine(context)` and `RemoteLogger.installCrashHandler(context)` (Task 3).

No new automated test — this is framework wiring (a real uncaught-exception handler) verified in Task 7's manual QA pass, exactly like the rest of `HaWidgetsApp.onCreate()` (WebView pre-warm, `SyncWorker.schedule`) has no unit test today.

- [ ] **Step 1: Install the crash handler and device-info line at app startup**

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

class HaWidgetsApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        RemoteLogger.ensureDeviceLine(this)
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

(Only the two `RemoteLogger` lines and the new import are additions — the rest of the file is unchanged.)

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/HaWidgetsApp.kt
git commit -m "feat: install crash handler and device-info logging at app startup"
```

---

### Task 5: Log HA connectivity/API errors as W from HaApiClient

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/data/HaApiClient.kt`

**Interfaces:**
- Consumes: `RemoteLogger.w(tag: String, message: String)` (Task 3).

Scope note: only real connection-level failures are logged (network exceptions, non-2xx HTTP on `checkConnection`/`callService`) — not a plain "entity not found" 404 on `getState`, which is more a config-drift signal than a connectivity problem and would be noisy during normal sync of a since-deleted entity. No automated test (matches the existing, already-untested `HaApiClient`); verified manually in Task 7.

- [ ] **Step 1: Log checkConnection errors**

Modify `checkConnection()` in `app/src/main/java/dk/akait/hawidgets/data/HaApiClient.kt`:

```kotlin
    suspend fun checkConnection(): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$base/api/")
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> Result.Ok
                    401 -> Result.Error("Token afvist (401). Tjek dit long-lived token.")
                        .also { RemoteLogger.w("HA", it.message) }
                    else -> Result.Error("Uventet svar fra HA: HTTP ${response.code}")
                        .also { RemoteLogger.w("HA", it.message) }
                }
            }
        } catch (e: Exception) {
            val msg = "Kunne ikke nå HA: ${e.message ?: e.javaClass.simpleName}"
            RemoteLogger.w("HA", msg)
            Result.Error(msg)
        }
    }
```

- [ ] **Step 2: Log getState network exceptions (not plain non-2xx)**

Modify `getState()`:

```kotlin
    suspend fun getState(entityId: String): EntityStateEntity? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$base/api/states/$entityId")
                .header("Authorization", authHeader)
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                EntityStateEntity(
                    entityId = json.getString("entity_id"),
                    state = json.getString("state"),
                    attributesJson = json.optJSONObject("attributes")?.toString() ?: "{}",
                    lastUpdated = System.currentTimeMillis(),
                )
            }
        } catch (e: Exception) {
            RemoteLogger.w("HA", "getState($entityId) failed: ${e.message ?: e.javaClass.simpleName}")
            null
        }
    }
```

- [ ] **Step 3: Log callService errors**

Modify `callService()`:

```kotlin
    suspend fun callService(
        domain: String,
        service: String,
        entityId: String,
        extraData: Map<String, Any> = emptyMap(),
        fast: Boolean = false,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("entity_id", entityId)
                extraData.forEach { (k, v) -> put(k, v) }
            }
            val body = bodyJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$base/api/services/$domain/$service")
                .header("Authorization", authHeader)
                .post(body)
                .build()
            (if (fast) httpFast else http).newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.Ok
                else Result.Error("HTTP ${response.code}").also {
                    RemoteLogger.w("HA", "callService $domain.$service failed: ${it.message}")
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            RemoteLogger.w("HA", "callService $domain.$service exception: $msg")
            Result.Error(msg)
        }
    }
```

- [ ] **Step 4: Log listStatesByDomains network exceptions**

Modify `listStatesByDomains()` — only the `catch` block changes:

```kotlin
        } catch (e: Exception) {
            RemoteLogger.w("HA", "listStatesByDomains failed: ${e.message ?: e.javaClass.simpleName}")
            emptyList()
        }
```

- [ ] **Step 5: Add the import**

Add near the top of `HaApiClient.kt`, with the other `dk.akait.hawidgets` imports:

```kotlin
import dk.akait.hawidgets.logging.RemoteLogger
```

- [ ] **Step 6: Verify it compiles and existing tests still pass**

Run: `./gradlew :app:compileDebugKotlin testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/data/HaApiClient.kt
git commit -m "feat: log HA connectivity/API errors to RemoteLogger as warnings"
```

---

### Task 6: "Send log nu" row in the settings sheet

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml` (English, default)
- Modify: `app/src/main/res/values-da/strings.xml` (Danish)
- Modify: `app/src/main/res/values-sv/strings.xml` (Swedish)

**Interfaces:**
- Consumes: `RemoteLogger.flush(force: Boolean, configLines: List<String>): Boolean` (Task 3), `collectWidgetConfigDump(context): List<String>` (Task 2), `HaWidgetsApp.appScope` (existing).

No automated test — this is Compose UI wiring, verified visually/functionally in Task 7 (matches how `ThemeRow`/`ColorThemeRow`/battery row have no unit tests either — they're covered by manual QA per this project's established workflow).

- [ ] **Step 1: Add strings (English default)**

Modify `app/src/main/res/values/strings.xml` — insert right after the existing battery strings (after the line `<string name="battery_status_restricted">Restricted by battery optimization</string>`):

```xml
    <!-- Remote error logging -->
    <string name="log_send_title">Error log</string>
    <string name="log_send_subtitle">Send a diagnostic log to the developer</string>
    <string name="log_send_now">Send log now</string>
    <string name="log_send_success">Log sent</string>
    <string name="log_send_failed">Couldn\'t send log</string>
```

- [ ] **Step 2: Add strings (Danish)**

Modify `app/src/main/res/values-da/strings.xml` — same insertion point:

```xml
    <!-- Remote error logging -->
    <string name="log_send_title">Fejllog</string>
    <string name="log_send_subtitle">Send en diagnostik-log til udvikleren</string>
    <string name="log_send_now">Send log nu</string>
    <string name="log_send_success">Log sendt</string>
    <string name="log_send_failed">Kunne ikke sende log</string>
```

- [ ] **Step 3: Add strings (Swedish)**

Modify `app/src/main/res/values-sv/strings.xml` — same insertion point:

```xml
    <!-- Remote error logging -->
    <string name="log_send_title">Fellogg</string>
    <string name="log_send_subtitle">Skicka en diagnostikslogg till utvecklaren</string>
    <string name="log_send_now">Skicka logg nu</string>
    <string name="log_send_success">Logg skickad</string>
    <string name="log_send_failed">Kunde inte skicka logg</string>
```

- [ ] **Step 4: Add the settings-sheet row and its helper function**

Modify `app/src/main/java/dk/akait/hawidgets/MainActivity.kt`. Add two imports near the existing `dk.akait.hawidgets.*` imports:

```kotlin
import dk.akait.hawidgets.logging.RemoteLogger
import dk.akait.hawidgets.logging.collectWidgetConfigDump
```

Add these near the existing `androidx.compose.material.icons.filled.*` and `kotlinx.coroutines.*` imports:

```kotlin
import androidx.compose.material.icons.filled.BugReport
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

Add a new private helper function right next to `updateAllWidgets` (same file, top level, e.g. directly below it):

```kotlin
/** Forcerer en upload af den nuværende log-buffer + widget-config-dump, uanset 30s-throttlen.
 * Kører på [HaWidgetsApp.appScope] (ikke en composable-scope) så den overlever at
 * indstillings-arket lukkes, samme begrundelse som [updateAllWidgets]. */
private fun sendLogNow(context: android.content.Context, onResult: (Boolean) -> Unit) {
    val app = context.applicationContext as HaWidgetsApp
    app.appScope.launch {
        val configLines = collectWidgetConfigDump(context.applicationContext)
        val ok = withContext(Dispatchers.IO) { RemoteLogger.flush(force = true, configLines = configLines) }
        withContext(Dispatchers.Main) { onResult(ok) }
    }
}
```

Add the new row inside `SettingsSheet`, right after the existing battery-optimization `Row { ... }` block (i.e., as the last thing in the `Column` before its closing brace):

```kotlin
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.log_send_title), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.log_send_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = {
                    sendLogNow(context) { ok ->
                        Toast.makeText(
                            context,
                            if (ok) R.string.log_send_success else R.string.log_send_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) { Text(stringResource(R.string.log_send_now)) }
            }
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/MainActivity.kt app/src/main/res/values/strings.xml app/src/main/res/values-da/strings.xml app/src/main/res/values-sv/strings.xml
git commit -m "feat: add \"Send log now\" row to settings sheet"
```

---

### Task 7: Version bump, CLAUDE.md update, full build, emulator + device QA

**Files:**
- Modify: `app/build.gradle.kts` (version bump)
- Modify: `CLAUDE.md` (status entry + known-quirks note)

**Interfaces:** none new — this task is integration verification of everything from Tasks 1–6.

- [ ] **Step 1: Bump version**

Modify `app/build.gradle.kts`:

```kotlin
        versionCode = 77
        versionName = "0.2.77"
```

- [ ] **Step 2: Full unit test run**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, every test green (existing suite + `LogBufferTest` + `WidgetConfigDumpTest`).

- [ ] **Step 3: Full debug build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, `app/build/outputs/apk/debug/app-debug.apk` produced.

- [ ] **Step 4: Emulator QA (pixel_test)**

Install: `<SDK>/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`

Verify, in order:
1. App launches without crash (confirms `RemoteLogger.ensureDeviceLine`/`installCrashHandler` don't blow up at startup).
2. With `LOG_UPLOAD_TOKEN` unset in `local.properties` (the default state for a fresh checkout): tap "Send log nu" in Settings → Toast shows "Kunne ikke sende log" (or whichever app-language is active) — confirms the no-op-when-no-token path doesn't crash and gives clear (if technically "failed") feedback.
3. Set a real `LOG_UPLOAD_TOKEN` in `local.properties`, rebuild/reinstall. Turn off the emulator's network (or point `SecureStore`'s base URL at an unreachable host), trigger a sync (`SyncWorker.runNow` fires automatically after any config save, or wait for the periodic one) — confirm via `adb logcat -s RemoteLogger` and, if reachable, the rtr.dk admin panel, that a `W [HA] ...` line was uploaded.
4. Restore network. Tap "Send log nu" → confirm HTTP `202` in logcat and "Log sendt" Toast, and that the uploaded body includes an `I [CONFIG] ...` section matching whatever widgets are currently placed.
5. Trigger a real crash to verify the crash path end-to-end: `adb shell am crash dk.akait.hawidgets` (available on API 33+ emulators) forces an uncaught exception in the running process. Confirm via logcat that an `E [CRASH] ...` line plus stacktrace plus `I [CONFIG] ...` lines were uploaded, and that the app still shows the standard Android "app has stopped" behavior afterward (i.e., `installCrashHandler` correctly delegated to the previous handler).

- [ ] **Step 5: Device QA (Galaxy S23)**

Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (reinstall, never uninstall — preserves existing config/token).

Repeat the same checklist as Step 4, items 2–4 at minimum (the `adb shell am crash` step is optional here since it's already covered on the emulator).

- [ ] **Step 6: Update CLAUDE.md**

Add a new entry under the `### M2 — Native entity-widgets` status section (after the most recent `v0.2.76` entry, following the file's existing chronological format) documenting:
- What was built (RemoteLogger, crash handler, HA-error breadcrumbs, "Send log nu", widget-config dump).
- The `local.properties` → `LOG_UPLOAD_TOKEN` requirement, and why (v0.2.45 precedent).
- QA results from Steps 4–5.

Also add one line to the "Kendte quirks / beslutninger" section:

```markdown
- **Remote logging kræver `LOG_UPLOAD_TOKEN` i `local.properties`:** uden nøglen bygger og kører
  appen fint, men `RemoteLogger.flush()` er en stille no-op (ingen logs når rtr.dk). Sæt
  `LOG_UPLOAD_TOKEN=<token>` lokalt for at aktivere upload; aldrig committet til git (v0.2.45-lære).
```

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts CLAUDE.md
git commit -m "chore: bump v0.2.77 (remote error logging: crash reports, HA-error breadcrumbs, send-log-now, widget-config dump)"
```

---

## Post-plan note

Do not push to the remote until the user explicitly asks — this project's convention (per repeated CLAUDE.md workflow) is commit locally after QA passes, push only on request.
