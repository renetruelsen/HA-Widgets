# Multi-entity widget ("kombineret widget")

**Dato:** 2026-07-01
**Status:** Godkendt af bruger, klar til implementeringsplan

## Baggrund

Bruger ønsker en ny widget-type der kan vise/styre op til 5 vilkårlige HA-entiteter i
én widget på hjemmeskærmen, hver med sin egen on-click-action — fx en "Mercedes"-widget
med opvarmning (climate), vinduer/udluftning (cover eller lock), batteristatus (sensor,
read-only). Alle eksisterende widget-typer i appen er domain-specifikke (1 entity, 1 fast
handling pr domain). Denne widget er den første der blander vilkårlige domains og
brugervalgte actions i én instans.

**Revideret 2026-07-01 efter brugerfeedback (2. runde):** tilføjet `device_tracker`,
afkoblet visnings-entitet fra action-mål-entitet (en slot kan vise én entitet men handle
på en anden — fx vis batteri-sensor, tryk udløser en automatisering), og forenklet
config-flow til én samlet liste-skærm (fjernet det separate "trin-for-trin"-flow).

## Scope

Native app: ny Room-model, ny config-flow (én samlet liste-skærm til både oprettelse og
rekonfiguration), nyt Glance-layout, udvidelse af eksisterende sync/fan-out-infrastruktur.
Genbruger så meget som muligt af eksisterende byggeklodser (`EntityActions.kt`,
`RangeControlActivity`, `GlanceWidgetCommon.kt`, `BaseEntityPickerActivity`-mønsteret).

**Eksplicit ikke i scope:**
- Drag-resize af widgetten (se "Størrelses-kompromis" nedenfor — bevidst fravalgt).
- Nye domains ud over de 12 listet nedenfor (fx `vacuum`, `media_player`).
- Ændringer af eksisterende enkelt-entity-widgets (light/switch/scene/script/automation/
  sensor/binary_sensor/cover/climate forbliver uændrede).

## Domains

De eksisterende 9 (light, switch, scene, script, automation, sensor, binary_sensor, cover,
climate) + **lock** + **number** + **device_tracker** = 12 domains valgbare i
entity-pickeren (både til visning og som action-mål).

## Data-model (Room)

Relationel model (ikke JSON-blob — matcher eksisterende Room-mønster, nemt at
tilføje/fjerne/genordne rækker, konsistent med `[[glance-reactive-flow]]`-kravet om reaktiv
Room `Flow`):

```kotlin
@Entity(tableName = "multi_widget")
data class MultiWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val title: String, // tom streng = ingen titel-linje
)

@Entity(
    tableName = "multi_widget_slot",
    primaryKeys = ["appWidgetId", "slotIndex"],
)
data class MultiWidgetSlotEntity(
    val appWidgetId: Int,
    val slotIndex: Int,        // 0..4, bestemmer venstre-til-højre rækkefølge
    val displayEntityId: String,
    val displayDomain: String,
    val actionEntityId: String,  // default = displayEntityId, kan afvige
    val actionDomain: String,    // default = displayDomain, kan afvige
    val action: String,        // "TOGGLE" | "RANGE" | "TRIGGER" | "NONE"
    val label: String,         // tom = brug friendly_name fra displayEntityId, maks 12 tegn
)
```

**Visning vs action er afkoblet:** `displayEntityId`/`displayDomain` bestemmer ikon+label+
status i slotten. `actionEntityId`/`actionDomain` bestemmer hvad et tryk gør. De to er
uafhængige, men UI'en foreslår `actionEntityId = displayEntityId` som standard (nul ekstra
klik i normaltilfældet) — bruger kan trykke "Skift mål" i action-trinnet for at pege
handlingen på en helt anden entitet (fx: vis en batteri-sensor, men tryk udløser en
automatisering).

DAO'er følger samme mønster som `EntityWidgetDao`: `upsert`/`get`/`observe` (Flow) på
`multi_widget`, samt `observeSlots(appWidgetId): Flow<List<MultiWidgetSlotEntity>>`,
`upsertSlot`, `deleteSlot(appWidgetId, slotIndex)`, `deleteAllSlots(appWidgetId)`, og en
`allEntityIds(): List<String>` (distinct UNION af `displayEntityId` og `actionEntityId`
på tværs af alle rækker) parallelt med `EntityWidgetDao.allEntityIds()`.

