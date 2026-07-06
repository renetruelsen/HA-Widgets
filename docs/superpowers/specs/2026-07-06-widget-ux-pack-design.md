# Widget UX Pack — design (2026-07-06)

Branch: `feature/widget-ux-pack`. 8 brugerrapporterede punkter, godkendt i 3 blokke (A/B/C).
Basis: v0.2.38 (`c7483fe`). Alle punkter gælder appen + Glance-widgets; WebView/HA-dashboard
berøres kun af tema-punktet (C1).

## Blok A — Config-UI

### A1. Slot-liste redesign (multi-widget config, Skærm 1)

**Problem:** `SlotCard` (`MultiEntityWidgetConfigActivity.kt` ~452–566) viser entitetsnavn +
chevron i en `weight(1f)`-kolonne uden `maxLines` — lange navne line-breaker og kortet bliver
uoverskueligt. Skraldespand (32dp) + ↑/↓-søjle (36dp) stjæler bredde fra navnet.

**Løsning:** Entitetsnavnet får sin egen fulde række øverst i kortet (maxLines=1+ellipsis eller
2 linjer — afgøres på mockup). Handling-resumé og sekundær-chips på rækker under. Skraldespand
og ↑/↓ omplaceres så de ikke konkurrerer med navnet om bredden (fx bund-række eller kompakt
højresøjle — afgøres på mockup). **Endeligt layout besluttes via UI-mockup i Fase 4 (hård gate).**

### A2. Widget-picker-ikoner usynlige i lyst tema

**Problem:** Alle 18 domæne-ikon-drawables har hardcodet `android:fillColor="#FFFFFF"`.
I widgets tintes de runtime (`ColorFilter.tint`), men `android:previewImage` i
`res/xml/*_widget_info.xml` viser den rå drawable → hvidt ikon på lys picker-baggrund.

**Løsning:** Separate preview-drawables (layer-list: afrundet plade i app-primærfarve
`#0B6FA4` + eksisterende hvidt ikon ovenpå) for alle widget-info-filer. Widget-runtime-rendering
uændret — kun `previewImage`-referencerne skifter.

### A3. Knap-tekst "Tilføj til widget" → "Opdater" ved redigering

**Problem:** Slot-editorens gem-knap (`MultiEntityWidgetConfigActivity.kt:767`) viser altid
hardcodet "Tilføj til widget" — også ved redigering af eksisterende slot.

**Løsning:** Redigering af eksisterende slot → "Opdater"; ny slot → "Tilføj til widget".
Editoren ved allerede om den redigerer (slot indlæst fra DB).

**Bonus (i18n-regression fra v0.2.13):** hardcodede danske strenge flyttes til `strings.xml`
i alle 3 sprog (en/da/sv): `MultiEntityWidgetConfigActivity.kt:442` ("Gem widget"), `:767`
("Tilføj til widget" + ny "Opdater"), `BaseEntityPickerActivity.kt:169` ("Gem widget").
Øvrige hardcodede strenge i samme filer tages med hvis de findes ved implementering.

## Blok B — Handlinger

### B1. "Bekræft ved tryk" på toggle/trigger-handlinger

**Problem:** Et uheldigt tryk på hjemmeskærmen udløser straks toggle/scene/script.

**Løsning:**
- Ny switch **"Bekræft ved tryk"** i Handling-sektionen — hoved-entitet OG hver sekundær-chip.
  Vises kun for handlingstyperne TOGGLE og TRIGGER (RANGE/TEXT/DATETIME åbner allerede en
  dialog = implicit bekræftelse).
- Ny **`ConfirmActionActivity`** efter `RangeControlActivity`-mønstret (translucent
  Material3-dialog): tekst "Sluk Køkken?" / "Kør Filmaften?" (verbum udledt af domæne+state),
  knapper Annullér/Bekræft. Bekræft kalder samme `EntityRepository.command`-vej som
  `ToggleEntityAction`/`TriggerEntityAction`.
