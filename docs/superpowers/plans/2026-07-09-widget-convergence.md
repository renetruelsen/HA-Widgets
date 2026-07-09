# Widget-konvergering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Slet de 9 single-entity-widgets fuldstændigt, så appen kun har `ShortcutWidget` (dashboard-genvej) og `MultiEntityWidget`.

**Architecture:** Afkobl først data-laget (entity-sync/-opdatering) fra single-widgets så build forbliver grønt, slet derefter single-koden + manifest, ryd Room-tabellen, slet så single-ressourcer + forældreløse strenge, og trim til sidst død delt kode. Hvert trin efterlader et byggbart, testbart repo.

**Tech Stack:** Kotlin, Jetpack Glance, Room, Jetpack Compose. Build: JDK17.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-09-widget-convergence-design.md` — følg den præcist.
- Byg med `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug` (JDK17 kræves, jf. `CLAUDE.md`).
- Kør tests med `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew testDebugUnitTest` — 40 tests skal fortsat passere (ingen rammer singles).
- Installér ALTID som `adb install -r` (aldrig uninstall).
- **BEHOLD:** hele `widget/multientity/`, `widget/ShortcutWidget*.kt`, al `widget/common/` UNDTAGEN `BaseEntityPickerActivity.kt`, ALLE `ic_*`-domæne-ikoner (multi bruger dem via `MultiDomainSupport`), og delte strenge (`state_*`, `climate_*` m.fl. brugt af multi).
- **Bump `versionCode`/`versionName`** i `app/build.gradle.kts` FØR sidste build: `67`/`"0.2.67"` → `68`/`"0.2.68"` (projekt-konvention).
- Ingen eksisterende brugere → orphaning af placerede single-widgets er acceptabelt; ingen bagudkompatibilitets-hensyn.

---

### Task 1: Afkobl data-laget fra single-widgets (multi-only)

Gør entity-sync og widget-fan-out multi-only, mens single-klasserne stadig eksisterer. Build forbliver grønt; placerede singles holder bare op med at opdatere (de slettes i Task 2).

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/data/WidgetUpdater.kt`
- Modify: `app/src/main/java/dk/akait/hawidgets/data/EntityRepository.kt:46-67`
- Modify: `app/src/main/java/dk/akait/hawidgets/MainActivity.kt` (`updateAllWidgets`)

**Interfaces:**
- Produces: `WidgetUpdater.updateForEntity(context, entityId)` og `EntityRepository.refreshAll(context)` uændrede signaturer, men multi-only internt. `updateAllWidgets(context)` uændret signatur.
- Consumes: `AppDatabase.multiWidgetDao()` (uændret), `MultiEntityWidget` (uændret).

- [ ] **Step 1: Gør `WidgetUpdater` multi-only**

Erstat HELE `app/src/main/java/dk/akait/hawidgets/data/WidgetUpdater.kt` med:

```kotlin
package dk.akait.hawidgets.data

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.widget.multientity.MultiEntityWidget

/**
 * Fan-out til hjemskærms-widgets. Kun `MultiEntityWidget` er entitets-drevet
 * (genvejen viser ingen live entitet).
 */
object WidgetUpdater {

    /** Opdatér alle multi-widgets der viser/handler på [entityId]. */
    suspend fun updateForEntity(context: Context, entityId: String) {
        val db = AppDatabase.get(context)
        if (db.multiWidgetDao().slotsForEntity(entityId).isEmpty()) return

        val manager = GlanceAppWidgetManager(context)
        val multiWidget = MultiEntityWidget()
        manager.getGlanceIds(multiWidget::class.java)
            .forEach { glanceId -> runCatching { multiWidget.update(context, glanceId) } }
    }
}
```

- [ ] **Step 2: Gør `EntityRepository.refreshAll` multi-only**

I `app/src/main/java/dk/akait/hawidgets/data/EntityRepository.kt`, erstat `refreshAll` (linje 46-67) med (fjern `db.entityWidgetDao().allEntityIds() +`):

```kotlin
    /** Pull alle konfigurerede entiteter (SyncWorker). Returnerer false hvis nogen fejlede. */
    suspend fun refreshAll(context: Context): Boolean {
        val api = client(context) ?: return true
        val db = AppDatabase.get(context)
        val multiDao = db.multiWidgetDao()
        val ids = (
            multiDao.allDisplayEntityIds() +
                multiDao.allActionEntityIds() +
                multiDao.allSecondaryEntityIds()
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

Ret desuden den forældede kommentar øverst i filen (linje ~13): erstat
`[dk.akait.hawidgets.widget.light.ToggleLightAction]` med `MultiEntityWidget`s tryk-handler
(den refererede klasse slettes i Task 2). Konkret — erstat linjen:

```
 * - **Tryk-handleren** ([dk.akait.hawidgets.widget.light.ToggleLightAction]) kører i en
