# Fejl-feedback i kontrol-dialoger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Når et HA `callService`-kald fejler i en af de fem kontrol-dialoger, skal brugeren se en Toast og (undtagen `DateTimeControlActivity`) beholde dialogen åben til et nyt forsøg — i stedet for at dialogen tavst lukker som om værdien blev gemt.

**Architecture:** Propagér `HaApiClient.Result` op gennem de to delte helper-funktioner (`RangeService.sendRangeValue`, `ConfirmActionActivity.executeConfirmedAction`) som `Boolean`. Hver af de fem `ComponentActivity`-klasser tjekker resultatet efter kaldet: succes → uændret adfærd (refresh + evt. `finish()`); fejl → ny delt `showActionError(context)`-helper (Toast) + `finish()` udelades (undtagen DateTime).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Android `Toast`, eksisterende `HaApiClient`/`EntityRepository`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-07-control-dialog-error-feedback-design.md` — alle værdier herunder er kopieret derfra.
- Fejlbesked-streng-nøgle: `action_failed`. Tekst: en "Couldn't send to Home Assistant", da "Kunne ikke sende til Home Assistant", sv "Kunde inte skicka till Home Assistant".
- Ingen succes-toast — kun fejl giver synlig feedback.
- `DateTimeControlActivity` er eneste undtagelse fra "dialog forbliver åben ved fejl" — den lukker altid (Toast + `finish()`), fordi de native pickers allerede er lukket når fejlen opdages. Alle andre dialoger dropper `finish()` ved fejl.
- **Testing-note (afvigelse fra normal TDD-krav):** dette projekt har ingen instrumenteret UI-test-infrastruktur (ingen filer under `app/src/androidTest/`) og ingen mocking-library i `app/build.gradle.kts` (kun `junit:junit` + `org.json:json`). De ændrede funktioner er `suspend`-funktioner der rammer netværk via `HaApiClient` og/eller kræver en Android `Context` — de kan ikke meningsfuldt JVM-unit-testes uden at introducere ny test-infrastruktur, hvilket er ude af scope for denne opgave (jf. spec "Ikke i scope"). Hver kode-opgave verificeres derfor med et Gradle-kompileringstjek (`assembleDebug`), og den reelle adfærd verificeres i den afsluttende manuelle QA-opgave (Task 7) — præcis den cyklus `CLAUDE.md` selv foreskriver for denne type ændring ("Rettelsesworkflow er altid iterativt": fix → byg → QA emulator → QA device → commit).
- Byg-kommando (fra `CLAUDE.md`): `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
- Install-kommando (ALDRIG uninstall): `<SDK>/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Version bumpes ved hver ændring, FØR build (nuværende: `versionCode = 40`, `versionName = "0.2.40"` i `app/build.gradle.kts:16-17`).

---

### Task 1: Delt fejl-Toast-helper + lokaliseret streng

**Files:**
- Create: `app/src/main/java/dk/akait/hawidgets/widget/common/ActionFeedback.kt`
- Modify: `app/src/main/res/values/strings.xml` (tilføj `action_failed` før `</resources>`)
- Modify: `app/src/main/res/values-da/strings.xml` (tilføj `action_failed` før `</resources>`)
- Modify: `app/src/main/res/values-sv/strings.xml` (tilføj `action_failed` før `</resources>`)

**Interfaces:**
- Produces: `fun showActionError(context: Context)` (pakke `dk.akait.hawidgets.widget.common`) — kaldes fra en coroutine bundet til Main-dispatcheren efter et fejlet HA-kald. Ingen returværdi.

- [ ] **Step 1: Tilføj engelsk streng**

I `app/src/main/res/values/strings.xml`, indsæt lige før den afsluttende `</resources>` (linje 189):

```xml
    <string name="action_failed">Couldn\'t send to Home Assistant</string>
</resources>
```

- [ ] **Step 2: Tilføj dansk streng**

I `app/src/main/res/values-da/strings.xml`, indsæt lige før den afsluttende `</resources>`:

```xml
    <string name="action_failed">Kunne ikke sende til Home Assistant</string>
</resources>
```

- [ ] **Step 3: Tilføj svensk streng**

I `app/src/main/res/values-sv/strings.xml`, indsæt lige før den afsluttende `</resources>`:

```xml
    <string name="action_failed">Kunde inte skicka till Home Assistant</string>
</resources>
```

- [ ] **Step 4: Opret ActionFeedback.kt**

```kotlin
package dk.akait.hawidgets.widget.common

