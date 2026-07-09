# Widget-config discoverability: gate til app-opsætning + indstillings-henvisning

**Dato:** 2026-07-09
**Status:** Godkendt (brainstorm), afventer implementeringsplan
**Branch:** `feature/widget-discoverability`

## Mål

Gør app-opsætningen synlig for widget-først-brugere. Når en bruger tilføjer en widget direkte fra
launcherens widget-vælger, lander de i widget-opsætningen — men den globale opsætning (forbindelse til
Home Assistant, sprog, tema, farver) bor kun i hoved-appen (`MainActivity`). I dag ser en ikke-forbundet
bruger enten en blindgyde-fejl (multi) eller en inline connect-formular (genvej), og opdager aldrig
sprog/tema/farve-indstillingerne. Denne ændring leder dem tydeligt til appen.

## Baggrund og beslutninger

- Efter widget-konvergeringen (v0.2.68) er der kun **2** config-skærme: `ShortcutWidgetConfigActivity`
  og `MultiEntityWidgetConfigActivity`.
- **Nuværende inkonsistens:** `MultiEntityWidgetConfigActivity` sætter `loadError = haNotConnectedError`
  (en tekst-blindgyde uden vej videre) når `!store.isConfigured`. `ShortcutWidgetConfigActivity` har en
  inline connect-formular (Step 1: URL + token).
- **Beslutning 1 (brugervalg): Gate → åbn appen.** Begge config-skærme viser et "ikke forbundet"-kort
  med en `Åbn HA Widgets`-knap. Genvejens inline connect-formular **fjernes**. Én kilde til al opsætning
  = appen (forbindelse + sprog + tema + farver). Konsistent, mindre kode.
- **Beslutning 2 (brugervalg): Re-check ved retur.** Når brugeren vender tilbage fra appen (activity
  resume), gen-tjekkes `isConfigured`, og config'en fortsætter automatisk — ingen nød til at fjerne og
  gen-tilføje widgetten.
- **Beslutning 3 (brugervalg): Deep-link til indstillings-arket.** Indstillings-henvisningen åbner
  `MainActivity` med et intent-extra, så indstillings-arket åbnes automatisk (lander direkte på
  sprog/tema/farve-valgene).
- **Ingen eksisterende brugere** → ingen bagudkompatibilitets-hensyn.

## Komponenter

### Ny fil: `widget/common/ConfigDiscoverability.kt`

To delte Compose-`@Composable`'er (de 2 config-skærme er `ComponentActivity` + Compose, ikke Glance):

- `NotConnectedGate(onOpenApp: () -> Unit)`: et kort med et ikon, titel "Ikke forbundet", en kort
  forklaring ("Forbind appen til Home Assistant først — det gøres i HA Widgets-appen"), og en knap
  "Åbn HA Widgets" der kalder `onOpenApp`. Følger app-UI'ets Material3-stil (samme som resten af
  config-skærmene).
- `AppSettingsHint(onOpenSettings: () -> Unit)`: en diskret bund-række med et lille tandhjuls-ikon,
  teksten "Sprog, tema og farver ændres i appen" og en "Åbn"-affordance; kalder `onOpenSettings`.

### `MainActivity.kt`: deep-link til indstillings-arket

- Nyt intent-extra-konstant `EXTRA_OPEN_SETTINGS = "open_settings"` (Boolean) på `MainActivity`.
- I `onCreate` læses `intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)` og videregives til
  Compose-træet, så `showSettings`-state'en (`MainActivity.kt:119`) initialiseres til `true` når extra'et
  er sat. Arket åbnes kun i den forbundne tilstand (hvor tandhjulet/arket findes) — hvilket altid er
  tilfældet her, fordi `AppSettingsHint` kun vises når widgetten allerede er forbundet (samme
  `SecureStore`).

### `MultiEntityWidgetConfigActivity.kt`

- Erstat den nuværende `!store.isConfigured` → `loadError = haNotConnectedError`-blindgyde med at vise
  `NotConnectedGate { startActivity(Intent(context, MainActivity::class.java)) }`.
