# Widget-config discoverability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Led widget-først-brugere til app-opsætningen: begge widget-config-skærme viser en "Åbn HA Widgets"-gate når appen ikke er forbundet, og en "indstillinger findes i appen"-henvisning når den er, med deep-link til indstillings-arket.

**Architecture:** To delte Compose-composables (`NotConnectedGate`, `AppSettingsHint`) + en `rememberResumeTick()`-helper i en ny fil. De 2 config-aktiviteter bruger dem; `MainActivity` får en `EXTRA_OPEN_SETTINGS`-deep-link. Genvejens inline connect-formular fjernes (én kilde til opsætning = appen).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), androidx.lifecycle-compose.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-09-widget-discoverability-design.md` — følg den præcist.
- Byg: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`; test: `... testDebugUnitTest` (55 tests skal passere — ingen rammer denne UI).
- Kun de 2 config-aktiviteter (`ShortcutWidgetConfigActivity`, `MultiEntityWidgetConfigActivity`), `MainActivity`, en ny delt fil, og `strings.xml` (×3) berøres. Ingen widget-rendering, ingen Room, ingen onboarding-logik-ændring.
- Alle nye bruger-synlige strenge i `values/`, `values-da/`, `values-sv/strings.xml`.
- Installér ALTID `adb install -r`.
- **Bump version** i sidste task: `68`/`"0.2.68"` → `69`/`"0.2.69"`.

---

### Task 1: Delte composables + strenge (`ConfigDiscoverability.kt`)

**Files:**
- Create: `app/src/main/java/dk/akait/hawidgets/widget/common/ConfigDiscoverability.kt`
- Modify: `app/src/main/res/values/strings.xml`, `values-da/strings.xml`, `values-sv/strings.xml`

**Interfaces:**
- Produces: `NotConnectedGate(onOpenApp: () -> Unit)`, `AppSettingsHint(onOpenSettings: () -> Unit)`, `rememberResumeTick(): Int` — alle brugt af Task 3 og 4. Strenge brugt af Task 1's composables.

- [ ] **Step 1: Tilføj strenge (engelsk default)**

I `app/src/main/res/values/strings.xml`, tilføj (et sted blandt de øvrige strenge):

```xml
    <string name="not_connected_gate_title">Not connected</string>
    <string name="not_connected_gate_body">Connect the app to Home Assistant first. Open HA Widgets to set up the connection, language, theme, and colors.</string>
    <string name="open_app_button">Open HA Widgets</string>
    <string name="settings_in_app_hint">Language, theme, and colors are set in the app</string>
    <string name="open_short">Open</string>
```

- [ ] **Step 2: Tilføj strenge (dansk)**

I `app/src/main/res/values-da/strings.xml`:

```xml
    <string name="not_connected_gate_title">Ikke forbundet</string>
    <string name="not_connected_gate_body">Forbind appen til Home Assistant først. Åbn HA Widgets for at opsætte forbindelse, sprog, tema og farver.</string>
    <string name="open_app_button">Åbn HA Widgets</string>
    <string name="settings_in_app_hint">Sprog, tema og farver ændres i appen</string>
    <string name="open_short">Åbn</string>
```

- [ ] **Step 3: Tilføj strenge (svensk)**

I `app/src/main/res/values-sv/strings.xml`:

```xml
    <string name="not_connected_gate_title">Inte ansluten</string>
    <string name="not_connected_gate_body">Anslut appen till Home Assistant först. Öppna HA Widgets för att ställa in anslutning, språk, tema och färger.</string>
    <string name="open_app_button">Öppna HA Widgets</string>
    <string name="settings_in_app_hint">Språk, tema och färger ändras i appen</string>
    <string name="open_short">Öppna</string>
```

- [ ] **Step 4: Opret `ConfigDiscoverability.kt`**