import android.content.Context
import android.widget.Toast
import dk.akait.hawidgets.R

/**
 * Delt fejl-feedback for kontrol-dialogerne (RangeControl/TextControl/NumberInput/
 * DateTimeControl/ConfirmAction) — vises når det underliggende HA callService-kald
 * fejler. Ingen tilsvarende succes-toast: succes signaleres af dialogens egen lukning/
 * opdaterede state.
 */
fun showActionError(context: Context) {
    Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
}
```

- [ ] **Step 5: Byg for at verificere det kompilerer**

Run: `cd "C:/Dev/GitHub/ha-widgets" && JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/ActionFeedback.kt app/src/main/res/values/strings.xml app/src/main/res/values-da/strings.xml app/src/main/res/values-sv/strings.xml
git commit -m "feat: delt fejl-toast-helper til kontrol-dialoger"
```

---

### Task 2: RangeService returnerer Boolean + wire ind i RangeControlActivity

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/common/RangeService.kt`
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/common/RangeControlActivity.kt:104-137` (`sendRangeCommand` og `sendToggle`)

**Interfaces:**
- Consumes: `showActionError(context: Context)` fra Task 1.
- Produces: `suspend fun sendRangeValue(context: Context, domain: String, entityId: String, value: Double): Boolean` — `true` når HA-kaldet lykkedes (var før `Unit`). `NumberInputActivity` (Task 3) er den anden forbruger af denne signatur.

- [ ] **Step 1: Ret sendRangeValue til at returnere Boolean**

Erstat hele indholdet af `app/src/main/java/dk/akait/hawidgets/widget/common/RangeService.kt` fra og med linje 21 (`suspend fun sendRangeValue`) til filens slutning:

```kotlin
suspend fun sendRangeValue(context: Context, domain: String, entityId: String, value: Double): Boolean {
    val store = SecureStore.get(context.applicationContext)
    val base = store.baseUrl ?: return false
    val token = store.token ?: return false
    val api = HaApiClient(base, token)
    val result = when (domain) {
        "light" -> api.callService(
            "light", "turn_on", entityId,
            extraData = mapOf("brightness" to (value.toInt() * 255 / 100).coerceIn(1, 255)),
        )
        "cover" -> api.callService(
            "cover", "set_cover_position", entityId,
            extraData = mapOf("position" to value.toInt()),
        )
        "climate" -> api.callService(
            "climate", "set_temperature", entityId,
            extraData = mapOf("temperature" to value.toInt()),
        )
        "number" -> api.callService(
            "number", "set_value", entityId,
            extraData = mapOf("value" to value),
        )
        "input_number" -> api.callService(
            "input_number", "set_value", entityId,
            extraData = mapOf("value" to value),
        )
        else -> return false
    }
    val ok = result is HaApiClient.Result.Ok
    if (ok) EntityRepository.refresh(context.applicationContext, entityId)
    return ok
}
```

(Docblock-kommentaren over funktionen, linje 8-20, forbliver uændret — kun selve funktionskroppen erstattes.)

- [ ] **Step 2: Wire sendRangeCommand (slider-vejen) i RangeControlActivity**

I `app/src/main/java/dk/akait/hawidgets/widget/common/RangeControlActivity.kt`, erstat (linje 105-107):

```kotlin
                    fun sendRangeCommand(value: Double) {
                        scope.launch { sendRangeValue(applicationContext, domain, entityId, value) }
                    }
```

med:

```kotlin
                    fun sendRangeCommand(value: Double) {
                        scope.launch {
                            val ok = sendRangeValue(applicationContext, domain, entityId, value)
                            if (!ok) showActionError(applicationContext)
                        }
                    }
```

- [ ] **Step 3: Wire sendToggle (tænd/sluk-knappen) i RangeControlActivity**

I samme fil, erstat hele `sendToggle`-funktionen (linje 109-137):

```kotlin
                    fun sendToggle() {
                        scope.launch {
                            busy = true
                            val store = SecureStore.get(applicationContext)
                            val base = store.baseUrl ?: run { busy = false; return@launch }
                            val token = store.token ?: run { busy = false; return@launch }
                            val api = HaApiClient(base, token)
                            when (domain) {
                                "light" -> if (isOn) {
                                    api.callService("light", "turn_off", entityId)
                                } else {
                                    api.callService("light", "turn_on", entityId)
                                }
                                "cover" -> if (isOn) {
                                    api.callService("cover", "close_cover", entityId)
                                } else {
                                    api.callService("cover", "open_cover", entityId)
                                }
                                "climate" -> if (isOn) {
                                    api.callService("climate", "turn_off", entityId)
                                } else {
                                    api.callService("climate", "turn_on", entityId)
                                }
                            }
                            isOn = !isOn
                            EntityRepository.refresh(applicationContext, entityId)
                            busy = false
                        }
                    }
