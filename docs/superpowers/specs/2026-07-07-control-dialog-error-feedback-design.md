# Fejl-feedback i kontrol-dialoger

**Dato:** 2026-07-07
**Status:** Godkendt (afventer skriftlig review)

## Baggrund

PR-review (2026-07, PR #1) fandt et gennemgående mønster: `RangeControlActivity`,
`TextControlActivity`, `DateTimeControlActivity`, `NumberInputActivity` og
`ConfirmActionActivity` (alle i `app/src/main/java/dk/akait/hawidgets/widget/common/`)
kalder `HaApiClient.callService(...)` (direkte eller via `RangeService.sendRangeValue`)
uden at tjekke det returnerede `HaApiClient.Result` (`Ok`/`Error`). Dialogerne lukker
(`finish()`) ubetinget, uanset om HA-kaldet reelt lykkedes.

Konsekvens: fejler kaldet (netværksfejl, HA utilgængelig), ser brugeren dialogen lukke
som om værdien blev gemt — men den blev aldrig sendt til Home Assistant. Ingen toast,
intet signal. Ikke en regression fra én PR — et etableret mønster på tværs af alle fem
dialog-aktiviteter.

## Formål

Giv brugeren synlig, konsistent feedback når et HA-kald fejler, og lad brugeren prøve
igen uden at skulle genstarte hele flowet (genåbne widget → dialog → gen-indtaste),
undtagen hvor det er teknisk umuligt.

## Løsning

### 1. Propagér resultatet (plumbing)

- **`RangeService.sendRangeValue`** (`RangeService.kt`): returtype `Unit` → `Boolean`.
  `true` når det underliggende `api.callService(...)`-kald returnerer `HaApiClient.Result.Ok`.
  `EntityRepository.refresh(...)`-kaldet efter service-kaldet er uændret og køres kun ved succes.
- **`ConfirmActionActivity.executeConfirmedAction`**: returtype `Unit` → `Boolean`.
  Begge grene (TOGGLE og TRIGGER) kalder allerede `EntityRepository.command(...)`, som
  allerede returnerer `Boolean` — ændringen er blot at returnere dette resultat i stedet
  for at kaste det væk.

Ingen ændring af `HaApiClient.callService`s signatur eller af `EntityRepository.command`s
eksisterende adfærd (den gendanner allerede `fromState` ved fejl — det bevares uændret).

### 2. Delt fejl-helper + streng

- Ny funktion i `RangeService.kt` (eller et nyt lille fælles fil — se
  implementeringsplan): `fun showActionError(context: Context)` →
  `Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()`.
  Én kilde til beskeden, ingen kopieret Toast-kald i fem filer.
- Ny lokaliseret streng-nøgle `action_failed` i alle tre sprogfiler
  (`values/strings.xml` engelsk, `values-da/strings.xml`, `values-sv/strings.xml`), fx:
  - en: "Couldn't send to Home Assistant"
  - da: "Kunne ikke sende til Home Assistant"
  - sv: "Kunde inte skicka till Home Assistant"
- Ingen succes-toast — dialogens lukning / opdateret state er selv succes-signalet.
  Kun fejl er larmende, for ikke at introducere støj ved den langt hyppigere succes-vej.

### 3. Adfærd pr. dialog

| Dialog | Handling | Ved fejl | Ved succes |
|---|---|---|---|
| `RangeControlActivity` — slider (`sendRangeCommand`) | `sendRangeValue(...)` | Toast. Dialogen er allerede "åben" (ingen `finish()` i denne kodesti i dag) — ingen ændring i åben/lukket-adfærd, kun tilføjet Toast ved fejl. | Ingen ændring (stille). |
| `RangeControlActivity` — toggle (`sendToggle`) | `api.callService(...)` for light/cover/climate turn on/off | Toast. `isOn`-flippet sker i dag ubetinget efter kaldet — ændres til KUN at flippe ved succes, så UI-state ikke løber fra virkeligheden. | `isOn` flipper som i dag. |
| `TextControlActivity.save()` | `callService("input_text", "set_value", ...)` | Toast, `busy = false`, **`finish()` udelades** — teksten brugeren skrev bevares i feltet, kan prøve "Gem" igen. | `finish()` som i dag. |
| `NumberInputActivity.save()` | `sendRangeValue(...)` | Toast, `busy = false`, **`finish()` udelades** — indtastet tal bevares. | `finish()` som i dag. |
| `DateTimeControlActivity.submit()` | `callService("input_datetime", "set_datetime", ...)` | Toast, **`finish()` bevares** (accepteret afvigelse — se nedenfor). | `finish()` som i dag. |
| `ConfirmActionActivity` | `executeConfirmedAction(...)` | Toast, `busy = false`, **`finish()` udelades** — bekræft-dialogen bliver stående, brugeren kan trykke "Bekræft" igen. | `finish()` som i dag. |

### 4. Undtagelse: `DateTimeControlActivity`

Denne aktivitet har ingen egen Compose-UI — den er kun et host for Androids native
`DatePickerDialog`/`TimePickerDialog`, som allerede er lukket når `submit()` kaldes (efter
sidste picker-callback). Der er intet UI at "holde åbent". Ved fejl: vis Toast og luk
aktiviteten som i dag (`finish()`). Brugeren må trykke widgetten igen og vælge dato/tid
forfra. Bevidst afvigelse fra "forbliv åben"-reglen — genvisning af native pickers ved fejl
blev overvejet og fravalgt (usædvanlig UX, "pickers popper op igen", højere kompleksitet
for lav gevinst — dato/tid-fejl forventes sjældne og ikke tab af meningsfuldt input, kun et
par tryk).

### 5. Tråd-sikkerhed

Alle berørte `save()`/`submit()`/`sendToggle()`-funktioner kører allerede i en
`rememberCoroutineScope()` (Compose) eller `lifecycleScope` (DateTimeControlActivity),
begge bundet til Main-dispatcheren. `HaApiClient.callService` skifter selv til
`Dispatchers.IO` og returnerer til opkalderens dispatcher — `Toast.makeText(...)` efter et
`suspend`-kald er derfor sikkert at kalde direkte, ingen ekstra `withContext(Main)` nødvendig.

## Ikke i scope

- Retry-knap / automatisk gen-forsøg — kun manuel gen-tryk (dialogen forbliver åben/klar).
- Detaljeret fejlbesked (fx HTTP-statuskode) i UI — kun generisk "kunne ikke sende"-tekst.
  `HaApiClient.Result.Error(message)` bærer allerede en besked, men den er ikke nødvendig
  for brugeren og undgår at lække tekniske detaljer i en Toast.
- `EntityRepository.command`s eksisterende retry-/stale-logik — uændret.
- Andre steder i appen der kalder `callService` uden resultat-tjek uden for de fem nævnte
  filer (ikke fundet i denne gennemgang — hvis flere findes, er de en separat opgave).

## Test / QA

Følger `CLAUDE.md`s rettelsesworkflow:

1. Byg (`assembleDebug`).
2. Emulator (`pixel_test`): fremtving fejl (fx sluk emulatorens netværk eller ret
   base-URL midlertidigt til noget ugyldigt) for hver af de 5 dialoger → verificér Toast
   vises + dialogen forbliver åben (undtagen DateTimeControl, som skal lukke) + intet
   falsk succes-signal (ingen "isOn"-flip, ingen state-opdatering).
   Gendan derefter netværk/URL og bekræft succes-vejen er uændret (ingen uventet Toast).
3. Device-QA (Galaxy S23, `adb install -r`) — samme scenarier på rigtig enhed.
4. `code-review` før merge.

## Åbne spørgsmål

Ingen — designet er afklaret i brainstorming-samtalen forud for dette dokument.