```kotlin
package dk.akait.hawidgets.widget.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dk.akait.hawidgets.R

/**
 * "Ikke forbundet"-gate til widget-config-skærmene. Vises når [dk.akait.hawidgets.data.SecureStore]
 * ikke er konfigureret — leder brugeren til hoved-appen, hvor AL global opsætning (forbindelse, sprog,
 * tema, farver) bor. [onOpenApp] skal starte MainActivity.
 */
@Composable
fun NotConnectedGate(onOpenApp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.not_connected_gate_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.not_connected_gate_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Text(
                stringResource(R.string.open_app_button),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Diskret bund-henvisning: minder om at sprog/tema/farver ligger i appen. [onOpenSettings] skal
 * starte MainActivity med indstillings-arket åbent (deep-link).
 */
@Composable
fun AppSettingsHint(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSettings() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.settings_in_app_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.open_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Tæller der stiger hver gang den omgivende aktivitet får `ON_RESUME`. Bruges som `LaunchedEffect`-key
 * i config-skærmene, så de gen-tjekker forbindelses-status når brugeren vender tilbage fra appen (efter
 * at have forbundet dér) — uden at skulle fjerne+gen-tilføje widgetten.
 */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return tick
}
```

- [ ] **Step 5: Kompilér**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Hvis `LocalLifecycleOwner`-importen fejler, brug `androidx.lifecycle.compose.LocalLifecycleOwner` i stedet — projektet har lifecycle-compose; verificér hvilken der resolver.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/ConfigDiscoverability.kt app/src/main/res/values/strings.xml app/src/main/res/values-da/strings.xml app/src/main/res/values-sv/strings.xml
git commit -m "feat: delte NotConnectedGate + AppSettingsHint + rememberResumeTick + strenge"
```

---

### Task 2: `MainActivity` deep-link til indstillings-arket

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/MainActivity.kt`

**Interfaces:**
- Produces: `MainActivity.EXTRA_OPEN_SETTINGS` (String-konstant) — brugt af Task 3 og 4 til at åbne indstillings-arket.
- Consumes: eksisterende `showSettings`-state (`MainActivity.kt:119`) og `SettingsSheet`.

- [ ] **Step 1: Tilføj konstant + læs intent**

I `MainActivity`-klassen (companion object, opret et hvis der ikke er et):

```kotlin
    companion object {
        const val EXTRA_OPEN_SETTINGS = "open_settings"
    }
```

Find den Composable der ejer `var showSettings by remember { mutableStateOf(false) }` (linje 119). Aktiviteten skal videregive intent-flaget dertil. I `onCreate`/`setContent` hvor denne Composable kaldes, beregn `val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)` og send den ind som parameter (fx `openSettingsInitially: Boolean`). Initialisér state'en fra den:

```kotlin
    var showSettings by remember { mutableStateOf(openSettingsInitially) }
```

Tilføj `openSettingsInitially: Boolean = false` til Composable-funktionens signatur og videregiv `openSettings` fra `setContent`. (Deep-link fra en frakoblet tilstand er harmløs: `SettingsSheet` vises kun i den forbundne UI-gren; hvis appen ikke er forbundet, ignoreres flaget de facto — ingen crash.)

- [ ] **Step 2: Kompilér**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/MainActivity.kt
git commit -m "feat: MainActivity EXTRA_OPEN_SETTINGS deep-link åbner indstillings-arket"
```

---

### Task 3: `MultiEntityWidgetConfigActivity` — gate + hint + resume-recheck

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityWidgetConfigActivity.kt`
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityListScreen.kt`

**Interfaces:**
- Consumes: `NotConnectedGate`, `AppSettingsHint`, `rememberResumeTick` (Task 1); `MainActivity.EXTRA_OPEN_SETTINGS` (Task 2).

- [ ] **Step 1: Gate + resume-recheck i `MultiEntityConfigScreen`**

I `MultiEntityWidgetConfigActivity.kt`, i `MultiEntityConfigScreen`:

Tilføj to states ved de øvrige (efter linje 84):

```kotlin
    var notConnected by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
```

Erstat `LaunchedEffect(Unit) { ... }` (linje 87-103) med en resume-keyed effekt der gater på forbindelse og kun loader én gang:

```kotlin
    val resumeTick = rememberResumeTick()
    LaunchedEffect(resumeTick) {
        val store = SecureStore.get(context)
        if (!store.isConfigured) {
            notConnected = true
            isLoading = false
            return@LaunchedEffect
        }
        notConnected = false
        if (loaded) return@LaunchedEffect
        val client = HaApiClient(store.baseUrl!!, store.token!!)
        allEntities = client.listStatesByDomains(MULTI_ENTITY_DOMAINS.toSet()).sortedBy { it.friendlyName }
        val db = AppDatabase.get(context)
        slots = db.multiWidgetDao().getSlots(appWidgetId)
        showRefreshIcon = db.multiWidgetDao().get(appWidgetId)?.showRefreshIcon ?: true
        attrsByEntityId = allEntities.mapNotNull { entity ->
            db.entityStateDao().get(entity.entityId)?.attributesJson?.let { entity.entityId to it }
        }.toMap()
        loaded = true
        isLoading = false
    }