```

med:

```kotlin
                    fun sendToggle() {
                        scope.launch {
                            busy = true
                            val store = SecureStore.get(applicationContext)
                            val base = store.baseUrl ?: run { busy = false; return@launch }
                            val token = store.token ?: run { busy = false; return@launch }
                            val api = HaApiClient(base, token)
                            val result = when (domain) {
                                "light" -> if (isOn) {
                                    api.callService("light", "turn_off", entityId)
                                } else {
                                    api.callService("light", "turn_on", entityId)
                                }
                                "cover" -> if (isOn) {
                                    api.callService("cover", "close_cover", entityId)
                                } else {
                                    api.callService("cover", "open_cover", entityId)
                                }
                                "climate" -> if (isOn) {
                                    api.callService("climate", "turn_off", entityId)
                                } else {
                                    api.callService("climate", "turn_on", entityId)
                                }
                                else -> null
                            }
                            if (result is HaApiClient.Result.Ok) {
                                isOn = !isOn
                                EntityRepository.refresh(applicationContext, entityId)
                            } else {
                                showActionError(applicationContext)
                            }
                            busy = false
                        }
                    }
```

- [ ] **Step 4: Byg for at verificere det kompilerer**

Run: `cd "C:/Dev/GitHub/ha-widgets" && JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/RangeService.kt app/src/main/java/dk/akait/hawidgets/widget/common/RangeControlActivity.kt
git commit -m "fix: RangeControlActivity viser fejl-toast og bevarer state ved mislykket HA-kald"
```

---

### Task 3: Wire NumberInputActivity til sendRangeValue's Boolean-resultat

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/common/NumberInputActivity.kt:79-87`

**Interfaces:**
- Consumes: `suspend fun sendRangeValue(...): Boolean` (Task 2), `showActionError(context: Context)` (Task 1).

- [ ] **Step 1: Ret save() til at tjekke resultatet**

Erstat (linje 79-87):

```kotlin
                    fun save() {
                        val value = parsed ?: return
                        scope.launch {
                            busy = true
                            sendRangeValue(applicationContext, domain, entityId, value)
                            busy = false
                            finish()
                        }
                    }
```

med:

```kotlin
                    fun save() {
                        val value = parsed ?: return
                        scope.launch {
                            busy = true
                            val ok = sendRangeValue(applicationContext, domain, entityId, value)
                            busy = false
                            if (ok) finish() else showActionError(applicationContext)
                        }
                    }
```

- [ ] **Step 2: Byg for at verificere det kompilerer**

Run: `cd "C:/Dev/GitHub/ha-widgets" && JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/NumberInputActivity.kt
git commit -m "fix: NumberInputActivity viser fejl-toast og bevarer input ved mislykket HA-kald"
```

---

### Task 4: TextControlActivity tjekker callService-resultat

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/common/TextControlActivity.kt:56-72`

**Interfaces:**
- Consumes: `showActionError(context: Context)` (Task 1). `HaApiClient.Result` (allerede importeret transitivt via `HaApiClient`-import i filen).

- [ ] **Step 1: Ret save() til at tjekke resultatet**

Erstat (linje 56-72):

```kotlin
                    fun save() {
                        scope.launch {
                            busy = true
                            val store = SecureStore.get(applicationContext)
                            val base = store.baseUrl
                            val token = store.token
                            if (base != null && token != null) {
                                HaApiClient(base, token).callService(
                                    "input_text", "set_value", entityId,
                                    extraData = mapOf("value" to text),
                                )
                                EntityRepository.refresh(applicationContext, entityId)
                            }
                            busy = false
                            finish()
                        }
                    }
```

med:

```kotlin
                    fun save() {
                        scope.launch {
                            busy = true
                            val store = SecureStore.get(applicationContext)
                            val base = store.baseUrl
                            val token = store.token
                            val result = if (base != null && token != null) {
                                HaApiClient(base, token).callService(
                                    "input_text", "set_value", entityId,
                                    extraData = mapOf("value" to text),
                                )
                            } else {
                                null
                            }
                            busy = false
                            if (result is HaApiClient.Result.Ok) {
                                EntityRepository.refresh(applicationContext, entityId)
                                finish()
                            } else {
                                showActionError(applicationContext)
                            }
                        }
                    }
