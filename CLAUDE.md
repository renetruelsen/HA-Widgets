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
- ✅ **v0.2.23 — MultiEntityWidget: korrekt startstørrelse + ramme + fjernet overskrift
  (2026-07-02, efter brugerrapport "4 rækker høj, fuld bredde"):**
  - **Root cause:** `resizeMode="none"` + fast `minWidth=280dp`/`minHeight=74dp` i
    widget-info-XML'en. Android låser widgettens grid-footprint ved PLACERING — før
    config-activity kører — så appen aldrig kan kende det faktiske slot-antal (2-5) i
    det øjeblik footprint besluttes.
  - **Løsning (2 UX-review-gates: design før kode, implementering efter):** 4 separate
    widget-picker-entries ("2/3/4/5-Entity HA Multi"), hver med egen `minWidth` matchende
    slot-antal (124/184/244/304dp, alle `minHeight=56dp` — matcher familiens øvrige
    1-rækkes widgets præcist). `resizeMode="horizontal|vertical"` (var `"none"`) som
    sikkerhedsnet. `MultiEntityWidgetReceiver`/`multi_entity_widget_info.xml` bevarer
    oprindeligt navn (bagudkompatibilitet — Android binder til class-navn, ikke
    XML-indhold) og bliver de facto "5-plads"; 3 nye receivers/XML-filer for 2/3/4.
  - **Elastisk boks-sizing:** `sizeMode = SizeMode.Exact` (kontinuerlig, ikke discrete
    buckets). Boks-bredde/-højde beregnes separat, clamped `[48dp, 56dp]` — 48dp er
    Android tap-target-minimum (skærpet fra oprindeligt 32dp-forslag efter
    UX-review-krav om tilgængelighed). Ved slot-antal > variantens plads: overflow
    samles i "+N"-badge i stedet for at klemme bokse under 48dp.
  - **Ramme:** slot-rækken wrappes i én `Box` med fast (ikke tema-baseret) grå
    `Color(0x80808080)`-baggrund, `cornerRadius=16dp`, `padding=4dp` — literal farve
    bevidst fastholdt over UX-reviewerens tema-farve-anbefaling (matcher brugerens
    eksplicitte "grå"-ord, alpha-blanding af Glance-tema-farver har intet etableret
    mønster i kodebasen).
  - **Titel fjernet:** `MultiWidgetEntity.title` er nu ubrugt af UI (gemmes altid tomt,
    ingen DB-migration) — fjernet fra config Skærm 1 (`OutlinedTextField`) og
    hjemmeskærm-render.
  - Fuld spec: `docs/widget-settings-spec.md` §6.
  - **QA-status:** emulator (`pixel_test`) — build OK, manifest/providers verificeret
    (4 receivers bundet korrekt), widget-picker viser korrekt differentierede
    navne/beskrivelser + korrekt footprint ("2×1"/"3×1", matcher `HA Climate`), config-
    flow verificeret (ingen titel-felt, entity-tilføjelse virker), EKSISTERENDE
    placerede widgets (4-slot og 1-slot) re-renderer korrekt med ny ramme/uden titel
    efter opdatering. Et live skærmbillede af en FRISK lille-footprint-placering blev
    ikke fanget denne session (emulator-UI-automation for drag-and-drop var flaky —
    logcat bekræftede widget blev bundet + config-skærm virkede korrekt ved ét forsøg,
    tabt kun pga. forkert tryk-koordinat på "Gem"). Device-QA (rigtig enhed) afventer —
    ingen enhed tilsluttet under denne session.
- ✅ **v0.2.24 — MultiEntityWidget: fjernet luft mellem slot-bokse og ramme (2026-07-02,
  efter brugerfeedback):**
  - **Problem:** rammen (`Box`) brugte `fillMaxSize()` og fyldte hele den tildelte plads,
    mens slot-boksene var loftet ved 56dp. Ved større launcher-celler (One UI/S23) blev
    boksene centreret i en for stor ramme → synlig luft.
  - **Fix (`MultiEntityWidget.kt`):** ydre `Box(fillMaxSize)` centrerer nu en indre ramme
    UDEN bredde-modifier → rammen krymper til boksenes naturlige bredde. `computeSlotLayout`
    strækker ikke længere boksene ud over 56dp når der er ekstra plads. Boks-HØJDEN fik
    fjernet sit 56dp-loft (kun 48dp-gulv), så en boks må være højere end bred og fylde
    rammens højde — bredde/højde afkoblet. `FRAME_PADDING_DP` 6→4.
