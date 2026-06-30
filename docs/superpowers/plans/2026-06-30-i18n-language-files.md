# i18n: sprogfiler (dansk/engelsk/svensk) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gør app-UI'et flersproget (dansk/engelsk/svensk) med en in-app sprog-vælger, så appen kan deles med ikke-danske brugere.

**Architecture:** `res/values/strings.xml` bliver engelsk default-fallback; nyt `res/values-da/strings.xml` og `res/values-sv/strings.xml` tilføjes. Ny sektion i `MainActivity` lader brugeren vælge Dansk/English/Svenska/Følg system via platform `android.app.LocaleManager` (API 33+ direkte, ingen ny dependency).

**Tech Stack:** Kotlin, Jetpack Compose, `android.app.LocaleManager` (platform API 33+).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-06-30-i18n-language-files-design.md`.
- Scope: kun native app-UI (`strings.xml`-baserede tekster). WebView/HA-dashboard-indhold IKKE rørt.
- Sprog-skift virker kun fuldt på API 33+ (alle nuværende testenheder). Accepteret begrænsning på API 26–32 — no-op, ingen særlig fejlhåndtering.
- Ingen ny dependency (ingen `androidx.appcompat`).
- Ingen ny separat Indstillinger-skærm — sprog-valg lever i `MainActivity`, connected-state.
- Følg projektets release-workflow (`CLAUDE.md`): bump version FØR build, byg, QA på emulator (`pixel_test`), QA på rigtig enhed (`adb install -r`, ALDRIG uninstall), kun commit+push når begge QA-trin er grønne.
- Installer altid med `adb install -r` (bevarer data/token) — aldrig `adb uninstall`.

---

### Task 1: Version bump + tresproget string-ressourcer (dansk/engelsk/svensk)

**Files:**
- Modify: `app/build.gradle.kts:16-17` (versionCode/versionName)
- Modify: `app/src/main/res/values/strings.xml` (bliver engelsk default)
- Create: `app/src/main/res/values-da/strings.xml` (dansk, nuværende indhold flyttet hertil + nye sprog-strenge)
- Create: `app/src/main/res/values-sv/strings.xml` (svensk)

**Interfaces:**
- Produces: 5 nye string-resource-navne som Task 2 bruger i UI: `section_language`, `language_danish`, `language_english`, `language_swedish`, `language_follow_system`.
- Produces: alle eksisterende 59 string-navne forbliver uændrede (kun værdier pr. sprog ændres) — ingen consumer-kode rørt af denne task.

- [ ] **Step 1: Bump version**

I `app/build.gradle.kts`, ændr:

```kotlin
        versionCode = 12
        versionName = "0.2.12"
```

til:

```kotlin
        versionCode = 13
        versionName = "0.2.13"
