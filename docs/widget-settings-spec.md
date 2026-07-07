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
| → supportingText | `"Vises på widget i stedet for enhedsnavn. Maks 22 tegn."` |
| → maxLength | 22 tegn (hard enforced: `if (it.length <= 22)`) |
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
| Tændt / aktiv | `primary` (solid, mættet) | `onPrimary` |
| Utilgængelig | `errorContainer` | `onErrorContainer` |
| Slukket / inaktiv / standby | `surfaceVariant` | `onSurfaceVariant` |

Brug `GlanceTheme.colors.*` (ikke hardkodede farver).

**v0.2.8 UX-review:** `primaryContainer`/`onPrimaryContainer` (pastel tonal) erstattet af solid `primary`/`onPrimary`
for tændt/aktiv tilstand — pastel-mod-pastel gav for lav kontrast mellem tændt/slukket på home screen
(svært at aflæse status på afstand). Sensor-widget har ingen aktiv/inaktiv-tilstand og forbliver
`surfaceVariant` (neutral, ikke styrbar).

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
Column (fillMaxSize, padding=6dp, centerH+V)
  ├── Image: domainIcon, 26dp, tint=contentColor
  ├── Text: label, 11sp, maxLines=1, color=contentColor
  └── Text: statusText, 13sp, FontWeight.Medium, maxLines=1, color=contentColor
```

**v0.2.8 UX-review:** ikon/tekst-størrelser øget (20→26dp ikon, status 11→13sp + Medium-vægt) for at
udfylde den faktiske launcher-cellestørrelse bedre — compact widgets virkede "tomme"/svævende i en
stor bleg boks ved tidligere mindre størrelser. Status er nu det visuelt tungeste element (primær info
ved et kig).

### ShortcutWidget (dashboard-genvej) — afviger fra entity-widget-mønsteret

Ikke en data-widget — ren genvej, jf. arkitektur-status i `CLAUDE.md`. Layout (`ShortcutWidget.kt`):

```
Box (fillMaxSize, centerH+V)
  └── Box: ikon-tile, kvadratisk = min(cellWidth, cellHeight) (samme bredde som nabo-widgets),
        baggrund #03A9F4, cornerRadius 16dp, klikbar → åbn dashboard
        └── Column (centerH)
              ├── Image: ic_dashboard, 28dp
              ├── Spacer 4dp
              └── Text: config.title, 11sp, hvid, centreret, maxLines=1 — KUN hvis konfigureret
