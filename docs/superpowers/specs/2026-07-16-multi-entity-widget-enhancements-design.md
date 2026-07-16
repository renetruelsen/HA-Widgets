# MultiEntityWidget: chip-only rækker, ubegrænsede chips, rå skyder-værdier

Dato: 2026-07-16. Godkendt af bruger via brainstorming (spørgsmål + mockups i visual-companion).

## 1. Auto-scroll til ny chip

Når en ny sekundær-chip tilføjes (entity-picker → tilbage til "Tilpas entitet"), scroller
`SlotEditorScreen` automatisk ned til bunden, så den nye chip er synlig med det samme.
Implementeres med `LaunchedEffect(draft.secondaryEntities.size)` der kalder
`scrollState.animateScrollTo(scrollState.maxValue)` når antallet vokser (ikke ved fjernelse).

## 2. Chips-only rækker (ingen hoved-entitet) + ubegrænsede chips

### Dataskema (Room v14 → v15)

- **Ny tabel `multi_widget_chip`** (`MultiWidgetChipEntity`), én række pr. chip:
  `appWidgetId`, `slotIndex`, `chipIndex` (sammensat primærnøgle) + `displayEntityId`,
  `displayDomain`, `actionEntityId`, `actionDomain`, `action` (alle non-null — rækkens
  eksistens = chippen er i brug), samt de valgfrie overrides `showValue`, `confirmAction`,
  `displayPrecision`, `datetimeFormat`, `rangeInputMode`, `label`, `showIcon`,
  `showRangeValue` (alle nullable, samme "uset = domæne-default"-mønster som i dag).
  Intet hardcoded loft i skemaet — UI håndhæver **maks 10** chips pr. række
  (`MAX_SECONDARY_ENTITIES = 10`).
- **`multi_widget_slot`**: `displayEntityId`/`displayDomain`/`actionEntityId`/`actionDomain`/
  `action` bliver **nullable**. Alle null samtidig = "chips-only række" (ingen delvis-null-
  tilstand opstår, da UI'en altid sætter/rydder dem samlet). Ny nullable kolonne
  `showRangeValue: Boolean?` (punkt 3, hoved-slot). De 48 `secondary1-4*`-kolonner **fjernes**.
- **Migration**: fuld tabel-genskabelse af `multi_widget_slot` (samme teknik som
  `MIGRATION_8_9`), kopiér `secondary1-4*`-data over i `multi_widget_chip` (4 INSERT/SELECT,
  én pr. sekundær-plads, kun hvor `displayEntityId IS NOT NULL`), drop gamle tabel, omdøb ny.

### Reversibilitet

`SlotEditorScreen`s Visning-sektion får to tilstande:
- **Ingen hoved-entitet:** "Ingen hoved-entitet — rækken viser kun chips" + knap
  "+ Vælg hoved-entitet" (åbner entity-picker for Display).
- **Med hoved-entitet:** uændret (entitetsnavn, "Skift entitet") + ny knap
  "Fjern hoved-entitet" (rydder displayEntity/actionEntity/action i draften).

Chips (sekundær-entiteter) berøres ikke af konverteringen i nogen retning.

### Entry point

"Tilføj slot" åbner stadig entity-picker'en som i dag (uændret for det almindelige
tilfælde — ingen ekstra klik). Picker'en får en ny "Spring over — kun chips"-linje nederst,
der går direkte til `SlotEditorScreen` med en tom `SlotDraft` (intet display/action valgt).

### Layout

- Række **med** hoved-entitet: chips forbliver højre-justeret (uændret — label-kolonnen
  har `defaultWeight()`, chips presses til højre).
- **Chips-only** række: chips centreres og fordeles jævnt ud over hele rækkens bredde.
  Glance's `Row` har ingen `Arrangement.SpaceEvenly` — simuleres med
  `GlanceModifier.defaultWeight()`-`Spacer`'e sat ind før/mellem/efter hver chip i en
  `fillMaxWidth()`-Row (lige store vægtede mellemrum = jævn fordeling).
- Ingen ikon/label/status-kolonne i chips-only rækker (ingen hoved-entitet at vise).

### Berørte filer

Ny: `MultiWidgetChipEntity.kt`.
Ændret: `MultiWidgetSlotEntity.kt`, `AppDatabase.kt` (v15 + migration), `MultiWidgetDao.kt`
(chip-queries, nullable-guards på `allDisplayEntityIds`/`allActionEntityIds`,
`allSecondaryEntityIds` mod ny tabel, `slotsForEntity`→ boolean-eksistens-tjek der også
dækker chip-tabellen), `MultiEntityDrafts.kt` (dropper `secondaryColumns()`/
`withSecondaryColumns()`, arbejder direkte med `List<MultiWidgetChipEntity>`),
`MultiEntityRendering.kt` (chips-only layout-gren, `SlotRow` null-sikker), `MultiEntitySlotEditor.kt`
(reversibilitets-UI, auto-scroll, loft 10), `MultiEntityListScreen.kt` (null-sikker
ikon/navn i `SlotCard` for chips-only rækker, entry-point-skip), `MultiEntityWidgetConfigActivity.kt`
(state-model bærer slot+chips sammen, gem/hent-flow for begge tabeller), `MultiEntityWidget.kt`
(`allEntityIds()`/`statesFlow` inkluderer chip-tabellen), `WidgetTransferSerializer.kt`,
`WidgetConfigDump.kt`, `WidgetReconciler.kt` (purge rydder også chip-tabellen),
`EntityRepository.kt`/`WidgetUpdater.kt`.

## 3. Vis rå skyder-værdi

Ny "Vis værdi"-toggle i Visning-sektionen for domænerne `light`, `cover`, `climate`
(RANGE-kapable, ikke allerede raw-value-domæner) — **uafhængig af valgt handling**,
default **fra** (bevarer nuværende tekst-adfærd for alle eksisterende widgets/chips).
Slået til: viser rå værdi ("45%" for lys/persienne, "21.5°" for klima) i stedet for
`formatEntityState`s faste tekst ("Tændt"/"Åben"/"Varme" osv.).

`rangeCurrentValue`/`rangeMin`/`rangeMax` flyttes fra `MultiEntityClickModifier.kt` til en
delt fil (`widget/common/RangeValues.kt`), så både klik-dialogen og den nye
visnings-formattering bruger samme domæne-udtræknings-logik (ingen duplikering).

Gælder både hoved-slot (`SlotDraft.showRangeValue`) og sekundær-chip
(`SecondarySlotDraft.showRangeValue`), samme mønster/kolonne-navn.

## QA-plan

Følger projektets standardworkflow (`CLAUDE.md`): build → emulator-QA (migration verificeret
direkte i DB, chips-only række renderer/gemmer/genindlæses korrekt, reversibilitet begge veje,
range-værdi-toggle for light/cover/climate, auto-scroll, import/eksport af en chips-only
konfiguration) → telefon-QA (bruger) → commit.
