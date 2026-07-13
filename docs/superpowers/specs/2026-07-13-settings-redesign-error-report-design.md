# Design: Indstillinger-redesign + rig fejlrapportering

**Dato:** 2026-07-13
**Status:** Godkendt af bruger (mockup-gates: sektionsstruktur + rapport-dialog-flow)

## Baggrund

Indstillinger-arket (`SettingsSheet` i `MainActivity.kt`) er vokset organisk (tema → farve → sprog →
batteri → error-log) uden struktur, og brugeren rapporterede at arket "ikke åbner helt" på en rigtig
enhed (Galaxy S23) — "Send log now"-rækken kunne ikke altid ses/nås.

To uafhængige UX-reviews (kørt mod emulator-screenshot + kildekode) identificerede:
- **Reviewer #1 (informationsarkitektur):** arket er 5 rækker uden gruppering — en ren
  personalisering-gruppe (Tema/Farve/Sprog) og en fejlfindings-gruppe (Batteri/Fejllog) blandes uden
  visuel adskillelse.
- **Reviewer #2 (interaktion/tilgængelighed):** roden til "åbner ikke helt" er at `SettingsSheet`s
  `Column` mangler `verticalScroll` (modsat hovedskærmens Column, som har det) kombineret med
  `rememberModalBottomSheetState()`s standard delvist-udfoldet-adfærd. Desuden manglende
  min-touch-target (48dp) på rækkerne, og Toast (ikke Snackbar) for resultat-feedback.

Samtidig skal "Send log now"-knappen (encif, ingen kontekst) erstattes af et rigere
rapport-flow, modelleret efter et tidligere løst mønster fra en Sonos-app: note-felt + Kopiér
log/Annullér/Send rapport, ikke-lukkelig sender-dialog, og et resultat i 4 tilstande. Rapporten skal
også kunne udløses automatisk efter et crash (crash-*logning* sker allerede automatisk i baggrunden
siden v0.2.77 — dette er en NY, synlig UI-invitation til at tilføje kontekst næste gang appen åbnes).

Begge mockups (sektionsstruktur, rapport-dialog-flow) er godkendt af brugeren uden ændringer.

## Omfang

1. **Bugfix:** Indstillinger-arket åbner altid fuldt og er altid scrollbart, uanset skærm/skrifttype.
2. **Redesign:** Indstillinger-arket får 2 sektioner: **Appearance** (Theme, Widget color, Language)
   og **Troubleshooting** (Battery optimization, Report a problem).
3. **Ny "Report a problem"-dialog** (erstatter "Send log now" helt): note-felt (valgfrit, maks 300
   tegn/3 linjer) + 3 knapper (Copy log / Cancel / Send report). Send viser en ikke-lukkelig
   sender-dialog (~15s timeout), derefter et resultat i 4 tilstande via Snackbar.
4. **Auto-trigger ved crash:** hvis forrige proces crashede (opdaget via en persisteret flag +
   log-linjer), åbnes SAMME dialog automatisk ved næste app-åbning, med en tilpasset intro-tekst.
   Kun denne ene tilstand automatiseres nu (ikke "HA-forbindelse fejler" eller "0 enheder fundet" —
   udskudt, kan tilføjes senere som samme mønster).

## Arkitektur

### A. `SettingsSheet`-container (bugfix, ingen visuel ændring udover sektionsheadere)

- `rememberModalBottomSheetState(skipPartiallyExpanded = true)` — arket åbner altid fuldt udfoldet,
  ingen undiscoverable drag-gestus krævet.
- Yderste `Column` får `Modifier.verticalScroll(rememberScrollState())` — sikkerhedsnet uanset
  skærmstørrelse/skriftskalering/sprog (svenske/danske labels er ofte længere end engelske).
- Hver klikbar række (dropdown-rækker + knap-rækker) får `Modifier.heightIn(min = 48.dp)` —
  Android-tilgængeligheds-minimum (samme mønster som `MultiEntityRendering`s a11y-tap-targets).
- Bund-padding skiftes fra hardcodet `padding(bottom = 24.dp)` til
  `.navigationBarsPadding()` + en mindre fast bund-margin, så indhold ikke sidder under
  gesture-nav-baren på enheder uden 3-knap-navigation.