```

Fjern den nu-ubrugte `val haNotConnectedError = stringResource(R.string.ha_not_connected_error)` (linje 85) og fjern `loadError`-tildeling i den slettede gren (behold `loadError`-state hvis den stadig bruges af `EntityPickerSubScreen`s `error`-param — den kan sættes til `null` konstant, eller behold variablen; verificér ved kompilering).

Wrap `when (val s = step) { ... }`-blokken (linje 112) i en gate-forgrening. Tilføj øverst i render-outputtet:

```kotlin
    if (notConnected) {
        NotConnectedGate(onOpenApp = {
            context.startActivity(Intent(context, dk.akait.hawidgets.MainActivity::class.java))
        })
        return
    }
```

(Placér `if (notConnected) { ... return }` lige før `when (val s = step)`. `return` fra en `@Composable` med `Unit`-retur er gyldigt.)

Tilføj de nødvendige imports: `dk.akait.hawidgets.widget.common.NotConnectedGate`, `AppSettingsHint`, `rememberResumeTick`, og `android.content.Intent` (allerede importeret).

- [ ] **Step 2: `AppSettingsHint` i `ListScreen`s bottomBar**

I `MultiEntityListScreen.kt`, tilføj en parameter til `ListScreen`:

```kotlin
    onOpenAppSettings: () -> Unit,
```

I `ListScreen`s `Scaffold`, tilføj en `bottomBar`:

```kotlin
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.multi_entity_config_title)) }) },
        bottomBar = { AppSettingsHint(onOpenSettings = onOpenAppSettings) },
    ) { padding -> ... }
```

Import `dk.akait.hawidgets.widget.common.AppSettingsHint`.

I `MultiEntityWidgetConfigActivity.kt`s `ListScreen(...)`-kald (linje 113), tilføj:

```kotlin
            onOpenAppSettings = {
                context.startActivity(
                    Intent(context, dk.akait.hawidgets.MainActivity::class.java)
                        .putExtra(dk.akait.hawidgets.MainActivity.EXTRA_OPEN_SETTINGS, true)
                )
            },
```

- [ ] **Step 3: Byg + test**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 55 tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityWidgetConfigActivity.kt app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityListScreen.kt
git commit -m "feat: multi-config gate + indstillings-hint + resume-recheck"
```

---

### Task 4: `ShortcutWidgetConfigActivity` — fjern inline connect, gate + hint + resume-recheck

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/ShortcutWidgetConfigActivity.kt`

**Interfaces:**
- Consumes: `NotConnectedGate`, `AppSettingsHint`, `rememberResumeTick` (Task 1); `MainActivity.EXTRA_OPEN_SETTINGS` (Task 2).

Nuværende `ConfigScreen` har en inline connect-formular: `haConfigured`/`haUrl`/`haToken`/`connecting`/
`connectError`-states, et Step-1-"Connect to HA"-`Column`, og en `HaApiClient(...).checkConnection()`-knap.
Hele Step 1 skal væk og erstattes af gaten.

- [ ] **Step 1: Fjern inline connect + indfør gate + resume-recheck**

I `ShortcutWidgetConfigActivity.kt`s `ConfigScreen`:
- Fjern states: `haConfigured`, `haUrl`, `haToken`, `connecting`, `connectError`, `connectScope`.
- Indfør: `var notConnected by remember { mutableStateOf(false) }` og brug `rememberResumeTick()`.
- Erstat `LaunchedEffect(haConfigured)` (dashboard-load) med en resume-keyed effekt der gater på forbindelse:

```kotlin
    val resumeTick = rememberResumeTick()
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(resumeTick) {
        if (!store.isConfigured) {
            notConnected = true
            loading = false
            return@LaunchedEffect
        }
        notConnected = false
        if (loaded) return@LaunchedEffect
        loading = true
        HaWebSocketClient(store.baseUrl!!, store.token!!).listDashboards()
            .onSuccess { list ->
                dashboards = list
                selected = if (existingConfig != null) {
                    list.firstOrNull { it.urlPath == existingConfig.dashboardPath } ?: list.firstOrNull()
                } else list.firstOrNull()
            }
            .onFailure { loadError = context.getString(R.string.load_dashboards_error, it.message ?: "") }
        loaded = true
        loading = false
    }