- Tilføj `AppSettingsHint { startActivity(MainActivity med EXTRA_OPEN_SETTINGS=true) }` nederst i den
  forbundne (normale) config-tilstand.
- Re-check ved resume (se nedenfor).

### `ShortcutWidgetConfigActivity.kt`

- **Fjern** den inline connect-formular (Step 1: URL/token-felter, connect-knap, `connecting`/
  `connectError`-state, `HaApiClient(...).checkConnection()`-kaldet og de tilhørende imports/felter).
- Når `!store.isConfigured`: vis `NotConnectedGate` (samme som multi).
- Tilføj `AppSettingsHint` nederst i den forbundne config-tilstand (dashboard-valg + visning).
- Re-check ved resume.

### Re-check ved resume (begge config-skærme)

Config-skærmen skal gen-evaluere `SecureStore.isConfigured` når aktiviteten kommer i forgrunden igen
(efter brugeren har forbundet i appen). Mekanisme: en `resumeTick`-tæller der inkrementeres på
`Lifecycle.Event.ON_RESUME` (via `LifecycleEventObserver` i en `DisposableEffect`), og som den
eksisterende "load entities / check isConfigured"-`LaunchedEffect` re-keyes på. Når brugeren vender
tilbage forbundet, kører load-effekten igen, `isConfigured` er nu true, gaten forsvinder, og
entitets-/dashboard-valget vises automatisk.

## i18n

Nye strenge i alle 3 filer (`values/`, `values-da/`, `values-sv/`):
- `not_connected_gate_title` — "Ikke forbundet" / "Not connected" / "Inte ansluten"
- `not_connected_gate_body` — forklaring
- `open_app_button` — "Åbn HA Widgets"
- `settings_in_app_hint` — "Sprog, tema og farver ændres i appen"
- `open_short` — "Åbn" (hvis ikke allerede findes; ellers genbrug)

Genbrug eksisterende strenge hvor muligt. Den nu-ubrugte `ha_not_connected_error` (efter multi ikke
længere bruger den) fjernes hvis grep bekræfter nul referencer.

## Non-goals

- Ingen "Tryk for at opsætte"-hint på selve widget-flisen (droppet — YAGNI; "Opsæt"-flisen åbner
  allerede config ved tryk).
- Ingen ændring af selve onboarding-flowet i `MainActivity` (kun deep-link-håndtering tilføjes).
- Ingen ændring af widget-rendering.

## Test / QA

1. **Build + unit-tests grønne** (55).
2. **Frakoblet-flow (emulator):** frakobl appen (eller frisk install uden token). Tilføj en multi-widget
   → config viser `NotConnectedGate` (ikke blindgyde-fejl). Tap "Åbn HA Widgets" → `MainActivity` åbner.
   Forbind i appen. Tryk tilbage → config'en re-checker, gaten forsvinder, entitets-valg vises
   automatisk. Gentag for genvej-config (bekræft at den gamle inline connect-formular er væk).
3. **Deep-link (emulator, forbundet):** åbn en config-skærm → `AppSettingsHint` vises nederst → tap →
   `MainActivity` åbner MED indstillings-arket allerede åbent på sprog/tema/farve-valgene.
4. **Ingen crash** gennem alle flows.
5. **S23 device-QA:** samme tjekliste på Nova.

## Risici

- **Re-check ved resume kan dobbelt-loade entiteter** hvis effekten re-keyes for aggressivt. Afbødning:
  key kun på `resumeTick` + `isConfigured`-overgang; load kun når forbundet.
- **Deep-link ved ikke-forbundet app** (teoretisk): kan ikke ske i praksis (hint vises kun når forbundet),
  men `MainActivity` skal alligevel ikke crashe hvis `EXTRA_OPEN_SETTINGS=true` modtages i frakoblet
  tilstand — arket åbnes blot ikke (eller åbnes tomt/harmløst). Verificér gracefully.
