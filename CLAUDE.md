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
  - Dashboard-genvej = **1x1 fast tile** (ikon, `resizeMode=none`), ikke en data-widget — "bare en genvej til webview".
    Overlay-*vinduets* størrelse er stadig konfigurerbar (sliders); det er adskilt fra tile-størrelsen.
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