```

med:

```
 * - **Tryk-handleren** (multi-widgettens `clickModifier`) kører i en
```

- [ ] **Step 3: Gør `updateAllWidgets` (MainActivity) 2-only**

I `app/src/main/java/dk/akait/hawidgets/MainActivity.kt`, find `private suspend fun updateAllWidgets`
og erstat de 11 `.updateAll(app)`-kald med kun de to beholdte:

```kotlin
private suspend fun updateAllWidgets(context: android.content.Context) {
    val app = context.applicationContext
    MultiEntityWidget().updateAll(app)
    ShortcutWidget().updateAll(app)
}
```

Fjern derefter de nu-ubrugte single-widget-imports i toppen af `MainActivity.kt`
(`LightWidget`, `SwitchWidget`, `SceneWidget`, `ScriptWidget`, `AutomationWidget`, `SensorWidget`,
`BinarySensorWidget`, `CoverWidget`, `ClimateWidget`). Behold `MultiEntityWidget`- og
`ShortcutWidget`-imports.

- [ ] **Step 4: Byg + test**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 40 tests passed. (Single-klasserne eksisterer stadig men kaldes ikke længere fra data-laget.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/data/WidgetUpdater.kt app/src/main/java/dk/akait/hawidgets/data/EntityRepository.kt app/src/main/java/dk/akait/hawidgets/MainActivity.kt
git commit -m "refactor: data-lag opdaterer/synker kun multi-widget (afkobl singles)"
```

---

### Task 2: Slet de 9 single-widget-pakker + BaseEntityPickerActivity + manifest