```

v0.2.10: label flyttet fra inde-i-ikongrafikken til en separat tekstlinje under ikonet.
v0.2.12: tile fylder igen hele cellen (kvadratisk, samme bredde som entity-widgets) i stedet for en
lille fast 64dp boks — ikon+label sidder begge inde i den fulde farvede tile, blot på separate linjer.

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

---

## 5. MultiEntityWidget config: slot-liste-redesign

**FORSLAG — AFVENTER GODKENDELSE**

**Status:** Kun analyse + spec-tekst. Ingen kode ændret. Kræver brugergodkendelse før implementering
(jf. `docs/ux-process.md`: UX-oplæg → impl → UX-review-loop → QA-loop).

**Kildefil (ved implementering):** `MultiEntityWidgetConfigActivity.kt`, funktionen `ListScreen` (nuværende linje ~224-295).

### Problem

Hver slot-række i `ListScreen` er i dag én `Row`: ikon (24dp) + `Column(weight=1f)` med to
tekstlinjer (label/entity-id, actionSummary) + 4 `TextButton`s i samme `Row` ("↑", "↓", "Rediger",
"Fjern"). De fire tekstknapper (danske labels "Rediger"/"Fjern" er brede) optager tilsammen langt
mere vandret plads end deres visuelle vægt tilsiger — verificeret på emulator (`pixel_test`,
appWidgetId 9, "Test"-widgetten) med rigtige HA-entitets-ID'er:

- `climate.spav2_spa_thermostat` ombrydes til 3 linjer.
- `sensor.zone9_rogdetektor_ovenpa_battery_level` ombrydes til 4 linjer, og action-summary
  (`"Udløs automatisering/script → script.sonos_stue_p4"`) ombrydes til yderligere 3 linjer under det —
  hvilket skubber ↑/↓/Rediger/Fjern-knapperne helt uden for rækkens synlige/forventede areal på
  skærmen. Det er altså ikke kun et læsbarheds-problem, men i praksis et **funktionsproblem**:
  handlingsknapperne for den slot kan blive utilgængelige.

Årsagen er ikke primært at navne er "for lange" — det er at kun ca. 40-45% af rækkens bredde er
tilbage til tekstkolonnen, fordi fire separate `TextButton`s (hver med Material3-standard min. 40dp
højde + vandret indre padding der vokser med tekstlængden) spiser resten.

### Vurderet: brugerens forslag (3 rækker pr. slot)

Brugerens forslag — visningsnavn / handlingsnavn / handlingsknapper som 3 separate rækker — **løser
læsbarheden**, men er ikke den anbefalede løsning:

- Det adresserer symptomet (for lidt plads) ved at hælde MERE plads i, i stedet for at fjerne
  årsagen (fire brede knapper). Det tredobler den vertikale plads pr. slot (fra ~56-64dp til
  ~150-180dp), hvilket ved 5 slots (det tilladte maksimum, `enabled = slots.size < 5`) gør listen
  betydeligt tungere at scrolle og scanne — for et simpelt CRUD-flow, hvor brugeren typisk kun vil
  overskue "hvilke 5 ting har jeg tilføjet" på ét blik.
- Det bryder med det etablerede kompakte ét-linjers-radio-mønster andre steder i samme activity
  (`EntityPickerSubScreen`: ikon + to tekstlinjer + chip, alt i én række) — inkonsistent visuel
  rytme i samme skærmflow.
- Det løser ikke roden: selv med 3 rækker vil et ekstremt langt navn (se `sensor.zone9…`-eksemplet)
  stadig kunne ombrydes over flere linjer på den brede visningsnavn-række, blot uden at kollidere med
  knapperne. Problemet er delvist afbødet, ikke elimineret.

### Anbefalet løsning: behold 1 række pr. slot, reclaim vandret plads i stedet

**Anbefaling: modificeret variant, IKKE brugerens 3-rækker-forslag**, fordi den løser både
læsbarheds- og funktions-problemet med mindre strukturel ændring, bevarer listens kompakthed, og er
mere konsistent med resten af config-UI'en.

**Kernefix:** erstat de 4 `TextButton`s (der isoleret spiser det meste af rækkens bredde) med et
kompakt sæt ikon-only `IconButton`s (48dp touch-target hver, ingen synlig tekst) samlet i en
overflow-menu for de sjældnere handlinger. Det frigiver den vandrette plads til tekstkolonnen uden at
tilføje en eneste ny linje til layoutet.

**Præcist layout (uændret 1-rows-struktur):**

```
Row (fillMaxWidth, padding vertical=8dp, verticalAlignment=CenterVertically)
  ├── Icon: domainIconResId(slot.displayDomain), 24dp
  ├── Spacer 12dp
  ├── Column (weight=1f, min bredde reclaimet fra knap-området)
  │     ├── Text: displayLabel, bodyLarge, maxLines=1, overflow=Ellipsis
  │     └── Text: actionSummary, bodySmall, onSurfaceVariant, maxLines=1, overflow=Ellipsis
  │           — vises KUN hvis slot.action != "NONE" ELLER actionEntityId != displayEntityId
  │           (samme betingelse som i dag — bevares uændret)
  ├── IconButton: ↑ (Icons.Default.KeyboardArrowUp), 40dp, enabled=index>0,
  │     contentDescription="Flyt op"
  ├── IconButton: ↓ (Icons.Default.KeyboardArrowDown), 40dp, enabled=index<slots.size-1,
  │     contentDescription="Flyt ned"
  └── IconButton: ⋮ (Icons.Default.MoreVert), 40dp, contentDescription="Flere valgmuligheder"
        → åbner DropdownMenu med to items: "Rediger" / "Fjern"