- ✅ **v0.2.25 — MultiEntityWidget slot-editor: Visning/Handling-opdeling + auto-detekteret
  handling (2026-07-02, efter brugerrapport + 2 UX-gates):**
  - **Rapporterede fejl:** «Kun visning» blev tilbudt i handlings-radiolisten selv efter man
    valgte et andet mål (giver ikke mening); ingen visuel opdeling af visning vs. handling;
    «Kort label» midt i det hele; misvisende titel «Tilpas handling»; «Annullér» skubbet ud
    af skærmen (ingen `verticalScroll`).
  - **Redesign (`MultiEntityWidgetConfigActivity.kt`, `SlotEditorScreen`):** titel →
    «Tilpas entitet». To indrammede sektioner («Visning»/«Handling») via ny `SectionCard`
    (1dp `outlineVariant`, 12dp radius, 16dp padding). «Kort label» flyttet øverst.
    `verticalScroll` tilføjet. Knap-rækkefølge: «Annullér» over «Tilføj til widget».
  - **«Kun visning» → «Reagér på tryk»-kontakt:** NONE er ikke længere et radio-valg —
    det er FRA-tilstanden på en `Switch` der KUN vises når mål == visning. Ved mål ≠ visning
    ingen kontakt (handling altid underforstået) → eliminerer rapporterede fejl strukturelt.
  - **Auto-detekteret handling:** domæner med én mulig handling (switch/lock→toggle,
    number→slider, scene/script→trigger) viser auto-linje «Ved tryk: X», ingen valg. Kun
    lys/cover/climate (toggle vs slider) + automation (toggle vs udløs) viser 2 radios.
    Read-only (sensor/binary_sensor/device_tracker) → «Denne enhed kan kun vises».
  - **Snap ved mål-skift + legacy-normalisering:** `defaultActionFor(domain)` snapper action
    til domænets første gyldige handling når mål/visning vælges. `draftFromSlot` normaliserer
    ELDRE data ved indlæsning (en slot gemt med `action=NONE` + andet mål — muligt i gammel
    UI — snappes til `opts.first()` så en radio er valgt). Ingen DB-ændring; `action`-feltet
    beholder sine 4 værdier.
  - **QA:** emulator (`pixel_test`) alle 5 tilstande grønne; S23 (`R3CWC00JY4M`) device-QA
    grøn — bekræftede scroll/knapper nåbare + legacy-normaliseringen (fandt fejlen her).
    UX-review: APPROVE WITH CHANGES (hovedpunkt FRA-caption rettet; rest er valgfri polish /
    bevidst design). Fuld spec: `docs/widget-settings-spec.md` §7.
- ✅ **v0.2.26 — code-review-fixes før merge (2026-07-02):** 8-vinklet code-review
  (line-scan/removed-behavior/cross-file/reuse/simplify/efficiency/altitude/conventions)
  fandt 9 fund; 2 reelle fejl rettet:
  - **OverflowBadge klikbar:** «+N»-badgen åbner nu config-activity ved tryk — før var
    slots i overflow både usynlige OG uopnåelige (ingen adgangsvej uden manuel resize).
  - **Kontakt-hukommelse:** «Reagér på tryk» FRA→TIL genopretter nu brugerens seneste
    handlings-valg (`rememberedAction`, keyed på display/mål-entitet) i stedet for altid
    at nulstille til `opts.first()` — fx valgt «Åbn skyder» overlevede ikke en toggle før.
  - **Kendte, accepterede fund (ikke rettet):** (1) slot-loft er 5 uanset variant —
    bevidst plan-beslutning (delt config, resize+overflow som sikkerhedsnet); badge-klik
    giver nu adgangsvejen. (2) `MultiWidgetEntity.title`-kolonnen + `get()/observe()` er
    død plumbing; gen-gem nulstiller gamle titler (featuren er fjernet — acceptabelt).
    (3) OverflowBadge bruger tema-farver inde i den faste grå ramme — mulig
    mørk-tilstands-kontrastklask; observér ved device-brug. (4) 4 receiver-klasser er
    identiske kopier — kandidat til delt base ved næste variant. (5) Handling-sektionens
    nesting → sealed HandlingState-kandidat. Verificeret på emulator; merged til main.
- ✅ **v0.2.27 — MultiEntityWidget: revert til én variant + resize-banner (2026-07-03,
  efter brugerfeedback):**
  - **Baggrund:** bruger påpegede at de 4 varianter fra v0.2.23 (2/3/4/5-Entity HA Multi)
    var kunstige — alle delte 100% config-logik (op til 5 slots, samme render/overflow-
    badge), variant-valget påvirkede KUN start-footprint. Besluttet at gå tilbage til
    ÉN variant og i stedet kompensere med en state-aware informationsbanner.
  - **Én variant:** `MultiEntityWidget2/3/4Receiver` + tilhørende XML/strings/drawables
    fjernet. `MultiEntityWidgetReceiver`/`multi_entity_widget_info.xml` bevarer
    bagudkompatibelt navn, footprint ændret til 4-slot-mål (`minWidth=244dp`,
    `targetCellWidth=4`, var 304dp/5). Picker-strengene konsolideret til generisk
    "HA Multi-entity"/"HA Multi-entitet" (ikke tals-specifik).
  - **Resize-banner:** `MultiEntityWidgetConfigActivity` Skærm 1 læser
    `AppWidgetManager.getAppWidgetOptions(appWidgetId)` → `OPTION_APPWIDGET_MIN_WIDTH`,
    genbruger widgettens egen `computeSlotLayout` (nu `internal`) til at afgøre om
    konfigurerede slots overstiger hvad der reelt er plads til. Vises KUN ved reel
    overflow (ny lokaliseret streng `multi_entity_resize_banner`, alle 3 sprogfiler) —
    ingen modal dialog ved tilføjelse (dårlig timing/kontekst).
  - **QA:** emulator (`pixel_test`) og S23 (Nova Launcher) — begge viser korrekt ÉT
    "HA Multi-entity"-entry i widget-pickeren (ikke 4), korrekt footprint ("4×1" på S23),
    placering + config-flow (tilføj/rediger/fjern slot) verificeret uden crashes.
    Banner-visning i faktisk overflow-tilstand blev IKKE visuelt bekræftet denne
    session — emulatorens launcher-grid gav generøs nok bredde til at alle 5 test-slots
    fik plads uden resize (ingen overflow opstod, banner korrekt fraværende); resize-
    håndtag var vertikale-kun i den konkrete emulator-instans. Underliggende
    `computeSlotLayout`-matematik er verificeret manuelt korrekt for 244dp-footprint
    (4 slots passer, 5. kræver resize). Anbefales bekræftet visuelt ved næste
    device-session med finere launcher-grid.
  - Kendt, udskudt UX-fund (ikke del af denne ændring): footprinttet på hjemmeskærmen
    kan være markant større end den visuelle widget (boks-vs-ramme-gap) — separat opgave.
  - Fuld spec: `docs/widget-settings-spec.md` §8.
