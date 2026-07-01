# Multi-entity widget ("kombineret widget") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new Glance widget type (`MultiEntityWidget`) that shows up to 5 arbitrary HA
entities in one fixed-size home-screen widget, each slot with its own independently
configurable on-click action (which may target a different entity than the one displayed).

**Architecture:** New relational Room tables (`multi_widget`, `multi_widget_slot`) parallel
to the existing `entity_widget` table. A single Compose config Activity handles both first-
time setup and reconfiguration via a list-builder UI (add/edit/remove/reorder slots). Widget
rendering reuses the existing `WidgetCompactLayout` per slot plus the existing generic
`ToggleEntityAction`/`TriggerEntityAction`/`RefreshEntityAction`/`RangeControlActivity`
building blocks (generalized where needed) — no parallel action system is introduced.

**Tech Stack:** Kotlin, Jetpack Glance (widget rendering), Jetpack Compose + Material3
(config activity), Room (persistence), OkHttp via existing `HaApiClient`.

**Spec:** `docs/superpowers/specs/2026-07-01-multi-entity-widget-design.md`

**Task order note:** tasks are sequenced so every file compiles the moment it's created —
no task references a Kotlin class that a *later* task creates. In particular the config
Activity (Task 8) is built before the widget rendering file (Task 9) that references it via
`::class.java`, and the sync/fan-out extension (Task 10) comes after the widget class (Task
9) that it instantiates.

## Global Constraints

- Widget-rendering text (Glance composables) and config-activity Compose UI text are
  **hardcoded Danish string literals** — this matches the exact, unanimous precedent in
  all 9 existing widgets (`LightWidget`, `CoverWidget`, `BaseEntityPickerActivity`, etc.);
  none of them use `stringResource()`. Only the widget-picker system metadata
  (`android:label` on the receiver, `android:description` on the widget-info XML) goes
  through `strings.xml` in all three locales (`values`/`values-da`/`values-sv`) — this is
  the one sub-pattern every existing widget actually follows correctly.
- Room schema changes must use an explicit `Migration` object with raw `CREATE TABLE` SQL
  — never `fallbackToDestructiveMigration()`. The test device (Galaxy S23) has real
  configured widgets in `entity_widget`/`entity_state`; those tables must never be dropped.
- Install with `adb install -r` only — **never** `adb uninstall` (wipes `SecureStore`
  token + all widget configs).
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` before the first build in this
  plan (once per feature, not per task).
- "Meld aldrig fikset uden bevis" — every task's build step must actually be run and its
  output checked, and the final tasks require real emulator/device QA before commit.

---

### Task 1: Room data model — `multi_widget` + `multi_widget_slot`

**Files:**
- Modify: `app/build.gradle.kts` (version bump)
- Create: `app/src/main/java/dk/akait/hawidgets/data/db/MultiWidgetEntity.kt`
- Create: `app/src/main/java/dk/akait/hawidgets/data/db/MultiWidgetSlotEntity.kt`
- Create: `app/src/main/java/dk/akait/hawidgets/data/db/MultiWidgetDao.kt`
- Create: `app/src/main/java/dk/akait/hawidgets/data/db/Migrations.kt`
- Modify: `app/src/main/java/dk/akait/hawidgets/data/db/AppDatabase.kt`

**Interfaces:**
- Produces: `MultiWidgetEntity(appWidgetId: Int, title: String)`,
  `MultiWidgetSlotEntity(appWidgetId: Int, slotIndex: Int, displayEntityId: String,
  displayDomain: String, actionEntityId: String, actionDomain: String, action: String,
  label: String)`, `MultiWidgetDao` with `upsert`, `get`, `observe`, `delete`, `upsertSlot`,
  `getSlots`, `observeSlots`, `deleteSlot`, `deleteAllSlots`, `allDisplayEntityIds`,
  `allActionEntityIds`, `slotsForEntity`. `AppDatabase.multiWidgetDao()`.

- [ ] **Step 1: Bump app version**

Edit `app/build.gradle.kts:16-17` from:
```kotlin
        versionCode = 18
        versionName = "0.2.18"
```
to:
```kotlin
        versionCode = 19
        versionName = "0.2.19"
```

- [ ] **Step 2: Create `MultiWidgetEntity.kt`**

```kotlin
package dk.akait.hawidgets.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "multi_widget")
data class MultiWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val title: String, // tom streng = ingen titel-linje på widgetten
)
```

- [ ] **Step 3: Create `MultiWidgetSlotEntity.kt`**

```kotlin
package dk.akait.hawidgets.data.db

import androidx.room.Entity

/**
 * Visning ([displayEntityId]/[displayDomain]) og action-mål ([actionEntityId]/[actionDomain])
 * er bevidst uafhængige felter — en slot kan vise én entitet (fx en batteri-sensor) men
 * handle på en helt anden (fx udløse en automatisering). Config-UI'en foreslår action =
 * samme entitet som default, men brugeren kan ændre det.
 */
