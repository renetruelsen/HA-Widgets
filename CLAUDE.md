# ha-widgets

Native Android-app der giver Home Assistant (HA) home screen widgets. "Appen lever i widgets."

## Arkitektur (kort)

- **Sprog/UI:** Kotlin, Jetpack Compose (app), Jetpack Glance (widgets).
- **Auth (M1):** long-lived access token (LLT) gemt i EncryptedSharedPreferences (AndroidKeyStore).
  Token aldrig i WebView-storage. WebView får token i hukommelsen via HA's external-auth JS-bro.
  OAuth/IndieAuth udskudt til M3 (kræver offentlig https client_id-side).
- **Render:** hybrid — live/små værdier native fra JSON-cache (M2); rig dashboard-visning i WebView (live).
- **Offline:** widgets tegner fra cache + staleness (M2). **Strøm:** native fetch frem for WebView-render;
  WorkManager + push frem for polling (M2/M3).

Fuld plan: `C:\Users\rtr\.claude\plans\du-m-gerne-tale-mossy-kazoo.md`.

## Pakke / org

- applicationId / namespace: `dk.akait.hawidgets`
- minSdk 26, targetSdk/compileSdk 35.

## Status

### M1 — Forbindelse, LLT-auth, WebView (under udvikling)
- ✅ Projekt-scaffold: Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, Glance 1.1.1.
- ✅ `SecureStore` (EncryptedSharedPreferences): base-URL + LLT + per-widget dashboard-sti.
- ✅ `HaApiClient.checkConnection()` validerer mod `/api/` (OkHttp).
- ✅ Onboarding (`MainActivity`, Compose): indtast URL + LLT, valider, gem; "Åbn dashboard"-test; frakobl.
- ✅ `WebViewActivity` + `ExternalAuthBridge`: implementerer HA's external-auth-kontrakt
  (`window.externalApp.getExternalAuth/revokeExternalAuth` → `window["externalAuthSetToken"](true, {access_token, expires_in})`).
- ✅ `ShortcutWidget` (Glance) + config-activity: åbner valgt dashboard ved tryk.
- ✅ Verificeret på emulator (`pixel_test`): build OK, `adb install -r` OK, app starter uden crash,
  onboarding-UI renderer korrekt (URL-felt, token-felt, Forbind). (2026-06-25)
