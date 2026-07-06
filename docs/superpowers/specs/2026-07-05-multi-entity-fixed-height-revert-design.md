# MultiEntityWidget: revert til fast/naturlig rækkehøjde — diskussion + beslutning

> Denne fil dokumenterer en diskussion/beslutningsproces til brug i en ny session. Den
> tekniske implementeringsplan ligger separat i
> `C:\Users\rtr\.claude\plans\delightful-scribbling-yao.md` (uden for repoet — kopiér
> indholdet ind hvis planen skal overleve på tværs af maskiner).

## Baggrund: hvordan vi endte her

**Oprindelig opgave (denne sessions start):** ret en bug i `MultiEntityWidget` hvor rækker
enten blev beskåret eller efterlod tomrum i bunden afhængigt af hjemmeskærm-grid-størrelsen,
og tilføj et refresh-alle-ikon.

**Første løsning (v0.2.35, commit `2912cc0`):** en dynamisk rækkehøjde-formel der læser
`LocalSize.current.height` og strækker rækkerne til at fylde den tildelte plads:

```kotlin
val availableHeightDp = LocalSize.current.height.value
val stripHeightDp = if (showRefreshIcon) REFRESH_STRIP_HEIGHT_DP else 0
val rowCount = sorted.size
val gapTotalDp = ROW_GAP_DP * rowCount
val framePaddingTotalDp = FRAME_PADDING_DP * 2
val usableForRowsDp = availableHeightDp - framePaddingTotalDp - stripHeightDp - gapTotalDp
val computedRowHeightDp = if (rowCount > 0) (usableForRowsDp / rowCount) else 0f
val rowHeight = computedRowHeightDp.coerceAtLeast(MIN_ROW_HEIGHT_DP.toFloat()).dp
```

Plus et refresh-ikon i en fast 32dp `Row` nederst (`RefreshStrip`), og en Room-migration
(v4→v5) for en `showRefreshIcon`-config-toggle.

**Bug #1 opdaget under device-QA (Galaxy S23, Nova Launcher):** `SizeMode.Exact` fik Nova til
konsekvent at vise den **landskabskomponerede** udgave af widgetten, selv mens telefonen var i
**portræt** — bekræftet ved midlertidig logging (korrekt portræt-matematik blev beregnet, men
den forkerte landskabs-udgave blev rent faktisk vist). Root cause: Android's `RemoteViews`
under `SizeMode.Exact` komponerer altid BÅDE en portræt- og en landskabs-udgave, og
launcheren vælger selv hvilken der vises ud fra `Configuration`-orientering ved
inflation — ikke noget appen/Glance kan styre.

**Fix for bug #1 (v0.2.36, commit `cc93dad`):** skift `sizeMode` til
`SizeMode.Responsive(setOf(DpSize(244.dp, 56.dp), DpSize(244.dp, 130.dp), DpSize(244.dp, 200.dp), DpSize(244.dp, 270.dp)))`.
Responsive bruger på API 31+ en reel størrelses-baseret vælger blandt deklarerede buckets i
stedet for en orienterings-baseret parring, hvilket omgår fejlvalget. Verificeret virkende på
S23.

**Bug #2 rapporteret af brugeren (efter bug #1-fix'et):** chips (`SecondaryChip`) mistede
deres runde hjørner, og hele kortet blev beskåret i bunden — specifikt kun når refresh-ikonet
var til stede. Root cause blev aldrig færdig-bekræftet i kode, men blev forstået under en
efterfølgende brainstorm (se næste afsnit) som endnu et symptom på samme underliggende årsag
som bug #1.

## Brainstorm: to udviklere + en UX'er

Brugeren bad eksplicit om at få denne diskussion ført som en struktureret samtale mellem to
uafhængige udviklere og en UX-person, der skulle forstå de Android-launcher-specifikke
udfordringer og afveje fleksibel vs. fast højde ud fra både robusthed og brugeroplevelse.

**Konklusion brugeren godkendte fuldt ud (før denne revert-diskussion):**
- `SizeMode.Responsive` med en FULD liste af 10 buckets (244dp bredde × højder fra 64dp til
  384dp, i to sæt — med og uden refresh-strip).
- Faste (ikke dynamisk beregnede) højder: 56dp kort, 32dp refresh-strip, 16dp gap.
- Top-alignment i en `LazyColumn` — aldrig stræk, aldrig beskæring; overflow scroller altid.
- Mockups blev lavet for 5 scenarier (A: normal+ikon, B: normal uden ikon, C: for lille +
  scroll, D: for stor + tomrum, E: for lille + ikon + scroll) og godkendt visuelt.

Denne konklusion var i sig selv rigtig i sin **kravspecifikation** (fast højde, aldrig stræk,
scroll ved overflow, tomrum accepteret ved oversize) — men implementeringsvejen dertil
(Responsive + 10 buckets + en ny beregning) var mere kompleks end nødvendigt.

