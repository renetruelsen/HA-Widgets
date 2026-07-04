# MultiEntityWidget: grid-adaptive row height + global refresh icon (v0.2.35)

## Baggrund

Brugerrapport: rækkerne i `MultiEntityWidget` cropper enten indhold, eller efterlader et
tomt tomrum i bunden, afhængig af hvilken hjemmeskærm-grid-størrelse widgetten er placeret
ved. Derudover ønskes en lille refresh-ikon i nederste højre hjørne, der opdaterer ALLE
widgets (ikke kun denne instans).

## Del 1: Adaptiv rækkehøjde

- Rækkehøjde beregnes ud fra den faktiske tildelte plads (`LocalSize.current.height`),
  antal konfigurerede slots, ramme-padding og (hvis vist) refresh-striben. Ingen øvre
  grænse — 1-2 entiteter i en meget høj widget strækker rækken(rne) til at fylde pladsen,
  indhold centreres lodret via eksisterende `verticalAlignment = Alignment.CenterVertically`.
- Nedre grænse: `MIN_ROW_HEIGHT_DP = 48` (tap-target-minimum). Falder tilbage til
  eksisterende `LazyColumn`-scroll når beregnet højde ville komme under denne.

## Del 2: Refresh-ikon (reserveret bundstribe)

- En fast `RefreshStrip`-composable (`REFRESH_STRIP_HEIGHT_DP = 32`) er altid til stede
  nederst i widgetten, retfærdigt til højre, når aktiveret. Rækkerne trækker denne højde fra
  først, så der aldrig er overlap.
- Ikonet (`ic_refresh.xml`) kalder `RefreshEntityAction` UDEN `entityId` — udløser
  `SyncWorker.runNow()`, som allerede faner ud til ALLE widgets (eksisterende, genbrugt
  adfærd — ingen ny kode i `EntityActions.kt`).
- Ny per-widget indstilling `MultiWidgetEntity.showRefreshIcon: Boolean = true` (Room
  migration v4→v5, `ALTER TABLE multi_widget ADD COLUMN showRefreshIcon INTEGER NOT NULL
  DEFAULT 1`), styret via en ny switch i `MultiEntityWidgetConfigActivity`s `ListScreen`.

## Del 3: SizeMode.Responsive (Nova-launcher-fix)

**Root cause fundet under device-QA:** `SizeMode.Exact` komponerer altid BÅDE en
portræt- og en landskabs-udgave (Android's indbyggede `RemoteViews(landscape, portrait)`
to-arguments-konstruktør, som launcheren selv vælger imellem baseret på
`Configuration`-orientering ved inflation). På Galaxy S23 + Nova Launcher blev
landskabs-udgaven konsekvent vist SELVOM telefonen var i portræt-tilstand — bekræftet via
midlertidig logging (`rawSize=344x327dp → rowHeight=139.5dp` portræt-pas beregnet korrekt,
men skærmen viste faktisk `rawSize=630x160dp → rowHeight=56dp` landskabs-udgaven).
Emulatorens AOSP-launcher rammer IKKE denne fejl.

**Fix:** skift `sizeMode` fra `SizeMode.Exact` til `SizeMode.Responsive` med et fast sæt
`DpSize`-buckets. Ifølge Android-dokumentationen bruger `Responsive` på API 31+ en
faktisk størrelses-baseret vælger (ikke orienterings-baseret parring) til at vælge mellem
de deklarerede buckets — det omgår derfor den orienterings-baserede fejlvalg-mekanisme.
Under API 31 opfører `Responsive` sig som `Exact` (samme eksponering som i dag — ingen
regression).

**Bucket-valg:** kun ÉN bredde (rækkehøjde-formlen bruger udelukkende `LocalSize.current.
height` — bredden håndteres allerede af `fillMaxWidth()`/`defaultWeight()`, som strækker
uanset bucket-bredde):

```kotlin
override val sizeMode = SizeMode.Responsive(
    setOf(
        DpSize(244.dp, 56.dp),
        DpSize(244.dp, 130.dp),
        DpSize(244.dp, 200.dp),
        DpSize(244.dp, 270.dp),
    )
)
```

244dp matcher det eksisterende deklarerede `minWidth` i `multi_entity_widget_info.xml`.
Højde-buckets følger 70dp/række-kadencen fra `ShortcutWidget`s historik (v0.2.15:
"70*n-30"-formlen).

**Accepteret trade-off:** en placering der falder MELLEM to buckets snapper til den
nærmeste MINDRE bucket (Android vælger "største der passer, ellers mindste") — efterlader
et begrænset (≤70dp) restrum, IKKE det ca. dobbelte misforhold der observeredes med
`Exact`+Nova-fejlen. `multi_entity_widget_info.xml`s `maxResizeHeight` strammes fra 400dp
til 270dp, så en bruger aldrig kan resize widgetten forbi den største deklarerede bucket
(hvilket ville genskabe en mindre udgave af gap-problemet i den øvre ende).

Rækkehøjde-formlen i `MultiEntityContent` ændres IKKE — den læser allerede
`LocalSize.current.height` uanset om værdien er kontinuerlig (Exact) eller bucket-baseret
(Responsive).

## Kendte, accepterede begrænsninger

- Under API 31 er Nova-fejlens eksponering uændret ift. i dag (ingen regression, men heller
  ingen garanteret fix på ældre enheder).
- En placering mellem to buckets efterlader op til ~70dp ubrugt plads i bunden — samme
  kategori af "boks-vs-indhold-gap" som allerede dokumenteret som udskudt i v0.2.27.
- Kun testet med Nova Launcher (S23) og AOSP-launcher (emulator) — andre launcheres
  håndtering af `Responsive`-buckets er ikke verificeret.

## QA-plan

1. Emulator (`pixel_test`): genbekræft eksisterende adfærd (stræk, centrering, floor+scroll,
   refresh-ikon, config-toggle) er uændret efter sizeMode-skiftet.
2. Galaxy S23 (Nova): resize widgetten til flere forskellige højder omkring bucket-grænserne
   og bekræft at rækkerne nu fylder boksen korrekt i PORTRÆT (den oprindeligt observerede
   fejl) — dette er den afgørende test for hele Del 3.