HorizontalDivider (uændret)
```

**Tekststile (uændret fra i dag, kun `maxLines`/`overflow` tilføjet):**

| Element | Stil | Ny egenskab |
|---|---|---|
| Visningsnavn | `bodyLarge` | `maxLines=1, overflow=TextOverflow.Ellipsis` |
| Action-summary | `bodySmall`, `onSurfaceVariant` | `maxLines=1, overflow=TextOverflow.Ellipsis` |

**Hvorfor ellipsis er acceptabelt her (og ikke et kompromis):** Radikalt fuld navnevisning er ikke
et hårdt krav — brugeren skal kunne *genkende* hvilken slot er hvilken ved et scan, ikke nødvendigvis
læse hele entity-navnet i listen. "Rediger" (uændret) viser allerede fuldt, ikke-afkortet
`friendlyName` + `entityId` på `SlotEditorScreen`. Ellipsis + "Rediger for detaljer" er samme mønster
som resten af appen (fx compact widget-layouts afkorter også label til 1 linje). Med vandret plads
reclaimet fra knapperne (~150dp+ mere til tekstkolonnen på en typisk telefonbredde) vil de fleste
realistiske HA friendly names (2-4 ord) formentlig slet ikke ombrydes længere — ellipsis bliver et
sikkerhedsnet for enkelte pato­logiske tilfælde, ikke normaltilstanden.

**Edge cases:**

| Case | Adfærd |
|---|---|
| Meget langt visningsnavn (fx `sensor.zone9_rogdetektor_ovenpa_battery_level` uden `friendlyName`) | Afkortes med ellipsis på én linje. Fuldt navn ses via "Rediger". |
| Meget kort navn (fx "Bad") | Ingen ombrydning, ingen visuel ændring fra i dag. |
| Visning = handling (samme entity) | Kun visningsnavnet vises; action-summary-linjen udelades helt (som i dag — `if (slot.actionEntityId == slot.displayEntityId) actionLabel(slot.action) else …`). For `action=="NONE"` udelades linjen helt (ingen handling at opsummere). |
| Visning ≠ handling (fx climate viser temperatur, men styrer en `input_boolean`) | Action-summary-linjen vises: `"<actionLabel> → <actionEntityId eller actionLabel>"`, afkortet med ellipsis hvis nødvendig. |
| 5 slots (maks) | Uændret listehøjde ift. i dag (stadig 1 række pr. slot) — ingen ekstra scroll introduceret af dette redesign. |
| Screen-reader / Talkback | Alle 3 `IconButton`s får eksplicit `contentDescription` — reelt en tilgængeligheds-forbedring ift. i dag, hvor `TextButton`s implicit tekst ("↑", "↓") er svagt beskrivende for skærmlæsere. |

### Fravalgte alternativer (og hvorfor)

- **Kun ellipsis, ingen knap-ændring:** løser læsbarhed for label, men gør intet ved at
  action-summary-linjen (som i sig selv kan blive lang, fx `"Udløs automatisering/script →
  script.sonos_stue_p4"`) stadig kan skubbe knapperne ud af rækken, som observeret i reproduktionen.
  Root cause (fire brede tekstknapper) forbliver.
- **Drag-to-reorder i stedet for ↑/↓:** teknisk mere "moderne", men kræver ny afhængighed/gestik-håndtering,
  er sværere at gøre tilgængelig (Talkback-drag er besværligt), og løser ikke det akutte
  bredde-problem alene — kan overvejes som separat, senere forbedring, men er ude af scope for denne fix.
- **Alt i overflow-menu (inkl. ↑/↞ done via array of pending)** dvs. også flytte ↑/↓ ind i ⋮-menuen:
  overvejet, men reorder er en hyppig nok handling (bruges ved hver ny slot-tilføjelse for at style
  rækkefølgen) til at fortjene synlige, direkte-tilgængelige knapper frem for et ekstra tryk gennem en menu.

---

## 6. MultiEntityWidget: varianter + elastisk sizing (v0.2.23)