```

- [ ] **Step 2: Skriv ny engelsk default `app/src/main/res/values/strings.xml`**

Erstat hele filens indhold med:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">HA Widgets</string>

    <!-- Widget metadata -->
    <string name="shortcut_widget_label">HA Dashboard Shortcut</string>
    <string name="shortcut_widget_description">Opens a Home Assistant dashboard.</string>
    <string name="open_dashboard">Open dashboard</string>

    <!-- Common -->
    <string name="cancel">Cancel</string>
    <string name="disconnect">Disconnect</string>
    <string name="connect">Connect</string>
    <string name="connecting">Connecting…</string>
    <string name="checking_connection">Checking connection…</string>
    <string name="ha_url_label">HA URL</string>
    <string name="token_label">Long-lived access token</string>

    <!-- MainActivity -->
    <string name="disconnect_dialog_title">Disconnect HA?</string>
    <string name="disconnect_dialog_body">This removes your connection. Existing widgets will stop working until you connect again.</string>
    <string name="connected_to">Connected to</string>
    <string name="add_widget_to_home">Add dashboard shortcut to home screen</string>
    <string name="pin_manual_instructions">Long-press an empty spot on the home screen → Widgets → find \'HA Widgets\' → drag the shortcut out.</string>
    <string name="pin_button_instructions">Tap the button and confirm in the dialog. Not working? Long-press the home screen → Widgets → \'HA Widgets\'.</string>
    <string name="how_to_use_title">How to use HA Widgets</string>
    <string name="how_to_use_step1">1. Tap \'Add dashboard shortcut\' above</string>
    <string name="how_to_use_step2">2. Choose dashboard and view in the configuration</string>
    <string name="how_to_use_step3">3. Tap the widget icon on the home screen to open your dashboard</string>
    <string name="onboarding_intro">Connect your Home Assistant instance below. You can also skip this and connect the first time you add a widget.</string>

    <!-- Language picker -->
    <string name="section_language">Language</string>
    <string name="language_danish">Dansk</string>
    <string name="language_english">English</string>
    <string name="language_swedish">Svenska</string>
    <string name="language_follow_system">Follow system</string>

    <!-- ShortcutWidgetConfigActivity -->
    <string name="configure_widget_title">Configure widget</string>
    <string name="save_widget">Save widget</string>
    <string name="select_dashboard">Select dashboard</string>
    <string name="section_dashboard">Dashboard</string>
    <string name="section_display">Display</string>
    <string name="display_fullscreen">Fullscreen</string>
    <string name="display_overlay">Overlay (window)</string>
    <string name="overlay_width">Width: %1$d %%</string>
    <string name="overlay_height">Height: %1$d %%</string>
    <string name="connect_to_ha_title">Connect to Home Assistant</string>
    <string name="connect_to_ha_body">Enter your HA address and a long-lived access token (HA → Profile → Long-Lived Access Tokens).</string>
    <string name="load_dashboards_error">Could not fetch dashboards: %1$s</string>

    <!-- Battery optimization dialog -->
    <string name="battery_dialog_title">Allow background access</string>
    <string name="battery_dialog_body">Widgets sync with Home Assistant in the background. If the system restricts background activity, tapping a widget may show stale data or fail to update.\n\nTap \"Allow\" to exempt HA Widgets from battery optimization — this keeps widgets responsive.</string>
    <string name="battery_dialog_allow">Allow</string>
    <string name="battery_dialog_later">Not now</string>
    <string name="battery_manage">Battery optimization</string>
    <string name="battery_status_exempt">Exempt from battery optimization</string>
    <string name="battery_status_restricted">Restricted by battery optimization</string>

    <!-- LightWidget -->
    <string name="light_widget_label">HA Light</string>
    <string name="light_widget_description">Control light sources</string>

    <!-- SwitchWidget -->
    <string name="switch_widget_label">HA Switch</string>
    <string name="switch_widget_description">Turn switches on/off</string>

    <!-- SceneWidget -->
    <string name="scene_widget_label">HA Scene</string>
    <string name="scene_widget_description">Activate scenes</string>

    <!-- ScriptWidget -->
    <string name="script_widget_label">HA Script</string>
    <string name="script_widget_description">Run scripts</string>

    <!-- AutomationWidget -->
    <string name="automation_widget_label">HA Automation</string>
    <string name="automation_widget_description">Trigger automations</string>

    <!-- SensorWidget -->
    <string name="sensor_widget_label">HA Sensor</string>
    <string name="sensor_widget_description">Show sensor values</string>

    <!-- BinarySensorWidget -->
    <string name="binary_sensor_widget_label">HA Binary sensor</string>
    <string name="binary_sensor_widget_description">Show binary sensors</string>

    <!-- CoverWidget -->
    <string name="cover_widget_label">HA Cover</string>
    <string name="cover_widget_description">Control blinds and shades</string>

    <!-- ClimateWidget -->
    <string name="climate_widget_label">HA Climate</string>
    <string name="climate_widget_description">Show and control temperature (2×1)</string>
</resources>
```

