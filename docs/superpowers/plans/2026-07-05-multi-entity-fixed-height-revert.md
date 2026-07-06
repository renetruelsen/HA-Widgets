# MultiEntityWidget: revert til fast/naturlig rækkehøjde + lille refresh-strip

## Context

Denne sessions oprindelige opgave var at rette en bug i `MultiEntityWidget` (rækker der enten
blev beskåret eller efterlod tomrum i bunden afhængig af grid-størrelse) og tilføje et
refresh-alle-ikon. Løsningen der blev bygget (v0.2.35-36, commits `2912cc0`/`cc93dad`/`5949578`)
introducerede en dynamisk rækkehøjde-formel baseret på `LocalSize.current.height`, hvilket i
praksis afslørede/skabte to nye bugs:

1. **Nova Launcher portræt/landskab-mismatch under `SizeMode.Exact`** — rettet ved at skifte til
   `SizeMode.Responsive` med 4 diskrete buckets.
2. **Chips mister runde hjørner + kortet bliver beskåret i bunden**, specifikt når refresh-ikonet
   er til stede — root cause aldrig færdig-rettet, men UNDER en efterfølgende brainstorm
   (2 udviklere + 1 UX'er, brugerens ord) blev det klart at BEGGE bugs stammer fra samme kilde:
   indhold der læser `LocalSize.current` for at beregne en strakt/dynamisk højde.

Brugeren observerede at commit `c152b6d` (v0.2.34, lige før denne sessions ændringer) allerede
har **naturlig/wrap-content rækkehøjde** (ingen `LocalSize`-afhængighed) og en `LazyColumn` der
**allerede scroller** ved overflow — dvs. den præcise egenskabskombination (fast højde, aldrig
strakt, scroll ved overflow, tomrum accepteret ved oversize) som blev godkendt i
brainstorm-konklusionen, uden behov for `SizeMode.Responsive`/bucket-systemet. Årsagen
`SizeMode.Exact`-buggen kun opstår når komponeret indhold rent faktisk afhænger af `LocalSize`
(Android komponerer en portræt- og en landskabs-udgave; hvis begge er identiske, er det ligegyldigt
hvilken launcheren viser) — så at fjerne `LocalSize`-afhængigheden fjerner selve
udløsningsbetingelsen for buggen, i stedet for at arbejde udenom den med buckets.

**Beslutning:** i stedet for at fortsætte med at lappe på stretch-formlen, flettes de gode dele
fra denne sessions arbejde (refresh-ikon + dens Room-kolonne/config-toggle) ind i `c152b6d`s
enklere, allerede-korrekte layout-fundament. `SizeMode.Responsive`/bucket-listen droppes helt
igen til fordel for `SizeMode.Exact` (nu sikkert, fordi intet indhold længere læser `LocalSize`).
Refresh-stripen gøres samtidig mindre efter brugerønske: 24dp total højde (var 32dp), ikon 16dp
(var 48dp, reelt klippet til ~32dp af den daværende 32dp-højde Row).

## Ændringer

### `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityWidget.kt`

Bevares uændret fra nuværende HEAD (ikke en del af stretch-bug'en, ortogonalt tilføjet i
`2912cc0` og stadig korrekt):
- `provideGlance`: config+slots+states `combine(...)` → `MultiWidgetViewState`, `showRefreshIcon`
  udledt af `config?.showRefreshIcon ?: true`.
- `MultiWidgetViewState`, `allEntityIds()`, `statesFlow`, `SecondaryChipData`,
  `secondaryChipData`, `secondaryChips()`.
- `SecondaryChip` (48dp tap-target, uændret).
- `clickModifier`, `rangeCurrentValue`, `rangeMin`, `rangeMax` (uændret).
- `MultiEntityWidgetReceiver` (uændret).

Ændres (revert af stretch-logikken + skrumpet refresh-strip):

1. **Imports** — fjern `androidx.compose.ui.unit.Dp`, `androidx.compose.ui.unit.DpSize`,
   `androidx.glance.LocalSize` (ingen af dem bruges længere).

2. **Konstanter** — fjern `MIN_ROW_HEIGHT_DP` helt. Ret:
   ```kotlin
   internal const val REFRESH_STRIP_HEIGHT_DP = 24
   ```
   (var `32`).

3. **`sizeMode`** — tilbage til `SizeMode.Exact`, med opdateret kommentar der forklarer HVORFOR
   det nu er sikkert (ingen `LocalSize`-afhængighed tilbage) og advarer mod at genindføre en
   `LocalSize`-baseret beregning uden at genoverveje dette:
   ```kotlin
   // SizeMode.Exact: sikkert her fordi INGEN komponeret indhold læser LocalSize.current —
   // ramme, LazyColumn og rækker bruger alle almindelige fillMaxSize/fillMaxWidth-modifiers med
   // naturlig (wrap-content) rækkehøjde. Android komponerer under Exact altid BÅDE en portræt- og
   // en landskabs-udgave (RemoteViews(landscape, portrait)) og lader launcheren vælge ud fra
   // Configuration-orientering — på Galaxy S23 + Nova Launcher blev landskabs-udgaven konsekvent
   // vist SELV I PORTRÆT, men det er kun synligt/skadeligt når de to udgaver rent faktisk kan
   // afvige (dvs. når indhold afhænger af LocalSize). Genindfør IKKE en LocalSize-baseret
   // rækkehøjde uden at gen-teste dette scenarie på en Nova-enhed.
   override val sizeMode = SizeMode.Exact
   ```

4. **`MultiEntityContent`** — fjern hele rækkehøjde-beregningsblokken
   (`availableHeightDp`/`stripHeightDp`/`rowCount`/`gapTotalDp`/`framePaddingTotalDp`/
   `usableForRowsDp`/`computedRowHeightDp`/`rowHeight`). Ny krop:
   ```kotlin
   @Composable
   private fun MultiEntityContent(
       context: Context,
       slots: List<MultiWidgetSlotEntity>,
       states: Map<String, EntityStateEntity?>,
       showRefreshIcon: Boolean,
   ) {
       val sorted = slots.sortedBy { it.slotIndex }

       // Rammen fylder hele det tildelte areal (fillMaxSize) — ved oversize efterlades tomrum
       // under listen/stripen i stedet for at strække indholdet (bevidst accepteret, se
       // brainstorm-konklusionen). LazyColumn scroller allerede ved undersize/overflow.
       Box(
           modifier = GlanceModifier
               .fillMaxSize()
               .background(FRAME_BACKGROUND)
               .cornerRadius(16.dp)
               .padding(FRAME_PADDING_DP.dp),
       ) {
           Column(modifier = GlanceModifier.fillMaxSize()) {
               LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                   items(sorted, itemId = { it.slotIndex.toLong() }) { slot ->
                       Column {
                           SlotRow(context, slot, states)
                           Spacer(modifier = GlanceModifier.height(ROW_GAP_DP.dp))
                       }
                   }
               }
               if (showRefreshIcon) {
                   RefreshStrip(context)
               }
           }
       }
   }
   ```

5. **`RefreshStrip`** — skrumpet strip (24dp) med et 16dp ikon; hele rækken (ikke kun ikonet) er
   klikbar, da et 16dp-bredt hit-areal alene ville være for lille at ramme pålideligt i en kun
   24dp høj bjælke:
   ```kotlin
   @Composable
   private fun RefreshStrip(context: Context) {
       Row(
           modifier = GlanceModifier
               .fillMaxWidth()
               .height(REFRESH_STRIP_HEIGHT_DP.dp)
               .clickable(actionRunCallback<RefreshEntityAction>(actionParametersOf())),
           horizontalAlignment = Alignment.End,
           verticalAlignment = Alignment.CenterVertically,
       ) {
           Image(
               provider = ImageProvider(R.drawable.ic_refresh),
               contentDescription = context.getString(R.string.multi_entity_refresh_all),
               modifier = GlanceModifier.size(16.dp).padding(end = 4.dp),
               colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
           )
       }
   }
   ```

6. **`SlotRow`** — fjern `rowHeight: Dp`-parameteren og `.height(rowHeight)` fra
   `rowModifier`; tilbage til naturlig/wrap-content højde (identisk med `c152b6d`):
   ```kotlin
   @Composable
   private fun SlotRow(
       context: Context,
       slot: MultiWidgetSlotEntity,
       states: Map<String, EntityStateEntity?>,
   ) {
       // ... uændret indhold ...
       val rowModifier = clickModifier(
           context = context,
           base = GlanceModifier.fillMaxWidth().background(bgColor).cornerRadius(12.dp).padding(8.dp),
           // (height(rowHeight) fjernet)
           action = slot.action,
           actionEntityId = slot.actionEntityId,
           actionDomain = slot.actionDomain,
           refreshEntityId = slot.displayEntityId,
           rangeLabel = label,
           actionState = actionState,
       )
       // ... resten uændret ...
   }
   ```
   Og opdatér det ene kaldested i `MultiEntityContent` fra `SlotRow(context, slot, states, rowHeight)`
   til `SlotRow(context, slot, states)` (allerede vist i punkt 4 ovenfor).

### `app/src/main/res/xml/multi_entity_widget_info.xml`

Ret `android:maxResizeHeight="270dp"` tilbage til `android:maxResizeHeight="400dp"` (som i
`c152b6d`) — intet bucket-loft nødvendigt, da indholdet ikke længere er størrelsesafhængigt.

### `app/build.gradle.kts`

Bump version: `versionCode = 36` / `versionName = "0.2.36"` → `versionCode = 37` /
`versionName = "0.2.37"`.

### Ingen ændringer nødvendige i:

- `MultiEntityWidgetConfigActivity.kt` (refresh-ikon-toggle er uafhængig af layout-logikken).
- Room (`MultiWidgetEntity.showRefreshIcon`, `MIGRATION_4_5`, `AppDatabase` version 5).
- `ic_refresh.xml`, `strings.xml`/`values-da`/`values-sv` (`multi_entity_refresh_all`).

### Dokumentation

- Tilføj en kort addendum til `docs/superpowers/specs/2026-07-04-multi-entity-row-height-refresh-design.md`
  (eller ny fil `docs/superpowers/specs/2026-07-05-multi-entity-fixed-height-revert-design.md`)
  der beskriver root cause-indsigten (LocalSize-afhængighed er fælles årsag til Nova-bug +
  chip-clipping) og reverten til naturlig rækkehøjde + `SizeMode.Exact`.
- Tilføj et nyt v0.2.37-punkt i `CLAUDE.md`s statusafsnit efter QA er grøn, jf. projektets faste
  konvention for at dokumentere hver mærkbar ændring der.

## Verifikation

1. **Byg:** `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
   → `BUILD SUCCESSFUL`.
2. **Emulator (`pixel_test`):** `adb install -r` (aldrig uninstall). Screenshot af eksisterende
   multi-entity widget: rækker har naturlig (ikke strakt) højde, chips har runde hjørner, 24dp
   refresh-strip med 16dp-ikon renderer og virker (tryk → app-wide sync, verificér fx via en
   ændret værdi eller logcat), tap på en almindelig række virker stadig (toggle/trigger/range),
   LazyColumn scroller når flere slots er konfigureret end der er plads til.
3. **Device-QA (Galaxy S23, Nova Launcher):** `adb install -r`. Resize widget'en til flere
   størrelser (lav/høj, portræt) via samme `uiautomator dump`-baserede teknik som tidligere i
   sessionen (aldrig gæt koordinater fra et screenshot alene). Bekræft: ingen
   orienterings-afhængig visnings-fejl (selve fix'et — Exact er nu sikkert), chips forbliver
   runde ved alle størrelser, refresh-ikonet kan rammes og virker på trods af den lille højde,
   tomrum (ikke strakt indhold) vises under listen ved oversize, ingen beskæring ved undersize
   (kun scroll).
4. **Commit** kun når begge QA-loops er grønne, jf. projektets faste "meld aldrig fikset uden
   bevis"-arbejdsgang.
