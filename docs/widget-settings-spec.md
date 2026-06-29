# Widgetindstillinger — UX-spec

Kanonisk spec for entity widget config-flow og widget display.
Gælder alle 9 entity widget-typer: light, switch, scene, script, automation, sensor, binary_sensor, weather, climate.

**Sidst opdateret:** v0.2.3  
**Kildefil:** `BaseEntityPickerActivity.kt`, `GlanceWidgetCommon.kt`

---

## 1. Config-flow (to skærme)

### Skærm 1: Entitetsvælger

Åbnes automatisk ved widget-placering og ved rekonfiguration.

**Komponent:** `EntityPickerScreen` i `BaseEntityPickerActivity.kt`

| Element | Spec |
|---|---|
| TopAppBar titel | `pickerTitle` (domain-specifik, se tabel nedenfor) |
| Søgefelt | `OutlinedTextField`, placeholder `"Søg…"`, `fillMaxWidth`, `singleLine=true` |
| Søgelogik | Filtrerer på `friendlyName` ELLER `entityId`, case-insensitiv |
| Entitetsliste | `LazyColumn`, sorteret alfabetisk efter `friendlyName` |
| Ikon | `domainIconResId`, 24dp, tint=`primary` hvis `state=="on"`, ellers `onSurfaceVariant` |
| Primær tekst | `friendlyName`, `bodyLarge` |
| Sekundær tekst | `entityId`, `bodySmall`, `onSurfaceVariant` |
| State chip | `SuggestionChip` (ikke klikbar), `formatEntityState(state)` |
| Separator | `HorizontalDivider` mellem rækker |
| Rækketap | Navigerer til Skærm 2 |
| Loading | `CircularProgressIndicator` centreret, vises mens API-kald kører |
| Fejl: HA ikke konfigureret | `"HA ikke forbundet. Åbn HA Widgets og forbind først."`, `color=error`, centreret |

**Domain-titler og ikoner:**

| Domain | `pickerTitle` | `domainIconResId` |
|---|---|---|
| light | "Vælg lyskilde" | `ic_lightbulb` |
| switch | "Vælg kontakt" | `ic_switch` |
| scene | "Vælg scene" | `ic_scene` |
| script | "Vælg script" | `ic_script` |
| automation | "Vælg automatisering" | `ic_automation` |
| sensor | "Vælg sensor" | `ic_sensor` |
| binary_sensor | "Vælg binær sensor" | `ic_binary_sensor` |
| weather | "Vælg vejrstation" | `ic_weather` |
| climate | "Vælg klimastyring" | `ic_climate` |

**State-formattering (`formatEntityState`):**

| Domain | "on" | "off" | Andre |
|---|---|---|---|
| light | "Tændt" | "Slukket" | råværdi |
| switch | "Tændt" | "Slukket" | råværdi |
| scene | — | — | altid "Aktiver" |
| script | "Kører" | "Klar" | råværdi |
| automation | "Aktiv" | "Deaktiveret" | råværdi |
| sensor | råværdi | råværdi | råværdi |
| binary_sensor | "Aktiv" | "Inaktiv" | råværdi |
| weather | råværdi | råværdi | råværdi |
| climate | se note | se note | se note |

Climate HVAC-mapping: `"heat"→"Opvarmning"`, `"cool"→"Køling"`, `"auto"/"heat_cool"→"Auto"`, `"dry"→"Affugtning"`, `"fan_only"→"Ventilator"`, `"off"→"Slukket"`.

---

### Skærm 2: Widget-tilpasning

Vises efter brugeren vælger entitet på Skærm 1.

**Komponent:** Inline i `EntityPickerScreen` (når `selectedEntity != null`)

| Element | Spec |
|---|---|
| TopAppBar titel | `"Tilpas widget"` |
| Entitetsnavn | `selectedEntity.friendlyName`, `titleMedium` |
| Entitets-ID | `selectedEntity.entityId`, `bodySmall`, `onSurfaceVariant` |
| Spacer | 8dp |
| Label-felt | `OutlinedTextField`, `fillMaxWidth`, `singleLine=true` |
| → label | `"Kort label (valgfrit)"` |
| → placeholder | `"f.eks. Bad 1"` |
| → supportingText | `"Vises på widget i stedet for enhedsnavn. Maks 12 tegn."` |
| → maxLength | 12 tegn (hard enforced: `if (it.length <= 12)`) |
| Spacer | 8dp |
| Gem-knap | `Button`, `fillMaxWidth`, tekst `"Gem widget"` |
| Tilbage-knap | `TextButton`, `fillMaxWidth`, tekst `"Tilbage"` → nulstiller til Skærm 1 |

**Gem-handling:**
1. Upsert `EntityWidgetEntity(appWidgetId, entityId, domain, label.trim())` i Room
2. `setResult(RESULT_OK)` med `EXTRA_APPWIDGET_ID`
3. `SyncWorker.runNow()` + `SyncWorker.schedule()`
4. `finish()`