**Baggrund:** widgetten viste sig som "4 rækker høj, fuld bredde" ved placering, uanset antal
konfigurerede slots (2-5). Root cause: `resizeMode="none"` + fast `minWidth=280dp`/`minHeight=74dp`
i widget-info-XML'en — Android låser widgettens grid-footprint ved PLACERING, FØR config-activity
kører, så appen aldrig kan kende det faktiske slot-antal i det øjeblik. Løst efter UX-review
(2 runder: design-godkendelse før kode, implementerings-godkendelse efter).

### Fire widget-varianter

Widget-pickeren viser nu 4 separate entries — hver med sin egen `minWidth` matchende det
tilsigtede slot-antal, så widgetten har korrekt startstørrelse fra placering i det almindelige
tilfælde. Al config-logik (tilføj/fjern/rediger slot, 1-5 slots) er 100% delt mellem varianterne —
varianten bestemmer KUN XML-footprint'en.

| Variant | Fil / Receiver-klasse | minWidth | minHeight | targetCellWidth |
|---|---|---|---|---|
| 2 pladser | `multi_entity_2_widget_info.xml` / `MultiEntityWidget2Receiver` | 124dp | 56dp | 2 |
| 3 pladser | `multi_entity_3_widget_info.xml` / `MultiEntityWidget3Receiver` | 184dp | 56dp | 3 |
| 4 pladser | `multi_entity_4_widget_info.xml` / `MultiEntityWidget4Receiver` | 244dp | 56dp | 4 |
| 5 pladser | `multi_entity_widget_info.xml` / `MultiEntityWidgetReceiver` (oprindeligt navn bevaret) | 304dp | 56dp | 5 |

Fælles: `resizeMode="horizontal|vertical"`, `maxResizeWidth=320dp`, `maxResizeHeight=120dp`,
samme `MultiEntityWidgetConfigActivity`, samme `MultiEntityWidget`-Glance-klasse (kun
manifest-registrering + XML-footprint differentierer). `minHeight=56dp` matcher familiens øvrige
1-rækkes widgets (climate/cover/light) præcist — verificeret via widget-pickerens egen
grid-beregning (viser "2×1"/"3×1", samme som `HA Climate`s "2×1").

**Bagudkompatibilitet:** `MultiEntityWidgetReceiver`/`multi_entity_widget_info.xml` bevarer sit
oprindelige class-/filnavn (Android binder en placeret widget til pakke+class-navn, ikke til
XML-indholdet) — allerede placerede widgets fortsætter uændret som de facto "5-plads"-varianten.
Kun 3 NYE receiver-klasser/XML-filer tilføjes for 2/3/4-plads.

**Picker-differentiering:** navne leder med tallet ("2-Entity HA Multi" ikke "HA Multi (2)") for
hurtigere skimning i en lang, uafiltreret OS-liste. 4 nye preview-ikoner
(`ic_multi_entity_2/3/4/5.xml`) viser N udfyldte bokse ud af 5 — ikke kun et tal.

### Elastisk boks-sizing (`MultiEntityWidget.kt`)

`sizeMode = SizeMode.Exact` (ikke `SizeMode.Responsive` med diskrete buckets som lys/climate/
cover) — bevidst afvigelse fra familiemønsteret, fordi denne widget har ÉT kontinuerligt-skalerende
layout (elastisk N-boks-række), ikke et discrete compact/wide-valg. Boks-BREDDE og -HØJDE beregnes
separat:

- **Bredde:** `boxWidth = ((tilgængelig bredde − 2×4dp ramme-padding − (n-1)×4dp mellemrum) / n)`,
  clamped til `[48dp, 56dp]`. 48dp = Android tap-target-minimum (skærpet efter UX-review — oprindeligt
  forslag var 32dp, for lille til tappable slot-bokse). 56dp = samme boksstørrelse som øvrige
  entity-widgets' compact-layout.
- **Højde:** `boxHeight = (tilgængelig højde − 2×4dp) clamped til [48dp, 56dp]` — beregnet UAFHÆNGIGT
  af bredden, så widgettens totalhøjde kan forblive 56dp (matcher familien) uden at boks-bredden
  behøver være kvadratisk med højden.