```

- [ ] **Step 2: Byg for at verificere det kompilerer**

Run: `cd "C:/Dev/GitHub/ha-widgets" && JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/TextControlActivity.kt
git commit -m "fix: TextControlActivity viser fejl-toast og bevarer indtastet tekst ved mislykket HA-kald"
```

---

### Task 5: DateTimeControlActivity tjekker callService-resultat (lukker altid)

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/common/DateTimeControlActivity.kt:43-60`

**Interfaces:**
- Consumes: `showActionError(context: Context)` (Task 1).

- [ ] **Step 1: Ret submit() til at tjekke resultatet (finish() bevares altid — accepteret afvigelse, se spec §4)**

Erstat (linje 43-60):

```kotlin
        fun submit(date: String?, time: String?) {
            lifecycleScope.launch {
                val store = SecureStore.get(applicationContext)
                val base = store.baseUrl
                val token = store.token
                if (base != null && token != null) {
                    val extraData = buildMap<String, Any> {
                        date?.let { put("date", it) }
                        time?.let { put("time", it) }
                    }
                    HaApiClient(base, token).callService(
                        "input_datetime", "set_datetime", entityId, extraData = extraData,
                    )
                    EntityRepository.refresh(applicationContext, entityId)
                }
                finish()
            }
        }
```

med:

```kotlin
        fun submit(date: String?, time: String?) {
            lifecycleScope.launch {
                val store = SecureStore.get(applicationContext)
                val base = store.baseUrl
                val token = store.token
                if (base != null && token != null) {
                    val extraData = buildMap<String, Any> {
                        date?.let { put("date", it) }
                        time?.let { put("time", it) }
                    }
                    val result = HaApiClient(base, token).callService(
                        "input_datetime", "set_datetime", entityId, extraData = extraData,
                    )
                    if (result is HaApiClient.Result.Ok) {
                        EntityRepository.refresh(applicationContext, entityId)
                    } else {
                        showActionError(applicationContext)
                    }
                }
                finish()
            }
        }
```

- [ ] **Step 2: Byg for at verificere det kompilerer**

Run: `cd "C:/Dev/GitHub/ha-widgets" && JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/DateTimeControlActivity.kt
git commit -m "fix: DateTimeControlActivity viser fejl-toast ved mislykket HA-kald"
```

---

### Task 6: ConfirmActionActivity + executeConfirmedAction returnerer Boolean

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/common/ConfirmActionActivity.kt:96-109` (onClick) og `:130-164` (`executeConfirmedAction`)

**Interfaces:**
- Consumes: `showActionError(context: Context)` (Task 1).
- Produces: `internal suspend fun executeConfirmedAction(context: Context, domain: String, action: String, entityId: String): Boolean` (var før `Unit`) — ingen andre forbrugere af denne funktion i kodebasen.

- [ ] **Step 1: Ret executeConfirmedAction til at returnere Boolean**

Erstat hele funktionen (linje 130-164):

```kotlin
internal suspend fun executeConfirmedAction(
    context: Context,
    domain: String,
    action: String,
    entityId: String,
) {
    val stateDao = AppDatabase.get(context).entityStateDao()
    if (action == "TRIGGER") {
        // Kopieret fra MultiEntityWidget.clickModifier "else -> // TRIGGER"-grenen.
        val service = when (domain) {
            "automation" -> "trigger"
            "input_button" -> "press"
            else -> "turn_on" // scene, script
        }
        // Kopieret fra TriggerEntityAction.onAction (uden eksplicit targetState).
        val current = stateDao.get(entityId)
        EntityRepository.command(
            context = context,
            domain = domain,
            service = service,
            entityId = entityId,
            targetState = current?.state ?: "on",
            fromState = current?.state,
        )
    } else {
        // "TOGGLE" — kopieret 1:1 fra ToggleEntityAction.onAction.
        val current = stateDao.get(entityId) ?: return
        val (targetState, service) = when (domain) {
            "lock" -> if (current.state == "locked") "unlocked" to "unlock" else "locked" to "lock"
            "cover" -> if (current.state == "open") "closed" to "close_cover" else "open" to "open_cover"
            else -> if (current.state == "on") "off" to "turn_off" else "on" to "turn_on"
        }
        EntityRepository.command(context, domain, service, entityId, targetState, current.state)
    }
}
```

med:

```kotlin
internal suspend fun executeConfirmedAction(
    context: Context,
    domain: String,
    action: String,
    entityId: String,
): Boolean {
    val stateDao = AppDatabase.get(context).entityStateDao()
    return if (action == "TRIGGER") {
        // Kopieret fra MultiEntityWidget.clickModifier "else -> // TRIGGER"-grenen.
        val service = when (domain) {
            "automation" -> "trigger"
            "input_button" -> "press"
            else -> "turn_on" // scene, script
        }
        // Kopieret fra TriggerEntityAction.onAction (uden eksplicit targetState).
        val current = stateDao.get(entityId)
        EntityRepository.command(
            context = context,
            domain = domain,
            service = service,
            entityId = entityId,
            targetState = current?.state ?: "on",
            fromState = current?.state,
        )
    } else {
        // "TOGGLE" — kopieret 1:1 fra ToggleEntityAction.onAction.
        val current = stateDao.get(entityId) ?: return false
        val (targetState, service) = when (domain) {
            "lock" -> if (current.state == "locked") "unlocked" to "unlock" else "locked" to "lock"
            "cover" -> if (current.state == "open") "closed" to "close_cover" else "open" to "open_cover"
            else -> if (current.state == "on") "off" to "turn_off" else "on" to "turn_on"
        }
        EntityRepository.command(context, domain, service, entityId, targetState, current.state)
    }
}
```

- [ ] **Step 2: Ret Bekræft-knappens onClick i Activity'en**

Erstat (linje 96-109):

```kotlin
                            Button(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        executeConfirmedAction(applicationContext, domain, action, entityId)
                                        finish()
                                    }
                                },
                                enabled = !busy,
                            ) {
                                Text(stringResource(R.string.confirm_dialog_confirm))
                            }