- `MultiEntityWidget.clickModifier` (~394–460): flag sat → `actionStartActivity(ConfirmActionActivity)`
  i stedet for direkte `actionRunCallback`.
- **DB (Room v5→v6):** `confirmAction` + `secondary{1,2,3}ConfirmAction` (Boolean/Int, default 0)
  på `multi_widget_slot`.
- Kun multi-widget i denne omgang; single-entity widgets kan udvides senere.

### B2. RANGE-handlinger: skyder vs. felt — beslutning på mockup

To varianter mockes i Fase 4; **beslutningen træffes dér**:

- **Variant A — config-valg:** ny option pr. RANGE-handling: "Skyder" eller "Indtast værdi".
  Felt-varianten åbner dialog med talfelt (mønster: `TextControlActivity`) og kalder samme
  service. Kræver DB-kolonne `rangeInputMode` (+ 3× secondary) i v5→v6-migrationen.
- **Variant B — skyder med −/+ trin-knapper:** `RangeControlActivity` får −/+ på hver side
  af skyderen. Trin: 0.5 hvis (max−min) ≤ 20, ellers 1. Første tryk afrunder til nærmeste
  halve/hele. Ingen ny config-option/DB-kolonne.
- Vælges A, kan B stadig tilføjes senere (ikke gensidigt udelukkende); mockup kan også vise
  kombinationen.
- Forbedringer af den delte `RangeControlActivity` gælder OGSÅ single-entity widgets
  (Light/Climate/Cover) — bevidst, jf. ADR-2.

**Bekræft-dialogens navn:** altid handlings-målets friendly name, jf. ADR-1.

## Blok C — Visning

### C1. Globalt tema-valg: Lys / Mørk / Følg system

- Dropdown **"Tema"** i `MainActivity` (connected-state, ved sprog-vælgeren). Persisteres i
  `SecureStore` (nyt felt, fx `themeMode`: `light|dark|system`, default `system`).
- **App-UI:** `Theme.kt` udvides med mørkt Material3-skema (i dag findes kun lyst);
  skema vælges af pref (system → `isSystemInDarkTheme()`).
- **Widgets:** ny delt `WidgetColors`-helper. pref=system → nuværende adfærd
  (`GlanceTheme.colors` / day-night `ColorProvider`); tvunget lys/mørk → faste farvesæt.
  Alle widget-renderers (multi + single-entity + shortcut) skifter til helperen.
- **WebView: UDGÅR** (grill-beslutning, ADR-4) — dashboardet følger HA-serverens eget tema.
  Bemærk: CLAUDE.md's v0.2.6-påstand om `themes:{darkMode}` matcher ikke koden;
  `replyExternalConfig` sender kun capabilities.
- **Re-render ved skift:** MainActivity kalder `updateAll` for alle providers (ADR-5).

### C2. Værdi-formatering

**Automatisk default (alle widgets, også single-entity sensor):**
- Numerisk state: maks 1 decimal (`23.88888` → `23.9`); heltalsværdier uden decimal.
  Parsing af HA-state altid med punktum (`Locale.ROOT`); visning kan følge app-locale
  (kendt faldgrube fra v0.2.34: `String.format` uden locale gav dansk komma).
- Datetime/timestamp: lokalt kort format udledt af `has_date`/`has_time`
  (dato+tid / kun dato / kun tid).

**Overstyring pr. slot/chip (kun multi-widget config, Visning-sektionen):**
- **Decimaler:** dropdown Auto / 0 / 1 / 2. DB: `displayPrecision` +
  `secondary{1,2,3}DisplayPrecision` (nullable Int, null=auto).
- **Datoformat:** tekstfelt, vises kun for datetime/timestamp-entiteter. Frit
  `DateTimeFormatter`-mønster (fx `dd/MM HH:mm`, `EEE HH:mm`, `d. MMM`). Tomt = auto.
  Ugyldigt mønster → fallback til auto, aldrig crash. **Live preview** af entitetens aktuelle
  værdi under feltet mens der tastes. DB: `datetimeFormat` +
  `secondary{1,2,3}DatetimeFormat` (nullable String).