- **Overflow:** hvis beregnet breddeboks ville komme under 48dp (bruger har konfigureret flere
  slots end den valgte variant er dimensioneret til), vises kun så mange slots som får plads ved
  48dp, og resten samles i én "+N"-overflow-badge (samme 48-56dp boksstørrelse, `surfaceVariant`-
  baggrund, tal-tekst) — aldrig et tap-mål under 48dp.

### Ramme

Hele slot-rækken (+ evt. overflow-badge) wrappes i én `Box`: `fillMaxSize`, baggrund = **literal
fast farve** `Color(0x80808080)` (grå, 50% alpha — IKKE `GlanceTheme.colors.surfaceVariant`),
`cornerRadius=16.dp`, `padding=4.dp`. Bevidst valg (fastholdt efter UX-review-anbefaling om
tema-farve): matcher brugerens eksplicitte spec-ord "grå", og alpha-blanding af Glance's
tema-`ColorProvider`-API har intet etableret mønster i denne kodebase — vurderet som
disproportioneret risiko for lav gevinst. Verificeret empirisk OK i både lys/mørk baggrund på
emulator; justér farve/alpha hvis det ser forkert ud på rigtig enhed.

### Fjernet: widget-titel

`MultiWidgetEntity.title`-feltet er UBRUGT af UI (gemmes altid som tom streng ved save) — ingen
DB-migration. Fjernet fra: `ListScreen`s `OutlinedTextField` (config Skærm 1) og
`MultiEntityContent`s betingede `Text`-linje (hjemmeskærm-render). Ingen ny "overskrift"-tilstand
introduceret nogen steder.

---

## 7. MultiEntityWidget slot-editor: Visning/Handling + auto-detekteret handling (v0.2.25)

**Kildefil:** `MultiEntityWidgetConfigActivity.kt`, `SlotEditorScreen` + `SectionCard`.

Skærmen konfigurerer én slot (visnings-entitet, handlings-mål, handlings-type, kort label).
Titel: **«Tilpas entitet»**. Layout (scrollbar `Column`, 16dp padding):

```
[ Kort label (valgfrit) ]  OutlinedTextField, maxLength 12 — ØVERST
┌ Visning ┐   SectionCard: 1dp outlineVariant, 12dp radius, 16dp padding, titleSmall/primary
│ friendlyName / entityId / [Skift entitet]
└─────────┘
┌ Handling ┐  (tilstandsmaskine, se nedenfor)
└──────────┘
[Annullér]           TextButton, fillMaxWidth
[Tilføj til widget]  filled Button, disabled ved ugyldigt mål
```

### Handling-tilstandsmaskine

`targetDiffers = actionEntity ≠ displayEntity`; `opts = compatibleActionsFor(target.domain) − NONE`.

| Tilfælde | Vises |
|---|---|
| `opts` tom, mål==visning | caption «Denne enhed kan kun vises — intet sker ved tryk.» |
| `opts` tom, mål≠visning (ugyldigt) | fejl-caption + «Tilføj til widget» disabled |
| mål==visning, styrbar | `Switch` «Reagér på tryk» (default TIL). TIL+1 valg → auto-linje «Ved tryk: X». TIL+2 valg → 2 radios. FRA → caption «viser kun status» |
| mål≠visning | INGEN kontakt. 1 valg → auto-linje; 2 valg → 2 radios. + «Mål: {navn}» |

«Kun visning» (NONE) er ALDRIG et radio-valg — det er kontaktens FRA-tilstand. Ved mål≠visning
findes kontakten ikke, så NONE kan ikke opstå (fikser den oprindelige fejl strukturelt).

### Snap-regler (data-integritet)

- `defaultActionFor(domain)` = `opts.firstOrNull() ?: "NONE"`. Kaldes i entity-picker-callbacken
  når visnings-entitet ELLER mål vælges → action snappes til domænets første gyldige handling.
- `draftFromSlot` normaliserer ÆLDRE data ved indlæsning: en slot gemt med `action=NONE` + andet
  mål (muligt i gammel UI) snappes til `opts.first()`, så en radio altid er valgt.
- Data-model uændret: `MultiWidgetSlotEntity.action` beholder `NONE/TOGGLE/RANGE/TRIGGER`.