- [ ] **Step 3: Opret `app/src/main/res/values-da/strings.xml`** (dansk — nuværende indhold + nye sprog-strenge)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">HA Widgets</string>

    <!-- Widget metadata -->
    <string name="shortcut_widget_label">HA Dashboard-genvej</string>
    <string name="shortcut_widget_description">Åbner et Home Assistant-dashboard.</string>
    <string name="open_dashboard">Åbn dashboard</string>

    <!-- Common -->
    <string name="cancel">Annuller</string>
    <string name="disconnect">Frakobl</string>
    <string name="connect">Forbind</string>
    <string name="connecting">Forbinder…</string>
    <string name="checking_connection">Tjekker forbindelse…</string>
    <string name="ha_url_label">HA-URL</string>
    <string name="token_label">Long-lived access token</string>

    <!-- MainActivity -->
    <string name="disconnect_dialog_title">Frakobl HA?</string>
    <string name="disconnect_dialog_body">Dette fjerner din forbindelse. Eksisterende widgets holder op med at virke, indtil du forbinder igen.</string>
    <string name="connected_to">Forbundet til</string>
    <string name="add_widget_to_home">Tilføj dashboard-genvej til hjemskærm</string>
    <string name="pin_manual_instructions">Hold på et tomt sted på hjemskærmen → Widgets → find \'HA Widgets\' → træk genvejen ud.</string>
    <string name="pin_button_instructions">Tryk på knappen og bekræft i dialogen. Virker det ikke? Hold på hjemskærmen → Widgets → \'HA Widgets\'.</string>
    <string name="how_to_use_title">Sådan bruger du HA Widgets</string>
    <string name="how_to_use_step1">1. Tryk \'Tilføj dashboard-genvej\' ovenfor</string>
    <string name="how_to_use_step2">2. Vælg dashboard og visning i konfigurationen</string>
    <string name="how_to_use_step3">3. Tryk på widget-ikonet på hjemskærmen for at åbne dit dashboard</string>
    <string name="onboarding_intro">Forbind din Home Assistant-instans nedenfor. Du kan også springe dette over og forbinde første gang du tilføjer en widget.</string>

    <!-- Language picker -->
    <string name="section_language">Sprog</string>
    <string name="language_danish">Dansk</string>
    <string name="language_english">English</string>
    <string name="language_swedish">Svenska</string>
    <string name="language_follow_system">Følg system</string>

    <!-- ShortcutWidgetConfigActivity -->
    <string name="configure_widget_title">Konfigurér widget</string>
    <string name="save_widget">Gem widget</string>
    <string name="select_dashboard">Vælg dashboard</string>
    <string name="section_dashboard">Dashboard</string>
    <string name="section_display">Visning</string>
    <string name="display_fullscreen">Fuldskærm</string>
    <string name="display_overlay">Overlay (vindue)</string>
    <string name="overlay_width">Bredde: %1$d %%</string>
    <string name="overlay_height">Højde: %1$d %%</string>
    <string name="connect_to_ha_title">Forbind til Home Assistant</string>
    <string name="connect_to_ha_body">Indtast din HA-adresse og et long-lived access token (HA → Profil → Long-Lived Access Tokens).</string>
    <string name="load_dashboards_error">Kunne ikke hente dashboards: %1$s</string>

    <!-- Battery optimization dialog -->
    <string name="battery_dialog_title">Tillad baggrundsadgang</string>
    <string name="battery_dialog_body">Widgets synkroniserer med Home Assistant i baggrunden. Hvis systemet begrænser baggrundsaktivitet, kan tryk på en widget vise forældet data eller fejle med at opdatere.\n\nTryk \"Tillad\" for at undtage HA Widgets fra batterioptimering — det holder widgets responsive.</string>
    <string name="battery_dialog_allow">Tillad</string>
    <string name="battery_dialog_later">Ikke nu</string>
    <string name="battery_manage">Batterioptimering</string>
    <string name="battery_status_exempt">Fritaget fra batterioptimering</string>
    <string name="battery_status_restricted">Begrænset af batterioptimering</string>

    <!-- LightWidget -->
    <string name="light_widget_label">HA Lys</string>
    <string name="light_widget_description">Styr lyskilder</string>

    <!-- SwitchWidget -->
    <string name="switch_widget_label">HA Kontakt</string>
    <string name="switch_widget_description">Tænd/sluk kontakter</string>

    <!-- SceneWidget -->
    <string name="scene_widget_label">HA Scene</string>
    <string name="scene_widget_description">Aktivér scener</string>

    <!-- ScriptWidget -->
    <string name="script_widget_label">HA Script</string>
    <string name="script_widget_description">Kør scripts</string>

    <!-- AutomationWidget -->
    <string name="automation_widget_label">HA Automatisering</string>
    <string name="automation_widget_description">Udløs automatiseringer</string>

    <!-- SensorWidget -->
    <string name="sensor_widget_label">HA Sensor</string>
    <string name="sensor_widget_description">Vis sensorværdier</string>

    <!-- BinarySensorWidget -->
    <string name="binary_sensor_widget_label">HA Binær sensor</string>
    <string name="binary_sensor_widget_description">Vis binære sensorer</string>

    <!-- CoverWidget -->
    <string name="cover_widget_label">HA Cover</string>
    <string name="cover_widget_description">Styr persienner og rullegardiner</string>

    <!-- ClimateWidget -->
    <string name="climate_widget_label">HA Klima</string>
    <string name="climate_widget_description">Vis og styr temperatur (2×1)</string>
