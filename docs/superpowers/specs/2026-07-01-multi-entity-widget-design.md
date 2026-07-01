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

## Scope

Native app: ny Room-model, ny config-flow (både første opsætning og rekonfiguration), nyt
Glance-layout, udvidelse af eksisterende sync/fan-out-infrastruktur. Genbruger så meget som
muligt af eksisterende byggeklodser (`EntityActions.kt`, `RangeControlActivity`,
`GlanceWidgetCommon.kt`, `BaseEntityPickerActivity`-mønsteret).

**Eksplicit ikke i scope:**
- Drag-resize af widgetten (se "Størrelses-kompromis" nedenfor — bevidst fravalgt).
- Nye domains ud over de 11 listet nedenfor (fx `vacuum`, `media_player`, `device_tracker`).
- Ændringer af eksisterende enkelt-entity-widgets (light/switch/scene/script/automation/
  sensor/binary_sensor/cover/climate forbliver uændrede).

## Domains

De eksisterende 9 (light, switch, scene, script, automation, sensor, binary_sensor, cover,
climate) + **lock** + **number** = 11 domains valgbare i slot-entity-pickeren.

## Data-model (Room)

Ny relationel model (ikke JSON-blob — matcher eksisterende Room-mønster, nemt at
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
    val slotIndex: Int,       // 0..4, bestemmer venstre-til-højre rækkefølge
    val entityId: String,
    val domain: String,
    val action: String,      // "TOGGLE" | "RANGE" | "TRIGGER" | "NONE"
    val label: String,       // tom = brug friendly_name, maks 12 tegn (som i dag)
)
```

DAO'er følger samme mønster som `EntityWidgetDao`: `upsert`/`get`/`observe` (Flow) på
`multi_widget`, samt `observeSlots(appWidgetId): Flow<List<MultiWidgetSlotEntity>>`,
`upsertSlot`, `deleteSlot(appWidgetId, slotIndex)`, `deleteAllSlots(appWidgetId)`, og en
`allEntityIds(): List<String>` (distinct) parallelt med `EntityWidgetDao.allEntityIds()`.

Range-værdier (min/max/aktuel) beregnes live fra entity-attributter ved render, ligesom
`LightWidget`/`ClimateWidget`/`CoverWidget` i dag — persisteres ikke i config.
Trigger-service (scene/script → `turn_on`, automation → `trigger`) udledes af `domain`
ved klik-tidspunkt — persisteres ikke.

## Action-model

Config-UI'en viser kun de action-typer der er meningsfulde for den valgte entitets domain
(kompatibilitets-filter, samme princip som eksisterende dimmable-tjek i `LightWidget`):

| Domain | Toggle | Range | Trigger | Ingen action |
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
| sensor / binary_sensor | – | – | – | ✓ (eneste mulighed, read-only) |

**"Ingen action":** tap = refresh af den ene entitet (samme som eksisterende
`RefreshEntityAction`, ikke en fuldstændig no-op) — konsistent med sensor/binary_sensor's
nuværende adfærd i enkelt-entity-widgets.

**Genbrug af eksisterende actions:**
- Toggle → `ToggleEntityAction` (EntityActions.kt). **Skal udvides:** i dag hardcoder den
  service-navne `turn_on`/`turn_off` for alle domains. Skal generaliseres til domain-bevidst
  mapping (lock: `lock`/`unlock` med state `locked`/`unlocked`; cover: `open_cover`/
  `close_cover` med state `open`/`closed`; øvrige: uændret `turn_on`/`turn_off`).
- Range → genbruger `RangeControlActivity`. **Skal udvides** til `number`-domain
  (service `number.set_value`, min/max/step fra entity-attributter `min`/`max`/`step`).
- Trigger → `TriggerEntityAction` (uændret, allerede domain+service-parametriseret).
- Ingen action → `RefreshEntityAction` (uændret).

## Config-flow

To forskellige UI-flows afhængig af om widgetten er ny eller allerede konfigureret:

### Ny widget (første opsætning) — step-for-step guide

1. Entity-picker (alle 11 domains i én liste, søgbar på tværs af domains) → vælg entitet.
2. Action-vælger (kun kompatible actions for entitetens domain vist) + kort label
   (maks 12 tegn, samme spec som eksisterende `Tilpas widget`-skærm).
3. "Tilføj endnu en slot?" — Ja (hvis < 5 slots) / Nej.
4. Efter sidste slot: valgfrit widget-titel-felt → "Gem widget".

### Eksisterende widget — rekonfiguration (long-press → widget-indstillinger)

Åbner i stedet en **oversigtsskærm**:
- Titel-felt øverst (forudfyldt).
- Liste af eksisterende slots (entity + action + label pr række), hver med:
  - "Rediger" → åbner samme entity+action-valg som i step-flowet, forudfyldt.
  - "Fjern" → sletter slotten (og omnummererer `slotIndex` for de resterende).
  - Op/ned-pile → genordning (bytter `slotIndex` med nabo).
- "+ Tilføj slot"-knap nederst, deaktiveret ved 5 slots.
- Én "Gem"-knap i bunden — persisterer alle ændringer på én gang, `SyncWorker.runNow()`
  + `schedule()`, `finish()`.

Dette matcher brugerønsket om at kunne justere én enkelt slot ud af 5 uden at klikke sig
igennem hele step-for-step-guiden igen.

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
- Hver slot: egen `Box` (`cornerRadius=16dp`), farve efter state — samme konvention som
  eksisterende entity-widgets (`primary`/`onPrimary` aktiv, `surfaceVariant` inaktiv,
  `errorContainer` unavailable) — der genbruger `WidgetCompactLayout` (ikon+label+status,
  `GlanceWidgetCommon.kt`) og egen `clickable`-modifier ud fra dens `action`-type.
- Ukonfigureret widget (0 slots): genbruger `UnconfiguredWidgetContent`-mønsteret.

## Sync/fan-out-integration

- `EntityRepository.refreshAll()`: skal udvide entityId-kilden til UNION af
  `entityWidgetDao().allEntityIds()` og ny `multiWidgetDao().allEntityIds()`.
- `WidgetUpdater.updateForEntity()`: skal udvides til også at tjekke
  `multi_widget_slot` for match på `entityId` — hvis fundet, kald `MultiEntityWidget()
  .update()` for alle dens glance-instanser (samme reaktive Room-Flow-mønster som
  eksisterende widgets — ingen ny opdateringslogik nødvendig, blot udvidet lookup).

## Navngivning / widget-picker metadata

- Klassenavn: `MultiEntityWidget` (+ `MultiEntityWidgetConfigActivity`,
  `MultiEntityWidgetReconfigureActivity` eller lignende for oversigtsskærmen,
  `MultiEntityWidgetReceiver`).
- Picker-titel: endeligt navn besluttes ved implementering (fx "Multi-widget" eller
  "Kombineret widget") — engelsk beskrivelse i `strings.xml` jf. eksisterende
  `docs/widget-settings-spec.md`-konvention (maks ~30 tegn).
- `android:widgetFeatures="reconfigurable"`, `android:widgetCategory="home_screen"`,
  `android:updatePeriodMillis="0"` (som alle andre entity-widgets).

## Test / QA

Følger eksisterende workflow (`CLAUDE.md` → "Workflow: rettelser og release"): build →
QA på `pixel_test`-emulator (alle 11 domains, alle 4 action-typer, tilføj/fjern/genordn
slots i rekonfig-oversigt, 1 og 5 slots) → QA på rigtig enhed (Galaxy S23, tjek fast
fodaftryk ikke beskæres af Nova/One UI) → commit.