Range-værdier (min/max/aktuel) beregnes live fra `actionEntityId`'s attributter ved render,
ligesom `LightWidget`/`ClimateWidget`/`CoverWidget` i dag — persisteres ikke i config.
Trigger-service (scene/script → `turn_on`, automation → `trigger`) udledes af
`actionDomain` ved klik-tidspunkt — persisteres ikke.

## Action-model

Config-UI'en viser kun de action-typer der er meningsfulde for **action-målets** domain
(`actionDomain` — ikke nødvendigvis samme som `displayDomain`), samme kompatibilitets-filter
som eksisterende dimmable-tjek i `LightWidget`:

| Domain (som action-mål) | Toggle | Range | Trigger | Ingen action |
|---|---|---|---|---|
| light | ✓ (`turn_on`/`turn_off`) | ✓ kun hvis dimmable (`supported_color_modes`) | – | ✓ |
| switch | ✓ (`turn_on`/`turn_off`) | – | – | ✓ |
| lock | ✓ (`lock`/`unlock`) | – | – | ✓ |
| cover | ✓ (`open_cover`/`close_cover`) | ✓ kun hvis position understøttet | – | ✓ |
| climate | ✓ (`turn_on`/`turn_off`) | ✓ temp-slider (min/max fra attrs) | – | ✓ |
| number | – | ✓ (`set_value`) | – | ✓ |
| automation | ✓ enable/disable | – | ✓ force-trigger | ✓ |
| scene | – | – | ✓ (`turn_on`) | ✓ |
| script | – | – | ✓ (`turn_on`) | ✓ |
| sensor / binary_sensor / device_tracker | – | – | – | ✓ (eneste mulighed, read-only) |

**"Ingen action":** tap = refresh af `displayEntityId` (samme som eksisterende
`RefreshEntityAction`, ikke en fuldstændig no-op) — konsistent med sensor/binary_sensor's
nuværende adfærd i enkelt-entity-widgets.

**Genbrug af eksisterende actions/UI (bekræftet, ingen ny action-UI opfindes):**
- Toggle → `ToggleEntityAction` (EntityActions.kt). **Skal udvides:** i dag hardcoder den
  service-navne `turn_on`/`turn_off` for alle domains. Skal generaliseres til domain-bevidst
  mapping (lock: `lock`/`unlock` med state `locked`/`unlocked`; cover: `open_cover`/
  `close_cover` med state `open`/`closed`; øvrige: uændret `turn_on`/`turn_off`).
- Range → genbruger **den eksisterende `RangeControlActivity`-popup 1:1** (samme dialog
  som i dag åbnes fra fx `LightWidget`s lysstyrke-tryk) — ingen ny UI. **Skal udvides**
  til `number`-domain (service `number.set_value`, min/max/step fra entity-attributter
  `min`/`max`/`step`).
- Trigger → `TriggerEntityAction` (uændret, allerede domain+service-parametriseret).
- Ingen action → `RefreshEntityAction` (uændret).

## Config-flow — én samlet liste-skærm

**Ændring fra første udkast:** det tidligere "trin-for-trin med Ja/Nej-tilføj-endnu-en-
slot" er droppet. I stedet bruges ÉN skærm til BÅDE oprettelse og rekonfiguration —
forskellen er kun om den starter tom (ny widget) eller forudfyldt (eksisterende widget).
Dette gør den tidligere plan om "to forskellige UI-flows" overflødig.

**Skærm: `MultiEntityWidgetConfigActivity`**
- Titel-felt øverst (valgfrit, tom = ingen titel-linje på widgetten).
- Liste af konfigurerede slots (starter tom for ny widget), hver række viser
  display-entitet + action-opsummering (fx "Batteri 12% · Udløs: Automation X").
  Hver række har "Rediger" og "Fjern".
- Permanent **"+ Tilføj slot"**-CTA (ikke en ja/nej-dialog) — deaktiveret ved 5 slots.
  Åbner:
  1. Entity-picker (alle 12 domains, søgbar) → vælg **display-entitet**.
  2. Action-trin: forudvalgt **action-mål = display-entiteten** ("Skift mål"-knap åbner
     samme entity-picker for at vælge en anden action-entitet). Action-type-dropdown
     filtreret efter det (evt. andre) action-målets domain. Kort label-felt (maks 12
     tegn, samme spec som eksisterende `Tilpas widget`-skærm).
  3. "Tilføj til widget" → returnerer til listen med den nye slot tilføjet.
