# Remote error logging til rtr.dk Log Collector — design

**Dato:** 2026-07-12
**Baggrund:** Guiden i [`docs/ha-widgets-logging.md`](../../ha-widgets-logging.md) beskriver rtr.dk's
Log Collector-endpoint (`POST https://rtr.dk/api/logs`, text/plain body, bearer-token-auth) og et
Flutter/Dart-klientdesign. HA-Widgets er native Kotlin/Android, så designet her er en oversættelse til
appens eksisterende konventioner (OkHttp, `HaApiClient`-mønsteret, `ActionFeedback.kt`-toast-mønsteret),
ikke en 1:1-kopi af Dart-koden.

**Formål:** før Play Store-release skal Claude (udvikleren) kunne modtage fejl- og diagnostik-logs fra
rigtige installationer, uden at skulle bede brugeren (som lige nu kun er dig selv) om at sende logcat
manuelt.

## Arkitektur

Ny fil `app/src/main/java/dk/akait/hawidgets/logging/RemoteLogger.kt` — et Kotlin-objekt (singleton),
strukturelt beslægtet med `HaApiClient` (egen `OkHttpClient`, sealed/Boolean-retur, ingen coroutine-
afhængighed udefra påkrævet for crash-stien).

### Token-håndtering (sikkerhed)

Appen har en dokumenteret hændelse (v0.2.45 i CLAUDE.md): et rigtigt HA-token var hardcoded i
`build.gradle.kts` og lækkede til det offentlige GitHub-repo. Log-upload-tokenet er lavere risiko (kan
kun *skrive* logs, ikke læse HA), men samme mønster undgås:

- Ny nøgle `LOG_UPLOAD_TOKEN` i `local.properties` (allerede git-ignoreret).
- `app/build.gradle.kts` læser den via `Properties()` og eksponerer den som
  `buildConfigField("String", "LOG_UPLOAD_TOKEN", ...)`, default `""` hvis nøglen mangler (så en frisk
  clone/CI stadig bygger — loggeren bliver blot en stille no-op).
- Aldrig committet i klartekst noget sted i git-trackede filer.

### HTTP-detaljer

