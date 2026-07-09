# Widget-konvergering: slet single-entity-widgets, behold kun genvej + multi

**Dato:** 2026-07-09
**Status:** Godkendt (brainstorm), afventer implementeringsplan
**Branch:** `feature/widget-convergence`

## Mål

Fjern de 9 single-entity-widgets fuldstændigt. Efter ændringen har appen kun **to** widgets:
`ShortcutWidget` (dashboard-genvej) og `MultiEntityWidget` (multi-entitet). Multi-widgetten —
evt. med én slot — er fremover den eneste måde at vise/styre en entitet på.

## Baggrund og beslutninger

- **Ingen eksisterende brugere:** appen er ikke udgivet, så vi er frie til at slette widget-providers
  uden at bekymre os om at orphane placerede widgets (den sædvanlige bagudkompatibilitets-bekymring
  gælder ikke). Bekræftet med bruger 2026-07-09.
- **Hvorfor:** multi-widgetten er langt mere gennemarbejdet end de 9 singles og dækker allerede *alle*
  deres kapabiliteter (toggle/trigger/range/sensor-værdi/domæne-ikoner/enheder). De 9 singles mangler
  til gengæld multi's visning/handling-opdeling, bekræft-ved-tryk, sekundær-chips og værdi-formatering,
  og koster 10× vedligehold (hver feature — i18n, tema, fejl-feedback — har skullet røre alle widgets).
- **Kompakt 1×1-flise droppes bevidst (brugerbeslutning):** den eneste kapabilitet der forsvinder er
  det lille kvadratiske single-tile-footprint. Vi tilføjer IKKE et kompakt layout til multi-widgetten.
  Vil man vise én entitet, laver man en 1-slot multi-widget (fuld-bredde række, resizable). Accepteret
  som simpleste løsning.
- **Genintroduktion:** hvis en single-widget nogensinde ønskes igen, kan den hentes fra git-historikken.

## Scope

### Slettes (single-only — verificeret via grep at intet i multi/genvej/delt kode bruger dem)

**Kotlin-pakker** (hver med `*Widget.kt` + `*WidgetConfigActivity.kt`):
`widget/light/`, `widget/switchwidget/`, `widget/scene/`, `widget/script/`, `widget/automation/`,
`widget/sensor/`, `widget/binarysensor/`, `widget/cover/`, `widget/climate/`.

**Delt-men-single-only:** `widget/common/BaseEntityPickerActivity.kt` (kun de 9 config-activities
subklasser den).

**Manifest** (`AndroidManifest.xml`): de 9 `*WidgetReceiver`-`<receiver>` + de 9
`*WidgetConfigActivity`-`<activity>` (behold `ShortcutWidget*` og `MultiEntityWidget*`).

**Ressourcer:**
- 9 `res/xml/*_widget_info.xml` (light/switch/scene/script/automation/sensor/binary_sensor/cover/climate).
- **Single-only drawables — KUN de 9 `preview_*`-plader** (verificeret: kun refereret af de 9
  `*_widget_info.xml`s `previewImage`): `preview_light`, `preview_switch`, `preview_scene`,
  `preview_script`, `preview_automation`, `preview_sensor`, `preview_binary_sensor`, `preview_cover`,
  `preview_climate`. **BEHOLD** `preview_multi_entity` + `preview_shortcut`.
- **BEHOLD alle `ic_*`-domæne-ikoner** (`ic_lightbulb`/`ic_switch`/`ic_scene`/`ic_script`/
  `ic_automation`/`ic_sensor`/`ic_binary_sensor`/`ic_cover`/`ic_climate`/`ic_lock`/`ic_device_tracker`/
  `ic_number`): verificeret at `MultiDomainSupport.domainIconResId` bruger dem ALLE til multi-widgettens
  række/chip-ikoner. At slette dem ville bryde multi-renderingen. (`ic_dashboard`/`ic_multi_entity`/
  `ic_refresh` beholdes ligeledes — brugt af genvej/multi.)
- Single-only strenge i alle 3 `strings.xml` (da/en/sv): picker-titler/beskrivelser som
  `pick_light`/"Vælg lyskilde" og widget-picker-navne/beskrivelser for de 9.
  **BEHOLD delte strenge** brugt af multi via `MultiDomainSupport`: `state_*`, `climate_*`,
  device-class-strenge osv. Fjern kun strenge der udelukkende refereres af slettet kode
  (verificér hver med grep før sletning).

### Beholdes (delt med multi og/eller genvej)

