# Widget UX Pack — implementeringsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 8 UX-forbedringer: slot-liste-redesign, synlige picker-ikoner, bekræft-ved-tryk, RANGE skyder/felt, globalt tema-valg, værdi-formatering, refresh-overlay, Opdater-knap.

**Architecture:** Alle nye per-slot-options i én Room-migration v5→v6 på `multi_widget_slot`. Ny pure-logik samles i `ValueFormatting.kt` (unit-testbar). Ny `ConfirmActionActivity` følger `RangeControlActivity`-dialog-mønstret. Tema via `SecureStore.themeMode` + delt `WidgetColors`-helper (ADR-5: updateAll ved skift).

**Tech Stack:** Kotlin, Jetpack Compose (app), Jetpack Glance 1.1.1 (widgets), Room, JUnit4 (ny).

**Spec:** `docs/superpowers/specs/2026-07-06-widget-ux-pack-design.md`. **ADR'er:** `docs/adr/2026-07-06-widget-ux-pack-adrs.md`.

## Global Constraints

- Byg: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`.
- Install ALTID `adb install -r` — aldrig uninstall.
- **Bump `versionCode` + `versionName` i `app/build.gradle.kts` FØR hvert build der installeres.** Serien starter på v0.3.0 (versionCode fortsætter fortløbende).
- Alle nye brugervendte strenge i ALLE 3 sprogfiler: `values/strings.xml` (EN, default), `values-da/`, `values-sv/`.
- `MultiEntityWidget.kt`: `SizeMode.Exact` — komponeret indhold må ALDRIG læse `LocalSize` (v0.2.37-advarsel).
- Glance-widgets: reaktiv Room `Flow` i `provideGlance`; aldrig `update()` fra config-activity.
- Tasks 11–12 (A1/B2-UI) må først implementeres EFTER mockup-gate-godkendelse (Fase 4).
- Task-numre committes enkeltvis: `feat:`/`fix:`-prefix, dansk beskrivelse (konvention fra git-log).

## Filstruktur

| Fil | Ansvar | Ny/Ændres |
|---|---|---|
| `app/build.gradle.kts` | junit-dep, version bump | Ændres |
| `app/src/test/java/dk/akait/hawidgets/widget/common/ValueFormattingTest.kt` | Unit-tests formatering | Ny |
| `app/src/main/java/dk/akait/hawidgets/widget/common/ValueFormatting.kt` | Pure formatering (decimaler, datetime) | Ny |
| `app/src/main/java/dk/akait/hawidgets/data/db/MultiWidgetSlotEntity.kt` | 8 nye kolonner | Ændres |
| `app/src/main/java/dk/akait/hawidgets/data/db/Migrations.kt` | MIGRATION_5_6 | Ændres |
| `app/src/main/java/dk/akait/hawidgets/data/db/AppDatabase.kt` | version 6 | Ændres |
| `app/src/main/res/values{,-da,-sv}/strings.xml` | Nye + flyttede strenge | Ændres |
| `app/src/main/res/drawable/preview_*.xml` | Picker-preview-plader | Nye |
| `app/src/main/res/xml/*_widget_info.xml` | previewImage-referencer | Ændres |
| `app/src/main/java/dk/akait/hawidgets/widget/common/ConfirmActionActivity.kt` | Bekræft-dialog | Ny |
| `app/src/main/java/dk/akait/hawidgets/widget/common/WidgetColors.kt` | Tema-tvang for Glance | Ny |
| `app/src/main/java/dk/akait/hawidgets/data/SecureStore.kt` | themeMode-felt | Ændres |
| `app/src/main/java/dk/akait/hawidgets/ui/theme/Theme.kt` | Mørkt skema + valg | Ændres |
| `app/src/main/java/dk/akait/hawidgets/MainActivity.kt` | Tema-dropdown | Ændres |
| `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityWidget.kt` | Confirm-routing, formatering, overlay | Ændres |
| `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityWidgetConfigActivity.kt` | Slot-kort-redesign, nye options, Opdater-knap | Ændres |
| `app/src/main/java/dk/akait/hawidgets/widget/common/RangeControlActivity.kt` | B2 (variant iht. mockup) | Ændres |
| `app/src/main/java/dk/akait/hawidgets/widget/SensorWidget.kt` | Auto-formatering | Ændres |
| `AndroidManifest.xml` | ConfirmActionActivity-registrering | Ændres |

---

### Task 1: Unit-test-infrastruktur

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/test/java/dk/akait/hawidgets/SmokeTest.kt`

**Interfaces:** Produces: kørbar `./gradlew testDebugUnitTest`.

- [ ] **Step 1:** Tilføj i `app/build.gradle.kts` dependencies-blok:

```kotlin
testImplementation("junit:junit:4.13.2")
```

- [ ] **Step 2:** Opret `app/src/test/java/dk/akait/hawidgets/SmokeTest.kt`:

```kotlin
package dk.akait.hawidgets

import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeTest {
    @Test fun testInfrastructureWorks() = assertTrue(true)
}
```

- [ ] **Step 3:** Kør `JAVA_HOME=... ./gradlew testDebugUnitTest` → Forventet: BUILD SUCCESSFUL, 1 test.
- [ ] **Step 4:** Commit `test: unit-test-infrastruktur (junit4)`.

---

### Task 2: ValueFormatting — pure formatering + tests (C2-kernen)

**Files:**
- Create: `app/src/test/java/dk/akait/hawidgets/widget/common/ValueFormattingTest.kt`
- Create: `app/src/main/java/dk/akait/hawidgets/widget/common/ValueFormatting.kt`

**Interfaces:**
- Produces: `formatNumericState(state: String, precision: Int?): String` (null=auto maks 1 decimal; ikke-numerisk → uændret input). `formatDateTimeState(state: String, pattern: String?, hasDate: Boolean, hasTime: Boolean, locale: Locale): String` (pattern null/tom/ugyldig → auto lokalt kort format; uparsbar state → uændret input). `isDateTimeLike(domain: String, attributesJson: String?): Boolean` (input_datetime-domæne eller sensor med `device_class=timestamp`).

- [ ] **Step 1: Failing tests**

```kotlin
package dk.akait.hawidgets.widget.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ValueFormattingTest {
    // Numerisk
    @Test fun autoRoundsToOneDecimal() = assertEquals("23.9", formatNumericState("23.88888888888", null))
    @Test fun autoDropsTrailingZeroDecimal() = assertEquals("24", formatNumericState("24.0", null))
    @Test fun integerStaysInteger() = assertEquals("42", formatNumericState("42", null))
    @Test fun precisionZeroRounds() = assertEquals("24", formatNumericState("23.9", 0))
    @Test fun precisionTwoKeeps() = assertEquals("23.89", formatNumericState("23.88888", 2))
    @Test fun precisionPadsZeros() = assertEquals("24.00", formatNumericState("24", 2))
    @Test fun nonNumericPassthrough() = assertEquals("1.2.3", formatNumericState("1.2.3", null))
    @Test fun usesDotNeverComma() = assertEquals("23.9", formatNumericState("23.88", 1)) // uanset enheds-locale

    // Datetime — HA-format "2026-07-04 15:30:00" (input_datetime) og ISO-8601 (timestamp-sensorer)
    @Test fun patternApplied() =
        assertEquals("04/07 15:30", formatDateTimeState("2026-07-04 15:30:00", "dd/MM HH:mm", true, true, Locale.ROOT))
    @Test fun isoTimestampParsed() =
        assertEquals("04/07 15:30", formatDateTimeState("2026-07-04T15:30:00+02:00", "dd/MM HH:mm", true, true, Locale.ROOT))
    @Test fun invalidPatternFallsBackToAuto() {
        val out = formatDateTimeState("2026-07-04 15:30:00", "VVVV-ugyldig", true, true, Locale.ROOT)
        assertEquals(autoDateTime("2026-07-04 15:30:00", true, true, Locale.ROOT), out)
    }
    @Test fun unparsableStatePassthrough() =
        assertEquals("unknown", formatDateTimeState("unknown", "dd/MM", true, true, Locale.ROOT))
    @Test fun timeOnlyEntity() =
        assertEquals("15:30", formatDateTimeState("15:30:00", null, false, true, Locale.ROOT))

    @Test fun timestampSensorDetected() =
        assertEquals(true, isDateTimeLike("sensor", """{"device_class":"timestamp"}"""))
    @Test fun inputDatetimeDetected() = assertEquals(true, isDateTimeLike("input_datetime", null))
    @Test fun plainSensorNotDateTime() = assertEquals(false, isDateTimeLike("sensor", """{"device_class":"temperature"}"""))
}
```

- [ ] **Step 2:** Kør → FAIL (unresolved references).
- [ ] **Step 3: Implementering** (`ValueFormatting.kt`):

```kotlin
package dk.akait.hawidgets.widget.common

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Afrund numerisk HA-state. precision=null → auto: maks 1 decimal, heltal forbliver heltal.
 *  Punktum altid decimalseparator (HA-state er Locale.ROOT; visning m. komma brød trim i v0.2.34). */