</resources>
```

- [ ] **Step 4: Opret `app/src/main/res/values-sv/strings.xml`** (svensk)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">HA Widgets</string>

    <!-- Widget metadata -->
    <string name="shortcut_widget_label">HA Instrumentpanelsgenväg</string>
    <string name="shortcut_widget_description">Öppnar en Home Assistant-instrumentpanel.</string>
    <string name="open_dashboard">Öppna instrumentpanel</string>

    <!-- Common -->
    <string name="cancel">Avbryt</string>
    <string name="disconnect">Koppla från</string>
    <string name="connect">Anslut</string>
    <string name="connecting">Ansluter…</string>
    <string name="checking_connection">Kontrollerar anslutning…</string>
    <string name="ha_url_label">HA-URL</string>
    <string name="token_label">Long-lived access token</string>

    <!-- MainActivity -->
    <string name="disconnect_dialog_title">Koppla från HA?</string>
    <string name="disconnect_dialog_body">Detta tar bort din anslutning. Befintliga widgetar slutar fungera tills du ansluter igen.</string>
    <string name="connected_to">Ansluten till</string>
    <string name="add_widget_to_home">Lägg till instrumentpanelsgenväg på hemskärmen</string>
    <string name="pin_manual_instructions">Håll ner ett tomt ställe på hemskärmen → Widgetar → hitta \'HA Widgets\' → dra ut genvägen.</string>
    <string name="pin_button_instructions">Tryck på knappen och bekräfta i dialogrutan. Fungerar det inte? Håll ner hemskärmen → Widgetar → \'HA Widgets\'.</string>
    <string name="how_to_use_title">Så använder du HA Widgets</string>
    <string name="how_to_use_step1">1. Tryck på \'Lägg till instrumentpanelsgenväg\' ovan</string>
    <string name="how_to_use_step2">2. Välj instrumentpanel och vy i konfigurationen</string>
    <string name="how_to_use_step3">3. Tryck på widgetikonen på hemskärmen för att öppna din instrumentpanel</string>
    <string name="onboarding_intro">Anslut din Home Assistant-instans nedan. Du kan även hoppa över detta och ansluta första gången du lägger till en widget.</string>

    <!-- Language picker -->
    <string name="section_language">Språk</string>
    <string name="language_danish">Dansk</string>
    <string name="language_english">English</string>
    <string name="language_swedish">Svenska</string>
    <string name="language_follow_system">Följ system</string>

    <!-- ShortcutWidgetConfigActivity -->
    <string name="configure_widget_title">Konfigurera widget</string>
    <string name="save_widget">Spara widget</string>
    <string name="select_dashboard">Välj instrumentpanel</string>
    <string name="section_dashboard">Instrumentpanel</string>
    <string name="section_display">Visning</string>
    <string name="display_fullscreen">Helskärm</string>
    <string name="display_overlay">Overlay (fönster)</string>
    <string name="overlay_width">Bredd: %1$d %%</string>
    <string name="overlay_height">Höjd: %1$d %%</string>
    <string name="connect_to_ha_title">Anslut till Home Assistant</string>
    <string name="connect_to_ha_body">Ange din HA-adress och en long-lived access token (HA → Profil → Long-Lived Access Tokens).</string>
    <string name="load_dashboards_error">Kunde inte hämta instrumentpaneler: %1$s</string>

    <!-- Battery optimization dialog -->
    <string name="battery_dialog_title">Tillåt bakgrundsåtkomst</string>
    <string name="battery_dialog_body">Widgetar synkroniserar med Home Assistant i bakgrunden. Om systemet begränsar bakgrundsaktivitet kan tryck på en widget visa inaktuell data eller misslyckas med att uppdatera.\n\nTryck på \"Tillåt\" för att undanta HA Widgets från batterioptimering — det håller widgetarna responsiva.</string>
    <string name="battery_dialog_allow">Tillåt</string>
    <string name="battery_dialog_later">Inte nu</string>
    <string name="battery_manage">Batterioptimering</string>
    <string name="battery_status_exempt">Undantagen från batterioptimering</string>
    <string name="battery_status_restricted">Begränsad av batterioptimering</string>

    <!-- LightWidget -->
    <string name="light_widget_label">HA Ljus</string>
    <string name="light_widget_description">Styr ljuskällor</string>

    <!-- SwitchWidget -->
    <string name="switch_widget_label">HA Strömbrytare</string>
    <string name="switch_widget_description">Slå på/av strömbrytare</string>

    <!-- SceneWidget -->
    <string name="scene_widget_label">HA Scen</string>
    <string name="scene_widget_description">Aktivera scener</string>

    <!-- ScriptWidget -->
    <string name="script_widget_label">HA Skript</string>
    <string name="script_widget_description">Kör skript</string>

    <!-- AutomationWidget -->
    <string name="automation_widget_label">HA Automatisering</string>
    <string name="automation_widget_description">Utlös automatiseringar</string>

    <!-- SensorWidget -->
    <string name="sensor_widget_label">HA Sensor</string>
    <string name="sensor_widget_description">Visa sensorvärden</string>

    <!-- BinarySensorWidget -->
    <string name="binary_sensor_widget_label">HA Binär sensor</string>
    <string name="binary_sensor_widget_description">Visa binära sensorer</string>

    <!-- CoverWidget -->
    <string name="cover_widget_label">HA Cover</string>
    <string name="cover_widget_description">Styr persienner och rullgardiner</string>

    <!-- ClimateWidget -->
    <string name="climate_widget_label">HA Klimat</string>
    <string name="climate_widget_description">Visa och styr temperatur (2×1)</string>
</resources>
```

