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
  - **Device-QA (S23, `R3CWC00JY4M`):** grøn — slukket lys = outline-række, tændt = fuld farve,
    tændt chip = fuld farve + mørk kant, slukket chip = gråt fyld, chip-label + værdi på 2 linjer,
    bekræft-dialog på ny toggle-chip, ingen crash, eksisterende widgets bevaret efter migration.
- ✅ **v0.2.43 — MultiEntityWidget: utilgængeligt action-mål giver nu error-styling (2026-07-07,
  code-review-fund #2):**
  - Chippens/rækkens `isUnavailable` aflæstes kun fra VISNINGS-entiteten, mens `isActive` (v0.2.42)
    blev flyttet til ACTION-målet. En asymmetrisk slot/chip (viser A, handler på B) med et
    utilgængeligt B så derfor tap-bar ud, selvom `clickModifier` tavst droppede klikket.
  - **Fix (`MultiEntityRendering.kt`, både `SlotRow` og `SecondaryChip`):** `isUnavailable`
    medregner nu action-målets state ved en rigtig handling —
    `displayState=="unavailable" || (action != "NONE" && actionState=="unavailable")` — så et dødt
    action-mål vises med error-styling i stedet for at ligne en aktiv kontrol.
  - **QA:** build grøn; device-QA på S23 grøn (samme tjekliste som v0.2.42 + edge-case bekræftet).
- ✅ **v0.2.44 — MultiEntityWidget: cleanup + bredere resize (2026-07-07, code-review-cleanup +
  brugerspørgsmål):**
  - **Resize-fix:** `multi_entity_widget_info.xml` `maxResizeWidth` 320→640dp. Med `minWidth=244dp`
    kunne widgetten kun vokse ~244→320dp (under én celle) → føltes som "kan ikke gøres bredere".
    Layoutet er fuld-bredde scrollbare rækker (v0.2.29+), så bredere er strengt bedre. Verificeret
    på emulator: venstre kant kan nu trækkes ud, lange navne vises fuldt.
  - **Cleanup (adfærds-bevarende, fra v0.2.42-code-review):**
    - Den 4×-gentagne to-lags-outline-branch i `SlotRow`/`SecondaryChip` samlet i én
      `StatefulSurface`-composable (outer/inner sizing + padding + klik-wrapper som parametre).
    - `DARK_RING`-literalen flyttet fra `MultiEntityRendering.kt` til `WidgetColors.chipActiveRing`
      (samlet med resten af widget-paletten).
    - Delte default-helpers `showValueOrDefault()`/`confirmActionOrDefault()`/`labelOrEmpty()` på
      `SecondaryColumns` — bruges nu af BÅDE `toChipData` (render) og `toDraft` (config), så de to
      sider aldrig kan divergere på hvad en null-kolonne betyder.
  - **QA:** build grøn; emulator (`pixel_test`) — refaktoren renderer identisk (outline-rækker/chips
    + neutral sensor, ingen crash), resize verificeret bredere. **Device-QA (S23): bekræftet OK af
    bruger (2026-07-07)** — samme gælder v0.2.41's resterende 4 kontrol-dialoger (RangeControl/
    TextControl/NumberInput/DateTimeControl) og v0.2.27's resize-banner, som tidligere stod som
    "afventer" i denne fil; alle bekræftet testet OK af bruger på rigtig enhed.
- ✅ **v0.2.45 — fjernet hardcoded HA-token/URL + LLT-vejledning + kodeoprydning (2026-07-07):**
  - **Sikkerhedsfund:** `app/build.gradle.kts`s `debug`-buildType havde et RIGTIGT, langtidsholdbart
    HA-access-token (JWT mod `home.rtr.dk:8123`) hardcoded som `DEV_TOKEN`/`DEV_URL`
    `buildConfigField`, sporet i git siden allerførste commit — og repoet er offentligt på GitHub.
    Tokenet var reelt lækket offentligt. **Fix:** `buildConfigField`'erne + hele `debug`-buildType-
    blokken fjernet fra `build.gradle.kts`; `BuildConfig.DEBUG`-forgreningerne i `MainActivity.kt`
    og `ShortcutWidgetConfigActivity.kt` fjernet, så URL/token-felterne altid falder tilbage til
    samme (tomme/default) værdi uanset build-type. **Bruger tilbagekalder selv tokenet i HA og
    rydder git-historikken** (uden for denne sessions scope).
  - **Default HA-URL:** onboarding og genvej-config'ens URL-felt bruger nu ubetinget
    `http://homeassistant.local:8123` som default (før kun for release-builds).
  - **LLT-vejledning:** nyt "Hvordan laver jeg et token?"-link under token-feltet i onboarding
    (`MainActivity.kt`) åbner en dialog med 4 nummererede trin (genbruger eksisterende `StepRow`),
    lokaliseret på alle 3 sprog (`token_help_*`-strenge).
  - **Kodeoprydning af udskudte fund (fra v0.2.26/v0.2.34):**
    - `MultiWidgetEntity.title`-kolonnen (ubrugt af UI siden v0.2.23) fjernet — ny
      `MIGRATION_8_9` (Room v8→v9) genskaber `multi_widget`-tabellen uden kolonnen (tabel-
      genskabelse i stedet for `DROP COLUMN`, som ikke er tilgængeligt på alle Android-versioner
      ved minSdk 26).
    - `MultiDomainSupport.kt`s 5 parallelle `when(domain)`-funktioner (`domainIconResId`,
      `formatEntityState`, `isActiveState`, `hasOnOffState`, `compatibleActionsFor`) konsolideret
      til én privat `DOMAIN_CAPABILITIES`-registry — nyt domæne kræver nu ét opslag, ikke fem.
      Verificeret felt-for-felt behavior-lig med originalen (inkl. de hardcodede danske
      state-tekster, som IKKE er en del af denne oprydning — se kendt udestående nedenfor).
    - `MultiEntityClickModifier.kt`s ikke-udtømmende `else -> // TRIGGER` gjort eksplicit
      (`"TRIGGER" -> {...}`) med en sikker `else -> base` (intet klik) for en ukendt action-værdi,
      i stedet for at antage TRIGGER.
    - Ny delt `resolveHaApiClient()` (`ActionFeedback.kt`) erstatter det 4×-kopierede
      `SecureStore.get()`+`HaApiClient(...)`-konstruktionsmønster i `RangeControlActivity`
      (`sendToggle`), `TextControlActivity`, `DateTimeControlActivity` og `RangeService`.
      `ConfirmActionActivity`s egen TOGGLE/TRIGGER-service-mapping er bevidst IKKE rørt — den er
      allerede dokumenteret som et accepteret, byte-for-byte-verificeret duplikat (v0.2.34-fund #7).
    - `SlotCard` (`MultiEntityListScreen.kt`) a11y: ↑/↓/slet-knappernes `contentDescription`
      inkluderer nu entitetsnavnet (`cd_move_up`/`cd_move_down`/`cd_remove` parametriseret,
      alle 3 sprog) i stedet for identiske "Flyt op"/"Flyt ned"/"Fjern" på tværs af kort; hoved-
      rækkens klikbare område har nu `role = Role.Button` + `onClickLabel`.
  - **QA:** build + 25 unit-tests grønne. Emulator (`pixel_test`, ægte HA mod `home.rtr.dk`,
    EKSISTERENDE data fra tidligere sessioner): migration v8→v9 verificeret direkte i databasen
    (`user_version=9`, `multi_widget`-skema uden `title`-kolonne, alle 5 eksisterende widget-rækker
    + 14 slots bevaret uden datatab); en placeret multi-entity-widget renderede identisk efter
    domain-registry-refaktoren (ikoner, danske state-tekster, outline/fuld-farve-styling); tryk på
    en lys-række udløste korrekt bekræft-dialogen (`"TRIGGER"`-omskrivningen ændrer ikke adfærd);
    config-skærmen for samme widget åbnede uden crash, viste `SlotCard` korrekt (ingen titel-felt),
    og ↑-knappen flyttede en slot korrekt (ikke gemt, for ikke at ændre brugerens rigtige widget).
    **Ikke live-testet:** LLT-hjælpedialogen (kræver frakobling af emulatorens ægte HA-forbindelse,
    som ikke kunne genoprettes uden et token efter denne sessions egen token-fjernelse — bevidst
    undladt for ikke at strande test-opsætningen) og RangeControlActivity/TextControlActivity/
    DateTimeControlActivity's `resolveHaApiClient`-sti (mekanisk, adfærds-lig ekstraktion, dækket af
    grøn build). Device-QA på S23 afventer bruger.
  - **Kendt, bevidst IKKE rettet i v0.2.45:** `formatEntityState`s state-tekster ("Tændt"/"Slukket"
    osv.) er hardcodet danske, uafhængigt af app-sprogvalget (da/en/sv) — opdaget under denne
    oprydning, men var ikke en del af de 5 udskudte fund. **Rettet i v0.2.46, se dér.**
