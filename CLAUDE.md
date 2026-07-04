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