### C3. Refresh-bar som halvtransparent overlay

**Problem/ønske:** Baren skal ligge OVER listen med halvtransparent baggrund, så
overflow-rækker skimtes/scroller bag den ("glas-look" — brugerens eksplicitte ønske).

**Løsning:** `MultiEntityContent` (~186–217): strip flyttes fra Column-søskende under
`LazyColumn` til **Box-overlay bund-justeret** — listen fylder hele rammen, strippen ovenpå
med halvtransparent baggrund (day/night `ColorProvider`, ~60–70 % alpha, afstemmes visuelt).
Højde 24dp, fuld-bredde-klik og `RefreshEntityAction` uændret.
Usynlig 24dp-spacer sidst i listen så fuldt nedscrollet viser alle rækker over baren (ADR-3).

## Data-migration (samlet)

Én Room-migration **v5→v6** på `multi_widget_slot` dækker alle nye kolonner:

| Kolonne | Type | Default | Punkt |
|---|---|---|---|
| `confirmAction` | INTEGER (Bool) | 0 | B1 |
| `secondary{1,2,3}ConfirmAction` | INTEGER (Bool) | 0 | B1 |
| `displayPrecision` | INTEGER nullable | NULL (=auto) | C2 |
| `secondary{1,2,3}DisplayPrecision` | INTEGER nullable | NULL | C2 |
| `datetimeFormat` | TEXT nullable | NULL (=auto) | C2 |
| `secondary{1,2,3}DatetimeFormat` | TEXT nullable | NULL | C2 |
| (`rangeInputMode` + 3× secondary — KUN hvis mockup vælger Variant A) | TEXT nullable | NULL (=skyder) | B2 |

`SecureStore`: nyt felt `themeMode` (C1). Migration skal være ikke-destruktiv
(verificeres på emulator + S23 som v2→v3 i v0.2.29).

## Test

- Unit: formatering (decimaler, datetime-mønster inkl. ugyldigt mønster-fallback),
  trin-logik (hvis B2 Variant B), migration v5→v6 (Room migration-test hvis opsætning findes,
  ellers manuel DB-verifikation).
- QA (emulator `pixel_test` + Galaxy S23/Nova, jf. CLAUDE.md-workflow): alle 8 punkter
  efterprøves end-to-end; tema-skift verificeres i app + widget + WebView; bekræft-dialog
  fra hjemmeskærm; picker-ikoner i lyst OG mørkt tema; migration med eksisterende widgets.

## Fase 4-mockups (hård gate før kode)

1. Slot-liste-kort (A1) — navnet på egen række, placering af skraldespand/↑/↓.
2. RANGE: Variant A vs Variant B (B2) — beslutning træffes her.
3. Bekræft-dialog (B1) — tekst/knap-layout.
4. Tema-dropdown-placering + mørkt app-skema (C1) — hurtig visning.

## Mockup-gate-beslutninger (2026-07-06, godkendt)

- A1: navn på egen række, 1 linje + ellipsis; ↑/↓/🗑 som ikon-række i kortets bund (48dp).
- B2: **kombination A+B** — config-valg Skyder/Indtast værdi (multi, migration v6→v7
  `rangeInputMode` + 3× secondary) OG −/+ trin-knapper på den delte skyder (alle widgets).
- B1-dialog og C1-tema godkendt som mocket (mørk primær ~#7FC3E8, finjusteres i kode).
- Mockup: claude.ai/code/artifact/dd3855a2-d320-4067-a4c0-49b980c594cf

## Uden for scope

- Bekræft/formatering-options på single-entity widgets (kun automatisk formatering følger med).
- `input_select`-handling, OAuth/M3-punkter, resize-footprint-gap (kendt, separat opgave).