```

med:

```kotlin
                            Button(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        val ok = executeConfirmedAction(applicationContext, domain, action, entityId)
                                        busy = false
                                        if (ok) finish() else showActionError(applicationContext)
                                    }
                                },
                                enabled = !busy,
                            ) {
                                Text(stringResource(R.string.confirm_dialog_confirm))
                            }
```

- [ ] **Step 3: Byg for at verificere det kompilerer**

Run: `cd "C:/Dev/GitHub/ha-widgets" && JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/ConfirmActionActivity.kt
git commit -m "fix: ConfirmActionActivity viser fejl-toast og bevarer dialogen ved mislykket HA-kald"
```

---

### Task 7: Version bump, CLAUDE.md-entry, manuel QA (emulator + device)

**Files:**
- Modify: `app/build.gradle.kts:16-17` (versionCode/versionName)
- Modify: `CLAUDE.md` (ny status-entry under M2)

**Interfaces:**
- Consumes: intet nyt — dette er den afsluttende verifikations- og dokumentations-opgave for hele planen.

- [ ] **Step 1: Bump version**

I `app/build.gradle.kts`, erstat (linje 16-17):

```kotlin
        versionCode = 40
        versionName = "0.2.40"
```

med:

```kotlin
        versionCode = 41
        versionName = "0.2.41"
```

- [ ] **Step 2: Byg**

Run: `cd "C:/Dev/GitHub/ha-widgets" && JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: QA på emulator (pixel_test)**

Installér (ALDRIG uninstall):
`<SDK>/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`

For hver af de 5 scenarier: midlertidigt ugyldiggør forbindelsen (fx sluk emulatorens netværk via `adb shell svc wifi disable` + `adb shell svc data disable`, eller peg base-URL på en ugyldig adresse i onboarding), udfør handlingen, og verificér:

1. **Light/cover/climate slider** (RangeControlActivity, `sendRangeCommand`): træk slider → Toast "Kunne ikke sende til Home Assistant" vises, dialogen forbliver åben, værdien i UI ændrer sig ikke permanent.
2. **Light/cover/climate tænd/sluk-knap** (RangeControlActivity, `sendToggle`): tryk knap → Toast vises, knappens label (Tænd/Sluk/Luk helt/Åbn helt) skifter IKKE.
3. **input_text-widget** (TextControlActivity): skriv tekst, tryk Gem → Toast vises, dialogen forbliver åben MED den indtastede tekst intakt.
4. **number/input_number "Indtast værdi"** (NumberInputActivity): skriv en gyldig værdi, tryk Gem → Toast vises, dialogen forbliver åben med tallet stadig i feltet.
5. **input_datetime-widget** (DateTimeControlActivity): vælg dato/tid gennem pickerne → Toast vises, dialogen/aktiviteten LUKKER (forventet, accepteret afvigelse).
6. **MultiEntityWidget "Bekræft ved tryk"-chip** (ConfirmActionActivity): tryk chip → bekræft-dialog → tryk "Bekræft" → Toast vises, bekræft-dialogen forbliver åben.