fun formatNumericState(state: String, precision: Int?): String {
    val value = state.toBigDecimalOrNull() ?: return state
    return if (precision == null) {
        val rounded = value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
        rounded.toPlainString()
    } else {
        value.setScale(precision, RoundingMode.HALF_UP).toPlainString()
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try { BigDecimal(trim()) } catch (_: Exception) { null }

private val HA_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun parseHaDateTime(state: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(state, HA_LOCAL) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(state).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDate.parse(state).atStartOfDay() }.getOrNull()
        ?: runCatching { LocalTime.parse(state).atDate(LocalDate.now()) }.getOrNull()

/** Auto-format: lokalt kort format udledt af has_date/has_time. */
fun autoDateTime(state: String, hasDate: Boolean, hasTime: Boolean, locale: Locale): String {
    val dt = parseHaDateTime(state) ?: return state
    val fmt = when {
        hasDate && hasTime -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        hasDate -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        else -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }.withLocale(locale)
    return fmt.format(dt)
}

/** Frit DateTimeFormatter-mønster; null/tom/ugyldig → auto; uparsbar state → passthrough. */
fun formatDateTimeState(state: String, pattern: String?, hasDate: Boolean, hasTime: Boolean, locale: Locale): String {
    val dt = parseHaDateTime(state) ?: return state
    if (pattern.isNullOrBlank()) return autoDateTime(state, hasDate, hasTime, locale)
    return runCatching { DateTimeFormatter.ofPattern(pattern, locale).format(dt) }
        .getOrElse { autoDateTime(state, hasDate, hasTime, locale) }
}

/** Er entiteten datetime-agtig (frit datoformat-felt vises i config)? */
fun isDateTimeLike(domain: String, attributesJson: String?): Boolean {
    if (domain == "input_datetime") return true
    if (domain != "sensor") return false
    return attributesJson?.let {
        runCatching { JSONObject(it).optString("device_class") == "timestamp" }.getOrDefault(false)
    } ?: false
}
```

- [ ] **Step 4:** Kør tests → PASS. NB: `invalidPatternFallsBackToAuto` kræver at "VVVV-ugyldig" reelt kaster — `ofPattern` kaster IllegalArgumentException på ukendte bogstaver; verificér, ellers vælg andet ugyldigt mønster (fx `"qqqq'"` med ulukket quote).
- [ ] **Step 5:** Commit `feat: ValueFormatting — decimal-afrunding + datetime-mønstre (C2-kerne)`.

---

### Task 3: Room v5→v6 — nye slot-kolonner

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/data/db/MultiWidgetSlotEntity.kt`
- Modify: `app/src/main/java/dk/akait/hawidgets/data/db/Migrations.kt`
- Modify: `app/src/main/java/dk/akait/hawidgets/data/db/AppDatabase.kt`

**Interfaces:**
- Produces: nye nullable felter på `MultiWidgetSlotEntity`: `confirmAction: Boolean` (default false), `secondary{1,2,3}ConfirmAction: Boolean?`, `displayPrecision: Int?`, `secondary{1,2,3}DisplayPrecision: Int?`, `datetimeFormat: String?`, `secondary{1,2,3}DatetimeFormat: String?`. (`rangeInputMode` tilføjes KUN i Task 12 hvis mockup vælger Variant A — da som separat v6→v7.)

- [ ] **Step 1:** Tilføj felter til `MultiWidgetSlotEntity` (efter `label`, hhv. efter hver `secondaryNShowValue`):

```kotlin
    // v0.3.0: Bekræft ved tryk (B1) — kun meningsfuld for TOGGLE/TRIGGER
    val confirmAction: Boolean = false,
    // v0.3.0: Værdi-formatering (C2) — null = auto
    val displayPrecision: Int? = null,
    val datetimeFormat: String? = null,
```

og pr. sekundær N=1..3 (efter `secondaryNShowValue`):

```kotlin
    val secondaryNConfirmAction: Boolean? = null,
    val secondaryNDisplayPrecision: Int? = null,
    val secondaryNDatetimeFormat: String? = null,
```

- [ ] **Step 2:** `Migrations.kt` — ny migration efter MIGRATION_4_5:

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN confirmAction INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN displayPrecision INTEGER")
        db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN datetimeFormat TEXT")
        for (n in 1..3) {
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}ConfirmAction INTEGER")
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}DisplayPrecision INTEGER")
            db.execSQL("ALTER TABLE multi_widget_slot ADD COLUMN secondary${n}DatetimeFormat TEXT")
        }
    }
}
```

- [ ] **Step 3:** `AppDatabase.kt`: `version = 6`, tilføj `MIGRATION_5_6` i builder-kæden (samme sted som 1_2..4_5).
- [ ] **Step 4:** Byg + installer (`-r`) på emulator med EKSISTERENDE multi-widgets → verificér i logcat ingen migration-crash, widgets renderer som før. DB-tjek: `adb shell "run-as dk.akait.hawidgets sqlite3 databases/hawidgets.db 'PRAGMA table_info(multi_widget_slot);'"` viser 12 nye kolonner. (Justér db-filnavn hvis anderledes — slå op i AppDatabase.)
- [ ] **Step 5:** Commit `feat: Room v5→v6 — confirmAction/displayPrecision/datetimeFormat pr. slot+chips`.

---

### Task 4: Strenge — nye keys + i18n-oprydning (A3-bonus)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, `values-da/strings.xml`, `values-sv/strings.xml`
- Modify: `MultiEntityWidgetConfigActivity.kt:442,767`, `BaseEntityPickerActivity.kt:169`

**Interfaces:** Produces string-keys: `save_widget` (findes), `add_to_widget`, `update_slot`, `confirm_action_switch`, `confirm_dialog_cancel`, `confirm_dialog_confirm`, `confirm_toggle_on`, `confirm_toggle_off`, `confirm_trigger`, `display_precision_label`, `precision_auto`, `datetime_format_label`, `datetime_format_hint`, `theme_label`, `theme_light`, `theme_dark`, `theme_system`.

- [ ] **Step 1:** Tilføj i `values/strings.xml` (EN):

```xml
<string name="add_to_widget">Add to widget</string>
<string name="update_slot">Update</string>
<string name="confirm_action_switch">Confirm on tap</string>
<string name="confirm_dialog_cancel">Cancel</string>
<string name="confirm_dialog_confirm">Confirm</string>
<string name="confirm_toggle_on">Turn on %1$s?</string>
<string name="confirm_toggle_off">Turn off %1$s?</string>
<string name="confirm_trigger">Run %1$s?</string>
<string name="display_precision_label">Decimals</string>
<string name="precision_auto">Auto</string>
<string name="datetime_format_label">Date format</string>
<string name="datetime_format_hint">e.g. dd/MM HH:mm — empty = automatic</string>
<string name="theme_label">Theme</string>
<string name="theme_light">Light</string>
<string name="theme_dark">Dark</string>
<string name="theme_system">Follow system</string>
```

`values-da`: Tilføj til widget / Opdater / Bekræft ved tryk / Annullér / Bekræft / Tænd %1$s? / Sluk %1$s? / Kør %1$s? / Decimaler / Auto / Datoformat / fx dd/MM HH:mm — tomt = automatisk / Tema / Lys / Mørk / Følg system.
`values-sv`: Lägg till i widget / Uppdatera / Bekräfta vid tryck / Avbryt / Bekräfta / Slå på %1$s? / Stäng av %1$s? / Kör %1$s? / Decimaler / Auto / Datumformat / t.ex. dd/MM HH:mm — tomt = automatiskt / Tema / Ljust / Mörkt / Följ systemet.

- [ ] **Step 2:** Erstat hardcodede tekster: `MultiEntityWidgetConfigActivity.kt:442` `Text("Gem widget")` → `Text(stringResource(R.string.save_widget))`; `:767` → `stringResource(R.string.add_to_widget)` (Opdater-logik kommer i Task 5); `BaseEntityPickerActivity.kt:169` → `stringResource(R.string.save_widget)`. Scan samme filer for andre hardcodede brugervendte strenge og flyt dem tilsvarende (behold nøjagtig dansk ordlyd i values-da).
- [ ] **Step 3:** Byg → grøn. Skift sprog i appen → alle 3 sprog viser korrekt.
- [ ] **Step 4:** Commit `fix: hardcodede strenge → strings.xml (i18n-regression) + nye UX-pack-keys`.

---

### Task 5: A3 — "Opdater" ved redigering af slot

**Files:**
- Modify: `MultiEntityWidgetConfigActivity.kt` (`SlotEditorScreen`, knap ~:767)

**Interfaces:** Consumes: `SlotEditorScreen` ved allerede om den redigerer (kaldes med eksisterende slot ≠ null — find parameteren, fx `existingSlot`/`draftFromSlot`-kilden).

- [ ] **Step 1:** Find hvordan `SlotEditorScreen` modtager eksisterende slot (v0.2.25's `draftFromSlot`). Tilføj/genbrug `isEditing: Boolean`.
- [ ] **Step 2:** Knappen:

```kotlin
Button(onClick = onSave, enabled = !invalidTarget, modifier = Modifier.fillMaxWidth()) {
    Text(stringResource(if (isEditing) R.string.update_slot else R.string.add_to_widget))
}
```

- [ ] **Step 3:** Byg + emulator: åbn eksisterende slot → "Opdater"; tilføj ny → "Tilføj til widget".
- [ ] **Step 4:** Commit `fix: slot-editor-knap viser Opdater ved redigering (punkt 8)`.

---

### Task 6: A2 — synlige picker-ikoner

**Files:**
- Create: `app/src/main/res/drawable/preview_light.xml` (+ én pr. widget-info-fil: switch, scene, script, automation, sensor, binary_sensor, climate, cover, multi_entity, shortcut — match de faktiske `previewImage`-referencer, slå op i `res/xml/*_widget_info.xml`)
- Modify: alle `res/xml/*_widget_info.xml` `android:previewImage`

**Interfaces:** Ingen kode-API.

- [ ] **Step 1:** Skabelon (layer-list, plade i brand-blå `#0B6FA4`, ikonet indsat med padding):

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#0B6FA4"/>
            <corners android:radius="12dp"/>
            <size android:width="56dp" android:height="56dp"/>
        </shape>
    </item>
    <item android:drawable="@drawable/ic_lightbulb"
          android:top="14dp" android:bottom="14dp" android:left="14dp" android:right="14dp"/>
</layer-list>
```

Én fil pr. widget-type; multi-entity bruger `ic_multi_entity_4` (bredere plade 110×56dp, padding tilsvarende).

- [ ] **Step 2:** Opdatér `previewImage` i hver widget-info-XML til `@drawable/preview_<type>`.
- [ ] **Step 3:** Byg + emulator: widget-picker i LYST tema → alle ikoner synlige på plade; mørkt tema → stadig OK.
- [ ] **Step 4:** Commit `fix: widget-picker preview-ikoner synlige i lyst tema (punkt 2)`.

---

### Task 7: B1 config — "Bekræft ved tryk"-switch

**Files:**
- Modify: `MultiEntityWidgetConfigActivity.kt` (SlotEditorScreen Handling-sektion + Ekstra info-sektion, draft-model, save-mapping)

**Interfaces:**
- Consumes: Task 3-felter. Produces: draft-felter `confirmAction: Boolean` (hoved) og pr. sekundær `confirmAction: Boolean` gemmes/indlæses symmetrisk med eksisterende felter (fx `secondaryNShowValue`-mønstret).

- [ ] **Step 1:** Udvid draft-dataklasserne (hoved + sekundær) med `confirmAction: Boolean = false`. Indlæs i `draftFromSlot`, skriv i save-mapping.
- [ ] **Step 2:** I Handling-sektionen, EFTER handlings-valget, KUN når valgt handling er TOGGLE eller TRIGGER:

```kotlin
if (draft.action == "TOGGLE" || draft.action == "TRIGGER") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.confirm_action_switch), modifier = Modifier.weight(1f))
        Switch(checked = draft.confirmAction, onCheckedChange = { draft = draft.copy(confirmAction = it) })
    }
}
```

Samme mønster i Ekstra info-sektionen pr. chip (følg `secondaryNShowValue`-switchens eksisterende placering/stil).

- [ ] **Step 3:** Byg + emulator: sæt switch på hoved + chip, gem, genåbn → værdi bevaret. DB-tjek kolonnerne.
- [ ] **Step 4:** Commit `feat: Bekræft ved tryk-switch i Handling (hoved + chips) (punkt 3, config-del)`.

---

### Task 8: B1 — ConfirmActionActivity + routing

**Files:**
- Create: `app/src/main/java/dk/akait/hawidgets/widget/common/ConfirmActionActivity.kt`
- Modify: `AndroidManifest.xml`, `MultiEntityWidget.kt` (`clickModifier` ~:394–460)

**Interfaces:**
- Consumes: `EntityRepository.command(context, domain, service, entityId, targetState, currentState)` (samme kald som `ToggleEntityAction`/`TriggerEntityAction` i `EntityActions.kt` — kopiér service-mappingen derfra). Slot-felterne fra Task 3.
- Produces: `ConfirmActionActivity` med Intent-extras: `EXTRA_ENTITY_ID`, `EXTRA_DOMAIN`, `EXTRA_LABEL` (handlings-målets friendly name, ADR-1), `EXTRA_ACTION` ("TOGGLE"/"TRIGGER"), `EXTRA_IS_ON` (Boolean, nuværende state for toggle-tekst).

- [ ] **Step 1:** Ny activity efter `RangeControlActivity`-mønstret (translucent tema, Material3 Surface-dialog, bootstrap kopieret — kendt duplikeret mønster, accepteret i v0.2.34-fund #7):

```kotlin
package dk.akait.hawidgets.widget.common

// imports som RangeControlActivity + R

class ConfirmActionActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ENTITY_ID = "entity_id"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_LABEL = "label"
        const val EXTRA_ACTION = "action"
        const val EXTRA_IS_ON = "is_on"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: return finish()
        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: return finish()
        val label = intent.getStringExtra(EXTRA_LABEL) ?: entityId
        val action = intent.getStringExtra(EXTRA_ACTION) ?: "TOGGLE"
        val isOn = intent.getBooleanExtra(EXTRA_IS_ON, false)

        setContent {
            HaWidgetsTheme {
                // Surface-dialog som RangeControlActivity
                val question = when {
                    action == "TRIGGER" -> stringResource(R.string.confirm_trigger, label)
                    isOn -> stringResource(R.string.confirm_toggle_off, label)
                    else -> stringResource(R.string.confirm_toggle_on, label)
                }
                ConfirmDialog(
                    question = question,
                    onCancel = { finish() },
                    onConfirm = {
                        lifecycleScope.launch {
                            executeAction(applicationContext, domain, action, entityId, isOn)
                            finish()
                        }
                    }
                )
            }
        }
    }
}

/** Samme service-mapping som Toggle/TriggerEntityAction i EntityActions.kt. */
internal suspend fun executeAction(context: Context, domain: String, action: String, entityId: String, isOn: Boolean) {
    // TOGGLE: lock → lock/unlock, cover → open_cover/close_cover, ellers toggle (kopiér præcis mapping fra ToggleEntityAction)
    // TRIGGER: automation → trigger, scene → turn_on, script/input_button → turn_on/press (kopiér fra TriggerEntityAction)
    // Kald EntityRepository.command(...) og derefter SyncWorker.runNow(context) hvis EntityActions gør det.
}
```

VIGTIGT: læs `EntityActions.kt` og kopiér mapping 1:1 — planen her viser strukturen, mappingen SKAL matche eksisterende adfærd (inkl. evt. optimistisk state og sync-kald).

- [ ] **Step 2:** Manifest-registrering (samme attributter som `RangeControlActivity`: translucent tema, `excludeFromRecents`, `taskAffinity=""` — kopiér blokken).
- [ ] **Step 3:** `MultiEntityWidget.clickModifier`: hvor TOGGLE/TRIGGER i dag laver `actionRunCallback<...>`, tilføj gren:

```kotlin
if (slotConfirm) { // confirmAction-feltet for hoved/chip
    val intent = Intent(context, ConfirmActionActivity::class.java).apply {
        putExtra(ConfirmActionActivity.EXTRA_ENTITY_ID, actionEntityId)
        putExtra(ConfirmActionActivity.EXTRA_DOMAIN, actionDomain)
        putExtra(ConfirmActionActivity.EXTRA_LABEL, actionTargetFriendlyName) // ADR-1: handlings-målets navn
        putExtra(ConfirmActionActivity.EXTRA_ACTION, action)
        putExtra(ConfirmActionActivity.EXTRA_IS_ON, currentState == "on")
        data = Uri.parse("hawidgets://confirm/$appWidgetId/$slotIndex/$chipIndex") // unik PendingIntent
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    base.clickable(actionStartActivity(intent))
} else { /* eksisterende actionRunCallback-vej uændret */ }
```

`actionTargetFriendlyName`: slå action-entitetens state op i Room (samme kilde som visnings-state) og brug `friendlyNameFromJson(attributesJson)`; fallback entityId. NB: action-entitet ≠ display-entitet → dens state skal hentes (tjek hvordan RANGE-grenen allerede gør dette for `EXTRA_IS_ON`/værdier — genbrug mønstret).

- [ ] **Step 4:** Byg + emulator: slot med confirm TIL → tap åbner dialog over hjemmeskærm; Annullér = ingen ændring; Bekræft = toggle udføres (verificér HA-state via MCP `ha_get_state`). Chip med confirm og ASYMMETRISK mål → dialog viser målets navn. Confirm FRA → direkte toggle som før.
- [ ] **Step 5:** Commit `feat: ConfirmActionActivity — bekræft-dialog for toggle/trigger (punkt 3)`.

---

### Task 9: C2 — formatering integreret i widgets + config-UI

**Files:**
- Modify: `MultiEntityWidget.kt` (SlotRow ~:242–306 + chips ~:361), `SensorWidget.kt`, `MultiEntityWidgetConfigActivity.kt` (Visning-sektion + Ekstra info)

**Interfaces:**
- Consumes: Task 2-funktioner, Task 3-felter (`displayPrecision`, `datetimeFormat` + sekundær-varianter).
- Produces: ny delt hjælper i `ValueFormatting.kt`:

```kotlin
/** Samlet visnings-værdi: datetime-agtig → formatDateTimeState; ellers numerisk-afrunding + enhed. */
fun formatDisplayValue(
    domain: String, state: String?, attributesJson: String?,
    precision: Int?, datetimePattern: String?, locale: Locale,
): String {
    if (state == null) return "…"
    if (state == "unavailable") return "" // kald formatEntityState for domæne-tekster FØR denne
    if (isDateTimeLike(domain, attributesJson)) {
        val attrs = attributesJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val hasDate = attrs?.optBoolean("has_date", true) ?: true
        val hasTime = attrs?.optBoolean("has_time", true) ?: true
        return formatDateTimeState(state, datetimePattern, hasDate, hasTime, locale)
    }
    val numeric = formatNumericState(state, precision)
    val unit = attributesJson?.let { unitFromJson(it) }
    return if (unit.isNullOrEmpty()) numeric else "$numeric $unit"
}
```

- [ ] **Step 1: Tests først** (tilføj i `ValueFormattingTest.kt`): `formatDisplayValue("sensor","23.888","""{"unit_of_measurement":"°C"}""",null,null,Locale.ROOT)` == `"23.9 °C"`; datetime-sensor med pattern; input_datetime auto. Kør → FAIL → implementér → PASS.
- [ ] **Step 2:** `MultiEntityWidget.kt`: hvor `formatEntityState(...)` kaldes for RÅ-VÆRDI-domæner (sensor/number/input_*), erstat med logik: domæne-tekst-domæner (light/switch/lock/...) beholder `formatEntityState`; værdi-domæner går gennem `formatDisplayValue` med slot/chip's precision+pattern. Enkleste snit: lad `formatEntityState` stå, og efter-behandl KUN når resultatet er `"$state $unit"`/rå state — dvs. flyt unit-grenen ud: kald `formatDisplayValue` for domæner hvor `compatibleActionsFor` ikke giver TOGGLE/TRIGGER-tekster… **Simplere og robust:** tilføj param til call-sites: brug `formatDisplayValue` når `domain in setOf("sensor","number","input_number","input_text","input_datetime","input_select")`, ellers `formatEntityState`. Locale: `context.resources.configuration.locales[0]`.
- [ ] **Step 3:** `SensorWidget.kt` (auto-only): i `buildSensorValue`, kør state gennem `formatNumericState(state, null)` og datetime-sensorer gennem `formatDateTimeState(state, null, …)` via `isDateTimeLike`.
- [ ] **Step 4:** Config-UI (Visning-sektion, hoved + pr. chip i Ekstra info):
  - **Decimaler-dropdown** (kun når entiteten IKKE er datetime-agtig og state er numerisk-agtig — vis altid for sensor/number/input_number): `ExposedDropdownMenuBox` med Auto/0/1/2 (genbrug sprog-vælgerens dropdown-mønster fra MainActivity). Gemmer `displayPrecision` null/0/1/2.
  - **Datoformat-felt** (kun `isDateTimeLike`): `OutlinedTextField` med `datetime_format_label`, supporting text = `datetime_format_hint`, og **live preview**: under feltet `Text(formatDateTimeState(currentState, draft.datetimeFormat, hasDate, hasTime, locale))` — nuværende state hentes fra den allerede indlæste entity-liste/Room.
- [ ] **Step 5:** Byg + emulator: sensor 23.88888 → "23.9 °C" på widget; override 0 → "24 °C"; input_datetime chip med `dd/MM HH:mm` → korrekt; ugyldigt mønster → auto (ingen crash). Unit-tests stadig grønne.
- [ ] **Step 6:** Commit `feat: værdi-formatering — auto + pr. slot/chip-override (punkt 6)`.

---

### Task 10: C1 — globalt tema-valg

**Files:**
- Modify: `SecureStore.kt`, `Theme.kt`, `MainActivity.kt`
- Create: `app/src/main/java/dk/akait/hawidgets/widget/common/WidgetColors.kt`
- Modify: alle Glance-widget-filer der bruger `GlanceTheme.colors` / day-night `ColorProvider` (MultiEntityWidget, GlanceWidgetCommon, ShortcutWidget m.fl. — grep `GlanceTheme.colors` og `androidx.glance.color.ColorProvider`)

**Interfaces:**
- Produces: `SecureStore.themeMode: String` ("light"|"dark"|"system", default "system"). `WidgetColors.resolve(context): WidgetPalette` hvor `WidgetPalette` udstiller de brugte roller (primary, onPrimary, surfaceVariant, onSurfaceVariant, errorContainer, onErrorContainer, frameBackground, refreshOverlay) som `ColorProvider`.

- [ ] **Step 1:** `SecureStore`: nyt felt (samme mønster som eksisterende properties):

```kotlin
var themeMode: String
    get() = prefs.getString("theme_mode", "system") ?: "system"
    set(value) = prefs.edit().putString("theme_mode", value).apply()
```

- [ ] **Step 2:** `Theme.kt`: tilføj `HaWidgetsDarkColorScheme = darkColorScheme(...)` (afstem primær #0B6FA4-familien mod mørk baggrund — fx primary #7FC3E8, surface #121417; finjusteres ved mockup-gate) og:

```kotlin
@Composable
fun HaWidgetsTheme(content: @Composable () -> Unit) {
    val mode = remember { SecureStore(LocalContext.current).themeMode } // + genlæs ved recomposition-trigger fra MainActivity
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) HaWidgetsDarkColorScheme else HaWidgetsColorScheme, content = content)
}
```

NB: eksisterende kald-signatur `HaWidgetsTheme { ... }` bevares — alle activities får automatisk valget. Tema-skift i MainActivity skal recompose (hold mode i `mutableStateOf` løftet til MainActivity og send som CompositionLocal/param, eller genstart activity ved skift — vælg simpleste: `activity.recreate()` efter gem).

- [ ] **Step 3:** `MainActivity`: Tema-dropdown ved sprog-vælgeren (~:480, kopiér `ExposedDropdownMenuBox`-mønstret 1:1) med `theme_light`/`theme_dark`/`theme_system`. Ved valg: `secureStore.themeMode = ...`, `recreate()`, og **updateAll** (ADR-5):

```kotlin
lifecycleScope.launch {
    // én linje pr. widget-klasse — slå den fulde provider-liste op i AndroidManifest
    MultiEntityWidget().updateAll(applicationContext)
    LightWidget().updateAll(applicationContext)
    // ... øvrige
}
```

- [ ] **Step 4:** `WidgetColors.kt`:

```kotlin
package dk.akait.hawidgets.widget.common