`widget/common/`: `GlanceWidgetCommon.kt` (genvej bruger `WidgetCompactLayout`/`WidgetWideLayout`/
`UnconfiguredWidgetContent`; multi bruger `unitFromJson`/`friendlyNameFromJson`/`hvacActionFromJson`),
`EntityActions.kt` (multi's `MultiEntityClickModifier`/`MultiEntityRendering` + `ConfirmActionActivity`
+ `RangeService` bruger det), `MultiDomainSupport.kt`, `RangeControlActivity.kt`, `RangeService.kt`,
`RangeStepping.kt`, `NumberInputActivity.kt`, `TextControlActivity.kt`, `DateTimeControlActivity.kt`,
`ConfirmActionActivity.kt`, `ValueFormatting.kt`, `ActionFeedback.kt`, `WidgetColors.kt`,
`WidgetColorPresets.kt`. Hele `widget/multientity/` og `widget/ShortcutWidget*.kt`.

### Delt kode — trim døde rester i samme branch

`EntityActions.kt` og `GlanceWidgetCommon.kt` beholdes som filer, men enkelte deklarationer kan blive
single-only efter sletning (fx Glance-`ActionCallback`-klasser kun singles registrerede via tap).
Behold filerne; lad `./gradlew assembleDebug`-warnings + code-review udpege præcist hvad der er
ubrugt, og fjern kun det der beviseligt er dødt. Ingen spekulativ oprydning.

## Data-lag

- **`EntityRepository.refreshAll`:** samler i dag entiteter fra `entityWidgetDao().allEntityIds()`
  (singles) **+** multi-slots. Fjern singles-delen → saml kun fra multi (`multiWidgetDao`).
- **`WidgetUpdater.updateForEntity`:** opdaterer i dag både per-domæne single-widgets **og** multi.
  Fjern single-delen → opdatér kun `MultiEntityWidget`. (Genvejen er ikke entitets-drevet.)
- **Room-migration `MIGRATION_10_11`** (version 10 → 11, tilføjes til `addMigrations(...)`):
  `DROP TABLE IF EXISTS entity_widget`. Slet `EntityWidgetEntity.kt` + `EntityWidgetDao.kt` og fjern
  dem fra `@Database`-entiteter/DAO-liste i `AppDatabase.kt`. **Behold** `entity_state`-cachen og
  `multi_widget`/`multi_widget_slot` uændret. Ikke-destruktiv for eksisterende multi-testdata.
- **`updateAllWidgets` (`MainActivity.kt`):** fra 11 `.updateAll()`-kald → 2 (`MultiEntityWidget`,
  `ShortcutWidget`).

## Widget-picker slutresultat

To entries: **HA Dashboard Shortcut** og **HA Multi-entitet**. Navne uændrede.

## Non-goals (bevidst udenfor scope)

- Intet kompakt/kvadratisk footprint til multi-widgetten (brugerbeslutning — række-layout er nok).
- Ingen discoverability-ændringer (gate/app-henvisning) — det er sin egen separate branch.
- Ingen omdøbning af `multientity`-pakken eller multi-widgetten.
- Ingen ændring af multi-widgettens default-footprint.

## Test / QA

1. **Build + unit-tests grønne.** Ingen eksisterende test rammer singles (de tester `ValueFormatting`,
   `RangeStepping`, `WidgetColors` — alle beholdt). 40 tests skal fortsat passere.
2. **Migration verificeret på emulator (`pixel_test`, ægte HA):** installér oven på eksisterende data
   (`adb install -r`) → `user_version=11`, `entity_widget`-tabel væk, `multi_widget`/`multi_widget_slot`/
   `entity_state` intakte, eksisterende multi-widget re-renderer uden crash/datatab.
3. **Widget-picker:** viser KUN de 2 entries (ikke 11).
4. **Frisk multi-widget placeret** → config-flow virker, entiteter synkes (SyncWorker SUCCESS i logcat),
   tap-handlinger virker.
5. **Ingen dangling referencer:** `assembleDebug` grøn uden usolvede referencer til slettede klasser.
6. **S23-QA (Nova):** samme tjekliste; bekræft ingen af de gamle single-widgets kan tilføjes, og at en
   evt. tidligere placeret single-widget (dev-testdata) håndteres gracefully (viser tom/fjernes af
   launcher — acceptabelt, da ingen rigtige brugere).

## Risici

- **Delte drawables/strenge:** største fælde er at slette et ikon eller en streng som multi stadig
  bruger via `MultiDomainSupport`. Afbødning: grep hver kandidat-ressource for referencer FØR sletning;
  `assembleDebug` fanger manglende ressource-referencer ved link-tid.
- **Room-migration:** en forkert `DROP TABLE` kunne ramme forkert tabel. Afbødning: kun
  `entity_widget` droppes; verificér skema på emulator efter migration.