- Op/ned-pile pr slot-række → genordning (bytter `slotIndex` med nabo).
- **"Gem widget"-knap altid synlig, aktiveret så snart ≥1 slot er konfigureret**
  (deaktiveret ved 0 slots). Trykkes når som helst — ingen tvungen rækkefølge.

Denne ene skærm dækker både first-time setup (Nova sender
`ACTION_APPWIDGET_UPDATE` før config åbner — widget viser `UnconfiguredWidgetContent`
indtil første "Gem") og rekonfiguration (long-press → widget-indstillinger åbner samme
skærm, forudfyldt fra Room).

## Rendering — fast fodaftryk

**Platformsbegrænsning (afklaret med bruger):** Android `widget_info.xml`-metadata
(`minWidth`/`minHeight`/`resizeMode`) er fælles for ALLE instanser af samme widget-type —
appen kan ikke sætte en per-instans bredde ud fra gemt config. "Bredde følger antal slots,
ingen drag-resize" er derfor implementeret som et fast maks-fodaftryk, ikke en dynamisk
per-instans-størrelse.

- `multi_widget_info.xml`: `resizeMode="none"`, fast bredde svarende til 5 slots
  (~272dp, samme per-celle-trin som eksisterende `56dp`/`110dp`-konvention), fast højde
  = 1 slot-række + titel-linje (~74dp) — ens for alle instanser, uanset konfigureret N.
- Widget med færre end 5 slots viser kun de N konfigurerede slot-bokse, venstre-justeret;
  resten af det faste fodaftryk er tomt/transparent.
- Titel-linje-pladsen er altid reserveret (også når titel er tom — blank i så fald), af
  samme årsag: kan ikke gøre højden betinget pr instans.
- Hver slot: egen `Box` (`cornerRadius=16dp`), farve efter `actionEntityId`'s state (falder
  tilbage til `displayEntityId`'s state hvis action=NONE) — samme konvention som
  eksisterende entity-widgets (`primary`/`onPrimary` aktiv, `surfaceVariant` inaktiv,
  `errorContainer` unavailable) — der genbruger `WidgetCompactLayout` (ikon+label+status
  fra `displayEntityId`, `GlanceWidgetCommon.kt`) og egen `clickable`-modifier ud fra dens
  `action`-type + `actionEntityId`.
- Ukonfigureret widget (0 slots): genbruger `UnconfiguredWidgetContent`-mønsteret.

## Sync/fan-out-integration

- `EntityRepository.refreshAll()`: skal udvide entityId-kilden til UNION af
  `entityWidgetDao().allEntityIds()` og ny `multiWidgetDao().allEntityIds()` (som selv
  allerede unionerer `displayEntityId`+`actionEntityId` på tværs af slots).
- `WidgetUpdater.updateForEntity()`: skal udvides til også at tjekke `multi_widget_slot`
  for match på `entityId` mod ENTEN `displayEntityId` ELLER `actionEntityId` — hvis
  fundet, kald `MultiEntityWidget().update()` for alle dens glance-instanser (samme
  reaktive Room-Flow-mønster som eksisterende widgets — ingen ny opdateringslogik
  nødvendig, blot udvidet lookup).

## Navngivning / widget-picker metadata

- Klassenavn: `MultiEntityWidget` (+ `MultiEntityWidgetConfigActivity`,
  `MultiEntityWidgetReceiver`) — én config-activity dækker både oprettelse og rekonfig.
- Picker-titel: endeligt navn besluttes ved implementering (fx "Multi-widget" eller
  "Kombineret widget") — engelsk beskrivelse i `strings.xml` jf. eksisterende
  `docs/widget-settings-spec.md`-konvention (maks ~30 tegn).
- `android:widgetFeatures="reconfigurable"`, `android:widgetCategory="home_screen"`,
  `android:updatePeriodMillis="0"` (som alle andre entity-widgets).

## Test / QA

Følger eksisterende workflow (`CLAUDE.md` → "Workflow: rettelser og release"): build →
QA på `pixel_test`-emulator (alle 12 domains som visning, alle 4 action-typer, afkoblet
display/action-entitet i mindst ét slot, tilføj/fjern/genordn slots via listen, 1 og 5
slots, rekonfiguration af en eksisterende widget) → QA på rigtig enhed (Galaxy S23, tjek
fast fodaftryk ikke beskæres af Nova/One UI) → commit.