`actionShortLabel` bruges til radios + auto-linje (kort: «Udløs» ikke «Udløs automatisering/script»).

---

## 8. MultiEntityWidget: revert til én variant + resize-banner (v0.2.27)

**Baggrund:** de 4 varianter fra §6 (2/3/4/5 pladser) delte 100% af config-logikken (tilføj/
fjern/rediger op til 5 slots, samme `computeSlotLayout`-render, samme "+N"-overflow-badge) —
variant-valget påvirkede KUN start-footprint. At vælge "3-Entity" forhindrede ikke bruger i
efterfølgende at tilføje 5 via config-UI, hvilket gjorde valget kunstigt/vildledende. Kendt fund
fra v0.2.26: "4 receiver-klasser er identiske kopier". Bruger besluttede (2026-07-03) at gå
tilbage til én variant og i stedet kompensere for at appen ikke kan auto-resize footprint til
valgt slot-antal.

**Afvist alternativ:** låse slot-antal til valgt variant (fjerner eksisterende tilføj/fjern-
frihed — værre UX end problemet det løser).

### Én variant, fast 4-slot default-footprint

`multi_entity_2/3/4_widget_info.xml` + `MultiEntityWidget2/3/4Receiver` fjernet.
`MultiEntityWidgetReceiver`/`multi_entity_widget_info.xml` bevarer sit oprindelige navn
(bagudkompatibilitet, jf. §6) men footprint ændret fra 5-slot- til 4-slot-mål:
`minWidth="244dp"`, `targetCellWidth="4"` (var 304dp/5). `minHeight=56dp`,
`maxResizeWidth=320dp`, `maxResizeHeight=120dp`, `resizeMode="horizontal|vertical"` uændret —
320dp rummer alle 5 slots ved naturlig 56dp-størrelse (5×56+4×4=296dp ≤ 320dp), så bruger kan
resize til at se alle 5 uden overflow, hvis de vælger 5. Widget-picker-strengene konsolideret til
én generisk label/beskrivelse (ikke tals-specifik): `multi_entity_widget_label` =
"HA Multi-entity"/"HA Multi-entitet". Preview-ikonerne for 2/3/5 slots samt de tilhørende
strenge er fjernet (ubrugte); `ic_multi_entity_4` genbruges som previewImage.

`computeSlotLayout`, `SlotLayout` og sizing-konstanterne (`MultiEntityWidget.kt`) ændret fra
`private` til `internal` for at kunne genbruges fra config-activity (uændret render-logik).

### Resize-banner (config-activity, Skærm 1)

Kompenserer for at footprint ikke kan auto-resizes til valgt slot-antal: en **state-aware**
informationsbanner (ikke en modal dialog ved widget-tilføjelse — dårlig timing, ingen kontekst
endnu) vises i `ListScreen`, KUN når konfigureret slot-antal overstiger hvad widgettens FAKTISK
placerede bredde kan rumme.

- `MultiEntityConfigScreen`: `LaunchedEffect(slots.size)` læser
  `AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)` →
  `OPTION_APPWIDGET_MIN_WIDTH` (dp, reel værdi — ikke gæt). Kalder samme
  `computeSlotLayout(minWidthDp.dp, slots.size)` som widgettens egen render bruger. Hvis
  `overflowCount > 0`: gemmer `(visibleSlots, slots.size)` i `overflowInfo`-state, ellers `null`.
  Ukendt/0-bredde (fx visse testforhold) → intet banner, ikke et forkert tal.
- `ListScreen` viser banneret som `Card` (`surfaceVariant`-baggrund, 16dp padding) øverst i
  Column'en når `overflowInfo != null` — samme visuelle mønster som `MainActivity`s
  info-kort, ingen ny komponent-type. Tekst: lokaliseret formatstreng
  `multi_entity_resize_banner` (%1$d synlige / %2$d total), alle 3 sprogfiler
  (`values`/`values-da`/`values-sv`).
- Ingen "vis ikke igen"-dismiss — banneret er 100% state-drevet og forsvinder selv når bruger
  fjerner slots eller resizer widgetten og genåbner config.