- ✅ **End-to-end mod rigtig HA (https://home.rtr.dk:8123) verificeret (2026-06-25):**
  onboarding → `/api/`-validering OK; "Åbn dashboard" → WebView logger ind via external-auth-broen
  UDEN login-side; WebSocket `connection-status: connected`; live "Overblik"-dashboard renderer med
  ægte entiteter + realtidsdata. Token aldrig i web-storage.
- ✅ **Webview-feature udvidet + verificeret mod rigtig HA (2026-06-25):**
  - Dashboard-vælger i config: henter listen via WebSocket `lovelace/dashboards/list`
    (`HaWebSocketClient`) — alle 5 dashboards vist (Overblik, Hjem, F4, Stue, Linus Dashboard).
  - **Kiosk mode** (plugin-frit): `KioskScript` går rekursivt gennem alle shadow roots og injicerer
    scoped CSS i `hui-root` (skjul header) + `home-assistant-main` (skjul sidebar). Verificeret: header væk.
  - **Visning pr. widget:** Fuldskærm ELLER Overlay (sized vindue, konfigurerbar bredde/højde %, dæmpet
    baggrund, luk-knap + tryk-udenfor). Begge verificeret.
  - **Pre-warm + cache:** `HaWidgetsApp` forvarmer Chromium; WebView `cacheMode=LOAD_DEFAULT` + HA's
    service worker → hurtig genåbning. Ingen vedvarende forbindelse (batterivenligt).
- ✅ **Genvej-model strammet op (2026-06-25, efter brugerfeedback):**
  - Dashboard-genvej = ikon-tile, ikke en data-widget — "bare en genvej til webview".
    (⚠️ "1x1 fast tile, `resizeMode=none`"-delen af denne beslutning reverseret i v0.2.15 —
    se dér.) Overlay-*vinduets* størrelse er stadig konfigurerbar (sliders); det er adskilt
    fra tile-størrelsen.
  - In-app **"Tilføj dashboard-genvej til hjemskærm"** via `requestPinAppWidget` + manuel vejledning som
    fallback (robust mod launchere uden pin-dialog).
  - Ukonfigureret tile-tryk → åbner config-skærm; konfigureret → åbner dashboard.
  - Adressefelt **forudfyldt** med `http://homeassistant.local:8123`.
  - In-app test-knapper **fjernet**.
  - Overlay flyder nu over **hjemskærmen** (WebViewActivity: `taskAffinity=""` + `excludeFromRecents`);
    "overlay over app" sås kun pga. de nu-fjernede in-app testknapper.
- ⬜ Faktisk placeret tile + tryk-åbning testet på rigtig enhed (installeret på Galaxy S23 til brugertest).

### M2 — Native entity-widgets (FÆRDIG 2026-06-29)
- ✅ **8 nye widget-typer implementeret:** switch, scene, script, automation, sensor, binary_sensor, weather (2×1), climate (2×1).
- ✅ **Fælles infrastruktur:** `GlanceWidgetCommon` (compact=icon+label+status, wide=icon+label+status), `BaseEntityPickerActivity`, `EntityActions` (Toggle/Trigger/Refresh callbacks).
- ✅ **Room reaktiv Flow:** alle widgets bruger `flatMapLatest` + `collectAsState` — Nova/Samsung placement quirk håndteret.
- ✅ **SyncWorker:** `runNow()` ved config-save + widget-tap; `schedule()` 15-min periodisk sync.
- ✅ **UX:** compact label+status (56dp, 10sp/11sp), valgfrit kort label i config (maks 12 tegn), scene "Aktiverer…" optimistisk feedback, script tap-disabled mens kører, automation "Udløs" CTA compact, weather compact=temp-only, climate wide=temp/mode split, read-widgets tap=refresh.
- ✅ **Widget-picker (v0.2.3):** domain-specifik `previewImage` + korte beskrivelser i `strings.xml`.
- ✅ **QA på emulator (pixel_test, 2026-06-29):** alle 10 providers i AppWidgetManager, alle 8 config activities åbner med korrekte HA-entiteter, alle states synket, LightWidget tap-toggle "Slukket"→"Tændt" via Room Flow, ingen crashes.
- ✅ **LightWidget spec-compliance (v0.2.4):** `LightWidgetConfigActivity` omskrevet til `BaseEntityPickerActivity`-subklasse (Screen 1 + Screen 2, korrekte chips, label-input). `LightWidget` bruger nu `WidgetCompactLayout`/`WidgetWideLayout` fra `GlanceWidgetCommon` → compact viser icon+label+status som spec. Widget-navne (`android:label`) på alle receivers i manifest. Rekonfiguration pre-fill: eksisterende entity + label vises direkte på Screen 2.
- ✅ **v0.2.5 (2026-06-29):**
  - **Strømspar-dialog:** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + `LaunchedEffect` i `MainActivity` → forklarings-dialog → system-dialog vises automatisk ved connect hvis ikke fritaget.
  - **SensorWidget dynamisk ikon:** `device_class` læses fra attributesJson → temperatur=termometer, fugt=vanddråbe, strøm/energi/spænding=lynnedslag, alt andet=søjlediagram (ic_sensor).
  - **Vejrudsigt-widget fjernet** (WeatherWidget, WeatherWidgetReceiver, strings, manifest, ic_weather.xml).
  - **LightWidget lysstyrke-slider:** wide (2×1) tap → åbner `RangeControlActivity` (dialog) med slider 1–100% + tænd/sluk-knap. Compact (1×1) beholder tap=toggle.
  - **Nyt CoverWidget:** `cover`-domæne, compact/wide layouts, tap → `RangeControlActivity` med position 0–100% + Åbn/Luk-knap. `cover_widget_info.xml`, `ic_cover.xml` (persienne-striber).
- ✅ **v0.2.6 (2026-06-29):**
  - **Tema-fix:** `ExternalAuthBridge.replyExternalConfig` sender `themes: {darkMode}` til HA-frontend. `WebViewActivity` beregner `isDark` for alle `ColorScheme`-værdier inkl. SYSTEM (via `Configuration.UI_MODE_NIGHT_MASK`). ThemeScript kører nu for SYSTEM-mode også.
  - **"Opsæt"-flash fix:** Alle 9 entity-widgets pre-loader `initialCfg`/`initialState` fra Room FØR `provideContent {}` → ingen flash til "Opsæt" under refresh.
  - **RefreshEntityAction specifik:** ActionParameters med entityId → kun tappet entity refreshes. SensorWidget + BinarySensorWidget + ClimateWidget compact opdateret.
  - **ClimateWidget styrbar:** Wide tap åbner `RangeControlActivity` med temperatur-slider (min/max fra HA attrs, default 16–30°C) + Tænd/Sluk. Bruger `climate.set_temperature` + `climate.turn_on/off`.
  - **Cover UX:** "Luk" → "Luk helt", "Åbn" → "Åbn helt". Bred Luk-knap fjernet (hjemknap/back lukker).
  - **LightWidget brightness-guard:** Wide slider kun for lys med brightness-support (`supported_features & 1` eller `brightness`-attr). Ikke-dimmable: wide = toggle.
  - **Batteri-administrér:** Connected-state viser "Batterioptimering"-knap + fritaget/begrænset status → åbner `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.
  - **RangeControlActivity:** `EXTRA_MIN_VALUE`/`EXTRA_MAX_VALUE` for dynamisk range. Climate-domain tilføjet.
- ✅ **v0.2.8:**
  - **LightWidget dimmable-detektion:** `supported_color_modes` (moderne HA) tilføjet ud over
    `supported_features`-bit — lys der kun rapporterer color_modes blev fejlagtigt vist som ikke-dimmable.
    Compact og wide tap åbner nu konsekvent lysstyrke-slider for alle dimmable lys.
- ✅ **v0.2.9–v0.2.12 — UX-review af widgets + app (2026-06-30):**
  - **Baggrund:** bruger oplevede widgets som "rodet" (forkert størrelse/placering) og appen som blandet
    sprog. UX-review kørt med screenshots på `pixel_test` før/efter, 3 beslutninger afklaret med bruger.
  - **Entity-widget state-farve:** tændt/aktiv tilstand (light/switch/scene/script/automation/
    binary_sensor/cover/climate) skiftet fra pastel `primaryContainer`/`onPrimaryContainer` til solid
    `primary`/`onPrimary` — tændt/slukket var næsten umuligt at skelne visuelt. Sensor (ingen on/off)
    uændret.
  - **Compact layout tættere på cellen:** ikon 20→26dp, status 11→13sp + Medium-vægt — undgår "tom/
    svævende" udseende i den faktiske (større) launcher-celle.
  - **ShortcutWidget (dashboard-genvej):** label vises nu som separat tekstlinje under ikonet (ikke bagt
    ind i ikongrafikken), og tilen fylder igen hele cellen kvadratisk — samme bredde som nabo-widgets.
  - **App 100% dansk:** `values-da`/`values-sv` fjernet, default `strings.xml` er nu dansk (var blandet
    engelsk/dansk — ingen reel i18n-ambition, kun én bruger).
  - Spec opdateret: `docs/widget-settings-spec.md`.
- ✅ **v0.2.13 — i18n genindført: dansk/engelsk/svensk (2026-07-01):**
  - **Baggrund:** v0.2.9-beslutningen ("kun én bruger") reverseret — appen skal fremover deles med
    flere brugere, der ikke nødvendigvis taler dansk. Proces: brainstorming → spec →
    plan → subagent-driven implementering → per-task review → device-QA → whole-branch review.
    Spec: `docs/superpowers/specs/2026-06-30-i18n-language-files-design.md`,
    plan: `docs/superpowers/plans/2026-06-30-i18n-language-files.md`.
  - **Sprogfiler:** `res/values/strings.xml` (qualifier-løs default) er nu **engelsk**. Dansk
    flyttet til `res/values-da/strings.xml`. Nyt `res/values-sv/strings.xml`. Alle 64 strenge
    (59 eksisterende + 5 nye til sprog-vælgeren) oversat i alle tre filer.
  - **Sprog-vælger:** dropdown (Material3 `ExposedDropdownMenuBox`) i `MainActivity`
    (connected-state, ved batteri-knappen) — Dansk/English/Svenska/Følg system. Bruger
    platform `android.app.LocaleManager` direkte (ikke `AppCompatDelegate`) — **kun API 33+**,
    ingen ny dependency (appen har ingen AppCompat/`attachBaseContext`). På API 26–32 er
    valget et no-op (accepteret begrænsning, minSdk forbliver 26). Oprindeligt designet som
    4 knapper, ændret til dropdown efter brugerønske under implementering.
  - **QA (2026-07-01):** verificeret på `pixel_test`-emulator (API 34) og Galaxy S23 (API 36,
    `R3CWC00JY4M`) — sprog-skift, persist efter genstart, "Følg system"-rundtur, ingen crashes,
    WebView/HA-dashboard upåvirket.
  - **Fund undervejs:** dropdown-versionens `options.first { }`-opslag kunne kaste
    `NoSuchElementException` hvis systemet rapporterer et locale-tag udenfor
    {null, "da", "en", "sv"} (f.eks. sprog sat via Android-systemindstillinger udenom
    denne picker) — rettet til `firstOrNull { } ?: fallback`.
  - **WebView/HA-dashboard sprog er IKKE påvirket** — styres fortsat af HA-serveren selv,
    helt uafhængigt af app-localen.
- ✅ **v0.2.15 — ShortcutWidget gjort resizable (2026-07-01, efter brugerfeedback):**
  - **Baggrund:** v0.2.9-beslutningen "Dashboard-genvej = fast 1x1 tile (`resizeMode=none`)"
    reverseret — brugeren oplevede at launcheren (Galaxy S23/One UI) alligevel lod widgetten
    ændre størrelse (2x2 vs 4x4), men ikon/tekst var hardcodet og skalerede ikke med, så
    tilen så "klemt"/forkert ud ved mindre størrelser.
  - `shortcut_widget_info.xml`: `resizeMode="none"` → `"horizontal|vertical"`,
    `minWidth`/`minHeight` 80dp → 110dp (≈2 grid-celler, formel `70*n-30`),
    nyt `minResizeWidth`/`minResizeHeight=110dp` (kan ikke formindskes under ~2x2),
    `targetCellWidth`/`targetCellHeight` 1 → 2.
  - `ShortcutWidget.kt`: fjernet den indre `.padding(4.dp)` helt — den blå boks fylder nu
    hele den tildelte plads kant-til-kant (uændret: launcher-grid-gutter mellem nabo-widgets
    er launcher-styret, ikke app-styret). Ikon- og tekststørrelse (samt spacer) skalerer nu
    kontinuerligt med `LocalSize.current` (`SizeMode.Exact`) i stedet for hardcodet
    28dp/11sp, med clamps så det ikke bliver absurd stort/lille ved ekstreme størrelser.
- ✅ **v0.2.16 — ShortcutWidget gjort strukturelt IDENTISK med entity-widgets (2026-07-01,
  efter brugerfeedback):**
  - **Baggrund:** v0.2.15 løste skalering/padding, men genvejen opførte sig stadig
    mærkeligt på Nova Launcher. Undersøgelse viste at ShortcutWidget var den ENESTE
    widget i appen der brugte `SizeMode.Exact` + en unik `minResizeWidth`/
    `minResizeHeight`-attribut og et 110dp/2x2-minimum — alle andre widgets
    (light/switch/scene/sensor/climate osv.) bruger `SizeMode.Responsive` med et fast
    sæt størrelser, `minWidth`/`minHeight=56dp` (1x1), `maxResizeWidth`/`Height`, og
    ingen `minResizeWidth`/`Height`. Da entity-widgets er verificeret til at fungere
    fint på Nova, blev ShortcutWidget lavet strukturelt identisk med dem i stedet for
    at beholde sin egen unikke sizing-strategi.
  - `shortcut_widget_info.xml`: `minWidth`/`minHeight` 110dp → 56dp,
    `targetCellWidth`/`targetCellHeight` 2 → 1, `minResizeWidth`/`minResizeHeight`
    fjernet helt, nyt `maxResizeWidth`/`maxResizeHeight=250dp` (kvadratisk loft,
    symmetrisk fordi genvejen altid skal forblive kvadratisk — i modsætning til fx
    climate der kun er bred, ikke høj).
  - `ShortcutWidget.kt`: `sizeMode` skiftet fra `SizeMode.Exact` til
    `SizeMode.Responsive` med fire kvadratiske buckets (56/110/180/250dp), samme
    mekanisme som `LightWidget`/`ClimateWidget`. Den proportionale ikon/tekst-skalering
    fra v0.2.15 (baseret på `LocalSize.current`) er bevaret uændret — `LocalSize.current`
    matcher nu bare altid én af de deklarerede buckets i stedet for en vilkårlig
    kontinuerlig værdi.
  - Minimum/default-placeringsstørrelse er dermed 56dp/1x1 igen (som resten af
    widget-familien), ikke 110dp/2x2 som i v0.2.15.
  - **Superseret af v0.2.17** — kvadratisk `maxResizeWidth/Height=250dp` og den
    bevarede kontinuerlige ikon/tekst-skalering viste sig stadig at afvige visuelt fra
    entity-widgets ved direkte side-by-side-sammenligning på Nova (se v0.2.17).
- ✅ **v0.2.17 — ShortcutWidget genbruger de FAKTISKE compact/wide-layouts (2026-07-01,
  efter direkte side-by-side-sammenligning med LightWidget på brugerens forespørgsel):**
  - **Baggrund:** v0.2.16 matchede XML-attributter og `SizeMode`, men brugeren bad om at
    resize genvejen og en entity-widget til samme størrelse og sammenligne. Det viste en
    reel forskel: LightWidget bruger et FAST compact/wide-layout (fast 26/28dp ikon,
    fast padding) der bevidst IKKE fylder boksen ved store størrelser — mens
    ShortcutWidgets bevarede kontinuerlige skalering gjorde noget andet (intet loft på
    højden, forsøgte at fylde boksen). To forskellige visuelle paradigmer, uanset ens
    XML-tal.
  - **Fix:** `ShortcutWidget.kt` smed al bespoke skalerings-kode (proportional
    ikon/tekst/spacer via `LocalSize.current`) væk og genbruger nu `WidgetCompactLayout`/
    `WidgetWideLayout` fra `GlanceWidgetCommon.kt` 1:1 — samme funktioner som
    Light/Switch/Scene/Sensor/Climate osv. kalder. `label` = dashboard-titel,
    `statusText` = tom streng (genveje har ingen live tilstand). Uconfigureret tilstand
    bruger nu også `UnconfiguredWidgetContent` (samme "Opsæt"-visning som alle andre
    widgets) i stedet for sin egen ikon-uden-tekst-variant.
  - `shortcut_widget_info.xml`: `sizeMode`-buckets ændret til `56x56dp`/`110x56dp`
    (samme to buckets som `LightWidget`), `maxResizeHeight` 250dp → 120dp (matcher
    `light_widget_info.xml` præcist — ikke længere kvadratisk-specifikt), tilføjet
    `updatePeriodMillis="0"` for fuld paritet med de øvrige widget-info-filer.
  - Verificeret på Nova: resize-håndtag fungerer nu pålideligt (brugte
    `uiautomator dump` til at finde præcise handle-koordinater, da visuelt gættede
    koordinater fra screenshots ofte ramte forkert — Nova's resize-menu-rækkefølge
    varierer). Side-by-side sammenligning viser nu identisk adfærd: fast ikon+tekst i
    en række, ingen skalering udover boksen, samme tomrum-ved-oversize-svaghed som
    resten af familien.
- ✅ **v0.2.18 — ikon/tekst vertikalt centreret ved 1x1 (2026-07-01, efter
  brugerfeedback):**
  - **Baggrund:** Efter v0.2.17 sad ikon+label en smule for højt i den kompakte (56dp)
    boks i stedet for centreret. Årsag: `ShortcutContent` kalder `WidgetCompactLayout`/
    `WidgetWideLayout` med `statusText = ""` (genveje har ingen live tilstand) — men en
    tom `Text` reserverer stadig en linjes højde i `Column`/`Row`'et, så
    "centrér-blokken" inkluderede en usynlig tom linje. Det gjorde blokkens midte
    lavere end den synlige tekst, og skubbede det synlige indhold visuelt opad.
  - `GlanceWidgetCommon.kt`: `WidgetCompactLayout` og `WidgetWideLayout` springer nu
    status-`Text`'en helt over når `statusText` er tom, i stedet for at rendere en
    usynlig linje. Ingen ændring for entity-widgets (de sender aldrig tom statusText).

## Næste skridt

- v0.2.6 QA på rigtig enhed (Galaxy S23): `adb install -r`, test:
  - Tema (mørk/lys/auto) virker korrekt på dashboards
  - Ingen "Opsæt"-flash på widgets under refresh
  - Tap på sensor/klima-widget refresher KUN den ene widget (ikke alle)
  - ClimateWidget wide → temperatur-slider åbner med korrekt range
  - CoverWidget: "Luk helt"/"Åbn helt", ingen Luk-knap
  - LightWidget: dimmable lys → slider, ikke-dimmable → toggle
  - Batteri-knap viser status + åbner indstillinger korrekt
- **Deferred:** Værdisensor med flere entiteter (op til 3-5) — kræver ny Room-kolonne + config-UI + widget-layout; separat opgave.
- M3: OAuth/IndieAuth, push-notifikationer (FCM), network-security-config pr. host.

### Åbne UX-problemer

_Alle kendte UX-problemer løst. Kanonisk spec i [`docs/widget-settings-spec.md`](docs/widget-settings-spec.md)._

- v0.2.3: compact label + picker previewImage/beskrivelser
- v0.2.4: widget-navne i picker (android:label på receivers) + rekonfiguration pre-fill + LightWidget spec-compliance
- v0.2.5: strømspar-dialog, sensor dynamisk ikon, fjern vejr, lysstyrke-slider, cover-widget

## Workflow: rettelser og release

**UX-ændringer:** følg [`docs/ux-process.md`](docs/ux-process.md). Spec for widget config-flow og display: [`docs/widget-settings-spec.md`](docs/widget-settings-spec.md).

**Aldrig meld "fikset" uden bevis.** Rettelsesworkflow er altid iterativt:

1. **Fix i kode** — ret fejlen.
2. **Byg** — `./gradlew assembleDebug`.
3. **QA på emulator** (`pixel_test`) — driv det faktiske flow via `adb shell input`, screenshots, DB-inspektion.
   Virker det ikke → tilbage til trin 1. Bliv i loopet til testen er grøn.
4. **QA på telefon** (`adb install -r`, ALDRIG uninstall) — bekræft samme flow på rigtig enhed.
5. **Commit + push** — kun når begge QA-trin er grønne.

`code-review` køres inden merge til main.

## Build & install

```
# Byg (JDK17 kræves)
JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug
# Installer SOM REINSTALL (bevarer data) — aldrig uninstall:
<SDK>/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Kendte quirks / beslutninger

- `usesCleartextTraffic=true` i M1 fordi lokale HA-instanser ofte er `http://`. Strammes senere
  (network-security-config pr. host) i takt med OAuth/TLS-arbejdet.
- **External-auth kræver MERE end token:** når `window.externalApp` injiceres, behandler HA frontend
  os som fuld companion-app og sender `config/get` over external-bus under bootstrap — og **blokerer
  ("Loading data") indtil vi svarer** via `window.externalBus(<objekt>)`. Vigtigt: svaret skal være et
  JS-**objekt**, ikke en JSON-streng (streng → `Received unknown msg ID undefined`). `ExternalAuthBridge`
  besvarer `config/get` med alle capabilities = false. Øvrige bus-beskeder (theme-update, connection-status,
  haptik, matter) ignoreres i M1.
- **WebView ≠ Chrome / versionsgab:** "Android System WebView" er en separat pakke, ofte ældre end Chrome.
  `pixel_test`-emulatoren (android-34 google_apis) har WebView **113** (maj 2023) → moderne CSS mangler
  (deraf `popover`-advarsel + custom cards som Bubble/ApexCharts/Sonos renderer anderledes end brugerens
  Chrome ~148). Rigtige enheder auto-opdaterer WebView via Play. **Til rendering-parity oprettes en ny
  AVD med Play Store-image** (`system-images;android-35;google_apis_playstore;x86_64`) hvor brugeren
  logger ind og opdaterer WebView. Funktionstests kan stadig køre på den gamle AVD.
- WebView sat til browser-parity: `useWideViewPort=true` + `loadWithOverviewMode=true` (honorér `<meta viewport>`).
- Diagnostik-logging (tag `HAWeb`) logger bus-beskeder + auth-callback-navne (IKKE selve token). Bør
  gates bag debug/fjernes før release.
- Emulatorens AOSP-launcher viser ikke `requestPinAppWidget`-dialogen (returnerer `supported=true` men
  no-op). Test pin-knappen på rigtig enhed (One UI/Pixel). Manuel widget-tilføjelse virker på emulator.
- **Nova Launcher + `resizeMode="none"` (historisk, forældet af v0.2.15):** i modsætning til
  AOSP-launcheren (som strækker widgets til hele grid-cellen) tildelte Nova et
  ikke-resizeable widget nøjagtigt de deklarerede `minWidth`/`minHeight` fra widget-info-xml'en
  — ikke cellestørrelsen. `ShortcutWidget`s indhold (ikon+label i en `cornerRadius`-clippet Box)
  blev derfor beskåret på Nova ved `minWidth/minHeight=40dp` (label helt usynlig), selvom
  emulatoren viste det fint. v0.2.15 gjorde widgetten reelt resizable (`resizeMode="horizontal|vertical"`,
  `minWidth/minHeight=110dp`) med indhold der skalerer via `LocalSize.current` — omgår problemet
  ved at lade layoutet tilpasse sig den faktiske tildelte størrelse i stedet for at gætte en fast
  størrelse. Verificér altid ShortcutWidget-ændringer på en Nova- og/eller One UI-enhed.
- `KioskScript`-selektorer (`.header`, `hui-root`, `home-assistant-main`) er HA-versionsfølsomme;
  den rekursive shadow-root-tilgang er robust mod sti-ændringer, men selektor-navne kan kræve justering
  ved store frontend-opdateringer.
- WebViewActivity bruger translucent tema (`Theme.HaWidgets.Translucent`) for at understøtte overlay-dim.
- **Ingen splash ved widget-klik:** translucent-temaet sætter `windowDisablePreview=true` +
  `windowAnimationStyle=@null`, så Android springer start-vinduet/splashet over. Bivirkning: ved kold
  proces-start vises launcher frosset ~1s før dashboardet tegnes (i stedet for et splash).
- Glance-widget bruger unik `data`-Uri pr. widget for distinkte PendingIntents.
- **Nova Launcher sender `ACTION_APPWIDGET_UPDATE` FØR config-activity åbner** (ikke efter RESULT_OK).
  `provideGlance` kørte med `cfg=null` → "Opsæt" sat i AppWidgetManager-cachen. Alle forsøg på at
  overskrive cachen bagefter (direkte `AppWidgetManager.updateAppWidget`, broadcasts, `update()`-kald)
  ignoreres af Nova under placement. **Fix (v0.2.2):** `provideGlance` bruger reaktiv Room `Flow`
  (`EntityWidgetDao.observe` + `flatMapLatest` til `EntityStateDao.observe`). Glance-sessionen holder
  sig i live og rekomponerer automatisk når `saveAndFinish` upsert'er config. Ingen broadcasts
  eller direkte AppWidgetManager-kald fra config-activity er nødvendige.
- **`GlanceAppWidget.update()` er fire-and-forget i Glance 1.1.1** — kører `provideGlance` asynkront
  i en session-coroutine. Returnerer ikke når RemoteViews er applied. Brug reaktiv Room `Flow` i stedet
  for at kalde `update()` fra config-activity.