```

- Slet hele `if (!haConfigured) { ... Step 1 connect-UI ... } else { ... }`-strukturen i `Column`-indholdet; behold KUN Step 2-indholdet (dashboard-dropdown + display-mode + overlay-sliders). Erstat den ydre `if (!haConfigured)`-gren med gaten på `ConfigScreen`-niveau:

```kotlin
    if (notConnected) {
        NotConnectedGate(onOpenApp = {
            context.startActivity(Intent(context, dk.akait.hawidgets.MainActivity::class.java))
        })
        return
    }
```

(Placér før `Scaffold`. Den eksisterende `bottomBar` med "Spara widget"-knappen bevares — men vises nu kun i den forbundne tilstand, hvilket er korrekt.)

- Fjern nu-ubrugte imports (`HaApiClient`, `PasswordVisualTransformation`, connect-relaterede strenge-referencer). Behold `HaWebSocketClient`, `SecureStore`, `DashboardInfo`, `DisplayMode`.

- [ ] **Step 2: `AppSettingsHint` i bottomBar**

Tilføj `AppSettingsHint` til `Scaffold`s `bottomBar` sammen med den eksisterende "Spara widget"-knap (wrap dem i en `Column`):

```kotlin
        bottomBar = {
            Column {
                AppSettingsHint(onOpenSettings = {
                    context.startActivity(
                        Intent(context, dk.akait.hawidgets.MainActivity::class.java)
                            .putExtra(dk.akait.hawidgets.MainActivity.EXTRA_OPEN_SETTINGS, true)
                    )
                })
                // ... eksisterende Box med "Spara widget"-Button uændret ...
            }
        }
```

Import `dk.akait.hawidgets.widget.common.NotConnectedGate`, `AppSettingsHint`, `rememberResumeTick`.

- [ ] **Step 3: Byg + test**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 55 tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/ShortcutWidgetConfigActivity.kt
git commit -m "feat: genvej-config gate (fjern inline connect) + indstillings-hint + resume-recheck"
```

---

### Task 5: Oprydning + versionsbump + QA

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, `values-da/`, `values-sv/` (evt.)
- Modify: `app/build.gradle.kts:16-17`

- [ ] **Step 1: Fjern `ha_not_connected_error` hvis forældreløs**

Run: `git grep -nE "R\.string\.ha_not_connected_error\b|@string/ha_not_connected_error\b" -- 'app/src' | grep -v 'res/values.*/strings.xml'`
Hvis tom: fjern `<string name="ha_not_connected_error">...</string>` fra alle 3 `strings.xml`. Hvis ikke tom: behold.

- [ ] **Step 2: Bump version**

I `app/build.gradle.kts`: `versionCode = 68` → `69`, `versionName = "0.2.68"` → `"0.2.69"`.

- [ ] **Step 3: Fuld build + test**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew clean assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 55 tests pass.

- [ ] **Step 4: Emulator-QA**

Installér (`adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`). Verificér:
1. **Frakoblet:** frakobl i appen (eller ryd token). Åbn multi-config (via widget-tap på uopsat widget eller placér ny) → `NotConnectedGate` vises (ikke tom liste/blindgyde). Tap "Åbn HA Widgets" → MainActivity åbner. Forbind. Tryk tilbage → config re-checker og viser slot-listen automatisk (ingen gen-tilføjelse).
2. Gentag for genvej-config → bekræft den gamle URL/token-inline-formular er VÆK, gaten vises i stedet.
3. **Forbundet + deep-link:** åbn en config-skærm → `AppSettingsHint` nederst → tap → MainActivity åbner MED indstillings-arket åbent.
4. Ingen crash (logcat).

Virker noget ikke → tilbage til relevant task. Bliv i loopet til grønt.

- [ ] **Step 5: Commit versionsbump (+ evt. streng-oprydning)**

```bash
git add -A
git commit -m "chore: bump version 0.2.69 + fjern forældreløs ha_not_connected_error"
```

**Bemærk:** Device-QA på S23 (Nova) udføres af brugeren efter planen.