- Eksisterende "+N"-overflow-badge PÅ selve widgetten (§6) er uændret — den vedvarende
  on-widget-reminder efter config lukkes.

### Kendt, udskudt UX-problem (ikke del af denne ændring)

Bruger observerede at footprinttet på hjemmeskærmen kan være markant større end den visuelle
widget (boksene centreres i den fulde tildelte plads, jf. §6's `computeBoxHeight`/ramme-logik) —
taget som separat opgave.

## 9. Widget UX Pack: slot-kort, bekræftelse, RANGE-input, tema, formatering (v0.2.39)

Fuld design/beslutningshistorik: `docs/superpowers/specs/2026-07-06-widget-ux-pack-design.md`,
plan: `docs/superpowers/plans/2026-07-06-widget-ux-pack.md`, ADR'er:
`docs/adr/2026-07-06-widget-ux-pack-adrs.md`. Dette afsnit opsummerer de config-flader
ændringen tilføjede.

### Slot-kort (Skærm 1, `SlotCard`)

4-rækkers layout erstatter det tidligere 3-zone side-om-side layout: (1) entitetsnavn på fuld
bredde, `maxLines=1` + ellipsis, chevron; (2) handlings-resumé; (3) sekundær-chips (uændret);
(4) ↑/↓/🗑 samlet i én ikon-række i bunden (48dp targets). Navn og knapper konkurrerer ikke
længere om bredden.

### Handling-sektionen: "Bekræft ved tryk"

Ny switch, vist KUN for TOGGLE/TRIGGER (hoved-entitet og hver sekundær-chip uafhængigt). Til:
tryk på hjemmeskærmen åbner `ConfirmActionActivity` (translucent dialog) i stedet for at udføre
handlingen direkte. Dialogteksten navngiver altid **handlings-målets** friendly name, ikke den
viste entitet (relevant ved asymmetrisk visning/handling, jf. §7). Fra (default, alle
eksisterende slots): uændret direkte adfærd. Room: `confirmAction`/`secondary{1,2,3}ConfirmAction`
(v6-migration).

### Handling-sektionen: RANGE — Skyder/Indtast værdi

For RANGE-handlinger tilbyder config et valg mellem "Skyder" (default, uændret) og "Indtast
værdi" (åbner `NumberInputActivity` med et talfelt i stedet for `RangeControlActivity`s skyder).
Room: `rangeInputMode`/`secondary{1,2,3}RangeInputMode` (v7-migration, `null`="Skyder"). Uanset
valg gælder samme min/max-validering og samme underliggende HA-service.

Selve skyderen (`RangeControlActivity`, delt af ALLE widgets med RANGE — ikke kun multi-entity)
fik desuden −/+ trinknapper: trin 0,5 hvis `(max-min) ≤ 20`, ellers 1. Første tryk snapper til
nærmeste trin i tryk-retningen.

### Visning-sektionen: værdi-formatering

For rå-værdi-domæner (sensor/number/input_number/input_text/input_datetime/input_select):
- **Decimaler**-dropdown (Auto/0/1/2) — Auto runder til maks 1 decimal.
- **Datoformat**-felt (kun for datetime-agtige entiteter, dvs. `input_datetime` eller sensor med
  `device_class=timestamp`) — frit `DateTimeFormatter`-mønster med live preview af den aktuelle
  værdi under feltet. Tomt/ugyldigt mønster falder tilbage til automatisk lokalt kort format.

Room: `displayPrecision`/`datetimeFormat` + sekundær-varianter (v6-migration).

### Globalt tema-valg (uden for multi-entity-config)

Dropdown "Tema" (Lys/Mørk/Følg system) i `MainActivity` ved sprog-vælgeren — `SecureStore.
themeMode`. Gælder app-UI og ALLE widgets (ikke kun multi-entity), ikke WebView-dashboardet
(ADR-4).

### Refresh-strip som overlay

Den klikbare refresh-bjælke i bunden af multi-entity-listen er nu en halvtransparent overlay
ovenpå rækkerne (ikke en fast bjælke under listen) — overflow-rækker skimtes bag den under
scroll.