- ✅ **v0.2.29 — MultiEntityWidget: ramme-fix + fuld-bredde rig-rækker med sekundær-chips
  (2026-07-03, efter brugerskærmbillede + omfattende brainstorm med visuel mockup-
  companion):**
  - **Root cause for "hul"-bugget:** rammen krympede til boksenes naturlige størrelse
    (v0.2.24-beslutning) i stedet for at fylde det faktisk tildelte footprint — resten
    viste tapetet igennem. Bekræftet fixet: long-press-resize-visning hugger nu tæt om
    indholdet uden luft.
  - **Layoutskifte:** fra "kvadratiske fliser side om side" (`computeSlotLayout`,
    `SlotBox`, `OverflowBadge` — alle fjernet) til fuld-bredde rækker i en Glance
    `LazyColumn` — løser ramme-bugget strukturelt (indhold bruger nu almindelige
    `fillMaxSize`/`fillMaxWidth`, ingen custom pixel-matematik) og gør plads til rigere
    per-række-indhold. Ingen "+N"-badge længere — overskydende rækker nås ved scroll
    (bekræftet virkende på S23: tynd scrollbar ses i mørk tilstand).
  - **Rammefarve** skiftet fra hardcodet grå literal til `androidx.glance.color.
    ColorProvider(day=.., night=..)` — auto lys/mørk-tilpasset, løser en kendt
    kontrast-risiko fra v0.2.26-review.
  - **Sekundære info/handlings-chips (op til 3 pr. række):** inspireret af brugerens
    egne `custom:bubble-card`-YAML-eksempler (Spa/Haven/Udestue). Hver chip har SAMME
    visning/handling-uafhængighed som hoved-entiteten (kan pege på en anden entitet end
    den viste — løser bl.a. et asymmetrisk stop/start-knap-par direkte, uden
    workaround). Chip-typen (info/toggle/trigger) udledes automatisk af domænet via
    eksisterende `compatibleActionsFor`/`defaultActionFor` — ingen ny handlings-model.
  - **Room-migration v2→v3:** 15 nye nullable kolonner (3× 5-felts sekundær-sæt) på
    `multi_widget_slot`. Bekræftet ikke-destruktiv på både emulator og S23 (alle
    eksisterende widgets — inkl. flere års testdata — overlevede uden data tab).
  - **Config-UI redesignet:**
    - Skærm 1 (slot-liste): kort-layout med 3 lodrette zoner — tonet klikbar zone
      (chevron efter navnet, åbner editor) → skraldespand i egen søjle → ↑/↓ i fuld
      kort-højde. ALLE sekundær-chips vises altid fuldt synlige i kortet (ingen skjult
      "+N ekstra"-optælling, efter eksplicit brugerønske). Erstatter den gamle 4-knaps-
      række (kendt overflow-fejl ved lange navne).
    - Skærm 2 ("Tilpas entitet"): ny "Ekstra info (N/3)"-sektion under Handling, med
      samme VISNING/HANDLING-genbrugsmønster som hoved-entiteten (inkl. "Skift" til et
      andet handlings-mål). Handlings-mål-picoren (både hoved-entitetens og
      sekundærernes) filtrerer nu til kun domæner med en gyldig handling — undgår at
      brugeren kan vælge et read-only mål.
    - Resize-banneret (v0.2.27) fjernet — urelateret til den nye scrollbare liste.
  - **QA:** emulator (`pixel_test`, network-isoleret — kunne kun verificere rendering/
    migration, ikke config-UI interaktivt) + Galaxy S23 (Nova, rigtig HA-forbindelse) —
    fuld interaktiv test af tilføj/fjern/skift sekundær-chip, inkl. det asymmetriske
    mål-tilfælde, gemt og verificeret direkte i databasen OG bekræftet renderet korrekt
    på hjemmeskærmen (ramme + hoved-række + sekundær-chip-ikon, korrekt aktiv-farve).
    Ingen crashes på nogen af enhederne.
  - Fuld spec/beslutningshistorik: `docs/widget-settings-spec.md` §9.