Genopret derefter netværk/URL og gentag ét scenarie (fx #1) for at bekræfte succes-vejen er uændret: ingen Toast, dialogen lukker/opdaterer som før.

Hvis noget scenarie fejler → tilbage til den relevante opgaves kode, ret, gentag build+QA.

- [ ] **Step 4: QA på telefon (Galaxy S23)**

`adb install -r app/build/outputs/apk/debug/app-debug.apk` (ALDRIG uninstall — bevarer token/config).
Oplys installeret version til brugeren: **v0.2.41 (versionCode 41)**.
Gentag samme 6 scenarier som Step 3 mod ægte HA-forbindelse (midlertidig netværksafbrydelse på telefonen, fx flytilstand, til fejl-scenarierne; gendan bagefter til succes-scenariet).

- [ ] **Step 5: Tilføj CLAUDE.md-entry**

I `CLAUDE.md`, under sektionen `### M2 — Native entity-widgets (FÆRDIG 2026-06-29)`, indsæt en ny linje efter den seneste `v0.2.38`-entry (før `## Næste skridt`):

```markdown
- ✅ **v0.2.41 — fejl-feedback i kontrol-dialoger (2026-07-07, efter PR-review-fund):**
  - **Baggrund:** PR-review (PR #1) fandt at `RangeControlActivity`, `TextControlActivity`,
    `DateTimeControlActivity`, `NumberInputActivity` og `ConfirmActionActivity` ignorerede
    `HaApiClient.callService`s returværdi og lukkede/opdaterede ubetinget — en fejlet
    HA-forbindelse så ud som en gemt værdi.
  - **Fix:** `RangeService.sendRangeValue` og `ConfirmActionActivity.executeConfirmedAction`
    returnerer nu `Boolean`. Ved fejl: ny delt `showActionError()`-Toast
    ("Kunne ikke sende til Home Assistant", alle 3 sprog), dialogen forbliver åben (input
    bevares) i stedet for at lukke. Eneste undtagelse: `DateTimeControlActivity` lukker
    stadig ved fejl (native date/time-pickers er allerede lukket på det tidspunkt — intet
    UI at holde åbent).
  - Spec: `docs/superpowers/specs/2026-07-07-control-dialog-error-feedback-design.md`,
    plan: `docs/superpowers/plans/2026-07-07-control-dialog-error-feedback.md`.
  - QA: emulator (`pixel_test`) + Galaxy S23, alle 6 scenarier (slider, tænd/sluk, tekst,
    tal, dato/tid, bekræft-chip) verificeret både fejl- og succes-vej.
```

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts CLAUDE.md
git commit -m "chore: bump version til 0.2.41 (v0.2.41 fejl-feedback i kontrol-dialoger)"
```

---

## Self-Review

**Spec coverage:**
- §1 Plumbing (`sendRangeValue`, `executeConfirmedAction` → Boolean) → Task 2 Step 1, Task 6 Step 1. ✓
- §2 Delt helper + streng (alle 3 sprog) → Task 1. ✓
- §3 Adfærdstabel — alle 5 dialoger + toggle-sti → Task 2 (slider + toggle), 3, 4, 5, 6. ✓
- §4 DateTime-undtagelse (Toast + finish altid) → Task 5. ✓
- §5 Tråd-sikkerhed — ingen kodeændring nødvendig, allerede Main-bundet; ikke en separat opgave, korrekt. ✓
- QA-plan (emulator → device → code-review) → Task 7 Steps 3-4; `code-review`-skillet køres af brugeren separat før merge, uden for denne plans scope (jf. `CLAUDE.md`: "code-review køres inden merge til main" — ikke en implementeringsopgave).

**Placeholder-scan:** ingen "TBD"/"senere"/uspecificerede skridt fundet — hvert steps kode er komplet og eksakt kopieret fra de læste kildefiler med de præcise ændringer indsat.

**Type-konsistens:** `sendRangeValue` er `Boolean` alle steder den bruges (Task 2, 3). `executeConfirmedAction` er `Boolean` alle steder (Task 6). `showActionError(context: Context)` signatur matcher alle 5 kaldesteder (ingen returværdi forventet noget sted).