**Files:**
- Delete: `app/src/main/java/dk/akait/hawidgets/widget/{light,switchwidget,scene,script,automation,sensor,binarysensor,cover,climate}/` (hele mapper — hver har `*Widget.kt` + `*WidgetConfigActivity.kt`)
- Delete: `app/src/main/java/dk/akait/hawidgets/widget/common/BaseEntityPickerActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: intet (Task 1 fjernede de sidste data-lag-referencer).
- Produces: manifest med kun `ShortcutWidget*` + `MultiEntityWidget*` widget-komponenter.

- [ ] **Step 1: Slet de 9 pakker + BaseEntityPickerActivity**

```bash
cd app/src/main/java/dk/akait/hawidgets/widget
rm -r light switchwidget scene script automation sensor binarysensor cover climate
rm common/BaseEntityPickerActivity.kt
```

- [ ] **Step 2: Fjern manifest-entries**

I `app/src/main/AndroidManifest.xml`, slet de 9 `<receiver>`-blokke for
`*.widget.{light.Light,switchwidget.Switch,scene.Scene,script.Script,automation.Automation,sensor.Sensor,binarysensor.BinarySensor,cover.Cover,climate.Climate}WidgetReceiver`
OG de 9 `<activity>`-blokke for de tilhørende `*WidgetConfigActivity`.
**Behold** `ShortcutWidgetReceiver`, `ShortcutWidgetConfigActivity`,
`MultiEntityWidgetReceiver`, `MultiEntityWidgetConfigActivity`, samt alle kontrol-aktiviteter
(`RangeControlActivity`, `NumberInputActivity`, `TextControlActivity`, `DateTimeControlActivity`,
`ConfirmActionActivity`) og `MainActivity`/`WebViewActivity`.

- [ ] **Step 3: Verificér ingen dangling referencer**

Run: `git grep -nE "widget\.(light|switchwidget|scene|script|automation|sensor|binarysensor|cover|climate)\.|BaseEntityPickerActivity" -- 'app/src/main'`
Expected: ingen output (heller ikke i `AndroidManifest.xml` — Step 2 fjernede dem; `res/xml/*_widget_info.xml` indeholder ikke klassenavne). Hvis noget dukker op, ret referencen før build.

- [ ] **Step 4: Byg**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (Ressource-referencer fra de slettede `*_widget_info.xml` er stadig gyldige — XML'erne findes endnu; de fjernes i Task 4. Manifest peger ikke længere på dem.)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: slet 9 single-entity-widgets + BaseEntityPickerActivity + manifest-entries"
```

---

### Task 3: Drop Room `entity_widget`-tabel + fjern EntityWidgetEntity/Dao

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/data/db/Migrations.kt` (tilføj `MIGRATION_10_11`)
- Modify: `app/src/main/java/dk/akait/hawidgets/data/db/AppDatabase.kt`
- Delete: `app/src/main/java/dk/akait/hawidgets/data/db/EntityWidgetEntity.kt`
- Delete: `app/src/main/java/dk/akait/hawidgets/data/db/EntityWidgetDao.kt`

**Interfaces:**
- Consumes: intet (alle `entityWidgetDao`-kald fjernet i Task 1+2).
- Produces: `MIGRATION_10_11` (version 10 → 11), `AppDatabase` uden `EntityWidgetEntity`/`entityWidgetDao()`.

- [ ] **Step 1: Tilføj `MIGRATION_10_11`**

I `app/src/main/java/dk/akait/hawidgets/data/db/Migrations.kt`, tilføj til sidst i filen (efter `MIGRATION_9_10`):

```kotlin
/** v10 → v11: fjerner den nu-ubrugte entity_widget-tabel (single-widgets slettet). Rører IKKE
 * entity_state/multi_widget/multi_widget_slot. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS entity_widget")
    }
}
```

- [ ] **Step 2: Opdatér `AppDatabase`**

I `app/src/main/java/dk/akait/hawidgets/data/db/AppDatabase.kt`:
- Fjern `EntityWidgetEntity::class,` fra `entities`-listen.
- Bump `version = 10` → `version = 11`.
- Fjern linjen `abstract fun entityWidgetDao(): EntityWidgetDao`.
- Tilføj `MIGRATION_10_11` til `.addMigrations(...)`-kaldet (til sidst).

Resultatet af `@Database` + `addMigrations`:

```kotlin
@Database(
    entities = [
        EntityStateEntity::class,
        MultiWidgetEntity::class,
        MultiWidgetSlotEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
```

```kotlin
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
```

- [ ] **Step 3: Slet DAO + entity**

```bash
rm app/src/main/java/dk/akait/hawidgets/data/db/EntityWidgetEntity.kt
rm app/src/main/java/dk/akait/hawidgets/data/db/EntityWidgetDao.kt
```

- [ ] **Step 4: Byg + test**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 40 tests passed. (Room KSP genererer skema uden entity_widget uden fejl.)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: drop Room entity_widget-tabel + EntityWidgetEntity/Dao (MIGRATION_10_11)"
```

---

### Task 4: Slet single-ressourcer (widget-info XML + preview-drawables)

**Files:**
- Delete: `app/src/main/res/xml/{light,switch,scene,script,automation,sensor,binary_sensor,cover,climate}_widget_info.xml`
- Delete: `app/src/main/res/drawable/preview_{light,switch,scene,script,automation,sensor,binary_sensor,cover,climate}.xml`

- [ ] **Step 1: Slet de 9 widget-info-XML'er**

```bash
cd app/src/main/res/xml
rm light_widget_info.xml switch_widget_info.xml scene_widget_info.xml script_widget_info.xml automation_widget_info.xml sensor_widget_info.xml binary_sensor_widget_info.xml cover_widget_info.xml climate_widget_info.xml
```

- [ ] **Step 2: Slet de 9 preview-drawables (BEHOLD `preview_multi_entity`/`preview_shortcut`)**

```bash
cd app/src/main/res/drawable
rm preview_light.xml preview_switch.xml preview_scene.xml preview_script.xml preview_automation.xml preview_sensor.xml preview_binary_sensor.xml preview_cover.xml preview_climate.xml
```

- [ ] **Step 3: Verificér ingen dangling ressource-referencer**

Run: `git grep -nE "(light|switch|scene|script|automation|sensor|binary_sensor|cover|climate)_widget_info|preview_(light|switch|scene|script|automation|sensor|binary_sensor|cover|climate)" -- 'app/src/main'`
Expected: ingen output.

- [ ] **Step 4: Byg**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (ressource-link fejler hvis en slettet drawable/xml stadig refereres).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: slet single-widget widget-info XML + preview-drawables"
```

---

### Task 5: Fjern forældreløse strenge (grep-verificeret)

Fjern KUN strenge uden tilbageværende referencer. Delte strenge (`state_*`, `climate_*`, `binary_*`
hvis brugt af multi) beholdes automatisk fordi grep'et finder referencer.

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-da/strings.xml`
- Modify: `app/src/main/res/values-sv/strings.xml`

- [ ] **Step 1: Beregn de forældreløse strenge**

Kør denne kommando — den lister kandidat-strenge og markerer hvilke der IKKE længere refereres
(hverken som `R.string.NAME` i `.kt` eller `@string/NAME` i `res`, ekskl. selve `strings.xml`-definitionen):

```bash
cd app/src/main
for name in light_widget_description switch_widget_description scene_widget_description script_widget_description automation_widget_description sensor_widget_description binary_sensor_widget_description cover_widget_description climate_widget_description picker_title_light picker_title_switch picker_title_scene picker_title_script picker_title_automation picker_title_sensor picker_title_binary_sensor picker_title_cover picker_title_climate binary_alarm binary_charging binary_clear binary_cold binary_connected binary_dark binary_detected binary_dry binary_hot binary_light binary_low_battery binary_motion binary_not_charging binary_not_connected binary_ok binary_problem binary_stopped binary_wet; do
  refs=$(git grep -lE "R\.string\.$name\b|@string/$name\b" -- 'app/src' | grep -v 'res/values.*/strings.xml' | wc -l)
  if [ "$refs" -eq 0 ]; then echo "ORPHAN: $name"; else echo "KEEP:   $name ($refs refs)"; fi