- ✅ **v0.2.30 — MultiEntityWidget: "diverse inputs"-domæner (2026-07-03, efter
  brugerønske):** `MULTI_ENTITY_DOMAINS` udvidet med HA's helper-domæner —
  `input_boolean` (mirror af switch: TOGGLE), `input_number` (mirror af number: RANGE,
  inkl. `RangeControlActivity`-understøttelse med `input_number.set_value`),
  `input_button` (TRIGGER via `input_button.press`), samt `input_text`/`input_datetime`/
  `input_select` (bevidst read-only — `input_select` kræver en options-vælger-skærm for
  en rigtig handling, ikke en simpel 1-tryks toggle/range/trigger; udskudt). Handlings-
  mål-picoren ekskluderer fortsat automatisk de read-only input-domæner (samme filter
  som v0.2.29). Byg grønt, ingen crashes på emulator/S23.
- ✅ **v0.2.31–33 — MultiEntityWidget: enheder, redigerbar input_text/input_datetime,
  bruger­valgt "vis værdi" pr. chip (2026-07-03, efter brugerfeedback fra device-QA):**
  - **v0.2.31 — Auto-detekteret enhed:** `formatEntityState` udvidet med en valgfri
    `unit`-parameter — matcher det eksisterende mønster fra `SensorWidget.
    buildSensorValue`. `HaApiClient.EntityBrief` fik et nyt `unit`-felt (fra
    `unit_of_measurement`-attributten); ny delt `unitFromJson()`-helper (i
    `GlanceWidgetCommon.kt`) bruges i widget-renderingen. Bekræftet på S23: en
    sensor-værdi der før viste bare "23" viser nu "23 °C".
  - **v0.2.31 — input_text/input_datetime gjort redigerbare:** to nye handlings-typer
    "TEXT" og "DATETIME" (`compatibleActionsFor`). Nye aktiviteter
    `TextControlActivity` (tekstfelt + Gem, kalder `input_text.set_value`) og
    `DateTimeControlActivity` (Androids indbyggede `DatePickerDialog`/
    `TimePickerDialog` — valgt frem for en tekstboks efter brugerspørgsmål, tilpasser
    sig entitetens `has_date`/`has_time`-attributter, kalder
    `input_datetime.set_datetime`). `input_select` forbliver bevidst read-only.
  - **v0.2.32-fund (device-QA):** en nytilføjet input_datetime-sekundær-chip viste kun
    et ikon, intet klokkeslæt — root cause: chip-værditekst var hardcodet til kun at
    vise for `action == "NONE"` (ren info-chip), så den nye "DATETIME"-handling faldt i
    den tavse ikon-kun-gruppe (designet til TOGGLE/TRIGGER). Midlertidigt rettet til at
    også vise værdi for RANGE/TEXT/DATETIME.
  - **v0.2.33 — "Vis værdi"-indstilling gjort brugervalgt pr. chip** (efter
    opfølgende brugerønske: "kan det ikke være en option?") i stedet for hardcodet til
    handlings-typen. Ny Room-migration v3→v4 (3× `secondaryNShowValue`-kolonne, nullable
    Boolean). Ny delt `defaultShowValueFor(action)` (i `MultiDomainSupport.kt`) giver et
    fornuftigt forslag (til for NONE/RANGE/TEXT/DATETIME, fra for TOGGLE/TRIGGER) som
    brugeren kan overstyre via en ny "Vis værdi på chippen"-switch i
    "Ekstra info"-sektionen. Bekræftet på S23: slog "Vis værdi" til på en
    automation-TOGGLE-chip, gemte, og widgetten viste straks "Deaktiveret"-teksten
    ved siden af ikonet.
  - **QA:** al test kørt direkte på Galaxy S23 mod ægte HA-data (ikke kun emulator,
    som er netværksisoleret) — fandt v0.2.32-fejlen undervejs, ingen crashes efter fix.
    Legacy-normalisering af `action="NONE"`-sekundærer, hvis domæne senere fik en rigtig
    handling (fx `input_datetime` før/efter v0.2.31), blev bevidst IKKE tilføjet efter
    brugerønske ("don't worry about legacy, appen er stadig i udvikling, jeg retter
    selv") — kun relevant for data fra før v0.2.31 i denne udviklings-database.
- ✅ **v0.2.34 — code-review-fixes efter v0.2.29-33-commit (2026-07-04):** 4 af 10
  fund fra `code-review` rettet (resten bevidst udskudt — se fundlisten i sessionen):
  - **RangeControlActivity min≥max-guard:** ugyldigt/omvendt range fra en entitets
    attributter faldt tidligere direkte igennem til `Slider`s `valueRange` og
    `coerceIn`, som begge kaster ved min≥max. Falder nu tilbage til et sikkert
    0..100-interval.
  - **MultiEntityWidget sekundær-chip 48dp tap-target:** chippens klikbare areal var
    kun ~22dp højt (ikon+lille padding, ingen "gratis" højde fra en nabo-label-kolonne
    som hoved-rækken har) — eksplicit `.height(48.dp)` tilføjet (Android
    tilgængeligheds-minimum).
  - **number/input_number decimal-præcision:** `RangeControlActivity` og
    `MultiEntityWidget`s `rangeCurrentValue`/`rangeMin`/`rangeMax` afrundede til Int
    før værdien nåede slideren — en entitet med fx step 0.5 mistede sin decimal. Nye
    `EXTRA_..._PRECISE`-intent-extras (Double) bruges nu for alle RANGE-handlinger fra
    MultiEntityWidget (light/cover/climate uændret heltals-adfærd, kun number/
    input_number sender den reelle decimalværdi videre til HA). Fundet undervejs:
    `String.format("%.2f", ...)` brugte enhedens locale (dansk komma) og brød
    tekst-trimningen ("38," i stedet for "38") — rettet med `Locale.ROOT`.
  - **SensorWidget/GlanceWidgetCommon unit-duplikering:** `SensorWidget.
    parseSensorAttrs` parsede `unit_of_measurement`/`friendly_name` selv i stedet for
    at genbruge de allerede eksisterende `unitFromJson`/`friendlyNameFromJson`
    (`GlanceWidgetCommon.kt`) — nu genbrugt, kun `device_class` er tilbage som
    sensor-specifik parsing.
  - **QA:** build grøn, emulator (`pixel_test`, ingen netværk) bekræftede ingen
    rendering-regression på et eksisterende multi-widget. Fuld funktionel test på
    Galaxy S23 mod ægte HA: decimal-slider verificeret (drag → "37.68°C" vist og sendt
    korrekt til `input_number.set_value`, ingen afrunding), min/max-guard uændret
    adfærd for normale entiteter, ingen crashes i logcat. **Bevidst udskudt** (ingen
    aktiv bug i dag, større indsats end gevinst lige nu): a11y-semantik for
    `SlotCard` (#3), ikke-udtømmende `else -> TRIGGER` (#4), én bundlet
    version-bump-commit for v0.2.29-33 (#6, proces-note — ingen retroaktiv handling),
    duplikeret aktivitets-bootstrap i Range/Text/DateTimeControlActivity (#7),
    hand-unrolled secondary1/2/3-felter (#9), 5 parallelle `when(domain)`-funktioner
    (#10).
- ✅ **v0.2.35-36 (2026-07-04/05, superseret af v0.2.37):** dynamisk rækkehøjde-formel
  (`LocalSize.current.height` strakte rækkerne til footprintet) + refresh-alle-ikon
  (Room v4→v5 `showRefreshIcon`, config-toggle). Stretch-formlen udløste to bugs:
  (1) Nova viste landskabs-RemoteViews i portræt under `SizeMode.Exact` (omgået i
  v0.2.36 med `SizeMode.Responsive` + 4 buckets), (2) chips mistede runde hjørner +
  kort beskåret i bunden med refresh-ikon. Kun refresh-ikon-featuren overlevede til
  v0.2.37.
- ✅ **v0.2.37 — MultiEntityWidget: revert til naturlig rækkehøjde + lille refresh-strip
  (2026-07-06):**
  - **Nøgleindsigt (brainstorm 2 udviklere + UX'er, brugergodkendt):** begge
    v0.2.35-36-bugs kræver at komponeret indhold LÆSER `LocalSize` — Android
    komponerer under `SizeMode.Exact` altid både portræt- og landskabs-udgave, og
    fejlvalget er kun synligt når de to kan afvige. `c152b6d` (v0.2.34) havde allerede
    naturlig wrap-content rækkehøjde + `LazyColumn`-scroll — dvs. præcis den godkendte
    kravspec (fast højde, aldrig stræk, scroll ved overflow, tomrum ved oversize).
  - **Revert:** `SizeMode.Exact` genindført (advarsels-kommentar mod fremtidig
    `LocalSize`-brug), hele rækkehøjde-formlen + `MIN_ROW_HEIGHT_DP` +
    `rowHeight`-parameter fjernet, `maxResizeHeight` 270→400dp. Refresh-featuren
    (Room v5, toggle, `MultiWidgetViewState`) bevaret uændret.
  - **Refresh-strip skrumpet:** 24dp høj (var 32) med 16dp ikon (var 48); HELE
    bjælken klikbar (16dp ikon alene for lille tap-target).
  - **QA:** emulator (ny widget m. 3 slots + chip mod ægte HA — emulatoren HAR
    netværk igen): naturlig højde, tomrum ved oversize, scroll ved undersize,
    strip-tryk → `SyncWorker` SUCCESS i logcat, række-toggle verificeret begge veje
    (HA-state bekræftet via MCP). S23/Nova: brugerverificeret OK.
  - Spec: `docs/superpowers/specs/2026-07-05-multi-entity-fixed-height-revert-design.md`,
    plan: `docs/superpowers/plans/2026-07-05-multi-entity-fixed-height-revert.md`.
- ✅ **v0.2.38 — slank scrollbar i Glance-lister (2026-07-06, efter brugerfeedback på
  S23-scrollbarens udseende):**
  - Glance's `LazyColumn` renderes som klassisk `ListView` (`glance_list.xml` i
    glance-appwidget), som peger på den BEVIDST TOMME style-hook
    `Glance.AppWidget.List` — appens `values/themes.xml` definerer samme style-navn
    og vinder ved resource-merge. Ingen layout-kopi/hack.
  - Style: `scrollbarSize=3dp`, afrundet halvtransparent thumb
    (`drawable/glance_scrollbar_thumb.xml`, farve `@color/glance_scrollbar_thumb` m.
    `values-night`-variant), fade efter 400ms. Valgt frem for `scrollbars=none` —
    overflow-rækker nås KUN via scroll, så affordance bevares.
  - Gælder globalt for alle Glance-lister i appen (kun multi-widget bruger LazyColumn
    i dag). Verificeret på emulator + S23.
  - **OBS:** code-review for v0.2.37-38 bevidst udskudt (flere rettelser på vej i
    samme serie).
- ✅ **v0.2.39 — Widget UX Pack: 8 punkter (2026-07-06/07, dev-pipeline med
  subagent-driven-development, 13 tasks + Opus-niveau review på de kritiske dele):**
  - **Baggrund:** brugerrapporterede 8 UX-problemer på tværs af multi-widget-config,
    widget-picker og handlinger. Kørt gennem fuld pipeline: brainstorm → grill-with-docs
    (5 ADR'er, se `docs/adr/2026-07-06-widget-ux-pack-adrs.md`) → implementeringsplan
    (`docs/superpowers/plans/2026-07-06-widget-ux-pack.md`) → bruger-godkendt mockup-gate
    → 13 subagent-implementerede tasks, hver med uafhængig task-reviewer (spec + kvalitet).
  - **Slot-liste-redesign (A1):** `SlotCard` omstruktureret til 4 rækker — entitetsnavn
    på fuld bredde (1 linje + ellipsis), handlings-resumé, sekundær-chips, og en
    ikon-række i bunden (↑/↓/🗑, 48dp targets) — erstatter det gamle 3-zone side-om-side
    layout hvor navn/knapper konkurrerede om bredden.
  - **Widget-picker-ikoner synlige i lyst tema (A2):** nye `preview_*.xml`-drawables
    (brand-blå plade `#0B6FA4` + eksisterende hvidt ikon ovenpå) for alle 11 widget-typer;
    selve widget-runtime-renderingen (tint) er uændret.
  - **"Opdater" ved redigering (A3):** slot-editorens gem-knap viser "Opdater" ved
    redigering af eksisterende slot, "Tilføj til widget" ved ny — afledt af
    `editIndex != null`. Samtidig fix af en i18n-regression: adskillige hardcodede
    danske strenge i `MultiEntityWidgetConfigActivity`/`BaseEntityPickerActivity` flyttet
    til `strings.xml` (alle 3 sprog).
  - **"Bekræft ved tryk" (B1):** ny switch i Handling-sektionen (hoved + hver sekundær-
    chip), vist kun for TOGGLE/TRIGGER. Ny `ConfirmActionActivity` (translucent dialog,
    samme mønster som `RangeControlActivity`) — bekræft-teksten navngiver ALTID
    handlings-målets friendly name, aldrig den viste entitet (ADR-1, verificeret separat
    for asymmetriske display≠action-tilfælde). Service-mapping er en verificeret
    byte-for-byte-kopi af `EntityActions.kt`s `ToggleEntityAction`/`TriggerEntityAction` —
    ingen risiko for forkert HA-kommando. `confirmAction=false` (default, alle
    eksisterende slots) er en ren tilføjelse oven på den uændrede originale click-sti.
  - **RANGE: kombination skyder + felt (B2, mockup-valgt):** delte
    `RangeControlActivity` fik −/+ trinknapper (trin 0,5 ved range ≤20, ellers 1,
    gælder ALLE widgets med RANGE — ADR-2) — formlen er uafhængigt verificeret mod
    brief'ens regneeksempler inkl. grænsetilfælde. Multi-entity fik desuden et
    config-valg "Skyder"/"Indtast værdi" pr. RANGE-handling (Room v6→v7,
    `rangeInputMode`) og en ny `NumberInputActivity`; begge aktiviteter deler nu én
    `sendRangeValue`-helper (undgår en tredje duplikeret domæne→service-mapping).
  - **Globalt tema-valg (C1):** ny dropdown "Tema" (Lys/Mørk/Følg system) ved
    sprog-vælgeren i `MainActivity`, persisteret i `SecureStore.themeMode`. Nyt mørkt
    Material3-skema (app-UI) + `WidgetGlanceTheme(context)`-wrapper der leverer
    tema-tvungne `ColorProviders` til Glance — for "system" genbruges Glance's egen
    `DynamicThemeColorProviders` direkte (pixel-identisk garanti), for tvunget lys/mørk
    faste farver. Alle 11 widget-providers wrappet; `updateAll()` kaldes for alle ved
    skift. WebView/dashboard er bevidst UDENFOR scope (ADR-4 — CLAUDE.md's ældre
    v0.2.6-påstand om `themes:{darkMode}` matchede ikke den faktiske kode).
  - **Værdi-formatering (C2):** ny `ValueFormatting.kt` (unit-testet: 25 tests) —
    numeriske værdier afrundes automatisk til maks 1 decimal (`23.888` → "23.9"),
    datetime/timestamp får automatisk lokalt kort format. Pr. slot/chip-overstyring:
    decimaler-dropdown (Auto/0/1/2) og frit `DateTimeFormatter`-mønster-felt med live
    preview (ugyldigt mønster falder sikkert tilbage til auto). Gælder også
    `SensorWidget` (auto-only).
  - **Refresh-bar som glas-overlay (C3, ADR-3):** refresh-stripen ligger nu som en
    halvtransparent overlay ovenpå listen (`Box`-stacking, ikke en Column-søskende) —
    overflow-rækker skimtes bag den under scroll. Usynlig bund-spacer sikrer sidste
    række altid er fuldt synlig når man scroller helt ned.
  - **QA:** emulator (`pixel_test`, ægte HA-forbindelse mod `home.rtr.dk`) —
    verificeret: build+test grønt (25 unit-tests), app/config-flows uden crashes
    gennem hele sessionen, mørkt tema fungerer korrekt i app OG på widgets, slot-kort-
    redesign matcher mockup, decimal-formatering ("23.9 °C") synlig på faktisk widget,
    "Bekræft ved tryk" persisterer korrekt end-to-end til Room via det etablerede
    to-trins gem-flow (slot-editor "Opdater" → in-memory liste → "Gem widget" på
    Skærm 1 → Room), "Opdater"-knap-tekst korrekt kontekstafhængig, picker-ikoner
    synlige i lyst tema for alle 11 widgets. `ConfirmActionActivity` bekræftet korrekt
    registreret (`exported=false`, sikker default). **Ikke visuelt device-bekræftet:**
    selve bekræft-dialogens rendering og RANGE −/+-knapperne interaktivt (ingen
    placeret RANGE/switch-widget med synligt tryk-flow fundet under denne session) —
    dette er dog dækket af uafhængig, streng kode-niveau-verifikation (Opus-reviews med
    byte-for-byte service-mapping-sammenligning og selvstændig formel-udregning for
    begge). Device-QA på fysisk S23 afventer.
  - Spec: `docs/superpowers/specs/2026-07-06-widget-ux-pack-design.md`, plan:
    `docs/superpowers/plans/2026-07-06-widget-ux-pack.md`, ADR'er:
    `docs/adr/2026-07-06-widget-ux-pack-adrs.md`.
- ✅ **v0.2.40 — MultiEntityWidget: fil-opsplitning efter ansvar (2026-07-07, ren
  refaktorering, INGEN adfærdsændring):**
  - **Baggrund:** `MultiEntityWidgetConfigActivity.kt` (1218 linjer) og
    `MultiEntityWidget.kt` (594) var vokset langt over ~300-linjers retningslinjen
    gennem v0.2.23→v0.2.39. Opsplittet efter ansvar; ingen logik rørt.
  - **Config-side (1218 → 6 filer):** `MultiEntityWidgetConfigActivity.kt` (246, Activity
    + `MultiEntityConfigScreen`-orchestrator + `Step`/`PickerTarget`), `MultiEntityDrafts.kt`
    (176, `SlotDraft`/`SecondarySlotDraft` + `draftFromSlot`/`SlotDraft.toSlotEntity` +
    `actionOptionsFor`/`defaultActionFor`), `MultiEntitySlotEditor.kt` (426,
    `SlotEditorScreen`/`SecondaryEntityRow`/`SectionCard` + label-helpers),
    `MultiEntityValueControls.kt` (145, `ValueFormattingControls`/`RangeInputModeControl`),
    `MultiEntityListScreen.kt` (234, `ListScreen`/`SlotCard`/`secondarySlotSummaries`),
    `MultiEntityPickerScreen.kt` (114, `EntityPickerSubScreen`).
  - **Widget-side (594 → 3 filer):** `MultiEntityWidget.kt` (110, widget-klasse +
    `provideGlance` + receiver + `statesFlow`/`allEntityIds`/`MultiWidgetViewState`),
    `MultiEntityRendering.kt` (327, `MultiEntityContent`/`SlotRow`/`SecondaryChip`/
    `RefreshStrip` + `SecondaryChipData`/`displayValueFor`), `MultiEntityClickModifier.kt`
    (174, `clickModifier` + `rangeCurrentValue`/`Min`/`Max`).
  - **Nøgle-ændring:** de nestede `draftFromSlot`/`saveSlot`-closures (fangede
    `allEntities`/`appWidgetId`/`slots`) blev rene top-level fns (`draftFromSlot(slot,
    allEntities)` + `SlotDraft.toSlotEntity(appWidgetId, slotIndex)`); alle delte
    `private` top-level-deklarationer → `internal`.
  - **Rester over ~300:** `MultiEntitySlotEditor.kt` (426) og `MultiEntityRendering.kt`
    (327) — sammenhængende composable-grupper, bevidst efterladt samlet.
  - **QA:** build grøn (kun præeksisterende `ExperimentalCoroutinesApi`-warning).
    Emulator (`pixel_test`, ægte HA): fuldt config-flow (list → slot-editor →
    entity-picker → sensor-valg m. Decimaler-dropdown → Opdater → Gem widget →
    SyncWorker SUCCESS), widget re-renderer korrekt på hjemskærm. Ingen crashes.
    Device-QA på S23 sprunget over efter brugerønske (ren refaktorering).
- ✅ **v0.2.41 — fejl-feedback i kontrol-dialoger (2026-07-07, efter PR-review-fund):**
  - **Baggrund:** PR-review (PR #1) fandt at `RangeControlActivity`, `TextControlActivity`,
    `DateTimeControlActivity`, `NumberInputActivity` og `ConfirmActionActivity` ignorerede
    `HaApiClient.callService`s returværdi og lukkede/opdaterede ubetinget — en fejlet
    HA-forbindelse så ud som en gemt værdi. Kørt gennem fuld proces: brainstorming →
    spec → subagent-driven-development (6 opgaver, hver med uafhængig task-review).
  - **Fix:** `RangeService.sendRangeValue` og `ConfirmActionActivity.executeConfirmedAction`
    returnerer nu `Boolean`. Ved fejl: ny delt `showActionError()`-Toast (`ActionFeedback.kt`,
    "Kunne ikke sende til Home Assistant", alle 3 sprog), dialogen forbliver åben (input
    bevares) i stedet for at lukke. Eneste undtagelse: `DateTimeControlActivity` lukker
    stadig ved fejl (native date/time-pickers er allerede lukket på det tidspunkt — intet
    UI at holde åbent).
  - Spec: `docs/superpowers/specs/2026-07-07-control-dialog-error-feedback-design.md`,
    plan: `docs/superpowers/plans/2026-07-07-control-dialog-error-feedback.md`.
  - **QA:** build grøn, alle 6 opgaver uafhængigt code-reviewet (0 fund på tværs).
    `ConfirmActionActivity` fuldt verificeret end-to-end på emulator mod ægte HA
    (`home.rtr.dk`): netværk slukket → Toast bekræftet i logcat + bekræft-dialogen forblev
    åben + widget-raden fik stale-markør "~"; netværk genoprettet → dialog lukkede korrekt
    + widget opdaterede uden stale-markør. De øvrige 4 dialoger deler samme verificerede
    mønster men blev ikke selv live-testet denne session (emulator gik ned midtvejs i
    forsøg på at placere en test-widget) — bruger fortsætter selv QA på emulator + S23.
- ✅ **v0.2.42 — MultiEntityWidget: DRY-oprydning + chip/række-styling + custom chip-label
  (2026-07-07, efter brugerønske, 5 punkter):**
  - **DRY (pragmatisk, ingen skema-ændring):** den 3×-gentagne `secondary1/2/3…`-udrulning
    samlet ét sted via ny `SecondaryColumns`-datatype + `MultiWidgetSlotEntity.secondaryColumns()`
    (læs) / `withSecondaryColumns()` (skriv) i `MultiEntityDrafts.kt`. `secondaryChips()`,
    `draftFromSlot` og `toSlotEntity` itererer nu over en liste i stedet for at kopiere ~11
    felter tre gange. De flade Room-kolonner bevaret bevidst (brugervalg).
  - **Række/chip-styling (`MultiEntityRendering.kt`, ny `surfaceFor`-helper):** on/off-domæner
    viser nu **kun outline når slukket** (primary-ring + neutralt fyld) og **fuld primary-farve
    når tændt**. Glance har ingen border-modifier → "outline" laves med to lag (ydre ring-Box +
    indre fyld-Box). Aktiv **chip** får en **mørk kant** (`DARK_RING`) så dens omrids ses når den
    sidder på en tændt (primary-farvet) række (brugerønske). Info-agtige domæner (sensor/number/
    scene/script) uændret neutralt fyld.
  - **Bug rettet:** chippens tændt/slukket fulgte før VISNINGS-entiteten; følger nu ACTION-målet
    (`isActiveState(chip.actionDomain, actionState)`) — en toggle-chip der viste en anden entitet
    blev aldrig fuld-farvet.
  - **Rækkehøjde −4dp:** rækkens padding 8→6dp pr. side (chippen selv forbliver 48dp a11y-min).
  - **Custom chip-label (Room v7→v8, `secondaryNLabel`):** ny "Chip-label"-felt pr. sekundær-chip
    i config. Chippen kan vise ikon + label + værdi (label/værdi på 2 linjer, som hovedrækken) —
    label og "vis værdi" er uafhængige valg.
  - **"Bekræft ved tryk" default TIL:** `SlotDraft`/`SecondarySlotDraft.confirmAction = true` for
    NYE slots/chips (eksisterende beholder deres gemte værdi).
  - **QA:** build grøn (kun præeksisterende coroutines-warning). Emulator (`pixel_test`, ægte HA):
    migration v7→v8 verificeret (`user_version=8`, 3 `secondaryNLabel`-kolonner, 11 eksisterende
    slots intakte, ingen crash); config-flow (chip-label-felt, vis-værdi, confirm-default TIL for
    ny toggle-chip, Opdater→Gem→SyncWorker SUCCESS); frisk widget placeret og renderet — aktiv
    række = fuld farve, aktiv chip = mørk-kant-outline (per nuance), slukket chip = gråt fyld
    (brugervalg — ægte hul-outline fravalgt), sensor = neutralt fyld, 2-linje label+værdi. **Ikke
    set:** slukket hovedrække (begge test-lys var tændt; samme kodesti som verificeret slukket
    chip) og præcis højde-delta. Device-QA på S23 afventer bruger.

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
- **rtr-dk `/projects/ha-widgets` bruger placeholder-billede:** websitet (`rtr-dk`-repoet) har
  endnu ingen rigtig markedsføringsgrafik/screenshots for HA-Widgets, og genbruger derfor et
  ubrugt Strata-skabelonbillede (`06.jpg`) på appens projektside, indtil rigtig grafik findes.