@Entity(
    tableName = "multi_widget_slot",
    primaryKeys = ["appWidgetId", "slotIndex"],
)
data class MultiWidgetSlotEntity(
    val appWidgetId: Int,
    val slotIndex: Int, // 0..4, venstre-til-højre rækkefølge
    val displayEntityId: String,
    val displayDomain: String,
    val actionEntityId: String,
    val actionDomain: String,
    val action: String, // "TOGGLE" | "RANGE" | "TRIGGER" | "NONE"
    val label: String, // tom = brug friendly_name fra displayEntityId, maks 12 tegn
)
```

- [ ] **Step 4: Create `MultiWidgetDao.kt`**

```kotlin
package dk.akait.hawidgets.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MultiWidgetDao {
    @Upsert
    suspend fun upsert(config: MultiWidgetEntity)

    @Query("SELECT * FROM multi_widget WHERE appWidgetId = :id")
    suspend fun get(id: Int): MultiWidgetEntity?

    @Query("SELECT * FROM multi_widget WHERE appWidgetId = :id")
    fun observe(id: Int): Flow<MultiWidgetEntity?>

    @Query("DELETE FROM multi_widget WHERE appWidgetId = :id")
    suspend fun delete(id: Int)

    @Upsert
    suspend fun upsertSlot(slot: MultiWidgetSlotEntity)

    @Query("SELECT * FROM multi_widget_slot WHERE appWidgetId = :id ORDER BY slotIndex ASC")
    suspend fun getSlots(id: Int): List<MultiWidgetSlotEntity>

    @Query("SELECT * FROM multi_widget_slot WHERE appWidgetId = :id ORDER BY slotIndex ASC")
    fun observeSlots(id: Int): Flow<List<MultiWidgetSlotEntity>>

    @Query("DELETE FROM multi_widget_slot WHERE appWidgetId = :id AND slotIndex = :slotIndex")
    suspend fun deleteSlot(id: Int, slotIndex: Int)

    @Query("DELETE FROM multi_widget_slot WHERE appWidgetId = :id")
    suspend fun deleteAllSlots(id: Int)

    @Query("SELECT DISTINCT displayEntityId FROM multi_widget_slot")
    suspend fun allDisplayEntityIds(): List<String>

    @Query("SELECT DISTINCT actionEntityId FROM multi_widget_slot")
    suspend fun allActionEntityIds(): List<String>

    /** Alle slots (på tværs af widgets) der viser ELLER handler på [entityId] — bruges til fan-out. */
    @Query("SELECT * FROM multi_widget_slot WHERE displayEntityId = :entityId OR actionEntityId = :entityId")
    suspend fun slotsForEntity(entityId: String): List<MultiWidgetSlotEntity>
}
```

- [ ] **Step 5: Create `Migrations.kt`**

```kotlin
package dk.akait.hawidgets.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 → v2: tilføjer multi_widget + multi_widget_slot. Rører IKKE entity_widget/entity_state. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `multi_widget` (
                `appWidgetId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                PRIMARY KEY(`appWidgetId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `multi_widget_slot` (
                `appWidgetId` INTEGER NOT NULL,
                `slotIndex` INTEGER NOT NULL,
                `displayEntityId` TEXT NOT NULL,
                `displayDomain` TEXT NOT NULL,
                `actionEntityId` TEXT NOT NULL,
                `actionDomain` TEXT NOT NULL,
                `action` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                PRIMARY KEY(`appWidgetId`, `slotIndex`)
            )
            """.trimIndent()
        )
    }
}
```

- [ ] **Step 6: Update `AppDatabase.kt`**

Replace the full file content:
```kotlin
package dk.akait.hawidgets.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EntityStateEntity::class,
        EntityWidgetEntity::class,
        MultiWidgetEntity::class,
        MultiWidgetSlotEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entityStateDao(): EntityStateDao
    abstract fun entityWidgetDao(): EntityWidgetDao
    abstract fun multiWidgetDao(): MultiWidgetDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ha_widgets.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}
```

- [ ] **Step 7: Build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Verify migration preserves existing data (emulator)**

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell run-as dk.akait.hawidgets sqlite3 databases/ha_widgets.db ".tables"
```
Expected output includes `multi_widget`, `multi_widget_slot`, AND the pre-existing
`entity_widget`/`entity_state` (confirms migration did not wipe data — if `pixel_test`
already has configured widgets, spot-check `SELECT * FROM entity_widget;` still returns rows).

- [ ] **Step 9: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/dk/akait/hawidgets/data/db/
git commit -m "feat: tilføj Room-model for multi-entity widget (v0.2.19)"
```

---

### Task 2: `HaApiClient` — cross-domain entity fetch

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/data/HaApiClient.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `EntityBrief.domain: String` (new field, default-derived from `entityId`),
  `HaApiClient.listStatesByDomains(domains: Set<String>): List<EntityBrief>`.

- [ ] **Step 1: Add `domain` field to `EntityBrief` and refactor `listStatesByDomain`**

In `app/src/main/java/dk/akait/hawidgets/data/HaApiClient.kt`, replace the `EntityBrief`
declaration (lines 23-27):
```kotlin
    data class EntityBrief(
        val entityId: String,
        val friendlyName: String,
        val state: String,
        val domain: String = entityId.substringBefore('.'),
    )
```

Replace the existing `listStatesByDomain` function (lines 106-135) with:
```kotlin
    /** Henter alle states og filtrerer på ét domæne — bruges til enkelt-entity widget-config. */
    suspend fun listStatesByDomain(domain: String): List<EntityBrief> = listStatesByDomains(setOf(domain))

    /** Henter alle states og filtrerer på flere domæner — bruges til multi-entity widget-config. */
    suspend fun listStatesByDomains(domains: Set<String>): List<EntityBrief> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$base/api/states")
                .header("Authorization", authHeader)
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val array = JSONArray(body)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val id = obj.getString("entity_id")
                        val entityDomain = id.substringBefore('.')
                        if (entityDomain !in domains) continue
                        val attrs = obj.optJSONObject("attributes")
                        add(EntityBrief(
                            entityId = id,
                            friendlyName = attrs?.optString("friendly_name")?.ifEmpty { null } ?: id,
                            state = obj.getString("state"),
                            domain = entityDomain,
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
```

- [ ] **Step 2: Build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (no call sites break — `domain` has a default value, and
`listStatesByDomain(domain)` keeps its existing signature).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/data/HaApiClient.kt
git commit -m "feat: tilføj domain-felt + listStatesByDomains til HaApiClient"
```

---

### Task 3: Shared domain-support helpers

**Files:**
- Create: `app/src/main/java/dk/akait/hawidgets/widget/common/MultiDomainSupport.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `MULTI_ENTITY_DOMAINS: List<String>`, `domainIconResId(domain: String): Int`,
  `formatEntityState(domain: String, state: String?): String`,
  `isActiveState(domain: String, state: String?): Boolean`,
  `compatibleActionsFor(domain: String): List<String>`. Used by both `MultiEntityWidget`
  (Task 9) and `MultiEntityWidgetConfigActivity` (Task 8).

- [ ] **Step 1: Create the file**

```kotlin
package dk.akait.hawidgets.widget.common

import dk.akait.hawidgets.R

/** De 12 domains valgbare i MultiEntityWidget (både til visning og som action-mål). */
val MULTI_ENTITY_DOMAINS = listOf(
    "light", "switch", "lock", "cover", "climate", "number",
    "automation", "scene", "script", "sensor", "binary_sensor", "device_tracker",
)

fun domainIconResId(domain: String): Int = when (domain) {
    "light" -> R.drawable.ic_lightbulb
    "switch" -> R.drawable.ic_switch
    "scene" -> R.drawable.ic_scene
    "script" -> R.drawable.ic_script
    "automation" -> R.drawable.ic_automation
    "sensor" -> R.drawable.ic_sensor
    "binary_sensor" -> R.drawable.ic_binary_sensor
    "cover" -> R.drawable.ic_cover
    "climate" -> R.drawable.ic_climate
    "lock" -> R.drawable.ic_lock
    "number" -> R.drawable.ic_number
    "device_tracker" -> R.drawable.ic_device_tracker
    else -> R.drawable.ic_sensor
}

/** Domain-specifik state-formattering — matcher docs/widget-settings-spec.md's tabel + de 3 nye domains. */
fun formatEntityState(domain: String, state: String?): String = when {
    state == null -> "…"
    state == "unavailable" -> "Utilgængelig"
    domain == "light" || domain == "switch" -> if (state == "on") "Tændt" else "Slukket"
    domain == "lock" -> if (state == "locked") "Låst" else "Ulåst"
    domain == "cover" -> when (state) {
        "open" -> "Åben"
        "closed" -> "Lukket"
        "opening" -> "Åbner…"
        "closing" -> "Lukker…"
        else -> state
    }
    domain == "climate" -> when (state) {
        "heat" -> "Opvarmning"
        "cool" -> "Køling"
        "auto", "heat_cool" -> "Auto"
        "dry" -> "Affugtning"
        "fan_only" -> "Ventilator"
        "off" -> "Slukket"
        else -> state
    }
    domain == "automation" -> if (state == "on") "Aktiv" else "Deaktiveret"
    domain == "binary_sensor" -> if (state == "on") "Aktiv" else "Inaktiv"
    domain == "scene" -> "Aktiver"
    domain == "script" -> if (state == "on") "Kører" else "Klar"
    domain == "device_tracker" -> if (state == "home") "Hjemme" else "Ude"
    else -> state // sensor, number: rå værdi
}

/** Skal domænets "aktiv"-tilstand fremhæves med primary-farve? Matcher eksisterende widgets' konvention. */
fun isActiveState(domain: String, state: String?): Boolean = when (domain) {
    "light", "switch", "automation", "climate", "binary_sensor" -> state == "on"
    "lock" -> state == "locked"
    "cover" -> state == "open" || state == "opening"
    "device_tracker" -> state == "home"
    else -> false // sensor, number, scene, script: intet vedvarende on/off-udseende
}

/**
 * Action-typer der giver mening for et givent domæne SOM ACTION-MÅL. Ikke filtreret på
 * live kapabilitet (fx dimmable/positionable) — brugeren vælger selv, ligesom resten af
 * "brugervalgt action pr slot"-modellen.
 */
fun compatibleActionsFor(domain: String): List<String> = when (domain) {
    "light", "cover", "climate" -> listOf("NONE", "TOGGLE", "RANGE")
    "switch", "lock" -> listOf("NONE", "TOGGLE")
    "number" -> listOf("NONE", "RANGE")
    "automation" -> listOf("NONE", "TOGGLE", "TRIGGER")
    "scene", "script" -> listOf("NONE", "TRIGGER")
    else -> listOf("NONE") // sensor, binary_sensor, device_tracker: read-only
}
```

- [ ] **Step 2: Build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/MultiDomainSupport.kt
git commit -m "feat: tilføj delt domain-metadata for multi-entity widget"
```

---

### Task 4: Generalize `ToggleEntityAction`

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/common/EntityActions.kt:11-26`

**Interfaces:**
- Consumes: `EntityRepository.command(context, domain, service, entityId, targetState, fromState)`
  (unchanged signature).
- Produces: `ToggleEntityAction` now maps lock/cover to their real service names instead of
  the generic `turn_on`/`turn_off`. Existing callers (`SwitchWidget` with `domain="switch"`,
  any future `domain="light"`/`"automation"`) fall through to the unchanged `else` branch —
  **no behavior change for existing widgets.**

- [ ] **Step 1: Replace `ToggleEntityAction`**

Replace lines 11-26 of `EntityActions.kt`:
```kotlin
/** Generisk toggle — domain-bevidst service-mapping. Bruges af switch, lock, cover, automation osv. */
class ToggleEntityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entityId = parameters[entityIdKey] ?: return
        val domain = parameters[domainKey] ?: return
        val current = AppDatabase.get(context).entityStateDao().get(entityId) ?: return
        val (targetState, service) = when (domain) {
            "lock" -> if (current.state == "locked") "unlocked" to "unlock" else "locked" to "lock"
            "cover" -> if (current.state == "open") "closed" to "close_cover" else "open" to "open_cover"
            else -> if (current.state == "on") "off" to "turn_off" else "on" to "turn_on"
        }
        EntityRepository.command(context, domain, service, entityId, targetState, current.state)
    }

    companion object {
        val entityIdKey = ActionParameters.Key<String>("entityId")
        val domainKey = ActionParameters.Key<String>("domain")
    }
}
```

- [ ] **Step 2: Build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Regression-check existing SwitchWidget on emulator**

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Place/open an existing configured `SwitchWidget` (or reconfigure one) on `pixel_test`, tap
it, confirm it still toggles on/off exactly as before (domain="switch" → unchanged
`turn_on`/`turn_off` path).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/EntityActions.kt
git commit -m "refactor: generalisér ToggleEntityAction til domain-bevidst service-navne"
```

---

### Task 5: Extend `RangeControlActivity` for `number` domain

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/common/RangeControlActivity.kt`

**Interfaces:**
- Produces: `RangeControlActivity.EXTRA_UNIT_SUFFIX` (new optional intent extra, defaults to
  existing `%`/`°C` logic when absent). `number` domain support in `sendRangeCommand`, and
  the toggle button is hidden + slider always-enabled for `number` (no on/off concept).

- [ ] **Step 1: Add `EXTRA_UNIT_SUFFIX` constant**

In the `companion object` (after line 41's `EXTRA_ACTUAL_TEMP`), add:
```kotlin
        /** Valgfri unit-override til value-label (fx "W", "kWh") — bruges af number-domain. Tom/null = domain-default (%). */
        const val EXTRA_UNIT_SUFFIX = "unit_suffix"
```

- [ ] **Step 2: Read the new extra in `onCreate`**

After line 54 (`val actualTemp = ...`), add:
```kotlin
        val unitSuffixOverride = intent.getStringExtra(EXTRA_UNIT_SUFFIX)
```

- [ ] **Step 3: Add `number` case to `sendRangeCommand`**

In the `when (domain)` block inside `sendRangeCommand` (around line 73-86), add a branch:
```kotlin
                                "number" -> api.callService(
                                    "number", "set_value", entityId,
                                    extraData = mapOf("value" to value)
                                )
```

- [ ] **Step 4: Hide the toggle button and always-enable the slider for `number`**

Replace the `valueLabel`/`unitSuffix` computation and the `Row`+`Slider` block (lines
140-171) with:
```kotlin
                        val valueLabel = when (domain) {
                            "cover" -> "Position"
                            "climate" -> "Temperatur"
                            "number" -> "Værdi"
                            else -> "Lysstyrke"
                        }
                        val unitSuffix = unitSuffixOverride ?: when (domain) {
                            "climate" -> "°C"
                            else -> "%"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$valueLabel: ${sliderValue.toInt()}$unitSuffix",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (domain != "number") {
                                OutlinedButton(onClick = { sendToggle() }, enabled = !busy) {
                                    Text(toggleLabel)
                                }
                            }
                        }

                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = { sendRangeCommand(sliderValue.toInt()) },
                            valueRange = minValue.toFloat()..maxValue.toFloat(),
                            enabled = domain == "number" || isOn,
                            modifier = Modifier.fillMaxWidth(),
                        )
```

- [ ] **Step 5: Build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Regression-check existing light/cover/climate range control on emulator**

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Open an existing dimmable `LightWidget`'s brightness slider — confirm it still opens, shows
the Tænd/Sluk button, and slider still works exactly as before (unaffected by the `number`
branch addition).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/RangeControlActivity.kt
git commit -m "feat: udvid RangeControlActivity til number-domain"
```

---

### Task 6: New domain drawables

**Files:**
- Create: `app/src/main/res/drawable/ic_lock.xml`
- Create: `app/src/main/res/drawable/ic_number.xml`
- Create: `app/src/main/res/drawable/ic_device_tracker.xml`
- Create: `app/src/main/res/drawable/ic_multi_entity.xml`

**Interfaces:**
- Produces: `R.drawable.ic_lock`, `R.drawable.ic_number`, `R.drawable.ic_device_tracker`
  (consumed by `domainIconResId` in Task 3 — already written assuming these exist),
  `R.drawable.ic_multi_entity` (widget picker preview + unconfigured-state icon, Task 9/11).

- [ ] **Step 1: Create `ic_lock.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,17c1.1,0,2,-0.9,2,-2s-0.9,-2,-2,-2,-2,0.9,-2,2S10.9,17,12,17z M18,8h-1V6c0,-2.76,-2.24,-5,-5,-5S7,3.24,7,6v2H6c-1.1,0,-2,0.9,-2,2v10c0,1.1,0.9,2,2,2h12c1.1,0,2,-0.9,2,-2V10C20,8.9,19.1,8,18,8z M8.9,6c0,-1.71,1.39,-3.1,3.1,-3.1s3.1,1.39,3.1,3.1v2H8.9V6z M18,20H6V10h12V20z" />
</vector>
```

- [ ] **Step 2: Create `ic_number.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M3,17v2h6v-2H3z M3,5v2h10V5H3z M13,21v-2h8v-2h-8v-2h-2v6H13z M7,9v2H3v2h4v2h2V9H7z M21,13v-2h-8v2H21z M11,3v2h-2v2h2v2h2V3z" />
</vector>
```

- [ ] **Step 3: Create `ic_device_tracker.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,2C8.13,2,5,5.13,5,9c0,5.25,7,13,7,13s7,-7.75,7,-13C19,5.13,15.87,2,12,2z M12,11.5c-1.38,0,-2.5,-1.12,-2.5,-2.5s1.12,-2.5,2.5,-2.5,2.5,1.12,2.5,2.5S13.38,11.5,12,11.5z" />
</vector>
```

- [ ] **Step 4: Create `ic_multi_entity.xml`**

Three small tiles side by side — represents "multiple slots in a row", distinct from the
existing `ic_dashboard` (used by `ShortcutWidget`, a 4-quadrant asymmetric grid).
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M2,9h6v6H2z M9,9h6v6H9z M16,9h6v6H16z" />
</vector>
```

- [ ] **Step 5: Build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/drawable/ic_lock.xml app/src/main/res/drawable/ic_number.xml app/src/main/res/drawable/ic_device_tracker.xml app/src/main/res/drawable/ic_multi_entity.xml
git commit -m "feat: tilføj ikoner for lock/number/device_tracker + multi-entity widget"
```

---

### Task 7: Widget-picker strings (3 locales)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-da/strings.xml`
- Modify: `app/src/main/res/values-sv/strings.xml`

**Interfaces:**
- Produces: `@string/multi_entity_widget_label`, `@string/multi_entity_widget_description`
  (consumed by `multi_entity_widget_info.xml` + `AndroidManifest.xml` in Task 11).

- [ ] **Step 1: Add to `values/strings.xml` (English default)**

Add near the other `_widget_label`/`_widget_description` pairs:
```xml
    <string name="multi_entity_widget_label">HA Multi</string>
    <string name="multi_entity_widget_description">Combine up to 5 entities</string>
```

- [ ] **Step 2: Add to `values-da/strings.xml`**

```xml
    <string name="multi_entity_widget_label">HA Multi</string>
    <string name="multi_entity_widget_description">Kombinér op til 5 entiteter</string>
```

- [ ] **Step 3: Add to `values-sv/strings.xml`**

```xml
    <string name="multi_entity_widget_label">HA Multi</string>
    <string name="multi_entity_widget_description">Kombinera upp till 5 enheter</string>
```

- [ ] **Step 4: Build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-da/strings.xml app/src/main/res/values-sv/strings.xml
git commit -m "feat: tilføj multi-entity widget-picker strenge (en/da/sv)"
```

---

### Task 8: `MultiEntityWidgetConfigActivity` — config UI

Built before the widget-rendering file (Task 9) because `MultiEntityWidget.kt` references
`MultiEntityWidgetConfigActivity::class.java` — Kotlin resolves that at compile time, so
this class must exist first.

**Files:**
- Create: `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityWidgetConfigActivity.kt`

**Interfaces:**
- Consumes: `HaApiClient.listStatesByDomains` (Task 2), `MULTI_ENTITY_DOMAINS`/
  `domainIconResId`/`formatEntityState`/`compatibleActionsFor` (Task 3),
  `MultiWidgetEntity`/`MultiWidgetSlotEntity`/`MultiWidgetDao` (Task 1), `SecureStore`,
  `SyncWorker` (existing).
- Produces: `MultiEntityWidgetConfigActivity` — consumed by `MultiEntityWidget.kt` (Task 9,
  `UnconfiguredWidgetContent`'s `configClass` param) and `AndroidManifest.xml` (Task 11).

- [ ] **Step 1: Create the file**

```kotlin
package dk.akait.hawidgets.widget.multientity

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.MultiWidgetEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.MULTI_ENTITY_DOMAINS
import dk.akait.hawidgets.widget.common.compatibleActionsFor
import dk.akait.hawidgets.widget.common.domainIconResId
import dk.akait.hawidgets.widget.common.formatEntityState
import dk.akait.hawidgets.worker.SyncWorker
import kotlinx.coroutines.launch

class MultiEntityWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent {
            MaterialTheme {
                MultiEntityConfigScreen(
                    appWidgetId = appWidgetId,
                    onSaved = {
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                        finish()
                    },
                )
            }
        }
    }
}

private enum class PickerTarget { DISPLAY, ACTION }

private data class SlotDraft(
    val displayEntity: HaApiClient.EntityBrief? = null,
    val actionEntity: HaApiClient.EntityBrief? = null,
    val action: String = "NONE",
    val label: String = "",
)

private sealed interface Step {
    data object ListScreen : Step
    data class SlotEditor(val editIndex: Int?, val draft: SlotDraft) : Step
    data class EntityPicker(val forTarget: PickerTarget, val editIndex: Int?, val draft: SlotDraft) : Step
}

private fun actionLabel(action: String): String = when (action) {
    "TOGGLE" -> "Slå til/fra"
    "RANGE" -> "Åbn skyder"
    "TRIGGER" -> "Udløs automatisering/script"
    else -> "Kun visning"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiEntityConfigScreen(appWidgetId: Int, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var titleInput by remember { mutableStateOf("") }
    var slots by remember { mutableStateOf<List<MultiWidgetSlotEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var allEntities by remember { mutableStateOf<List<HaApiClient.EntityBrief>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf<Step>(Step.ListScreen) }

    LaunchedEffect(Unit) {
        val store = SecureStore.get(context)
        if (!store.isConfigured) {
            loadError = "HA ikke forbundet. Åbn HA Widgets og forbind først."
            isLoading = false
            return@LaunchedEffect
        }
        val client = HaApiClient(store.baseUrl!!, store.token!!)
        allEntities = client.listStatesByDomains(MULTI_ENTITY_DOMAINS.toSet()).sortedBy { it.friendlyName }
        val db = AppDatabase.get(context)
        titleInput = db.multiWidgetDao().get(appWidgetId)?.title.orEmpty()
        slots = db.multiWidgetDao().getSlots(appWidgetId)
        isLoading = false
    }

    fun draftFromSlot(slot: MultiWidgetSlotEntity): SlotDraft {
        val display = allEntities.find { it.entityId == slot.displayEntityId }
            ?: HaApiClient.EntityBrief(slot.displayEntityId, slot.displayEntityId, "unknown", slot.displayDomain)
        val action = allEntities.find { it.entityId == slot.actionEntityId }
            ?: HaApiClient.EntityBrief(slot.actionEntityId, slot.actionEntityId, "unknown", slot.actionDomain)
        return SlotDraft(display, action, slot.action, slot.label)
    }

    fun saveSlot(editIndex: Int?, draft: SlotDraft) {
        val display = draft.displayEntity ?: return
        val action = draft.actionEntity ?: display
        val newSlot = MultiWidgetSlotEntity(
            appWidgetId = appWidgetId,
            slotIndex = editIndex ?: slots.size,
            displayEntityId = display.entityId,
            displayDomain = display.domain,
            actionEntityId = action.entityId,
            actionDomain = action.domain,
            action = draft.action,
            label = draft.label.trim(),
        )
        slots = if (editIndex == null) slots + newSlot
        else slots.toMutableList().also { it[editIndex] = newSlot }
        step = Step.ListScreen
    }

    when (val s = step) {
        Step.ListScreen -> ListScreen(
            titleInput = titleInput,
            onTitleChange = { titleInput = it },
            slots = slots,
            onAddSlot = { step = Step.EntityPicker(PickerTarget.DISPLAY, null, SlotDraft()) },
            onEditSlot = { index -> step = Step.SlotEditor(index, draftFromSlot(slots[index])) },
            onRemoveSlot = { index ->
                slots = slots.filterIndexed { i, _ -> i != index }.mapIndexed { i, sl -> sl.copy(slotIndex = i) }
            },
            onMoveSlot = { index, delta ->
                val target = index + delta
                if (target in slots.indices) {
                    val mutable = slots.toMutableList()
                    val a = mutable[index]
                    val b = mutable[target]
                    mutable[index] = b.copy(slotIndex = index)
                    mutable[target] = a.copy(slotIndex = target)
                    slots = mutable.sortedBy { it.slotIndex }
                }
            },
            onSave = {
                scope.launch {
                    val db = AppDatabase.get(context)
                    db.multiWidgetDao().upsert(MultiWidgetEntity(appWidgetId, titleInput.trim()))
                    db.multiWidgetDao().deleteAllSlots(appWidgetId)
                    slots.forEachIndexed { i, sl -> db.multiWidgetDao().upsertSlot(sl.copy(slotIndex = i)) }
                    SyncWorker.runNow(context)
                    SyncWorker.schedule(context)
                    onSaved()
                }
            },
        )

        is Step.EntityPicker -> EntityPickerSubScreen(
            title = if (s.forTarget == PickerTarget.DISPLAY) "Vælg entitet der skal vises" else "Vælg entitet der skal udløses",
            entities = allEntities,
            isLoading = isLoading,
            error = loadError,
            onSelected = { brief ->
                val updatedDraft = if (s.forTarget == PickerTarget.DISPLAY) {
                    s.draft.copy(displayEntity = brief, actionEntity = brief, action = "NONE")
                } else {
                    s.draft.copy(actionEntity = brief, action = "NONE")
                }
                step = Step.SlotEditor(s.editIndex, updatedDraft)
            },
            onBack = {
                step = if (s.draft.displayEntity == null) Step.ListScreen else Step.SlotEditor(s.editIndex, s.draft)
            },
        )

        is Step.SlotEditor -> SlotEditorScreen(
            draft = s.draft,
            onChangeDisplay = { step = Step.EntityPicker(PickerTarget.DISPLAY, s.editIndex, s.draft) },
            onChangeTarget = { step = Step.EntityPicker(PickerTarget.ACTION, s.editIndex, s.draft) },
            onActionChange = { newAction -> step = Step.SlotEditor(s.editIndex, s.draft.copy(action = newAction)) },
            onLabelChange = { newLabel -> step = Step.SlotEditor(s.editIndex, s.draft.copy(label = newLabel)) },
            onSave = { saveSlot(s.editIndex, s.draft) },
            onBack = { step = Step.ListScreen },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    titleInput: String,
    onTitleChange: (String) -> Unit,
    slots: List<MultiWidgetSlotEntity>,
    onAddSlot: () -> Unit,
    onEditSlot: (Int) -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onMoveSlot: (Int, Int) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Kombineret widget") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = titleInput,
                onValueChange = onTitleChange,
                label = { Text("Widget-titel (valgfrit)") },
                placeholder = { Text("f.eks. Mercedes") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.padding(8.dp))
            if (slots.isEmpty()) {
                Text(
                    "Ingen slots endnu — tryk \"Tilføj slot\" for at starte.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                slots.sortedBy { it.slotIndex }.forEachIndexed { index, slot ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(domainIconResId(slot.displayDomain)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(slot.label.ifEmpty { slot.displayEntityId }, style = MaterialTheme.typography.bodyLarge)
                            val actionSummary = if (slot.actionEntityId == slot.displayEntityId) {
                                actionLabel(slot.action)
                            } else {
                                "${actionLabel(slot.action)} → ${slot.actionEntityId}"
                            }
                            Text(actionSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onMoveSlot(index, -1) }, enabled = index > 0) { Text("↑") }
                        TextButton(onClick = { onMoveSlot(index, 1) }, enabled = index < slots.size - 1) { Text("↓") }
                        TextButton(onClick = { onEditSlot(index) }) { Text("Rediger") }
                        TextButton(onClick = { onRemoveSlot(index) }) { Text("Fjern") }
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.padding(8.dp))
            Button(onClick = onAddSlot, enabled = slots.size < 5, modifier = Modifier.fillMaxWidth()) {
                Text("+ Tilføj slot")
            }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = onSave, enabled = slots.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Gem widget")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotEditorScreen(
    draft: SlotDraft,
    onChangeDisplay: () -> Unit,
    onChangeTarget: () -> Unit,
    onActionChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val display = draft.displayEntity ?: return
    val action = draft.actionEntity ?: display
    val compatible = compatibleActionsFor(action.domain)

    Scaffold(topBar = { TopAppBar(title = { Text("Tilpas handling") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(display.friendlyName, style = MaterialTheme.typography.titleMedium)
            Text(display.entityId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onChangeDisplay) { Text("Skift entitet") }
            Spacer(Modifier.padding(8.dp))
            OutlinedTextField(
                value = draft.label,
                onValueChange = { if (it.length <= 12) onLabelChange(it) },
                label = { Text("Kort label (valgfrit)") },
                placeholder = { Text("f.eks. Bad 1") },
                supportingText = { Text("Vises på widget i stedet for enhedsnavn. Maks 12 tegn.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.padding(12.dp))
            Text("Handling", style = MaterialTheme.typography.titleSmall)
            Text(
                if (action.entityId == display.entityId) "Mål: samme entitet" else "Mål: ${action.friendlyName}",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onChangeTarget) { Text("Skift mål") }
            compatible.forEach { actionType ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onActionChange(actionType) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = draft.action == actionType, onClick = { onActionChange(actionType) })
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel(actionType))
                }
            }
            Spacer(Modifier.padding(8.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Tilføj til widget") }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Annullér") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityPickerSubScreen(
    title: String,
    entities: List<HaApiClient.EntityBrief>,
    isLoading: Boolean,
    error: String?,
    onSelected: (HaApiClient.EntityBrief) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { TextButton(onClick = onBack) { Text("Tilbage") } },
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Søg…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val filtered = entities.filter {
                        query.isBlank() ||
                            it.friendlyName.contains(query, ignoreCase = true) ||
                            it.entityId.contains(query, ignoreCase = true)
                    }
                    LazyColumn {
                        items(filtered) { brief ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelected(brief) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(domainIconResId(brief.domain)),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (brief.state == "on") MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(brief.friendlyName, style = MaterialTheme.typography.bodyLarge)
                                    Text(brief.entityId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                SuggestionChip(onClick = {}, label = { Text(formatEntityState(brief.domain, brief.state)) })
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityWidgetConfigActivity.kt
git commit -m "feat: implementér MultiEntityWidgetConfigActivity (liste-baseret config-UI)"
```

---

### Task 9: `MultiEntityWidget` — rendering + click wiring

**Files:**
- Create: `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityWidget.kt`

**Interfaces:**
- Consumes: `MultiWidgetDao` (Task 1), `domainIconResId`/`formatEntityState`/
  `isActiveState` (Task 3), `ToggleEntityAction`/`TriggerEntityAction`/`RefreshEntityAction`
  (Task 4, `EntityActions.kt`), `RangeControlActivity` (Task 5), `WidgetCompactLayout`/
  `UnconfiguredWidgetContent`/`friendlyNameFromJson`/`isStale()` (existing
  `GlanceWidgetCommon.kt`), `R.drawable.ic_multi_entity` (Task 6),
  `MultiEntityWidgetConfigActivity` (Task 8).
- Produces: `MultiEntityWidget` (`GlanceAppWidget`), `MultiEntityWidgetReceiver`
  (`GlanceAppWidgetReceiver`) — consumed by `WidgetUpdater` (Task 10) and
  `AndroidManifest.xml` (Task 11).

- [ ] **Step 1: Create the file**

```kotlin
package dk.akait.hawidgets.widget.multientity

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.EntityStateEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.RangeControlActivity
import dk.akait.hawidgets.widget.common.RefreshEntityAction
import dk.akait.hawidgets.widget.common.ToggleEntityAction
import dk.akait.hawidgets.widget.common.TriggerEntityAction
import dk.akait.hawidgets.widget.common.UnconfiguredWidgetContent
import dk.akait.hawidgets.widget.common.WidgetCompactLayout
import dk.akait.hawidgets.widget.common.domainIconResId
import dk.akait.hawidgets.widget.common.formatEntityState
import dk.akait.hawidgets.widget.common.friendlyNameFromJson
import dk.akait.hawidgets.widget.common.isActiveState
import dk.akait.hawidgets.widget.common.isStale
import dk.akait.hawidgets.worker.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private const val SLOT_SIZE_DP = 56

class MultiEntityWidget : GlanceAppWidget() {

    // resizeMode="none" i widget-info.xml → fast størrelse, ingen responsive buckets nødvendige.
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val db = AppDatabase.get(context)
        val initialTitle = db.multiWidgetDao().get(appWidgetId)?.title.orEmpty()
        val initialSlots = db.multiWidgetDao().getSlots(appWidgetId)

        provideContent {
            val viewState by db.multiWidgetDao().observe(appWidgetId)
                .combine(db.multiWidgetDao().observeSlots(appWidgetId)) { cfg, slots ->
                    (cfg?.title.orEmpty()) to slots
                }
                .flatMapLatest { (title, slots) ->
                    statesFlow(db, slots).map { states -> Triple(title, slots, states) }
                }
                .collectAsState(
                    initial = Triple(initialTitle, initialSlots, emptyMap<String, EntityStateEntity?>())
                )
            val (title, slots, states) = viewState

            GlanceTheme {
                if (slots.isEmpty()) {
                    UnconfiguredWidgetContent(
                        context, appWidgetId, MultiEntityWidgetConfigActivity::class.java, R.drawable.ic_multi_entity,
                    )
                } else {
                    MultiEntityContent(context, title, slots, states)
                }
            }
        }
    }
}

private fun statesFlow(
    db: AppDatabase,
    slots: List<MultiWidgetSlotEntity>,
): Flow<Map<String, EntityStateEntity?>> {
    val ids = slots.flatMap { listOf(it.displayEntityId, it.actionEntityId) }.distinct()
    if (ids.isEmpty()) return flowOf(emptyMap())
    val flows = ids.map { id -> db.entityStateDao().observe(id) }
    return combine(flows) { arr -> ids.zip(arr.toList()).toMap() }
}

@Composable
private fun MultiEntityContent(
    context: Context,
    title: String,
    slots: List<MultiWidgetSlotEntity>,
    states: Map<String, EntityStateEntity?>,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            for (slot in slots.sortedBy { it.slotIndex }) {
                SlotBox(context, slot, states[slot.displayEntityId], states[slot.actionEntityId])
            }
        }
    }
}

@Composable
private fun SlotBox(
    context: Context,
    slot: MultiWidgetSlotEntity,
    displayState: EntityStateEntity?,
    actionState: EntityStateEntity?,
) {
    val isUnavailable = displayState?.state == "unavailable"
    val isActive = displayState != null && isActiveState(slot.displayDomain, displayState.state)

    val bgColor = when {
        isUnavailable -> GlanceTheme.colors.errorContainer
        isActive -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.surfaceVariant
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        isActive -> GlanceTheme.colors.onPrimary
        else -> GlanceTheme.colors.onSurfaceVariant
    }

    val label = slot.label.ifEmpty {
        friendlyNameFromJson(displayState?.attributesJson ?: "{}") ?: slot.displayEntityId
    }
    val statusBase = formatEntityState(slot.displayDomain, displayState?.state)
    val statusText = if (displayState != null && displayState.isStale()) "$statusBase ~" else statusBase

    val baseModifier = GlanceModifier.size(SLOT_SIZE_DP.dp).background(bgColor).cornerRadius(16.dp)
    val modifier = slotClickModifier(context, baseModifier, slot, actionState)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        WidgetCompactLayout(domainIconResId(slot.displayDomain), label, statusText, contentColor)
    }
}

private fun slotClickModifier(
    context: Context,
    base: GlanceModifier,
    slot: MultiWidgetSlotEntity,
    actionState: EntityStateEntity?,
): GlanceModifier {
    if (actionState == null || actionState.state == "unavailable") return base
    return when (slot.action) {
        "TOGGLE" -> base.clickable(
            actionRunCallback<ToggleEntityAction>(
                actionParametersOf(
                    ToggleEntityAction.entityIdKey to slot.actionEntityId,
                    ToggleEntityAction.domainKey to slot.actionDomain,
                )
            )
        )
        "RANGE" -> {
            val attrs = try { JSONObject(actionState.attributesJson) } catch (_: Exception) { JSONObject() }
            val intent = Intent(context, RangeControlActivity::class.java).apply {
                putExtra(RangeControlActivity.EXTRA_ENTITY_ID, slot.actionEntityId)
                putExtra(RangeControlActivity.EXTRA_LABEL, slot.label.ifEmpty { slot.actionEntityId })
                putExtra(RangeControlActivity.EXTRA_DOMAIN, slot.actionDomain)
                putExtra(RangeControlActivity.EXTRA_CURRENT_VALUE, rangeCurrentValue(slot.actionDomain, actionState, attrs))
                putExtra(RangeControlActivity.EXTRA_IS_ON, actionState.state != "off" && actionState.state != "closed")
                putExtra(RangeControlActivity.EXTRA_MIN_VALUE, rangeMin(slot.actionDomain, attrs))
                putExtra(RangeControlActivity.EXTRA_MAX_VALUE, rangeMax(slot.actionDomain, attrs))
                if (slot.actionDomain == "number") {
                    putExtra(RangeControlActivity.EXTRA_UNIT_SUFFIX, attrs.optString("unit_of_measurement", ""))
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            base.clickable(actionStartActivity(intent))
        }
        "TRIGGER" -> {
            val service = if (slot.actionDomain == "automation") "trigger" else "turn_on"
            base.clickable(
                actionRunCallback<TriggerEntityAction>(
                    actionParametersOf(
                        TriggerEntityAction.entityIdKey to slot.actionEntityId,
                        TriggerEntityAction.domainKey to slot.actionDomain,
                        TriggerEntityAction.serviceKey to service,
                    )
                )
            )
        }
        else -> base.clickable( // "NONE" → refresh den viste entitet
            actionRunCallback<RefreshEntityAction>(
                actionParametersOf(RefreshEntityAction.entityIdKey to slot.displayEntityId)
            )
        )
    }
}

private fun rangeCurrentValue(domain: String, state: EntityStateEntity, attrs: JSONObject): Int = when (domain) {
    "light" -> attrs.optInt("brightness", 255).let { (it * 100 / 255).coerceIn(0, 100) }
    "cover" -> attrs.optInt("current_position", if (state.state == "open") 100 else 0)
    "climate" -> attrs.optInt("temperature", 20)
    "number" -> state.state.toDoubleOrNull()?.toInt() ?: 0
    else -> 0
}

private fun rangeMin(domain: String, attrs: JSONObject): Int = when (domain) {
    "climate" -> attrs.optInt("min_temp", 16)
    "number" -> attrs.optDouble("min", 0.0).toInt()
    else -> 1
}

private fun rangeMax(domain: String, attrs: JSONObject): Int = when (domain) {
    "climate" -> attrs.optInt("max_temp", 30)
    "number" -> attrs.optDouble("max", 100.0).toInt()
    else -> 100
}

class MultiEntityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MultiEntityWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
```

- [ ] **Step 2: Build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityWidget.kt
git commit -m "feat: implementér MultiEntityWidget rendering + click-wiring"
```

---

### Task 10: Extend sync/fan-out for multi-widget

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/data/EntityRepository.kt:46-61`
- Modify: `app/src/main/java/dk/akait/hawidgets/data/WidgetUpdater.kt`

**Interfaces:**
- Consumes: `AppDatabase.multiWidgetDao()` (Task 1), `MultiEntityWidget` (Task 9).
- Produces: `EntityRepository.refreshAll()` now also pulls entities referenced only by
  multi-widget slots. `WidgetUpdater.updateForEntity()` now also refreshes
  `MultiEntityWidget` instances when the entity is used as a display or action target.

- [ ] **Step 1: Update `EntityRepository.refreshAll`**

Replace lines 46-61 of `EntityRepository.kt`:
```kotlin
    /** Pull alle konfigurerede entiteter (SyncWorker). Returnerer false hvis nogen fejlede. */
    suspend fun refreshAll(context: Context): Boolean {
        val api = client(context) ?: return true
        val db = AppDatabase.get(context)
        val multiDao = db.multiWidgetDao()
        val ids = (
            db.entityWidgetDao().allEntityIds() +
                multiDao.allDisplayEntityIds() +
                multiDao.allActionEntityIds()
            ).distinct()
        var allOk = true
        for (id in ids) {
            val state = api.getState(id)
            if (state != null) {
                db.entityStateDao().upsert(state)
                WidgetUpdater.updateForEntity(context, id)
            } else {
                allOk = false
            }
        }
        return allOk
    }
```

- [ ] **Step 2: Update `WidgetUpdater.updateForEntity`**

Replace the full content of `WidgetUpdater.kt`:
```kotlin
package dk.akait.hawidgets.data

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.widget.automation.AutomationWidget
import dk.akait.hawidgets.widget.binarysensor.BinarySensorWidget
import dk.akait.hawidgets.widget.climate.ClimateWidget
import dk.akait.hawidgets.widget.light.LightWidget
import dk.akait.hawidgets.widget.multientity.MultiEntityWidget
import dk.akait.hawidgets.widget.scene.SceneWidget
import dk.akait.hawidgets.widget.script.ScriptWidget
import dk.akait.hawidgets.widget.sensor.SensorWidget
import dk.akait.hawidgets.widget.cover.CoverWidget
import dk.akait.hawidgets.widget.switchwidget.SwitchWidget

/**
 * Fan-out til hjemskærms-widgets.
 * For hvert berørt domæne: opdatér alle widgets af den type via updateAll.
 */
object WidgetUpdater {

    private val domainWidgets: Map<String, GlanceAppWidget> = mapOf(
        "light" to LightWidget(),
        "switch" to SwitchWidget(),
        "scene" to SceneWidget(),
        "script" to ScriptWidget(),
        "automation" to AutomationWidget(),
        "sensor" to SensorWidget(),
        "binary_sensor" to BinarySensorWidget(),
        "cover" to CoverWidget(),
        "climate" to ClimateWidget(),
    )

    /** Opdatér alle widgets der viser [entityId]. */
    suspend fun updateForEntity(context: Context, entityId: String) {
        val db = AppDatabase.get(context)
        val domains = db.entityWidgetDao().widgetsForEntity(entityId).map { it.domain }.toSet()
        val hasMultiWidgetMatch = db.multiWidgetDao().slotsForEntity(entityId).isNotEmpty()
        if (domains.isEmpty() && !hasMultiWidgetMatch) return

        val manager = GlanceAppWidgetManager(context)
        for (domain in domains) {
            val widget = domainWidgets[domain] ?: continue
            manager.getGlanceIds(widget::class.java)
                .forEach { glanceId -> runCatching { widget.update(context, glanceId) } }
        }
        if (hasMultiWidgetMatch) {
            val multiWidget = MultiEntityWidget()
            manager.getGlanceIds(multiWidget::class.java)
                .forEach { glanceId -> runCatching { multiWidget.update(context, glanceId) } }
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/data/EntityRepository.kt app/src/main/java/dk/akait/hawidgets/data/WidgetUpdater.kt
git commit -m "feat: udvid sync/fan-out til multi-entity widget-slots"
```

---

### Task 11: `multi_entity_widget_info.xml` + manifest registration

**Files:**
- Create: `app/src/main/res/xml/multi_entity_widget_info.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `@string/multi_entity_widget_label`/`description` (Task 7),
  `@drawable/ic_multi_entity` (Task 6), `MultiEntityWidgetConfigActivity` (Task 8),
  `MultiEntityWidgetReceiver` (Task 9).

- [ ] **Step 1: Create `multi_entity_widget_info.xml`**

Fixed footprint for 5 slots (56dp × 5 = 280dp) + a title row (74dp total height),
`resizeMode="none"` per the spec's platform-constraint resolution (no per-instance sizing
possible in Android, so this widget never resizes and always reserves its max footprint):
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="280dp"
    android:minHeight="74dp"
    android:targetCellWidth="5"
    android:targetCellHeight="1"
    android:resizeMode="none"
    android:widgetCategory="home_screen"
    android:widgetFeatures="reconfigurable"
    android:configure="dk.akait.hawidgets.widget.multientity.MultiEntityWidgetConfigActivity"
    android:description="@string/multi_entity_widget_description"
    android:previewImage="@drawable/ic_multi_entity"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/glance_default_loading_layout" />
```

- [ ] **Step 2: Register in `AndroidManifest.xml`**

Insert immediately before the existing `<activity android:name=".widget.common.RangeControlActivity" ...>`
block (around line 243), following the exact same shape as the Cover block:
```xml
        <!-- Multi-entity -->
        <activity
            android:name=".widget.multientity.MultiEntityWidgetConfigActivity"
            android:exported="true"
            android:taskAffinity=""
            android:excludeFromRecents="true"
            android:theme="@style/Theme.HaWidgets">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
            </intent-filter>
        </activity>
        <receiver
            android:name=".widget.multientity.MultiEntityWidgetReceiver"
            android:label="@string/multi_entity_widget_label"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/multi_entity_widget_info" />
        </receiver>

```

- [ ] **Step 3: Build**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Verify widget appears in system picker (emulator)**

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Long-press home screen → Widgets → scroll to "HA Widgets" → confirm "HA Multi" tile with
the 3-square preview icon appears in the list.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/xml/multi_entity_widget_info.xml app/src/main/AndroidManifest.xml
git commit -m "feat: registrér MultiEntityWidget i manifest + widget-info"
```

---

### Task 12: Emulator end-to-end QA

**Files:** none (verification only).

- [ ] **Step 1: Install**

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Place widget, configure 5 slots**

Long-press home screen on `pixel_test` → Widgets → "HA Widgets" → "HA Multi" → drag to home
screen. Config screen opens automatically (empty list). Add 5 slots covering:
1. A `light` entity, action = TOGGLE (same target).
2. A `climate` entity, action = RANGE (same target) — confirm slider opens with correct
   min/max from `min_temp`/`max_temp`.
3. A `lock` entity, action = TOGGLE — confirm it calls `lock`/`unlock` (not `turn_on`/`off`).
4. A `sensor` (e.g. a battery %) entity, action = TRIGGER, target changed via "Skift mål"
   to a `script` or `automation` entity — this is the decoupled display/action case from
   the spec. Confirm the list row shows "Udløs automatisering/script → <other entity>".
5. A `number` entity, action = RANGE — confirm slider opens, min/max from entity attrs,
   no Tænd/Sluk button shown, slider always enabled.

Set a widget title (e.g. "Test"). Tap "Gem widget".

- [ ] **Step 3: Verify rendering**

Confirm the widget shows 5 slot boxes left-to-right, title line above, correct icons per
domain, correct highlight colors (locked=primary, light on=primary, etc.), and tapping each
slot performs the expected action (light toggles, climate opens slider, lock toggles,
sensor-slot triggers the automation/script, number opens slider).

- [ ] **Step 4: Verify reconfiguration**

Long-press the widget → widget settings → confirm the SAME list screen opens, pre-filled
with all 5 configured slots (not the empty step-by-step flow). Remove one slot, reorder two
via ↑/↓, edit one slot's label, save. Confirm the widget updates to show 4 slots in the new
order.

- [ ] **Step 5: Verify fixed footprint**

Attempt to long-press-drag-resize the widget on the home screen — confirm no resize handles
appear (resizeMode="none" honored).

- [ ] **Step 6: Verify Room state directly**

```
adb shell run-as dk.akait.hawidgets sqlite3 databases/ha_widgets.db "SELECT * FROM multi_widget_slot;"
```
Confirm 4 rows (after the removal in Step 4), `slotIndex` values 0-3 contiguous, and the
decoupled slot's `displayEntityId` != `actionEntityId`.

- [ ] **Step 7: Fix any issues found, repeat Steps 1-6 until green**

Per `CLAUDE.md`'s iterative QA workflow — do not proceed to Task 13 until all of the above
pass cleanly.

---

### Task 13: Device QA (Galaxy S23) + final commit

**Files:** none (verification only).

- [ ] **Step 1: Install on Galaxy S23**

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Repeat Task 12's Steps 2-6 on the real device**

Pay special attention to:
- Whether One UI clips or otherwise misrenders the fixed 280×74dp footprint (the CLAUDE.md
  history notes Nova/One UI have previously handled fixed-size non-resizable widgets
  differently than the AOSP emulator launcher — this is exactly the kind of discrepancy
  that class of bug falls into).
- That the widget picker shows the "HA Multi" preview correctly.

- [ ] **Step 3: Fix any device-specific issues, rebuild, reinstall (`-r`, never uninstall), retest**

- [ ] **Step 4: Final commit (if any device-only fixes were needed)**

```bash
git add -A
git commit -m "fix: device-QA rettelser til MultiEntityWidget (Galaxy S23)"
```

If no device-only fixes were needed, this task is just verification — nothing to commit.