- [ ] **Step 5: Byg og verificér**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. Hvis fejl om duplicate/missing string-ressourcer: tjek at alle tre filer har præcis samme sæt `name="..."`-attributter (64 strenge hver).

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/res/values/strings.xml app/src/main/res/values-da/strings.xml app/src/main/res/values-sv/strings.xml
git commit -m "feat: tilføj engelsk/svensk sprogressourcer, dansk flyttet til values-da (v0.2.13)"
```

---

### Task 2: Sprog-vælger UI + locale-switch i MainActivity

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/MainActivity.kt`

**Interfaces:**
- Consumes: string-ressourcer fra Task 1 — `R.string.section_language`, `R.string.language_danish`, `R.string.language_english`, `R.string.language_swedish`, `R.string.language_follow_system`.
- Produces: ingen nye offentlige interfaces (ren UI-tilføjelse internt i `MainActivity.kt`).

- [ ] **Step 1: Tilføj imports**

I `app/src/main/java/dk/akait/hawidgets/MainActivity.kt`, tilføj efter linje 9 (`import android.provider.Settings`):

```kotlin
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
```

- [ ] **Step 2: Tilføj locale-helper-funktioner**

Tilføj nederst i filen, efter `OnboardingScreen`-funktionen (efter linje 281, før sidste `}`... bemærk: filen slutter med `OnboardingScreen` som sidste top-level deklaration, så tilføj disse som nye top-level-funktioner til sidst i filen):

```kotlin
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun currentLanguageTag(context: android.content.Context): String? {
    val localeManager = context.getSystemService(LocaleManager::class.java)
    val tag = localeManager.applicationLocales.toLanguageTags()
    return tag.takeIf { it.isNotBlank() }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun setAppLocale(context: android.content.Context, languageTag: String?) {
    val localeManager = context.getSystemService(LocaleManager::class.java)
    localeManager.applicationLocales =
        if (languageTag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(languageTag)
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    fillWidth: Boolean = false,
    onClick: () -> Unit
) {
    val modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}
```

- [ ] **Step 3: Tilføj sprog-sektion i connected-state UI**

I `OnboardingScreen`, indsæt en ny sektion lige efter batteri-status-blokken og før disconnect-knappen. Find dette eksisterende stykke (linje 209-217):

```kotlin
                Text(
                    if (batteryExempted) stringResource(R.string.battery_status_exempt)
                    else stringResource(R.string.battery_status_restricted),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (batteryExempted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))
```

Erstat det med (tilføjer sprog-sektionen mellem batteri-status og den eksisterende Spacer):

```kotlin
                Text(
                    if (batteryExempted) stringResource(R.string.battery_status_exempt)
                    else stringResource(R.string.battery_status_restricted),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (batteryExempted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var currentTag by remember { mutableStateOf(currentLanguageTag(context)) }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.section_language), style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LanguageOption(
                                    label = stringResource(R.string.language_danish),
                                    selected = currentTag == "da"
                                ) {
                                    setAppLocale(context, "da")
                                    currentTag = "da"
                                }
                                LanguageOption(
                                    label = stringResource(R.string.language_english),
                                    selected = currentTag == "en"
                                ) {
                                    setAppLocale(context, "en")
                                    currentTag = "en"
                                }
                                LanguageOption(
                                    label = stringResource(R.string.language_swedish),
                                    selected = currentTag == "sv"
                                ) {
                                    setAppLocale(context, "sv")
                                    currentTag = "sv"
                                }
                            }
                            LanguageOption(
                                label = stringResource(R.string.language_follow_system),
                                selected = currentTag == null,
                                fillWidth = true
                            ) {
                                setAppLocale(context, null)
                                currentTag = null
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
```