**Rekonfiguration:** Når `appWidgetId` allerede har en eksisterende `EntityWidgetEntity` (rekonfiguration via long-press → Widget-indstillinger), springer config-flow direkte til Skærm 2 med:
- `selectedEntity` = eksisterende entitet (fundet i API-listen via `entityId`)
- `labelInput` = eksisterende `label`
Bruger kan ændre entitet via "Tilbage"-knappen eller gem uændret med "Gem widget".

---

## 2. Widget-display

### Label-opløsning

```
label = config.label hvis ikke tom
     ?: friendlyName fra attributesJson["friendly_name"]
     ?: config.entityId
```

Bruges i begge compact og wide layouts.

### Baggrundsfarver og indholdssfarver

| Tilstand | Baggrund | Indholdsfarve |
|---|---|---|
| Tændt / aktiv | `primaryContainer` | `onPrimaryContainer` |
| Utilgængelig | `errorContainer` | `onErrorContainer` |
| Slukket / inaktiv / standby | `surfaceVariant` | `onSurfaceVariant` |

Brug `GlanceTheme.colors.*` (ikke hardkodede farver).

### Status-tekst

| Tilstand | Tekst |
|---|---|
| `state == null` (ingen data) | `"Henter status…"` |
| `state == "unavailable"` | `"Utilgængelig"` |
| Forældet data (>15 min) | `"<statusBase> ~"` (tilde suffix) |
| Normal | domain-specifik (se state-formattering ovenfor) |

Grænse for forældethed: `STALE_THRESHOLD_MS = 15 * 60 * 1000L` (15 minutter).

### Compact layout (56×56dp)

**Komponent:** `WidgetCompactLayout` i `GlanceWidgetCommon.kt`

```
Column (fillMaxSize, padding=4dp, centerH+V)
  ├── Image: domainIcon, 20dp, tint=contentColor
  ├── Text: label, 10sp, maxLines=1, color=contentColor
  └── Text: statusText, 11sp, maxLines=1, color=contentColor
```

Corner radius: 16dp. Klikbar (domain-specifik action) medmindre `state==null` eller `unavailable`.

### Wide layout (≥110dp bredde)

**Komponent:** `WidgetWideLayout` i `GlanceWidgetCommon.kt`

```
Row (fillMaxSize, padding=12dp H / 8dp V, centerV)
  ├── Image: domainIcon, 28dp, tint=contentColor
  ├── Spacer: 10dp
  └── Column (fillMaxWidth)
        ├── Text: label, 13sp, FontWeight.Medium, maxLines=1, color=contentColor
        └── Text: statusText, 11sp, maxLines=1, color=contentColor
```

### Size mode

```kotlin
override val sizeMode = SizeMode.Responsive(
    setOf(DpSize(56.dp, 56.dp), DpSize(110.dp, 56.dp))
)
```

Widgets med `minWidth=110dp` (weather, climate) starter i wide layout.

### Ukonfigureret tilstand

```
Box (fillMaxSize, surfaceVariant, cornerRadius=16dp, klikbar → åbn config)
  └── Column (centerH)
        ├── Image: domainIcon, 24dp, tint=onSurfaceVariant
        └── Text: "Opsæt", 10sp, onSurfaceVariant
```

---

## 3. Widget-picker metadata

Defineres i `app/src/main/res/xml/<domain>_widget_info.xml`.

| Felt | Spec |
|---|---|
| `android:previewImage` | Domain-specifikt drawable (samme som `domainIconResId`) |
| `android:description` | `@string/<domain>_widget_description` — maks ~30 tegn, engelsksprog, domain-fokuseret |
| `android:widgetFeatures` | `"reconfigurable"` (alle entity widgets) |
| `android:resizeMode` | `"horizontal\|vertical"` |
| `android:widgetCategory` | `"home_screen"` |
| `android:updatePeriodMillis` | `"0"` (SyncWorker styrer opdatering) |

**Nuværende beskrivelser (strings.xml):**

| Domain | Streng |
|---|---|
| light | "Control lights" |
| switch | "Control switches" |
| scene | "Activate scenes" |
| script | "Run scripts" |
| automation | "Trigger automations" |
| sensor | "Show sensor values" |
| binary_sensor | "Show binary sensors" |
| weather | "Weather forecast (2×1)" |
| climate | "Climate control (2×1)" |

---

## 4. Template: ny widget-type

Checkliste ved tilføjelse af ny widget-type:

- [ ] `<Domain>WidgetConfigActivity.kt` — extends `BaseEntityPickerActivity`, override `domain`, `pickerTitle`, `domainIconResId`, `formatEntityState`
- [ ] `<Domain>Widget.kt` — `GlanceAppWidget` med reaktiv Room Flow, compact/wide via `SizeMode.Responsive`
- [ ] `<Domain>WidgetReceiver` — `GlanceAppWidgetReceiver`, `onUpdate` → `SyncWorker.runNow()`
- [ ] `<domain>_widget_info.xml` — udfyld alle felter fra sektion 3
- [ ] `@string/<domain>_widget_description` i `strings.xml`
- [ ] `@drawable/ic_<domain>` drawable
- [ ] Registrér receiver i `AndroidManifest.xml`
- [ ] Tilføj domain til `SyncWorker` entitets-fetch
- [ ] UX-review: screenshot compact + wide + ukonfigureret tilstand
- [ ] QA: test på emulator + rigtig enhed