/** Tema-tvang for Glance. system → dynamiske day/night-farver (nuværende adfærd);
 *  light/dark → faste farver så widgetten ignorerer systemets nattilstand. */
object WidgetColors {
    data class WidgetPalette(
        val primary: ColorProvider, val onPrimary: ColorProvider,
        val surfaceVariant: ColorProvider, val onSurfaceVariant: ColorProvider,
        val errorContainer: ColorProvider, val onErrorContainer: ColorProvider,
        val frameBackground: ColorProvider, val refreshOverlay: ColorProvider,
    )

    fun resolve(context: Context): WidgetPalette {
        return when (SecureStore(context).themeMode) {
            "light" -> fixed(light = true)
            "dark" -> fixed(light = false)
            else -> systemAdaptive() // day/night ColorProviders — genbrug eksisterende værdier
        }
    }
    // fixed(): ColorProvider(Color(...)) med de konkrete lys-/mørk-værdier fra systemAdaptive()s to sider
}
```

Migrér forbrugere: `MultiEntityWidget` (FRAME_BACKGROUND, slot-farver, strip-tint), `GlanceWidgetCommon`-layouts, øvrige widgets. Hvor `GlanceTheme.colors.X` bruges i dag → `palette.X`. Bevar præcis samme farveværdier for "system"-mode (ingen visuel regression).

- [ ] **Step 5:** Byg + emulator: skift Mørk → app-UI mørk med det samme, ALLE placerede widgets skifter (updateAll), config-skærme mørke. Skift Lys/Følg system tilsvarende. Emulator-systemtema togglet → kun "system"-mode følger.
- [ ] **Step 6:** Commit `feat: globalt tema-valg lys/mørk/system — app + widgets (punkt 5)`.

---

### Task 11: C3 — refresh-bar som halvtransparent overlay (+spacer)

**Files:**
- Modify: `MultiEntityWidget.kt` (`MultiEntityContent` ~:186–217, `RefreshStrip` ~:220–239)

**Interfaces:** Consumes: `WidgetColors.refreshOverlay` (Task 10) — implementeres denne task FØR Task 10, brug midlertidigt `androidx.glance.color.ColorProvider(day = Color(0xA6FFFFFF), night = Color(0xA6202124))` (~65 % alpha) og skift til palette i Task 10.

- [ ] **Step 1:** Omstrukturér `MultiEntityContent`: erstat Column(list, strip) med:

```kotlin
Box(modifier = GlanceModifier.fillMaxSize()) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(...) { /* uændrede rækker */ }
        if (showRefreshIcon) item { Spacer(GlanceModifier.height(REFRESH_STRIP_HEIGHT_DP.dp)) } // ADR-3
    }
    if (showRefreshIcon) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            RefreshStrip(...) // får .background(refreshOverlay) + cornerRadius i bunden matchende rammen
        }
    }
}
```

NB Glance-begrænsning: `Alignment.BottomCenter` på Box-child — verificér at Glance 1.1.1 Box understøtter contentAlignment (den gør: `Box(contentAlignment=...)`)  og at klik på strip stadig virker OVER LazyColumn (sidst deklarerede barn ligger øverst). Fungerer stacking ikke på RemoteViews (test!), fallback: behold Column men giv strippen negativ-margin-fri løsning — dokumentér fund og stop, spørg bruger.

- [ ] **Step 2:** `RefreshStrip`: tilføj `.background(refreshOverlay)`. Ikon-tint uændret.
- [ ] **Step 3:** Byg + emulator: widget med overflow → rækker skimtes bag baren under scroll; fuldt nedscrollet → sidste række helt synlig over baren; uden overflow → ingen skjult info. Tap på strip → sync kører (logcat SUCCESS).
- [ ] **Step 4:** Commit `feat: refresh-bar som halvtransparent overlay med bund-spacer (punkt 7, ADR-3)`.

---

### Task 12: A1 — slot-liste-redesign (EFTER mockup-godkendelse)

**Files:**
- Modify: `MultiEntityWidgetConfigActivity.kt` (`SlotCard` ~:452–566)

**GATE: Implementér KUN det layout mockup-gaten godkendte.** Udgangsforslag (mockes i Fase 4):

- Række 1 (fuld bredde): entitetsnavn `bodyLarge`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, chevron til højre. Hele rækken klikbar → editor.
- Række 2: handling-resumé (`bodySmall`, maxLines 1 ellipsis).
- Række 3: sekundær-chips (alle synlige, som i dag).
- Række 4 (bund, ikon-række højrestillet): ↑ ↓ 🗑 som `IconButton`s (48dp targets) — navnet konkurrerer ikke længere med søjlerne om bredde.

- [ ] **Step 1:** Implementér godkendt layout. Behold al eksisterende funktionalitet (klik → editor, slet, flyt op/ned, chips altid synlige).
- [ ] **Step 2:** Byg + emulator: lange entitetsnavne (fx "1.sal bevægelser temperatur…") → én linje m. ellipsis, kort overskueligt. Alle knapper virker.
- [ ] **Step 3:** Commit `feat: slot-liste-kort redesignet — navn på egen række (punkt 1)`.

---

### Task 13: B2 — RANGE skyder/felt (EFTER mockup-beslutning)

**Files:**
- Modify: `RangeControlActivity.kt` (+ evt. `MultiEntityWidgetConfigActivity.kt`, `Migrations.kt` v6→v7, `MultiWidgetSlotEntity.kt` — kun Variant A)

**GATE: Mockup-gaten vælger Variant A, B eller kombination.**

**Variant B (−/+ trin-knapper, gælder ALLE widgets — ADR-2):**
- [ ] Step B1: Unit-test trin-logik:

```kotlin
class RangeStepTest {
    @Test fun smallRangeUsesHalfStep() = assertEquals(0.5, stepFor(16.0, 30.0), 0.0)
    @Test fun largeRangeUsesWholeStep() = assertEquals(1.0, stepFor(0.0, 100.0), 0.0)
    @Test fun minusRoundsToNearestStep() = assertEquals(23.5, stepValue(23.7, -1, 0.5, 16.0, 30.0), 0.0)
    @Test fun plusFromRoundValue() = assertEquals(24.0, stepValue(23.5, +1, 0.5, 16.0, 30.0), 0.0)
    @Test fun clampsAtMax() = assertEquals(30.0, stepValue(30.0, +1, 0.5, 16.0, 30.0), 0.0)
}
```

- [ ] Step B2: Implementér i `ValueFormatting.kt` eller ny `RangeStepping.kt`:

```kotlin
fun stepFor(min: Double, max: Double): Double = if (max - min <= 20.0) 0.5 else 1.0
fun stepValue(current: Double, direction: Int, step: Double, min: Double, max: Double): Double {
    val snapped = Math.round(current / step) * step
    val next = if (snapped != current && direction < 0) snapped else snapped + direction * step
    return next.coerceIn(min, max)
}
```

(NB: "første tryk afrunder" — minus på 23.7 → 23.5, plus på 23.7 → 24.0; justér formlen så begge retninger snapper først, verificér mod tests.)
- [ ] Step B3: `RangeControlActivity`: `IconButton("−")` + Slider + `IconButton("+")` i Row; knapper kalder `stepValue` og opdaterer slider-state. Send til HA ved `onValueChangeFinished`-ækvivalent (samme sted som slider-commit i dag).
- [ ] Step B4: Byg + emulator + S23: klima 16–30 → trin 0.5; cover 0–100 → trin 1. Decimal-præcision (v0.2.34) uændret for direkte slider-træk.

**Variant A (config-valg skyder/felt, kun multi):**
- [ ] Step A1: Migration v6→v7: `rangeInputMode TEXT` (+ `secondary{1,2,3}RangeInputMode`), null = "SLIDER".
- [ ] Step A2: Radio/segmented i Handling-sektionen når action == RANGE: "Skyder" / "Indtast værdi".
- [ ] Step A3: Ny genbrugelig `NumberInputActivity` (mønster: `TextControlActivity`, `keyboardType = Number`, validér mod min/max, kald samme HA-service som RangeControlActivity for domænet).
- [ ] Step A4: `clickModifier`: RANGE + mode=FIELD → `NumberInputActivity` i stedet for `RangeControlActivity`.

- [ ] **Sidste step:** Commit `feat: RANGE-input — <valgt variant> (punkt 4)`.

---

### Task 14: Version bump, docs, samlet QA

**Files:**
- Modify: `app/build.gradle.kts`, `CLAUDE.md` (status-sektion), `docs/widget-settings-spec.md` (§10: nye options)

- [ ] **Step 1:** Bump til v0.3.0 (hvis ikke sket løbende), byg.
- [ ] **Step 2:** Emulator-QA (pixel_test) hele pakken: de 8 punkter efterprøves jf. spec'ens Test-sektion. Migration verificeret med eksisterende widgets.
- [ ] **Step 3:** S23-QA (`adb install -r`, meld versionsnavn + build-nummer til bruger): samme flow på Nova, især picker-ikoner (One UI-picker), tema-skift, confirm-dialog over hjemmeskærm, refresh-overlay-scroll.
- [ ] **Step 4:** Opdatér `CLAUDE.md` (ny v0.3.0-statusblok) + `docs/widget-settings-spec.md` §10 med de nye config-options.
- [ ] **Step 5:** Commit `docs: v0.3.0 status + spec §10 (widget UX pack)`.

---

## Self-review (kørt)

- **Spec-dækning:** A1→T12, A2→T6, A3→T4+T5, B1→T3+T4+T7+T8, B2→T13, C1→T10, C2→T2+T3+T9, C3→T11. Migration→T3(+T13A). Test→T1+T2+T9+T13. Docs→T14. Ingen huller.
- **Placeholder-scan:** Task 8 `executeAction` kræver bevidst 1:1-kopi fra `EntityActions.kt` (læses ved implementering — mapping må ikke opfindes i planen uden filen); markeret VIGTIGT, acceptabelt. Task 13 er variant-gated by design.
- **Typekonsistens:** `formatNumericState/formatDateTimeState/isDateTimeLike/formatDisplayValue/autoDateTime` konsistente mellem T2 og T9; `WidgetPalette`-roller mellem T10 og T11 (T11 har midlertidig fallback hvis rækkefølge byttes).