done
```

Expected: `ORPHAN:` for de 9 `*_widget_description` + de 9 `picker_title_*` (+ evt. `binary_*` hvis
multi ikke bruger dem). `KEEP:` for enhver `binary_*`/anden streng multi stadig refererer.

- [ ] **Step 2: Fjern hver ORPHAN-streng fra alle 3 sprogfiler**

For hvert navn markeret `ORPHAN` i Step 1, slet dets `<string name="NAME">...</string>`-linje i
`values/strings.xml`, `values-da/strings.xml` OG `values-sv/strings.xml`. Rør IKKE `KEEP`-strenge.

- [ ] **Step 3: Verificér + byg**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 40 tests passed. (Manglende streng-reference giver link-fejl — fanger hvis en streng blev fjernet forkert.)

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: fjern forældreløse single-widget-strenge (da/en/sv)"
```

---

### Task 6: Trim død delt kode

Fjern deklarationer i delt kode der blev single-only efter sletningen. Compiler-warnings guider præcist.

**Files:**
- Modify (efter behov): `app/src/main/java/dk/akait/hawidgets/widget/common/EntityActions.kt`
- Modify (efter behov): `app/src/main/java/dk/akait/hawidgets/widget/common/GlanceWidgetCommon.kt`

- [ ] **Step 1: Find ubrugte deklarationer**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug 2>&1 | grep -iE "never used|is never used|unused" | grep -iE "EntityActions|GlanceWidgetCommon"`
Kør desuden for hver offentlig deklaration i `EntityActions.kt` (fx `ToggleEntityAction`,
`TriggerEntityAction`, `RefreshEntityAction`): `git grep -lE "\bDECLNAME\b" -- 'app/src/main' | grep -v EntityActions.kt`
— tom output = ubrugt.

- [ ] **Step 2: Fjern KUN beviseligt ubrugte deklarationer**

Slet de deklarationer Step 1 viste som ubrugte (typisk single-only Glance `ActionCallback`-klasser).
Behold alt der stadig refereres af `MultiEntityClickModifier`/`MultiEntityRendering`/
`ConfirmActionActivity`/`RangeService`/`ShortcutWidget`. Ved tvivl: behold (ingen spekulativ sletning).

- [ ] **Step 3: Byg + test**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 40 tests passed.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: trim død delt widget-kode efter single-sletning"
```

---

### Task 7: Versionsbump, fuld build, emulator- + device-QA

**Files:**
- Modify: `app/build.gradle.kts:16-17`

- [ ] **Step 1: Bump version**

I `app/build.gradle.kts`: `versionCode = 67` → `68`, `versionName = "0.2.67"` → `"0.2.68"`.

- [ ] **Step 2: Fuld build + test**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew clean assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 40 tests passed.

- [ ] **Step 3: Installér på emulator**

Run: `<SDK>/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: `Success`.

- [ ] **Step 4: Emulator-QA — driv flowet**

Verificér (via `adb shell input`, screenshots, DB-inspektion):
1. **Migration:** `adb -s emulator-5554 shell "run-as dk.akait.hawidgets sqlite3 databases/ha_widgets.db 'PRAGMA user_version; .tables'"` → `user_version=11`, INGEN `entity_widget`-tabel, men `entity_state`/`multi_widget`/`multi_widget_slot` til stede. Eksisterende multi-widget re-renderer uden crash.
2. **Widget-picker:** long-press hjemskærm → Widgets → søg "HA" → KUN 2 entries (`HA Dashboard Shortcut`, `HA Multi-entitet`), ikke 11.
3. **Frisk multi-widget:** placér, konfigurér en slot, gem → renderer korrekt; tap-handling virker; `adb logcat | grep SyncWorker` → SUCCESS.
4. Ingen FATAL i logcat gennem flowet.

Virker noget ikke → tilbage til relevant task. Bliv i loopet til grønt (jf. `CLAUDE.md`).

- [ ] **Step 5: Commit versionsbump**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version til 0.2.68 (widget-konvergering)"
```

**Bemærk:** Device-QA på fysisk S23 (Nova) udføres af brugeren efter planen: samme tjekliste + bekræft at ingen af de gamle single-widgets kan tilføjes.