- `Authorization: Bearer ${BuildConfig.LOG_UPLOAD_TOKEN}`
- `X-App-Id: ha-widgets`
- `X-App-Version: ${BuildConfig.VERSION_NAME}`
- `X-App-Platform: android`
- `User-Agent: HAWidgets-Android/${BuildConfig.VERSION_NAME}` (sat eksplicit — guiden advarer om at
  UnoEuros WAF blokerer generiske/manglende UA'er; OkHttp sætter ikke automatisk en brugbar en)
- `Content-Type: text/plain; charset=utf-8`
- Kort timeout (5s connect/read, matcher `HaApiClient`s "fast"-klient-filosofi)

### Ring-buffer

- `ArrayDeque<String>`, cap 300 linjer (som guidens Dart-design).
- Linjeformat uændret fra guiden: `<ISO-8601 UTC> <NIVEAU> [<TAG>] <besked>` — kun linjer med literalt
  `" E ["` udløser Fejl-status/mail server-side, så niveau-valget pr. kilde (se nedenfor) er vigtigt.
- Første linje i bufferen pr. proces-start (lazy, én gang): enheds-info —
  `I [DEVICE] <manufacturer> <model>, Android <release> (API <sdk>), launcher=<launcher-pakkenavn>`.
  Launcher-pakkenavn findes via `PackageManager.resolveActivity(Intent(ACTION_MAIN).addCategory(HOME))`.
  Ingen andre identifikatorer (intet IMEI/Android-ID/annonce-ID).

## Hvad logges, og hvornår sendes der (build-type: **begge**, debug og release)

### A) Crash-reporting (automatisk, niveau `E`)

`HaWidgetsApp.onCreate()` installerer `Thread.setDefaultUncaughtExceptionHandler`:

1. Skriv `E`-linje (exception-besked) + stacktrace til bufferen.
2. Tilføj widget-config-dump (se afsnit D) — synkron Room-læsning, hurtig lokal SQLite-forespørgsel.
3. Kør et **blokerende** (ikke suspend) OkHttp-POST med kort timeout på selve den crashende tråd.
4. Swallow enhver fejl fra selve uploaden.
5. Kald den oprindelige `defaultUncaughtExceptionHandler` videre (eller `Process.killProcess` hvis der
   ingen var) — så systemets crash-dialog og evt. anden crash-reporting sker helt uændret.

### B) HA-forbindelse/API-fejl (løbende breadcrumbs, niveau `W`)

Instrumenteres ét sted — inde i `HaApiClient` (`checkConnection`, `callService`, `getState`,
`listStatesByDomains`) — fremfor spredt ud i `EntityRepository`/`SyncWorker`. Niveau **`W`**, bevidst
ikke `E`: en droppet forbindelse under periodisk sync er forventet, forbigående støj og skal ikke
udløse jeres mail-alarm. Automatisk flush er throttlet til **1×/30 sek** (guidens anbefaling) så en
længere netværksudfald ikke rammer serverens 10-req/min-loft. Denne rutine-flush inkluderer **ikke**
config-dumpet (kun crash og manuel send gør, se D).

### C) "Send log nu"-knap (manuel, niveau `I` for selve trykket)

Ny række i `MainActivity`s `SettingsSheet`, lige efter Batteri-optimering-rækken (Variant A, se UI
nedenfor). Tryk kalder `RemoteLogger.flush(force = true, includeConfig = true)` på
`HaWidgetsApp.appScope` (overlever at arket lukkes), og viser bagefter en Toast — "Log sendt" eller
"Kunne ikke sende log" — samme mønster som `ActionFeedback.kt`s eksisterende `showActionError`.

**UI (Variant A, brugergodkendt via mockup):** to-linjers tekst (titel "Fejllog" + undertekst "Send
diagnostik-log til udvikleren") + en outline-knap "Send log nu" til højre, strukturelt identisk med
den eksisterende Batteri-optimering-rækkes layout (ikon + `Column(vægt=1f)` med titel/undertekst +
knap). Ingen ny delt komponent nødvendig — kopierer det eksisterende `Row`-mønster direkte (rækken er
enkeltstående, for lille til at retfærdiggøre en `SettingsDropdownRow`-lignende abstraktion).

### D) Widget-config-dump (kun ved crash og manuel send, ikke ved rutine-W-flush)

For at kunne genskabe et widget-setup uden at have adgang til enheden selv, tilføjes en dump af den
aktuelle konfiguration fra Room, **ikke** live HA-data, og **uden** HA-URL/token:

```
I [CONFIG] shortcut widget=12 dashboard=lovelace-hjem
I [CONFIG] multi widget=20 slots=3
I [CONFIG]   slot0 display=light.hue_stuelampe domain=light action=TOGGLE confirm=true showIcon=true
I [CONFIG]   slot0.secondary1 display=sensor.temp action=NONE showValue=true label="Temp"
I [CONFIG]   slot1 display=climate.spa domain=climate action=RANGE target=climate.spa
```

Bygges af en ny funktion `RemoteLogger.dumpWidgetConfig(context): List<String>` der læser
`AppDatabase.get(context).multiWidgetDao()` (widgets + slots + sekundær-kolonner via den eksisterende
`secondaryColumns()`-helper) og `WidgetConfigStore` (ShortcutWidget-dashboard-stier). Kun entity-ID'er,
domæner, handlings-typer og display-indstillinger — intet fra selve HA-forbindelsen.

**Eksplicit fravalgt (bruger-beslutning):** screenshot-vedhæftning. Log Collector-endpointet
understøtter i dag kun tekst; Google Play Store er desuden typisk restriktiv over for automatisk
skærmoptagelse/-upload uden eksplicit brugerinteraktion pr. gang. Config-dumpet dækker samme behov
("kunne rekreere widget-opsætningen") uden de komplikationer.

## Fejlhåndtering / edge cases

- Al netværksfejl i `flush()` swallowes — matcher appens generelle "fire-and-forget crasher aldrig"-
  princip for widget-netværk.
- Tom buffer → `flush()` no-op (server ville give 400 ved tom body).
- Manglende/tomt token (`local.properties` ikke sat) → `flush()` no-op, **ingen** fejl-Toast fra den
  manuelle knap i dette tilfælde (ellers ville knappen altid vise "Kunne ikke sende log" på enhver
  maskine uden nøglen sat, inkl. en frisk clone) — kun Logcat-linje lokalt til synlighed under udvikling.
- 429 (rate-limit) → behandles som "kunne ikke sende", ingen retry-logik i v1.
- Ingen disk-persistering af bufferen (overlever ikke proces-død før flush — accepteret, som guidens
  eget design). Ingen ANR-detektion (kun uhåndterede exceptions). Ingen body-størrelses-trunkering
  udover 300-linjers-loftet (bør aldrig nærme sig 512 KB med denne linjemængde/format).

## Ikke i scope for v1 (kan tilføjes senere uden at ændre arkitekturen)

- Widget-rendering-fejl, Room-migrationsfejl, generel app-lifecycle-breadcrumbs, og den eksisterende
  `HAWeb`-WebView-diagnostik — alt sammen kan senere kalde `RemoteLogger.i/w/e(tag, msg)` direkte.
- Screenshot-vedhæftning (se ovenfor).
- Retry-logik ved fejlet upload.

## Test

- **Unit-tests** (matcher eksisterende mønster, fx `ValueFormatting`/`WidgetSlotStyle`):
  - Ring-buffer-cap (300 linjer, ældste linje falder ud ved linje 301).
  - Linjeformat: ISO-8601-UTC-tidsstempel + niveau-bogstav + `[TAG]` + besked.
  - `dumpWidgetConfig`-serialisering: givet kendte `MultiWidgetEntity`+slots → forventet tekstoutput.
  - Token-tomt-scenarie: `flush()` returnerer `false`/no-op uden exception.
- **Emulator-QA (`pixel_test`):**
  - Sluk netværk → udløs en HA-forbindelsesfejl → verificér `W`-linje i bufferen.
  - Tryk "Send log nu" → verificér `202` i logcat + "Log sendt"-Toast + config-dump med i body.
  - Midlertidig test-crash-knap (fjernes bagefter) → verificér `E`-linje + config-dump sendes, og at
    appens normale crash-adfærd (system-dialog) er uændret bagefter.
- **Device-QA (S23):** samme flow som emulator, samt bekræft build/kørsel uden `LOG_UPLOAD_TOKEN` sat
  (no-op, ingen crash) for at sikre en frisk maskine uden nøglen ikke går i stykker.

## Sammenfatning af beslutninger (fra brainstorming-dialogen)

| Spørgsmål | Valg |
|---|---|
| Token-opbevaring | `local.properties` → `BuildConfig` (ikke hardcoded i git) |
| Log-omfang | Crashes (auto) + HA-forbindelse/API-fejl (auto, `W`) + manuel "Send log nu" |
| Enheds-info-linje | Ja (model, Android-version, launcher) |
| UI-placering | Indstillinger-arket, Variant A (to-linjers tekst + outline-knap), Toast-feedback |
| Build-type | Begge (debug og release) |
| Screenshot-vedhæftning | Fravalgt (Play Store-risiko + intet server-endpoint i dag) |
| Widget-config-dump | Tilføjet — kun ved crash + manuel send, ikke ved rutine-W-flush |