- [ ] **Step 4: Byg og verificér**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/MainActivity.kt
git commit -m "feat: tilføj sprog-vælger (dansk/engelsk/svensk/følg system) i MainActivity (v0.2.13)"
```

---

### Task 3: QA-loop (emulator + rigtig enhed) — kun commit, ingen yderligere kodeændring forventet

**Files:** ingen nye — verifikation af Task 1+2's output.

**Interfaces:**
- Consumes: APK bygget fra Task 1+2.
- Produces: intet nyt — denne task er ren verifikation før release.

- [ ] **Step 1: Installer på `pixel_test`-emulator**

Run:
```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n dk.akait.hawidgets/.MainActivity
```
Expected: app starter uden crash, viser dansk UI (device-locale).

- [ ] **Step 2: Screenshot baseline + verificér sprog-sektion synlig**

Run:
```bash
adb -s emulator-5554 exec-out screencap -p > "C:\Users\rtr\AppData\Local\Temp\claude\C--Dev-GitHub-ha-widgets\2227e62b-b6ec-4749-a5a7-bc5dd1ed1cf3\scratchpad\lang_baseline.png"
```
Læs screenshot (Read-tool). Forventet: sprog-sektion ("Sprog") synlig under batteri-knap med 4 valg (Dansk/English/Svenska/Følg system), "Dansk" highlighted (da device-locale er dansk → `currentLanguageTag` er null ved første åbning, men UI-default er device locale så Dansk-knappen vises ikke som "selected" medmindre eksplicit valgt — bekræft visuelt at alle 4 knapper er synlige og trykbare).

- [ ] **Step 3: Tryk "English", verificér UI skifter sprog**

Find koordinater for "English"-knappen i screenshottet fra Step 2 (læs billedet, estimer pixel-position). Tryk:
```bash
adb -s emulator-5554 shell input tap <X> <Y>
```
Vent 1 sekund, tag nyt screenshot:
```bash
adb -s emulator-5554 exec-out screencap -p > "C:\Users\rtr\AppData\Local\Temp\claude\C--Dev-GitHub-ha-widgets\2227e62b-b6ec-4749-a5a7-bc5dd1ed1cf3\scratchpad\lang_english.png"
```
Læs screenshot. Forventet: al UI-tekst (overskrifter, knapper, "Connected to" osv.) er nu på engelsk, "English"-knappen vises highlighted/filled.

- [ ] **Step 4: Genstart app, verificér persist**

```bash
adb -s emulator-5554 shell am force-stop dk.akait.hawidgets
adb -s emulator-5554 shell am start -n dk.akait.hawidgets/.MainActivity
adb -s emulator-5554 exec-out screencap -p > "C:\Users\rtr\AppData\Local\Temp\claude\C--Dev-GitHub-ha-widgets\2227e62b-b6ec-4749-a5a7-bc5dd1ed1cf3\scratchpad\lang_persist.png"
```
Læs screenshot. Forventet: app åbner stadig på engelsk (valget persisterede over genstart).

- [ ] **Step 5: Tryk "Svenska", verificér; tryk "Følg system", verificér tilbage til dansk**

Gentag tap+screenshot-mønstret fra Step 3 for "Svenska" (forventet: svensk UI-tekst), derefter for "Følg system" (forventet: tilbage til dansk, da emulatorens device-locale er dansk).

- [ ] **Step 6: Verificér WebView/dashboard upåvirket**

Med app sat til engelsk: åbn et dashboard via "Add widget to home screen"-flowet eller eksisterende widget. Forventet: HA-dashboardets eget sprog (styret af HA-brugerens profil-indstilling i Home Assistant) er uændret — app-sprogvalget påvirker IKKE WebView-indholdet. Ingen crash i WebView.

- [ ] **Step 7: Installer på Galaxy S23 (rigtig enhed)**

```bash
adb -s <S23-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```
Gentag Step 2–6 på enheden. Bekræft samme adfærd.

- [ ] **Step 8: Commit (kun hvis QA afslørede behov for rettelser) + push**

Hvis begge QA-trin (emulator + enhed) er grønne uden rettelser, er Task 1+2's commits allerede de endelige. Push til main:
```bash
git push
```
Hvis QA afslørede fejl: ret koden, gentag Task 1 Step 5 eller Task 2 Step 4 (build), commit rettelsen separat, gentag denne QA-loop fra Step 1, til grøn.