- ✅ **v0.2.46 — al widget-statustekst lokaliseret + widgets opdaterer ved sprogskift
  (2026-07-07, opfølgning på v0.2.45's fund):**
  - **Omfang udvidet efter afklaring med bruger:** startede som "ret formatEntityState" (kun
    `MultiEntityWidget`), men samme mønster (hardcodet dansk uafhængigt af `da/en/sv`-valget) fandtes
    i **alle** 9 entity-widgets' live rendering — ikke kun `MultiDomainSupport.kt`. Bruger valgte
    eksplicit "alle widgets" frem for kun det oprindeligt flaggede sted.
  - **`MultiDomainSupport.kt`:** `DomainCapability.stateText`-lambdaen tager nu `Context` og
    resolver via nye `state_*`/`climate_*`-strengressourcer (alle 3 sprog) i stedet for danske
    literals; `formatEntityState()` fik en `context`-parameter (opdateret begge call-sites:
    `MultiEntityRendering.displayValueFor` og `MultiEntityPickerScreen` via `LocalContext.current`).
  - **De 8 øvrige live widgets** (`LightWidget`, `SwitchWidget`, `CoverWidget`, `BinarySensorWidget`,
    `AutomationWidget`, `SensorWidget`, `ClimateWidget`, `ScriptWidget`) + `GlanceWidgetCommon`s
    fælles `UnconfiguredWidgetContent` ("Opsæt"): alle hardcodede status-/loading-/"unavailable"-
    strenge erstattet med `context.getString(R.string.xxx)` — `BinarySensorContent` manglede helt en
    `context`-parameter og fik den tilføjet. `BinarySensorWidget`s ~20-værdis device-class-tabel
    (bevægelse/batteri/fugt/alarm osv.) fik hver sin strengressource; mange genbruges på tværs af
    device-classes hvor teksten reelt er identisk (fx "OK" på 5 forskellige classes).
  - **De 9 `*WidgetConfigActivity`s entity-picker-titler** ("Vælg lyskilde" osv.) var SEPARAT
    hardcodet i hver config-activity (ikke relateret til `MultiDomainSupport`). `BaseEntityPicker
    Activity.pickerTitle` ændret fra `abstract val` til `abstract fun pickerTitle()` — en `val`-
    initializer kører under objekt-konstruktion, FØR `attachBaseContext()`, hvor `getString()` endnu
    ikke er sikkert at kalde; en funktion udskyder opslaget til `onCreate`/`setContent`-tidspunktet.
  - **`RangeControlActivity`/`TextControlActivity`:** fandt undervejs (bredere grep for resterende
    danske literals) at disse to — brugt af LightWidget/CoverWidget/ClimateWidget/number/
    input_number's skyder-dialog samt input_text-dialogen — også havde hardcodede knap-/felt-labels
    ("Luk helt"/"Tænd"/"Værdi"/"Annullér"/"Gem" m.fl.). Rettet med nye `range_*`-ressourcer hhv.
    genbrug af eksisterende `R.string.cancel`/`R.string.save`.
  - **Ny opdaget fejl, rettet:** app'ens sprogvælger (`MainActivity`s `LanguageRow`/`setAppLocale`)
    kaldte IKKE `updateAllWidgets()` efter et sprogskift — modsat tema-vælgeren, som eksplicit gør
    det (ADR-5-kommentaren forklarer hvorfor: widgets observerer ikke `SecureStore` reaktivt). Uden
    dette ville placerede widgets først vise det nye sprog ved næste periodiske sync (op til 15 min).
    Tilføjet samme `scope.launch { updateAllWidgets(context) }`-kald i sprogvælgerens `onSelect`.
  - **Bevidst IKKE rettet (separat bug-klasse, større omfang):** `HaApiClient.kt`s forbindelses-
    fejlbeskeder ("Token afvist", "Kunne ikke nå HA: …") er stadig hardcodet danske — klassen har
    ingen `Context` og bruges fra mange call-sites; at rette kræver enten at tråde `Context` gennem
    hele netværkslaget eller omlægge `Result.Error` til at bære en strengressource-id + args i stedet
    for en færdigformateret besked. Ikke gjort her.
  - **QA:** build + 25 unit-tests grønne efter hver iteration. Emulator (`pixel_test`, ægte HA):
    skiftede app-sprog via den FAKTISKE in-app sprogvælger (Indstillinger → Sprog → Svenska) —
    placeret multi-entity-widget skiftede STRAKS fra "Slukket" til "Av" uden manuel widget-
    genopfriskning (bekræfter `updateAllWidgets()`-fixet virker via den rigtige brugervej, ikke kun
    via adb-bypass som IKKE trigger widget-opdatering). `LightWidgetConfigActivity`s entity-picker
    viste korrekt "Välj ljuskälla" (lokaliseret titel) + "Av" (lokaliseret off-state) på svensk.
    `ConfirmActionActivity`s bekræft-dialog verificeret på engelsk ("Turn on Hue Stuelampe?").
    **Kendt, ikke-regressivt fund:** enkelt-widgets' entity-picker-lister (BaseEntityPickerActivity)
    viser stadig rå `"unavailable"`-state uoversat i chippen — det gjorde de også FØR denne ændring
    (kun "on"/"off" blev nogensinde oversat i disse pickere; `else -> state` er uændret adfærd, ikke
    en regression, og ikke rettet her).
- ✅ **v0.2.47 — kort label udvidet fra 12 til 22 tegn (2026-07-07, brugerønske):**
  - `if (it.length <= 12)` → `<= 22` på de 3 steder der håndhæver grænsen: enkelt-widgets'
    label-felt (`BaseEntityPickerActivity.kt`), MultiEntityWidget-slottets hoved-label og
    sekundær-chip-label (begge `MultiEntitySlotEditor.kt`). Tilhørende supporting-tekster
    (`short_label_supporting`/`chip_label_supporting`, alle 3 sprog) og kommentaren i
    `MultiWidgetSlotEntity.kt` opdateret til "22 tegn"/"22 characters"/"22 tecken". Ren UI-
    grænse — ingen Room-migration nødvendig (`label` var altid en ubegrænset `String`-kolonne).
    `docs/widget-settings-spec.md` (kanonisk spec) opdateret til at matche.
  - **QA:** build + unit-tests grønne. Emulator: åbnede `LightWidgetConfigActivity`s label-felt,
    indtastede 26 tegn, feltet stoppede korrekt ved præcis 22 ("1234567890123456789012"),
    supporting-teksten viste "Max 22 tecken" (app-sprog var stadig svensk fra v0.2.46-QA).
- ✅ **v0.2.48-50 — MultiEntityWidget: slukket hoved-række uden ring + climate-varme-rød + chip-ring
  fjernet (2026-07-07, brugerfeedback på v0.2.42-46's styling):**
  - **v0.2.48 — slukket hoved-række uden ring:** `surfaceFor()` (`MultiEntityRendering.kt`) gav
    tidligere BÅDE rækker og chips en primary-ring når on/off-domænet var slukket. Bruger ønskede at
    hoved-rækken (ikke chippen) skal være rent neutralt gråt fyld uden ring når slukket — kun det
    fyldte lag skiller nu tændt/slukket for rækker; chips beholdt deres outline (indtil v0.2.50, se
    nedenfor).
  - **v0.2.48 — climate/hvac "varmer nu" → rød:** ny `hvacActionFromJson()`-helper
    (`GlanceWidgetCommon.kt`) læser climate-entitetens `hvac_action`-attribut (hvad anlægget FAKTISK
    gør: heating/cooling/idle/off — adskilt fra `state`, som er den VALGTE hvac_mode). En climate-
    række/chip bliver rød når `hvac_action == "heating"` — matcher enten visnings- ELLER action-
    entiteten (samme mønster som `isUnavailable`), så en slot der viser en anden entitet end den
    styrede spa/klimaanlæg stadig farves rødt. Alle andre climate-tilstande (idle/cool/auto/off)
    forbliver neutrale — bevidst fravalgt at farve køling blå (climate.isActive er i forvejen aldrig
    true, se v0.2.34-fund).
  - **v0.2.49 → v0.2.50 — rød-nuance valgt iterativt af bruger:** startede med Material Red 700
    (`#D32F2F`, for kraftig/mørk), derefter lys koral `#FF6B6B` (v0.2.49, brugerfeedback: "står ikke
    godt med de øvrige farver" — for mættet/skrigende ved siden af app'ens dæmpede lavendel-blå
    tændt-farve `#B8C6EE` og grå slukket-farve `#44464F`, begge samplet direkte fra et S23-
    skærmbillede). Endte på dæmpet rose-rød `#CF6679` (v0.2.50) — valgt via en side-om-side visuel
    sammenligning (klikbar mockup) mod de faktiske sampled hex-værdier. `WidgetColors.heatingFill`/
    `onHeating` (hvid tekst) — fast, tema-uafhængig, som resten af widget-paletten.
  - **v0.2.50 — chip-ring fjernet helt:** brugerfeedback: den mørke ring om AKTIVE chips (v0.2.42,
    `WidgetColors.chipActiveRing`) skulle enten væk eller matche den blå tændt-farve. Valgte at
    fjerne ringen helt (chippen står nu solidt `primary`/`onPrimary` som rækken, ingen ring-lag) —
    simplere end at style en blå ring der reelt ville være usynlig oven på en allerede blå flade.
    `chipActiveRing`-konstanten fjernet fra `WidgetColors.kt` (ubrugt efter ændringen). Slukkede
    chips beholder deres outline uændret (kun aktive/tændte mistede ringen).
  - **QA:** build grøn efter hver iteration. Border-fjernelse (v0.2.48) og chip-ring-fjernelse
    (v0.2.50) verificeret direkte på Galaxy S23 mod ægte HA (skærmbilleder: aktive Power/Filter-
    chips solidt blå uden sort kant, slukket "Planlagt opv"-række neutralt gråt uden ring). Climate-
    varme-rød (v0.2.48) verificeret ved at HA's rigtige spa (`climate.spav2_spa_thermostat`) reelt
    varmede under testen (bekræftet ægte `hvac_action`-værdi via MCP før/efter); rose-nuancen
    (v0.2.50) bruger-bekræftet OK på S23. Emulator (`pixel_test`) brugt til at verificere
    entity-attributter og DB-cache undervejs (direkte SQL-injektion af testtilstand i
    `entity_state`-tabellen for at fremtvinge visning uden at skulle vente på spaen varmede rigtigt).
- **v0.2.51 og v0.2.56** blev lavet i en anden session (sekundær-chip-sortering/tættere config-UI
  hhv. "Vis historik"-handling + chip-fix + chip-border — se `git log` for detaljer) og blev ikke
  logget her dengang; nævnt for at forklare hvorfor versionsnummeret springer fra v0.2.50 til v0.2.57
  nedenfor.
- ✅ **v0.2.57 — MultiEntityWidget: climate-varme-farve skiftet til HA's egen orange (2026-07-08,
  brugerønske):** `WidgetColors.heatingFill` ændret fra den dæmpede rose `#CF6679` (v0.2.50) til
  `#FF6D00` — brugeren sammenlignede direkte mod et skærmbillede af sit eget HA-dashboard (en
  climate-korts "Aktuel temp"-badge i orange) og ville have samme farve i widgetten. Fire
  HA-inspirerede orange-nuancer blev stillet op visuelt (A `#FF9800` HA-standard/gul-orange, B
  `#FF6D00` dybere orange, C `#FF5722` orange-rød, D `#FF3D00` mest mættet) — brugeren valgte B som
  bedste match til det rødlige/mættede udtryk i sit skærmbillede. `onHeating` (hvid tekst) uændret.
  **QA:** build grøn, installeret + bruger-bekræftet OK på Galaxy S23.
- ✅ **v0.2.58 — MultiEntityWidget: "skjul ikon" pr. hoved-slot + pr. sekundær-chip (2026-07-09,
  brugerønske):**
  - **Data (Room v9→v10):** ny `showIcon: Boolean = true` på hoved-slotten (ikke-nullable, DEFAULT 1
    i migrationen — eksisterende rækker uændrede) + ny nullable `secondaryNShowIcon` × 3, samme
    mønster som `secondaryNShowValue` (`showIconOrDefault() = showIcon ?: true`).
  - **Config-UI:** ny "Vis ikon"-switch i hoved-entitetens **Visning**-sektion
    (`MultiEntitySlotEditor.kt`) og en tilsvarende switch pr. sekundær-chip (ved siden af det
    eksisterende "Vis værdi på chippen").
  - **Rendering (`MultiEntityRendering.kt`):** når hoved-rækkens ikon skjules, fylder navn/status-
    kolonnen hele rækkens bredde (intet tomrum efterladt — brugervalg). For chips: skjules ikonet,
    springes kun den indledende ikon-spacer over; har chippen hverken label eller værdi, bliver den
    bevidst helt tom (tilladt, intet automatisk ikon-fallback — brugervalg).
  - **Chip-padding-skævhed (bruger-rapporteret, uafklaret):** bruger observerede mere luft i venstre
    end højre side af sekundær-chips på S23 (skærmbillede). Al padding/ring-logik i
    `StatefulSurface`/`SecondaryChip` er verificeret symmetrisk i koden (samme dp-værdi begge sider
    på hvert lag) — årsagen er ikke fundet ved kodegennemgang alene og kan være en RemoteViews/One
    UI-specifik rendering-kvirk (fx ripple/foreground-drawable fra `clickable()`). **Ikke rettet i
    denne omgang** — afventer en tættere beskåret skærmbillede af én enkelt chip fra bruger for at
    kunne diagnosticere præcist.
  - **QA:** build grøn, installeret på emulator (`pixel_test`) og Galaxy S23 (`adb install -r`,
    reinstall). App verificeret cold-start uden crash på emulator (logcat, ingen FATAL). **Bruger
    tester selv funktionsflowet** (config-switches, rendering med/uden ikon, migration af
    eksisterende widgets) — ikke gennemført af Claude denne session efter eksplicit brugerønske.
  - **Bruger-bekræftet OK på S23 (2026-07-09).**
- ✅ **v0.2.59 — MultiEntityWidget: bredere chip-padding uden ikon (2026-07-09, opfølgning på
  v0.2.58):** bruger observerede at fjernet chip-ikon "afslørede" en meget stram 6dp kant-padding
  (før virkede 6dp proportionalt fin fordi ikonets 14dp+4dp-spacer gav en ekstra visuel buffer foran
  teksten). Al padding er verificeret matematisk symmetrisk i koden (samme dp begge sider, uafhængigt
  af `showIcon` — `StatefulSurface` wrapper indholdet udefra og ved ikke om der er ikon inde i det).
  Nye konstanter `CHIP_INNER_H_PAD_NO_ICON_DP=6`/`CHIP_SINGLE_H_PAD_NO_ICON_DP=8` (`MultiEntity
  Rendering.kt`) — `SecondaryChip` vælger nu 8dp total padding (var 6dp) når `chip.showIcon==false`,
  uændret 6dp når ikonet vises. Kun chips ramt (bekræftet med bruger at kun chips var relevante, ikke
  hoved-rækken). **QA:** build grøn, installeret på emulator + S23 (`adb install -r`). Visuel
  bekræftelse på device afventer bruger.
- ✅ **v0.2.60 — MultiEntityWidget: asymmetrisk chip-padding når ikon vises (2026-07-09,
  opfølgning på v0.2.58-59):** bruger identificerede den reelle årsag til skævheden fra
  screenshot-diskussionen: selve ikon-asset'et har sin egen indre "luft" i sit 14dp-felt, hvilket
  gør at teksten i den anden ende visuelt sidder skævt selv med matematisk symmetrisk 6dp/6dp
  container-padding. Rettelse: når `chip.showIcon == true`, er venstre side (før ikon) fortsat 6dp,
  højre side (efter tekst) bumpet til 8dp — nye `CHIP_INNER_H_PAD_END_DP=6`/
  `CHIP_SINGLE_H_PAD_END_DP=8` (ring: +2dp = 8dp), brugt via `GlanceModifier.padding(start=…,
  end=…)` i stedet for det tidligere symmetriske `padding(horizontal=…)`. Ikon-skjult-tilstanden
  (v0.2.59, symmetrisk 8dp/8dp) er uændret. **Scope bekræftet med bruger: kun chips** — hoved-
  rækken (`SlotRow`) er ikke rørt. **QA:** build grøn, installeret på emulator + S23
  (`adb install -r`). Visuel bekræftelse afventer bruger.
- ✅ **v0.2.61 — MultiEntityWidget: "Vis historik" åbner nu more-info-dialogen (2026-07-09,
  brugerønske):** bruger ønskede at "Aktiviteter" (HA's logbog) skal være tilgængelig i det
  vindue der åbnes ved HISTORY-handlingen, ikke kun historik-grafen. Afklaret med bruger
  (spørgsmål med 3 valgmuligheder): skift fra den rene `/history`-side til entitetens
  more-info-dialog, som samler historik-graf OG logbog/aktivitet i én dialog med faner
  (samme dialog man får ved at klikke en entitet på et rigtigt HA-dashboard).
  - **Fix (`MultiEntityClickModifier.kt`):** `EXTRA_NAVIGATE_PATH` ændret fra
    `"/history?entity_id=$actionEntityId"` til `"/lovelace?entity_id=$actionEntityId"`.
    HA's frontend synker more-info-dialogens åben/lukket-state med URL'ens `?entity_id=`-
    query (`url-sync-mixin`) — samme mekanisme som når man selv klikker en entitet på et
    dashboard. Uændret client-side SPA-navigation (pushState + `location-changed`-event,
    samme mønster som den oprindelige HISTORY-handling fra v0.2.56).
  - Gælder BÅDE hoved-slottet og sekundær-chips (fælles `clickModifier`-funktion, ingen
    separat kode for de to).
  - **QA:** build grøn. Emulator (`pixel_test`, ægte HA mod `home.rtr.dk`): tap på en
    HISTORY-konfigureret sensor-række (`sensor.termometer_temperature`, appWidgetId 15)
    udløser korrekt `WebViewActivity`-overlayet og WebSocket forbinder (`connection-status:
    connected` + `theme-update` i logcat) — men selve HA-dashboardet blev aldrig færdigt
    (den native loading-spinner forsvandt ikke, `onDashboardReady` blev aldrig kaldt,
    reproduceret identisk 4×, inkl. efter `am force-stop` for frisk proces). Da
    `EXTRA_NAVIGATE_PATH` kun bruges INDE i `onDashboardReady`-callbacket, sker stoppet
    FØR denne ændrings kode overhovedet eksekverer — samme hæng ville opstå for ALLE
    WebViewActivity-åbninger (fx også ShortcutWidget) på denne emulator lige nu, uafhængigt
    af denne ændring. **Ikke** visuelt bekræftet at more-info-dialogen rent faktisk viser
    Historik+Logbog-faner. Device-QA (S23) og/eller undersøgelse af hvorfor dashboard-
    loading hænger på emulatoren afventer bruger. **Superseret af v0.2.62 — se dér, root
    cause fundet.**
- ✅ **v0.2.62 — MultiEntityWidget: HISTORY-hæng løst (root cause fundet); more-info-dialog
  droppet, "Aktiviteter" kræver bruger-beslutning (2026-07-09, systematisk debugging efter
  brugerrapport "samme resultat på S23 — åbner aldrig Aktiviteter/Logbogen"):**
  - **Root cause (bekræftet via Home Assistant MCP):** `WebViewActivity`s INDLEDENDE side-load
    for HISTORY-handlingen brugte (både før og efter v0.2.61) `EXTRA_DASHBOARD_PATH=""`, som i
    `buildUrl()` falder tilbage til det hardcodede antagne standard-dashboard `"lovelace"`.
    Denne HA-instans (`home.rtr.dk`) har INGEN dashboard på url_path `lovelace` — alle 4
    dashboards er custom-navngivne (`lovelace-hjem`, `dashboard-f4`, `dashboard-stue`,
    `linus_dashboard`); `ha_config_get_dashboard(url_path="default")` returnerer eksplicit
    "No config found". Navigation til den ikke-eksisterende rute fik HA's frontend til aldrig
    at nå den tilstand `KioskScript` venter på (`home-assistant-main`s shadow-root) — og
    `KioskScript`s klar-poll giver kun 40×300ms=12s, hvorefter den permanent stopper
    (`clearInterval`) uden nogensinde at kalde `onDashboardReady()`. Det er derfor IKKE en
    engangs-emulator-flaky-hed: reproduceret identisk på BÅDE emulator og S23 (bruger-
    bekræftet), og var reelt en PRE-EKSISTERENDE fejl i HISTORY-handlingen siden v0.2.56 —
    aldrig faktisk verificeret end-to-end før nu, uafhængigt af v0.2.61-ændringen.
  - **Fix (`MultiEntityClickModifier.kt`):** `EXTRA_DASHBOARD_PATH` ændret fra `""` til det
    indbyggede `"history"`-panel (findes ALTID, uafhængigt af brugerens Lovelace-dashboards —
    modsat det formodede `"lovelace"`-standarddashboard). **Verificeret virkende** på emulator:
    historik-grafen for `sensor.termometer_temperature` renderer nu korrekt med det samme.
  - **More-info-dialog-forsøget (v0.2.61) droppet igen efter empirisk test:** afprøvede at
    pushState'e til en af de FAKTISKE dashboards (`/lovelace-hjem?entity_id=X`, ikke den
    ikke-eksisterende `lovelace`) for at teste om `?entity_id=`-query'en overhovedet kan
    udløse more-info-dialogen via denne apps client-side SPA-navigation (pushState +
    `location-changed`-event). Resultat: dashboardet loader, men INGEN dialog åbner — kun det
    rå dashboard-indhold vises. HA's `url-sync-mixin`-mekanisme reagerer tilsyneladende ikke
    på en programmatisk `pushState` efter appen allerede er bootet (kun på en RIGTIG
    sidenavigation/deep-link), så tricket er ikke brugbart i denne apps arkitektur uden en
    fuld sideindlæsning (langsom, ~10s+, pr. tryk — dårlig UX, fravalgt).
  - **`/logbook?entity_id=X` afprøvet og VIRKER** (panelet hedder bogstaveligt "Aktivitet" på
    dansk — matcher brugerens eget ord) — men ERSTATTER historik-grafen, viser den ikke ved
    siden af (HA har intet indbygget panel der viser begge dele uden en gyldig Lovelace-
    dashboard-kontekst). For `sensor.termometer_temperature` viste den "Der blev ikke fundet
    nogen aktivitet" — forventet HA-adfærd (kontinuerte numeriske sensorer logges typisk ikke
    i logbogen), ikke en fejl i vores kode; entity_id-filter-chippen viste korrekt den rigtige
    entitet, så selve mekanismen er bekræftet virkende.
  - **Bruger-beslutning (spørgsmål med 3 valgmuligheder):** skift HISTORY-handlingen helt til
    `/logbook` — dropper historik-grafen til fordel for aktivitetsloggen, fremfor at bygge et
    "kendt dashboard"-fallback for at redde more-info-dialog-tricket (mere arbejde, ekstra
    ventetid pr. tryk) eller beholde kun grafen uden aktivitet.
  - **Endelig fix (`MultiEntityClickModifier.kt`):** `EXTRA_DASHBOARD_PATH` sat direkte til
    `"logbook?entity_id=$actionEntityId"` (ingen separat `EXTRA_NAVIGATE_PATH`/SPA-pushState-
    trin nødvendigt — `buildUrl()` håndterer et allerede-forekommende `?` i stien korrekt,
    entity_id-filteret er derfor med fra selve den indledende sideindlæsning). Handlingens
    UI-label ændret fra "Vis historik"/"Historik" til "Vis aktivitet"/"Aktivitet" i alle 3
    sprogfiler (`action_history`/`action_history_short`) — teksten skal matche den faktiske
    nye adfærd. Handlingens interne id (`"HISTORY"` i DB'en) er UÆNDRET — ren UI-tekst- og
    navigations-ændring, ingen migration nødvendig.
  - **QA:** build grøn. Emulator (ægte HA mod `home.rtr.dk`): tap på
    `sensor.termometer_temperature`-raden (appWidgetId 15, slot 2) åbner nu korrekt
    "Aktivitet"-panelet med det samme (ingen hæng), korrekt entity-filter-chip, forventet tomt
    resultat for denne sensor-type. Midlertidigt testet med en lys-entitet
    (`light.hue_stuelampe`, direkte DB-mutation af samme slot, reverteret bagefter til
    `sensor.termometer_temperature` — INGEN permanent ændring af brugerens rigtige
    widget-config) for at bekræfte panelet også kan vise ikke-tomme resultater; selve
    tomheds-verifikationen ovenfor er den der blev kørt på den RIGTIGE, permanente
    slot-konfiguration. Installeret på emulator + S23 (`adb install -r`). Visuel
    device-bekræftelse på S23 afventer bruger.
- ✅ **v0.2.63 — Aktivitet-panelet: HA-header skjult + 36-timers standardvindue (2026-07-09,
  brugerønske efter v0.2.62):**
  - **Ønske 1 — fjern HA's egen header (hamburger/titel/filter-ikon) fra vinduet:** root cause
    fundet via diagnostisk `console.log` af `KioskScript`s shadow-root-scan (midlertidig,
    fjernet igen efter brug): `/history`/`/logbook`-panelernes toolbar sidder i
    `ha-top-app-bar-fixed`s EGEN shadow-root som `<header class="top-app-bar">` — hverken det
    gamle `host==='hui-root'`-filter (kun Lovelace-dashboards) eller CSS-selectorlisten
    (`.header,.toolbar,ha-app-toolbar,app-header,.mdc-top-app-bar`) ramte denne kombination
    (klassen er `top-app-bar`, ikke `header`). To uafhængige fixes i `KioskScript.kt`:
    (1) `HEADER_CSS` injiceres nu i ALLE shadow roots (ikke kun `hui-root`) — selectoren er
    scoped, så injektion i en shadow root uden de elementer er en no-op; (2) selector-listen
    udvidet med `.top-app-bar`. Diagnosticeret ved midlertidigt at logge alle shadow-root-
    hosts + innerHTML for paneler matchende /panel|tabs|toolbar|app-bar/i via
    `webChromeClient.onConsoleMessage` → logcat, IKKE ved at gætte.
  - **Ønske 2 — 36 timers logs i stedet for panelets eget ~3-timers standardvindue:**
    `MultiEntityClickModifier.kt` beregner nu `start_date`/`end_date` (ISO 8601, ms-præcision,
    `Instant.now()`/`.minus(36, HOURS)`) og sender dem med i selve `/logbook`-dashboard-stien.
    HA's logbook-panel læser begge fra URL'en ved selve sideindlæsningen (samme mekanisme som
    `entity_id`, se v0.2.62).
  - **QA:** build grøn. Emulator (ægte HA mod `home.rtr.dk`), tap på
    `sensor.termometer_temperature`-raden (appWidgetId 15, slot 2): header (hamburger/"Aktivitet"
    -titel/filter-ikon) er væk — kun appens egen X-luk-knap øverst; dato-feltet viser korrekt et
    36-timers vindue ("8. jul. 03.18 - 9. jul. 15.18" ved test kl. 15.19). Installeret på
    emulator + S23 (`adb install -r`). Visuel device-bekræftelse på S23 afventer bruger.
- ✅ **v0.2.64 — Aktivitet-handling: domænebaseret graf/liste-valg (2026-07-09, brugerrapport
  efter v0.2.63 — "Udestue Temperaturen" viste tom aktivitetsliste, hvor rigtig HA viser graf):**
  - **Bekræftet med bruger:** ikke en fejl i entity_id/tidsvindue-mekanikken — HA's egen logbog
    ekskluderer som forventet kontinuerte numeriske sensor-tilstandsskift, så `/logbook` er
    reelt altid tom for den slags entiteter (matcher hvad rigtig HA selv viser: kun graf).
    Andre domæner (light/switch/lock osv.) virker allerede fint med logbog-listen.
  - **Fix (`MultiEntityClickModifier.kt`):** HISTORY-handlingen vælger nu panel ud fra
    `actionDomain` — `sensor`/`number`/`input_number` (kontinuerte værdi-domæner) → `/history`
    (grafen, som rigtig HA også bruger for disse); alle andre domæner → `/logbook` (uændret fra
    v0.2.63). 36-timers `start_date`/`end_date`-vinduet gælder nu begge paneler ens (samme
    URL-forgrening, kun panel-navnet skifter).
  - **QA:** build grøn. Emulator (ægte HA mod `home.rtr.dk`): tap på
    `sensor.termometer_temperature`-raden viser nu korrekt en 36-timers temperaturgraf (ingen
    HA-header, "8. jul. 03.34 - 9. jul. 15.34" i dato-feltet) i stedet for den tomme
    aktivitetsliste fra v0.2.63. Installeret på emulator; S23 var ikke tilsluttet ved
    session-slut — installation og bekræftelse dér afventer bruger.
- ✅ **v0.2.65 — Widget-farvetemaer: 6 globale, valgbare farver (2026-07-09, brugerønske via
  dev-pipeline):**
  - **Baggrund:** genoptaget fra en parallel session (`feature/widget-color-themes`-worktree,
    startet under en anden Claude-login på samme maskine). Featuren tilføjer 6 globale
    farvetemaer (Blå/Grøn/Lilla/Orange/Rød/Teal) til ALLE Glance-widgets, uafhængigt af
    app-UI'et (som forbliver fast blå). Proces: brainstorm → spec → plan →
    subagent-implementering → code-review → QA. Spec:
    `docs/specs/2026-07-09-widget-color-themes-design.md`, plan:
    `docs/superpowers/plans/2026-07-09-widget-color-themes.md`.
  - **Arkitektur:** nyt `SecureStore.widgetColorTheme` (samme mønster som `themeMode`). Ny
    `WidgetColorPresets.kt` med `WidgetColorPreset(light, dark)` + `presetFor()` + én kilde til
    sandhed `WIDGET_COLOR_THEMES` (læst af BÅDE pickeren og `presetFor`). Kun `primary`/`onPrimary`
    er hue-specifikke; de neutrale roller (surfaceVariant/error/background/surface) genbruges fra
    de eksisterende `HaBlue*`-konstanter (verificeret farve-neutrale) på tværs af alle presets.
    `WidgetColors.providers(context)` kombinerer farvetema + `themeMode` via ren, unit-testet
    `resolveColorMode()` (`DYNAMIC`/`FORCED_LIGHT`/`FORCED_DARK`/`SYSTEM_PAIR`) — **Blå+system
    bevarer den historiske `DynamicThemeColorProviders` (nul regression)**; alt andet bruger faste
    presets. 15 nye unit-tests (8 presets + 7 color-mode).
  - **UI:** ny "Farvetema"-dropdown i `MainActivity`s indstillinger (ved Tema/Sprog), med en
    farveprik pr. valg. Kalder `updateAllWidgets()` ved skift (widgets observerer ikke SecureStore
    reaktivt — ADR-5). Ingen `recreate()` (påvirker kun widgets, ikke app-UI'et).
  - **Merge:** `main` v0.2.64 flettet rent ind i branchen (kun additive `strings.xml`-overlap,
    auto-merged) → v0.2.65 = supersæt (farvetemaer + Aktivitet-panel fra v0.2.61-64).
  - **Code-review-fixes (samme serie):** (1) dropdown-swatch bruger nu `dark.primary` i mørkt tema
    (matcher den farve widgetten faktisk renderer) i stedet for altid `light.primary`; (2)
    `ThemeRow`/`LanguageRow`/`ColorThemeRow` samlet i én generisk `SettingsDropdownRow<T>` (de tre
    var near-dubletter); (3) `WIDGET_COLOR_THEMES` som eneste kilde til farvesættet.
  - **QA:** build + 40 unit-tests grønne. Emulator (`pixel_test`, ægte HA): ny Farvetema-række +
    6 korrekte swatches, valg af Grøn → placeret multi-widget blev grøn uden manuel opdatering,
    revert til Blå, swatch-nuancer korrekte i mørkt tema efter fix. Ingen crashes.
- ✅ **v0.2.66 — dashboard-genvej (ShortcutWidget) følger nu farvetemaet (2026-07-09,
  brugerønske):** genvejens konfigurerede tile brugte hardcodet `Color(0xFF03A9F4)` (app-ikon-blå)
  + hvid — den ENESTE widget der ikke fulgte det globale farvetema. Bruger nu
  `GlanceTheme.colors.primary`/`onPrimary` (genvejen er altid "tændt" → samme aktive look som
  entity-widgets). **QA:** verificeret på S23 (Nova) — placerede genveje "Hjem"/"Linus Dashbo…"
  vises i temaets farve (rød/lyserød ved test) og konfigureret.
- ✅ **v0.2.67 — frisk-placeret dashboard-genvej registrerer config med det samme (2026-07-09,
  brugerrapport + systematic-debugging):**
  - **Root cause (reproduceret på emulator):** `ShortcutWidget.provideGlance` læste config ÉN gang
    fra ikke-reaktiv `WidgetConfigStore` (SharedPreferences). Ved placering kørte `provideGlance`
    med `null` config, og `saveAndFinish`'s fire-and-forget `update()` genfremkaldte den ikke
    pålideligt → en frisk-placeret genvej viste "Opsæt"/"Konfigurera" SELVOM config VAR gemt
    (bekræftet: `widget_20` i prefs, men flisen renderede "Konfigurera"); man måtte konfigurere
    igen. **Identisk med v0.2.2-bugten for entity-widgets, som blev løst med reaktiv Room Flow —
    genvejen blev bare aldrig migreret.**
  - **Fix:** ny `WidgetConfigStore.observe(appWidgetId)` (callbackFlow på
    `OnSharedPreferenceChangeListener`) + `ShortcutWidget.provideGlance` collecter den via
    `collectAsState`, præcis som entity-widgets' Room-Flow. Glance-sessionen holdes i live og
    rekomponerer straks når config-activity'en gemmer.
  - **QA:** reproduceret FØR (frisk genvej = "Konfigurera") og verificeret EFTER (frisk genvej =
    grøn konfigureret flise straks) på emulator. Præeksisterende bug (ikke fra farvetema-arbejdet),
    men merged sammen med serien. Device-QA på S23 (Nova rammes hårdest) afventer bruger.
  - **Merged til `main` (fast-forward) 2026-07-09.** IKKE pushed endnu.
- ✅ **v0.2.68 — Widget-konvergering: slettet alle 9 single-entity-widgets (2026-07-09,
  brugerbeslutning, subagent-driven-development):**
  - **Baggrund:** appen har ingen udgivne brugere, så vi er frie til at slette widget-providers
    uden bagudkompatibilitets-hensyn. Multi-widgetten dækker allerede alt de 9 singles kunne
    (toggle/trigger/range/sensor-værdi/domæne-ikoner/enheder) og har desuden visning/handling-
    opdeling, bekræft-ved-tryk, sekundær-chips og værdi-formatering — mens de 9 singles kostede
    10× vedligehold. Appen har fremover kun **to** widgets: `ShortcutWidget` (dashboard-genvej)
    og `MultiEntityWidget`. Proces: brainstorm → spec → plan (7 tasks) → subagent-driven
    implementering (frisk implementer + task-reviewer pr. task) → opus whole-branch-review.
    Spec: `docs/superpowers/specs/2026-07-09-widget-convergence-design.md`, plan:
    `docs/superpowers/plans/2026-07-09-widget-convergence.md`.
  - **Slettet:** 9 widget-pakker (light/switch/scene/script/automation/sensor/binary_sensor/
    cover/climate — hver `*Widget.kt` + `*WidgetConfigActivity.kt`), `BaseEntityPickerActivity`,
    18 manifest-komponenter (9 receivers + 9 config-activities), 9 `*_widget_info.xml`, 9
    `preview_*`-drawables, og de forældreløse single-strenge (9 `*_widget_description`, 9
    `picker_title_*`, 18 `binary_*`, 9 `*_widget_label`) + 3 forældreløse drawables
    (`ic_power`/`ic_thermometer`/`ic_humidity`).
  - **Bevaret:** al delt `widget/common/`-infra (GlanceWidgetCommon, EntityActions,
    MultiDomainSupport, kontrol-aktiviteterne, ValueFormatting, RangeStepping, WidgetColors/
    Presets, ActionFeedback), ALLE `ic_*`-domæne-ikoner (multi bruger dem via `MultiDomainSupport`),
    og delte strenge (`state_*`/`climate_*`).
  - **Data-lag gjort multi-only:** `EntityRepository.refreshAll` samler kun entiteter fra
    multi-slots; `WidgetUpdater.updateForEntity` opdaterer kun `MultiEntityWidget`;
    `updateAllWidgets` 11→2 kald. **Room-migration `MIGRATION_10_11`** (v10→11) dropper
    `entity_widget`-tabellen + `EntityWidgetEntity`/`Dao` fjernet fra `AppDatabase`;
    `entity_state`/`multi_widget`/`multi_widget_slot` uændrede.
  - **Kompakt 1×1-flise droppet bevidst** (brugerbeslutning): vil man vise én entitet, laver man
    en 1-slot multi-widget (fuld-bredde række, resizable). Intet kompakt layout tilføjet.
  - **QA (emulator `pixel_test`, ægte HA):** clean build + 55 unit-tests grønne; migration
    v10→11 verificeret oven på eksisterende data (`user_version=11`, `entity_widget`-tabel væk,
    5 multi_widget/14 slots/21 entity_state bevaret, ingen crash); widget-picker viser præcis 2
    widgets (ikke 11); placeret multi-widget re-renderer med live data; `SyncWorker` SUCCESS
    (multi-only sync). Whole-branch-review (opus): READY TO MERGE, 2 minor orphan-fund fixet.
    Device-QA på S23 afventer bruger.
  - **Merged til `main` (fast-forward) + pushed 2026-07-09.**
- ✅ **v0.2.69-70 — Widget-config discoverability: gate til app-opsætning + app-genvej
  (2026-07-09/10, brugerønske, subagent-driven-development):**
  - **Baggrund:** widget-først-brugere lander i launcherens widget-opsætning og opdager ikke, at
    global opsætning (forbindelse, sprog, tema, farver) bor i hoved-appen. Efter konvergeringen er
    der kun 2 config-skærme: `ShortcutWidgetConfigActivity` + `MultiEntityWidgetConfigActivity`.
    Proces: brainstorm → spec → plan (5 tasks) → subagent-driven implementering → opus
    whole-branch-review. Spec: `docs/superpowers/specs/2026-07-09-widget-discoverability-design.md`,
    plan: `docs/superpowers/plans/2026-07-09-widget-discoverability.md`.
  - **Delte composables** (`widget/common/ConfigDiscoverability.kt`): `NotConnectedGate` (kort med
    "Åbn HA Widgets"-knap → MainActivity), `AppSettingsHint` (diskret bund-henvisning "Sprog, tema
    og farver ændres i appen"), `rememberResumeTick()` (lifecycle ON_RESUME-tæller).
  - **Gate → én kilde til opsætning:** begge config-skærme viser gaten når `!SecureStore.isConfigured`
    (i stedet for multis blindgyde-fejl / genvejens inline URL+token-formular, som blev **fjernet** —
    forbindelse bor nu kun i appen). **Resume-recheck:** vender man tilbage forbundet, gen-tjekker
    `rememberResumeTick`-keyed effekt `isConfigured`, gaten forsvinder, config fortsætter automatisk
    (ingen gen-tilføjelse). Load kører kun én gang (`loaded`-guard).
  - **Deep-link:** `MainActivity.EXTRA_OPEN_SETTINGS` åbner indstillings-arket direkte.
  - **App-genvej i config (v0.2.70):** Tune-ikon-action i BEGGE config-skærmes TopAppBar (dér brugere
    kigger efter indstillinger) + den bevarede bund-henvisning → to synlige veje til app-indstillinger.
  - **v0.2.70 også:** `loadError` i multi-config gjort funktionel (try/catch om entitets-load,
    `load_entities_error`-streng) — var død/altid-null efter gate-omlægningen.
  - **Ryddet:** forældreløse strenge `ha_not_connected_error`, `connect_to_ha_title/body`.
  - **QA:** build + 55 unit-tests grønne. Emulator (forbundet): begge config-skærme viser
    top-bar-genvej + bund-henvisning; begge deep-linker til indstillings-arket; genvej-config viser
    dashboard-picker UDEN token-felt. **S23 bruger-bekræftet OK (2026-07-10).** *Ikke-forbundet-gaten
    kunne ikke testes non-destruktivt på emulatoren (ville strande token) — dækket af opus-review +
    S23-QA.*
  - **Merged til `main` (fast-forward) + pushed 2026-07-10.**

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
- ~~Discoverability~~ — **GJORT i v0.2.69-70** (gate + app-genvej i top-bar + bund-henvisning +
  deep-link + resume-recheck; genvejens inline connect fjernet).
- ~~Konvergering på multi-widget~~ — **GJORT i v0.2.68** (slettede alle 9 singles i stedet for
  skjul-men-behold, da appen ingen brugere har; ingen kompakt 1-slot-footprint tilføjet).
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