- Ny sektions-header-composable `SectionHeader(text: String)` (samme rolle som hovedskærmens
  `SectionLabel`, men uden ikon — kun label + farve, matcher mockuppens "Appearance"/"Troubleshooting"
  stil): `MaterialTheme.colorScheme.primary`, `labelMedium`, uppercase via strengens egen
  værdi (ikke `textTransform` — strings.xml holder allerede sprogkorrekt store bogstaver hvor
  relevant, samme mønster som `section_connection`/`section_getting_started`).

### B. Sektionsstruktur (indhold uændret pr. række, kun grupperet + "Error log" omdøbt)

```
[SectionHeader: Appearance]
  ThemeRow
  ColorThemeRow
  LanguageRow (API 33+ only, som i dag)
[divider]
[SectionHeader: Troubleshooting]
  Battery-row (uændret)
  "Report a problem"-row (renamed fra "Error log", åbner ny dialog i stedet for at sende direkte)
```

Nye strenge (3 sprog: en/da/sv): `section_appearance`, `section_troubleshooting`. Fjernede strenge:
`log_send_title`, `log_send_subtitle`, `log_send_now`, `log_send_success`, `log_send_failed` (alle 3
sprog) — erstattet af nye `report_problem_*`-strenge (se D).

### C. `RemoteLogger.flush()` — resultat-type udvidet (Boolean → sealed result)

Nuværende signatur (`fun flush(force: Boolean, configLines: List<String>): Boolean`) kan ikke skelne
*hvorfor* en upload fejlede — nødvendigt for de 4 UI-resultat-tilstande. Ændres til:

```kotlin
sealed class UploadResult {
    object Success : UploadResult()
    object NotConfigured : UploadResult()   // intet LOG_UPLOAD_TOKEN på denne build
    object NetworkError : UploadResult()    // IOException, timeout, ingen forbindelse
    data class ServerRejected(val code: Int) : UploadResult() // ikke-2xx HTTP-svar
}

fun flush(force: Boolean = false, configLines: List<String> = emptyList()): UploadResult
```

Eksisterende kaldesteder (`HaApiClient.w/e`'s auto-flush, crash-handleren) ignorerer allerede
returværdien (fire-and-forget) — ren typeændring, ingen adfærdsændring for dem udover mere præcis
intern logging (`Log.d`/`Log.w` kan nu nævne den specifikke årsag).

Ny helper: `fun recentLines(n: Int = 30): List<String>` → `buffer.snapshot().takeLast(n)`, bruges af
"Copy log" (netværksfri, rører ikke throttle/lastFlushAt).

### D. `ReportProblemDialog` — ny genanvendelig composable (ny fil `ReportProblemDialog.kt` i
`dk.akait.hawidgets.logging` eller `dk.akait.hawidgets.ui`, afgøres i plan)

```kotlin
@Composable
fun ReportProblemDialog(
    crashSummary: String?,      // null = menu-trigger; ikke-null = crash-auto-trigger, vises som ekstra intro-linje
    onDismiss: () -> Unit,       // kaldes ÉN gang, uanset Cancel/Send-udfald — rydder evt. pending-crash-flag
    onResult: (UploadResult) -> Unit  // controller viser Snackbar
)
```

Intern tilstandsmaskine (3 skærme, matcher mockup):

1. **NOTE_INPUT** (default): titel = `report_problem_title` ("Report a problem"), body =
   `report_problem_body` normalt, ELLER `report_problem_crash_body` (med `crashSummary` indsat) hvis
   crash-variant. `OutlinedTextField` — 3 linjer, `it.take(300)` on change, `supportingText` viser
   "$len/300". Knapper: `Copy log` (venstrestillet `TextButton`, netværksfri: skriver
   `recentLines(30)` + `collectWidgetConfigDump()` til `ClipboardManager`, viser kort Toast "Copied",
   lukker IKKE dialogen) · `Cancel` (`dismissButton`, kalder `onDismiss()`) · `Send report` (primær
   `confirmButton`, går til SENDING).
2. **SENDING**: `AlertDialog(onDismissRequest = {})` (no-op — ikke-lukkelig), ingen knapper, kun
   `CircularProgressIndicator` + `Text(sending_in_progress)`. Coroutine: `withTimeoutOrNull(15_000)`
   omkring `withContext(Dispatchers.IO) { RemoteLogger.flush(force = true, configLines = noteLine +
   collectWidgetConfigDump(context)) }` — timeout mappes til `UploadResult.NetworkError`.