## Den afgørende observation: `c152b6d` opfylder allerede kravene

Brugeren spurgte: **"Er det ikke lige før, vi kan gå tilbage til commit `c152b6d` og bare
tilføje vertikal scroll?"**

Ved at læse `MultiEntityWidget.kt` som den så ud ved `c152b6d` (v0.2.34, lige før denne
sessions ændringer) blev det klart at den allerede har:

- `SlotRow` med **naturlig/wrap-content højde** — intet `LocalSize`-opslag nogen steder.
- En `LazyColumn` i en `fillMaxSize()`-ramme, som **allerede scroller** ved overflow (intet at
  tilføje der).
- `SizeMode.Exact` uden nogen synlig bug, fordi der intet steds refereres til `LocalSize` i det
  komponerede indhold.

**Nøgleindsigt:** Nova-orienteringsbuggen (bug #1) kan kun være synlig/skadelig når det
komponerede indhold rent faktisk AFHÆNGER AF `LocalSize` — hvis portræt- og
landskabs-udgaven altid komponerer til IDENTISK indhold (fast/naturlig højde, ingen
`LocalSize`-læsning), er det ligegyldigt hvilken af de to udgaver launcheren vælger at vise.
Samme rod-årsag forklarer chip-clipping-buggen (bug #2): den kunne kun opstå fordi en
beregnet rækkehøjde kunne blive lille nok til at klemme en 48dp chip.

Med andre ord: **`c152b6d`s enkle, ældre kode er allerede strukturelt identisk med den
kravspecifikation brainstormen nåede frem til** (fast højde, aldrig stræk, scroll ved
overflow, tomrum ved oversize) — bare uden `Responsive`/bucket-systemets kompleksitet, og uden
at have været udsat for hverken bug #1 eller bug #2.

## Endelig beslutning

I stedet for at fortsætte med at lappe på stretch-formlen (Responsive-buckets, en ny
rækkehøjde-beregning, osv.), flettes de gode dele fra denne sessions arbejde ind i `c152b6d`s
fundament:

1. **Behold fra `c152b6d`:** naturlig/wrap-content `SlotRow`-højde, `SizeMode.Exact`,
   `LazyColumn`-scroll, `maxResizeHeight="400dp"` (uncapped, intet bucket-loft nødvendigt).
2. **Behold fra denne sessions arbejde (uafhængigt af stretch-bug'en, stadig korrekt):**
   refresh-ikon-featuren i sin helhed — `RefreshEntityAction`-wiring, Room-kolonnen
   `showRefreshIcon` + migration v4→v5, config-toggle'en i
   `MultiEntityWidgetConfigActivity`, reaktiv `MultiWidgetViewState`-kombination af
   config+slots+states i `provideGlance`.
3. **Drop helt:** `SizeMode.Responsive`, de 10 buckets, `MIN_ROW_HEIGHT_DP`, hele
   rækkehøjde-beregningsformlen, `rowHeight: Dp`-parameteren på `SlotRow`.
4. **Ny justering (brugerønske under denne samtale):** refresh-stripen gøres mindre —
   **24dp total højde** (var 32dp) med et **16dp ikon** (var 48dp, reelt klippet til ~32dp af
   den daværende 32dp Row). Hele stripens `Row` (ikke kun ikonet) gøres klikbar for et mere
   favorbart tryk-areal, da et 16dp-bredt ikon-areal alene ville være svært at ramme i en kun
   24dp høj bjælke.

Denne beslutning betyder at `SizeMode.Exact`s tidligere-observerede Nova-bug ikke kan
genopstå MED MINDRE nogen senere genindfører en `LocalSize`-baseret beregning — det er
eksplicit dokumenteret som en kode-kommentar-advarsel i den tekniske plan, så det ikke
glemmes ved en fremtidig ændring.

## Status og næste skridt

- **Denne fil:** dokumenterer selve diskussionen/beslutningen (til brug på tværs af sessioner).
- **Teknisk implementeringsplan:** `C:\Users\rtr\.claude\plans\delightful-scribbling-yao.md`
  (fil-for-fil ændringer, præcis kode, verifikationsplan). Ligger uden for repoet — hvis den
  skal være tilgængelig i en helt ny session/maskine, bør indholdet kopieres et sted i repoet
  (fx til `docs/superpowers/plans/`) før sessionen lukkes.
- **Intet er implementeret endnu.** Koden i `MultiEntityWidget.kt` er stadig på HEAD's
  v0.2.36-tilstand (Responsive + stretch-formel + 32dp/48dp refresh-strip) på det tidspunkt
  denne fil skrives.
- Version skal bumpes til 37/"0.2.37" som del af implementeringen.
- Efter QA (emulator + Galaxy S23/Nova) bør `CLAUDE.md`s statusafsnit opdateres med et nyt
  v0.2.37-punkt, jf. projektets faste konvention for at dokumentere hver mærkbar ændring der.
