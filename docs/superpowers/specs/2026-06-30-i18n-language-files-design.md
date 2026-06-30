# i18n: sprogfiler (dansk/engelsk/svensk)

**Dato:** 2026-06-30
**Status:** Godkendt af bruger, klar til implementeringsplan

## Baggrund

App blev i v0.2.9–v0.2.12 lavet 100% dansk (fjernede `values-da`/`values-sv`) med
begrundelsen "ingen reel i18n-ambition, kun én bruger". Beslutningen reverseres nu:
appen skal kunne deles med flere brugere fremover, der ikke nødvendigvis taler dansk.

## Scope

Native app-UI (onboarding, widget-config-skærme, picker, `RangeControlActivity`,
øvrige `strings.xml`-baserede tekster) — alle 59 nuværende strenge.

**Eksplicit ikke i scope:** WebView/HA-dashboard-indhold. Dashboardets sprog styres af
HA-serveren selv, helt uafhængigt af app-localen.

## Arkitektur

- `res/values/strings.xml` (qualifier-løs, default/fallback) bliver **engelsk**.
  Nuværende danske indhold flyttes til ny `res/values-da/strings.xml`.
- Ny `res/values-sv/strings.xml` oprettes — alle 59 strenge oversat til svensk.
- Mekanisme: **platform `android.app.LocaleManager`** direkte (API 33+), ingen ny dependency.
  - `context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("da")`.
  - "Følg system" = `LocaleList.getEmptyLocaleList()`.
  - Systemet persisterer valget og recreate'r alle åbne Activities automatisk — virker for
    enhver Activity-type (ingen AppCompatActivity/AppCompat-tema krævet).
  - Ingen custom `SecureStore`-felt, ingen `attachBaseContext`-override nogen steder.

**Fravalgte tilgange:**
- `AppCompatDelegate.setApplicationLocales` (AndroidX-backport) — kræver `androidx.appcompat`-
  dependency + at Activity er `AppCompatActivity`/AppCompat-tema for at virke på API <33.
  Appen bruger ren `ComponentActivity` + Compose `MaterialTheme`. Da scope alligevel er
  begrænset til API 33+ (se nedenfor), giver ren platform-API samme resultat med
  **ingen ny dependency og ingen Activity-migrering** — simplere.
- Manuel Context-wrapping (`attachBaseContext` per Activity) — mere kode, fejlbarlig.
- Global `Locale.setDefault()` — persisterer ikke korrekt over proces-genstart.

**Kendt begrænsning:** `LocaleManager` findes kun fra API 33. Sprog-skift virker fuldt på
**API 33+** (dækker emulator `pixel_test` API 34 og Galaxy S23 Android 14+ — alle nuværende
testenheder). På API 26–32 er sprog-valget et no-op (forbliver på device-locale). minSdk
forbliver 26; dette er en accepteret begrænsning.

## UI

Ny sektion i `MainActivity` (connected-state, ved siden af eksisterende batteri-knap):
4 valg — **Dansk / English / Svenska / Følg system**. Valget anvendes med det samme
(ingen restart krævet — systemet recreate'r Activity og genindlæser ressourcer).
Sektionen vises kun når `Build.VERSION.SDK_INT >= 33` (ellers ingen funktionel effekt,
jf. kendte begrænsning).

## Data-flow

1. Bruger trykker sprog-valg i `MainActivity`.
2. `LocaleManager.setApplicationLocales(...)` kaldes (kun udført hvis `Build.VERSION.SDK_INT >= 33`).
3. Systemet persisterer valget + recreate'r alle åbne Activities.
4. Resources genindlæses med ny qualifier (`values-da`/`values-sv`/default `values`).
5. Ved koldstart efter valg: systemet anvender gemt locale automatisk — ingen ekstra
   boilerplate nødvendig.

## Fejlhåndtering

Ingen særskilt fejlhåndtering nødvendig — ren system-API, ingen I/O, ingen netværk,
hardcodede locale-tags (`"da"`, `"en"`, `"sv"`) kan ikke fejle på ugyldig input.

## Test

Intet automatiseret testsuite i projektet i dag (tilføjes kun ved eksplicit
bruger-ønske). Følger projektets normale QA-loop (jf. `CLAUDE.md`):

1. Byg: `./gradlew assembleDebug`.
2. QA på `pixel_test`-emulator: skift gennem alle 4 sprog-valg, verificér korrekt
   UI-tekst pr. skærm, verificér persist efter app-genstart, verificér
   WebView/HA-dashboard er upåvirket.
3. QA på rigtig enhed (Galaxy S23): `adb install -r` (aldrig uninstall), samme flow.
4. Commit + push kun når begge QA-trin er grønne.

## Ikke-mål

- Ingen ændring af WebView/HA-dashboard-sprog.
- Ingen ny separat Indstillinger-skærm (sprog-valg lever i `MainActivity`).
- Ingen automatiseret test-infrastruktur tilføjes som del af denne feature.