3. Ved resultat: dialog lukkes (`onDismiss()` kaldes), `onResult(result)` kaldes til controlleren
   (`OnboardingScreen`), som viser Snackbar:
   - `Success` → `report_problem_success` ("Report sent")
   - `NotConfigured` → `report_problem_not_configured` (informativ, IKKE alarmerende — logges ikke
     som "fejl" for brugeren, appen mangler bare en build-time nøgle)
   - `NetworkError` → `report_problem_network_error` + Snackbar-action `retry_action` som genkalder
     samme send (samme note/configLines, ny SENDING-omgang)
   - `ServerRejected(code)` → `report_problem_server_rejected`

Note-linjen sendes som almindelig logline (ingen JSON/struktureret felt — matcher serverens
rene-tekst-kontrakt): `formatLogLine('I', "USER_NOTE", note.trim())`, kun tilføjet til `configLines`
hvis noten ikke er blank.

`ReportProblemDialog` monteres ÉN gang i `OnboardingScreen` (samme mønster som de øvrige
top-level-dialoger: `showDisconnectDialog`, `showBatteryDialog`, `showTokenHelp`), drevet af to
uafhængige `remember { mutableStateOf(...) }`-triggere: `showReportDialog` (menu, sat af
"Report a problem"-rækken i `SettingsSheet` via callback) og en initial værdi udledt af
`store.pendingCrashSummary` ved compose-opstart (se E).

### E. Crash-auto-trigger — persisteret på tværs af proces-genstart

**Problem:** `LogBuffer` er kun i hukommelsen. Efter et crash + app-genstart er den gamle proces'
buffer-indhold (selve crash-linjen + stacktrace) væk — kun `ensureDeviceLine()`s nye linje er der.
Uden persistering ville et "Send report" fra crash-dialogen derfor IKKE indeholde selve crashet.

**Løsning:**
- To nye felter på `SecureStore`: `var pendingCrashSummary: String?` (throwable.toString(), null =
  intet ventende) og `var pendingCrashLog: String?` (buffer.snapshot() på crash-tidspunktet,
  newline-joinet).
- `RemoteLogger.installCrashHandler()`: FØR det eksisterende forsøg på øjeblikkelig `flush(force =
  true, ...)`, persisteres `buffer.snapshot()` + `throwable.toString()` til `SecureStore` (synkront,
  try/catch Throwable — må aldrig forhindre den eksisterende delegering til den rigtige
  crash-handler). Det eksisterende forsøg på øjeblikkelig baggrunds-upload er UÆNDRET (best-effort,
  kan fejle stille ved intet netværk — derfor er persisteringen nødvendig som backup).
- Ny `RemoteLogger.restorePersistedLines(lines: List<String>)`: `lines.forEach(buffer::addRaw)`.
  Kaldes fra `HaWidgetsApp.onCreate()` (efter `ensureDeviceLine`), hvis
  `SecureStore.get(this).pendingCrashLog` er ikke-null — genindsætter den forrige proces' log-linjer
  (inkl. crash+stacktrace) i den NYE proces' buffer, så et efterfølgende `flush()` faktisk sender dem.
- `OnboardingScreen`: ved compose-opstart, hvis `store.pendingCrashSummary != null`, initialiseres
  `showReportDialog`-state til at åbne `ReportProblemDialog` med `crashSummary =
  store.pendingCrashSummary`. Uanset Cancel eller Send (succes ELLER fejl) rydder `onDismiss`-callbacket
  BEGGE `SecureStore`-felter — "spørg kun én gang pr. crash", ingen gentagen nagging på tværs af
  fremtidige app-åbninger hvis brugeren ignorerer/afviser.

### F. Ny streng-oversigt (alle 3 sprog: en/da/sv)

Tilføjes: `section_appearance`, `section_troubleshooting`, `report_problem_row_title`,
`report_problem_row_subtitle`, `report_problem_button` (erstatter `log_send_now`),
`report_problem_title`, `report_problem_body`, `report_problem_crash_body` (med `%1$s`-placeholder
til crash-summary), `report_problem_note_label`, `report_problem_note_counter` (med `%1$d`/`%2$d`),
`copy_log`, `sending_in_progress`, `report_problem_success`, `report_problem_not_configured`,
`report_problem_network_error`, `report_problem_server_rejected`, `retry_action`, `copied_to_clipboard`.

