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
- Mekanisme: AndroidX **per-app language API**, `AppCompatDelegate.setApplicationLocales(LocaleListCompat)`.
  - API 33+: systemet persisterer valget.
  - API 26–32 (app's minSdk er 26): AppCompat persisterer internt selv.
  - "Følg system" = `LocaleListCompat.getEmptyLocaleList()`.
  - Ingen custom `SecureStore`-felt, ingen `attachBaseContext`-override nogen steder —
    AppCompat recreate'r automatisk alle åbne Activities ved locale-skift.

**Fravalgte tilgange:**
- Manuel Context-wrapping (`attachBaseContext` per Activity) — mere kode, fejlbarlig.
- Global `Locale.setDefault()` — persisterer ikke korrekt over proces-genstart.

## UI

Ny sektion i `MainActivity` (connected-state, ved siden af eksisterende batteri-knap):
4 valg — **Dansk / English / Svenska / Følg system**. Valget anvendes med det samme
(ingen restart krævet — AppCompat recreate'r Activity og genindlæser ressourcer).

## Data-flow

1. Bruger trykker sprog-valg i `MainActivity`.
2. `AppCompatDelegate.setApplicationLocales(...)` kaldes.
3. AppCompat persisterer valget + recreate'r alle åbne Activities.
4. Resources genindlæses med ny qualifier (`values-da`/`values-sv`/default `values`).
5. Ved koldstart efter valg: AppCompat injicerer gemt locale automatisk før
   `attachBaseContext` — ingen ekstra boilerplate nødvendig.

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