Fjernes: `log_send_title`, `log_send_subtitle`, `log_send_now`, `log_send_success`, `log_send_failed`.

## Fejlhåndtering / edge-cases

- **Ingen token konfigureret (`RemoteLogger.isConfigured() == false`):** "Send report"-knappen kan
  stadig trykkes (ingen forhåndstjek, konsistent med at "Copy log" altid virker) — `flush()` returnerer
  `NotConfigured` med det samme (ingen netværkskald, ingen 15s-ventetid), Snackbar vises informativt.
- **Tom note:** tilladt — `configLines` udelader blot `USER_NOTE`-linjen. Send-knappen er ALDRIG
  disabled af tom note (matcher Sonos-kildens "ikke-blokerende note-felt").
- **Crash-dialog vist, men bruger lukker HELE appen uden at trykke Cancel/Send** (fx home-knap):
  `pendingCrashSummary`/`pendingCrashLog` er IKKE ryddet endnu (kun ryddet i `onDismiss`), så dialogen
  vises igen ved næste rigtige åbning — acceptabelt (ikke en ny nagging-cyklus, samme crash, samme
  én-gangs-tilbud, bare udskudt).
- **Flere crashes før brugeren når at reagere:** `pendingCrashSummary`/`pendingCrashLog` overskrives af
  det SENESTE crash (ingen kø) — kun seneste crash tilbydes rapporteret, ældre tabes. Acceptabelt scope
  for v1 (svarer til hvordan `installCrashHandler` allerede kun kender "den ene aktuelle proces'
  buffer").
- **15s timeout rammes:** behandles som `NetworkError` (med Retry-action), selvom request'et i
  virkeligheden stadig kører i baggrunden på OkHttp-siden — acceptabelt (samme `flush()`-blokerende
  kald bruges allerede af crash-handleren uden dette problem, fordi der intet UI er at holde åbent der).

## Ikke i scope (bevidst udskudt)

- Auto-trigger ved "HA-forbindelse fejler" eller "0 enheder fundet i entity-picker" — samme mønster
  kan genbruges senere, men kræver at definere PRÆCIST hvilken tilstand der tæller (ikke afklaret nu).
  Kun crash-auto-trigger er i scope denne omgang (brugerens eksplicitte valg).
- Skærmbillede-vedhæftning til rapporten (allerede fravalgt under research af selve
  logging-featuren, v0.2.77 — Play Store-risiko + intet server-endpoint).
- Ændringer til hovedskærmens "Connection"/"Getting started"-sektioner — uden for scope, de ligger
  allerede uden for Indstillinger-arket og er ikke del af denne redesign.

## Test

- Unit-tests: `RemoteLogger`s nye `UploadResult`-gren (success/not-configured/network-error/
  server-rejected — mockbar via en injicerbar `OkHttpClient` eller ved at teste de rene grene der
  ikke kræver et rigtigt netværkskald, fx `NotConfigured` når token er blank). `recentLines(n)`
  (ren funktion på `LogBuffer`, allerede delvist dækket af eksisterende `LogBufferTest`).
- Emulator-QA: sektionsstruktur renderer korrekt (screenshot-diff mod mockup), arket åbner fuldt
  første gang (ingen delvist-udfoldet-tilstand), scroll virker ved kunstigt forstørret skrifttype
  (Android "Font size"-tilgængeligheds-indstilling, for at fremtvinge overflow uden en rigtig lille
  enhed), Report-dialogens 3 skærme (note→sending→resultat) gennemgås for alle 4 resultat-tilstande
  (kan fremtvinges: sluk netværk for NetworkError, midlertidigt ugyldigt token for ServerRejected(403),
  tomt `LOG_UPLOAD_TOKEN`-build for NotConfigured), Copy log verificeres via clipboard-indhold (adb
  `service call clipboard` eller ved at paste i et tekstfelt), crash-auto-trigger verificeres ved
  `adb shell am crash` efterfulgt af app-genåbning.
- Device-QA (Galaxy S23): bekræfter selve bug-rapporten er løst (arket åbner fuldt, Report a
  problem-rækken altid synlig/nåbar).
