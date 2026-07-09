# Widget-farvetemaer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tilføj 6 valgbare, globale farvetemaer (Blå/Grøn/Lilla/Orange/Rød/Teal) til Glance-widgets, uafhængigt af app-UI'et, med en ny "Farvetema"-vælger i `MainActivity`.

**Architecture:** Et nyt globalt `SecureStore.widgetColorTheme`-valg (samme mønster som det eksisterende `themeMode`) vælger blandt 6 faste `ColorScheme`-par (lys+mørk). `WidgetColors.providers(context)` kombinerer `widgetColorTheme` og `themeMode` til det rigtige `ColorProviders`-objekt via to nye rene (Context-uafhængige, unit-testbare) hjælpefunktioner: `presetFor()` og `resolveColorMode()`. Kun `dk.akait.hawidgets` app-modulet berøres — ingen nye dependencies.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 `ColorScheme`), Jetpack Glance (`material3ColorProviders`), JUnit4 (rene unit-tests, ingen Robolectric).

## Global Constraints

- Spec: `docs/specs/2026-07-09-widget-color-themes-design.md` — følg denne præcist.
- Kun widgets farvelægges — app-UI'et (`MainActivity`, `HaWidgetsTheme`) forbliver fast blå, uændret.
- Blå-temaet på `themeMode=system` skal fortsat bruge `DynamicThemeColorProviders` (nul regression) — al anden kombination bruger faste presets.
- Ingen Room-migration. Intet nyt tredjeparts-bibliotek.
- Byg med `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug` (jf. `CLAUDE.md`).
- **Bump `versionCode`/`versionName` i `app/build.gradle.kts` FØR sidste build** (projekt-konvention, jf. `CLAUDE.md`): nuværende `versionCode = 60` / `versionName = "0.2.60"` → `61` / `"0.2.61"`.
- Installér ALTID som `adb install -r` (aldrig uninstall) — bevarer eksisterende config/token.
- Alle nye bruger-synlige strenge skal findes i `values/strings.xml` (engelsk, default), `values-da/strings.xml` og `values-sv/strings.xml` — samme linjenummer-nabolag som de eksisterende `theme_*`-strenge for at holde de tre filer i sync.

---

### Task 1: `SecureStore` — nyt `widgetColorTheme`-valg

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/data/SecureStore.kt`

**Interfaces:**
- Produces: `SecureStore.widgetColorTheme: String` (get/set), samt konstanterne `SecureStore.COLOR_BLUE`, `COLOR_GREEN`, `COLOR_PURPLE`, `COLOR_ORANGE`, `COLOR_RED`, `COLOR_TEAL` (alle `String`), brugt af Task 2, 3 og 6.

Dette er en ren getter/setter-tilføjelse oven på et eksisterende `EncryptedSharedPreferences`-lag (samme mønster som `themeMode` lige ovenover den i filen) — kræver en rigtig Android `Context` og kan derfor ikke unit-testes uden Robolectric (som projektet ikke bruger). Ingen eksisterende `SecureStore`-property (`baseUrl`, `token`, `themeMode`) har en unit-test i dag — dette følger samme, allerede etablerede mønster. Verifikation sker via kompilering (Step 2) og senere gennem Task 6's UI-flow.

- [ ] **Step 1: Tilføj property + konstanter**

Åbn `app/src/main/java/dk/akait/hawidgets/data/SecureStore.kt`. Indsæt en ny property lige efter den eksisterende `themeMode`-property (efter linje 34, før `val isConfigured`):

```kotlin
    /**
     * Globalt farvetema for ALLE Glance-widgets (IKKE app-UI'et — det forbliver fast blå).
     * Gyldige værdier: "blue" | "green" | "purple" | "orange" | "red" | "teal". Default
     * "blue" → identisk med den historiske (eneste) farve.
     */
    var widgetColorTheme: String
        get() = prefs.getString(KEY_WIDGET_COLOR_THEME, COLOR_BLUE) ?: COLOR_BLUE
        set(value) = prefs.edit().putString(KEY_WIDGET_COLOR_THEME, value).apply()
```

Indsæt en ny nøgle-konstant i companion object'et, lige efter `KEY_THEME_MODE` (efter linje 58):

```kotlin
        private const val KEY_WIDGET_COLOR_THEME = "widget_color_theme"
```

Indsæt de 6 farve-konstanter lige efter `THEME_SYSTEM` (efter linje 63):

```kotlin
        const val COLOR_BLUE = "blue"
        const val COLOR_GREEN = "green"
        const val COLOR_PURPLE = "purple"
        const val COLOR_ORANGE = "orange"
        const val COLOR_RED = "red"
        const val COLOR_TEAL = "teal"
```

- [ ] **Step 2: Kompilér for at bekræfte syntaks**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/data/SecureStore.kt
git commit -m "feat: SecureStore.widgetColorTheme til globalt widget-farvetema"
```

---

### Task 2: Nye farve-presets (`WidgetColorPresets.kt`)

**Files:**
- Create: `app/src/main/java/dk/akait/hawidgets/widget/common/WidgetColorPresets.kt`
- Test: `app/src/test/java/dk/akait/hawidgets/widget/common/WidgetColorPresetsTest.kt`

**Interfaces:**
- Consumes: `dk.akait.hawidgets.data.SecureStore.COLOR_BLUE/COLOR_GREEN/COLOR_PURPLE/COLOR_ORANGE/COLOR_RED/COLOR_TEAL` (Task 1); `dk.akait.hawidgets.ui.theme.HaWidgetsColorScheme`, `HaWidgetsDarkColorScheme`, `HaBlueSurfaceVariant`, `HaBlueOnSurfaceVariant`, `HaBlueDarkSurfaceVariant`, `HaBlueDarkOnSurfaceVariant`, `HaBlueDarkBackground`, `HaBlueDarkOnBackground`, `HaBlueDarkSurface`, `HaBlueDarkOnSurface`, `HaBlueDarkError`, `HaBlueDarkOnError`, `HaBlueDarkErrorContainer`, `HaBlueDarkOnErrorContainer` (alle allerede eksisterende, uændrede).
- Produces: `internal data class WidgetColorPreset(val light: ColorScheme, val dark: ColorScheme)`; `internal fun presetFor(colorTheme: String): WidgetColorPreset` — brugt af Task 3 (`WidgetColors.kt`) og Task 6 (`MainActivity.kt`, til dropdown-swatch-farver via `presetFor(x).light.primary`).

`ColorScheme` (Material3) og `Color` (Compose UI graphics) er begge rene Kotlin-klasser uden Android-runtime-afhængighed — testbare i almindelig JUnit uden Robolectric, samme princip som de eksisterende `RangeSteppingTest`/`ValueFormattingTest`.

- [ ] **Step 1: Skriv den fejlende test**

Opret `app/src/test/java/dk/akait/hawidgets/widget/common/WidgetColorPresetsTest.kt`:

```kotlin
package dk.akait.hawidgets.widget.common

import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.ui.theme.HaBlueOnSurfaceVariant
import dk.akait.hawidgets.ui.theme.HaBlueSurfaceVariant
import dk.akait.hawidgets.ui.theme.HaWidgetsColorScheme
import dk.akait.hawidgets.ui.theme.HaWidgetsDarkColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetColorPresetsTest {

    @Test fun bluePresetReusesExistingAppScheme() {
        val preset = presetFor(SecureStore.COLOR_BLUE)
        assertEquals(HaWidgetsColorScheme, preset.light)
        assertEquals(HaWidgetsDarkColorScheme, preset.dark)
    }

    @Test fun greenPresetHasExpectedPrimaryColors() {
        val preset = presetFor(SecureStore.COLOR_GREEN)
        assertEquals(Color(0xFF1E8E3E), preset.light.primary)
        assertEquals(Color(0xFFFFFFFF), preset.light.onPrimary)
        assertEquals(Color(0xFF8FDB94), preset.dark.primary)
        assertEquals(Color(0xFF00390D), preset.dark.onPrimary)
    }

    @Test fun purplePresetHasExpectedPrimaryColors() {
        val preset = presetFor(SecureStore.COLOR_PURPLE)
        assertEquals(Color(0xFF6750A4), preset.light.primary)
        assertEquals(Color(0xFFFFFFFF), preset.light.onPrimary)
        assertEquals(Color(0xFFD0BCFF), preset.dark.primary)
        assertEquals(Color(0xFF381E72), preset.dark.onPrimary)
    }

    @Test fun orangePresetHasExpectedPrimaryColors() {
        val preset = presetFor(SecureStore.COLOR_ORANGE)
        assertEquals(Color(0xFF9C5700), preset.light.primary)
        assertEquals(Color(0xFFFFFFFF), preset.light.onPrimary)
        assertEquals(Color(0xFFFFB870), preset.dark.primary)
        assertEquals(Color(0xFF552F00), preset.dark.onPrimary)
    }

    @Test fun redPresetHasExpectedPrimaryColors() {
        val preset = presetFor(SecureStore.COLOR_RED)
        assertEquals(Color(0xFFC2185B), preset.light.primary)
        assertEquals(Color(0xFFFFFFFF), preset.light.onPrimary)
        assertEquals(Color(0xFFFFB2C8), preset.dark.primary)
        assertEquals(Color(0xFF5E1133), preset.dark.onPrimary)
    }

    @Test fun tealPresetHasExpectedPrimaryColors() {
        val preset = presetFor(SecureStore.COLOR_TEAL)
        assertEquals(Color(0xFF00696B), preset.light.primary)
        assertEquals(Color(0xFFFFFFFF), preset.light.onPrimary)
        assertEquals(Color(0xFF4FD8DA), preset.dark.primary)
        assertEquals(Color(0xFF00373A), preset.dark.onPrimary)
    }

    @Test fun nonBluePresetsShareNeutralSurfaceVariantWithBlue() {
        val preset = presetFor(SecureStore.COLOR_GREEN)
        assertEquals(HaBlueSurfaceVariant, preset.light.surfaceVariant)
        assertEquals(HaBlueOnSurfaceVariant, preset.light.onSurfaceVariant)
    }

    @Test fun unknownColorThemeFallsBackToBlue() {
        val preset = presetFor("not-a-real-theme")
        assertEquals(HaWidgetsColorScheme, preset.light)
        assertEquals(HaWidgetsDarkColorScheme, preset.dark)
    }
}
```

Tilføj det manglende `import androidx.compose.ui.graphics.Color` øverst i testfilen (lige under `package`-linjen).

- [ ] **Step 2: Kør testen for at bekræfte den fejler**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew :app:testDebugUnitTest --tests "dk.akait.hawidgets.widget.common.WidgetColorPresetsTest"`
Expected: FAIL (kompileringsfejl — `presetFor`/`WidgetColorPreset` findes ikke endnu)

- [ ] **Step 3: Implementér `WidgetColorPresets.kt`**

Opret `app/src/main/java/dk/akait/hawidgets/widget/common/WidgetColorPresets.kt`:

```kotlin
package dk.akait.hawidgets.widget.common

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.ui.theme.HaBlueDarkBackground
import dk.akait.hawidgets.ui.theme.HaBlueDarkError
import dk.akait.hawidgets.ui.theme.HaBlueDarkErrorContainer
import dk.akait.hawidgets.ui.theme.HaBlueDarkOnBackground
import dk.akait.hawidgets.ui.theme.HaBlueDarkOnError
import dk.akait.hawidgets.ui.theme.HaBlueDarkOnErrorContainer
import dk.akait.hawidgets.ui.theme.HaBlueDarkOnSurface
import dk.akait.hawidgets.ui.theme.HaBlueDarkOnSurfaceVariant
import dk.akait.hawidgets.ui.theme.HaBlueDarkSurface
import dk.akait.hawidgets.ui.theme.HaBlueDarkSurfaceVariant
import dk.akait.hawidgets.ui.theme.HaBlueOnSurfaceVariant
import dk.akait.hawidgets.ui.theme.HaBlueSurfaceVariant
import dk.akait.hawidgets.ui.theme.HaWidgetsColorScheme
import dk.akait.hawidgets.ui.theme.HaWidgetsDarkColorScheme

/**
 * Lys/mørk [ColorScheme]-par for ét widget-farvetema. Kun widget-koden læser [ColorScheme.primary]/
 * [onPrimary]/[surfaceVariant]/[onSurfaceVariant]/[errorContainer]/[onErrorContainer] (verificeret via
 * grep gennem alle 9 entity-widgets + MultiEntityWidget) — øvrige roller er derfor aldrig sat eksplicit
 * og falder tilbage til Material3's baseline.
 */
internal data class WidgetColorPreset(val light: ColorScheme, val dark: ColorScheme)

// De neutrale roller (surfaceVariant/error-familie/background/surface) er IKKE hue-specifikke og deles
// derfor af ALLE presets, inkl. Blå — genbruger bevidst de "HaBlue"-navngivne konstanter fra
// ui/theme/Color.kt, som reelt er farve-neutrale (kun primary/onPrimary er brand-farven).
private fun sharedLight(primary: Color, onPrimary: Color): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    secondaryContainer = HaBlueSurfaceVariant,
    onSecondaryContainer = HaBlueOnSurfaceVariant,
    surfaceVariant = HaBlueSurfaceVariant,
    onSurfaceVariant = HaBlueOnSurfaceVariant,
)

private fun sharedDark(primary: Color, onPrimary: Color): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    secondaryContainer = HaBlueDarkSurfaceVariant,
    onSecondaryContainer = HaBlueDarkOnSurfaceVariant,
    surfaceVariant = HaBlueDarkSurfaceVariant,
    onSurfaceVariant = HaBlueDarkOnSurfaceVariant,
    background = HaBlueDarkBackground,
    onBackground = HaBlueDarkOnBackground,
    surface = HaBlueDarkSurface,
    onSurface = HaBlueDarkOnSurface,
    error = HaBlueDarkError,
    onError = HaBlueDarkOnError,
    errorContainer = HaBlueDarkErrorContainer,
    onErrorContainer = HaBlueDarkOnErrorContainer,
)

internal val GreenPreset = WidgetColorPreset(
    light = sharedLight(Color(0xFF1E8E3E), Color(0xFFFFFFFF)),
    dark = sharedDark(Color(0xFF8FDB94), Color(0xFF00390D)),
)
internal val PurplePreset = WidgetColorPreset(
    light = sharedLight(Color(0xFF6750A4), Color(0xFFFFFFFF)),
    dark = sharedDark(Color(0xFFD0BCFF), Color(0xFF381E72)),
)
internal val OrangePreset = WidgetColorPreset(
    light = sharedLight(Color(0xFF9C5700), Color(0xFFFFFFFF)),
    dark = sharedDark(Color(0xFFFFB870), Color(0xFF552F00)),
)
internal val RedPreset = WidgetColorPreset(
    light = sharedLight(Color(0xFFC2185B), Color(0xFFFFFFFF)),
    dark = sharedDark(Color(0xFFFFB2C8), Color(0xFF5E1133)),
)
internal val TealPreset = WidgetColorPreset(
    light = sharedLight(Color(0xFF00696B), Color(0xFFFFFFFF)),
    dark = sharedDark(Color(0xFF4FD8DA), Color(0xFF00373A)),
)

/** [colorTheme] → dets [WidgetColorPreset]. Ukendt/uventet værdi falder sikkert tilbage til Blå
 * (samme defensive mønster som v0.2.13's sprog-dropdown-fix — undgår en kastet exception). */
internal fun presetFor(colorTheme: String): WidgetColorPreset = when (colorTheme) {
    SecureStore.COLOR_GREEN -> GreenPreset
    SecureStore.COLOR_PURPLE -> PurplePreset
    SecureStore.COLOR_ORANGE -> OrangePreset
    SecureStore.COLOR_RED -> RedPreset
    SecureStore.COLOR_TEAL -> TealPreset
    else -> WidgetColorPreset(HaWidgetsColorScheme, HaWidgetsDarkColorScheme)
}
```

- [ ] **Step 4: Kør testen for at bekræfte den er grøn**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew :app:testDebugUnitTest --tests "dk.akait.hawidgets.widget.common.WidgetColorPresetsTest"`
Expected: BUILD SUCCESSFUL, 8 tests passed

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/WidgetColorPresets.kt app/src/test/java/dk/akait/hawidgets/widget/common/WidgetColorPresetsTest.kt
git commit -m "feat: 5 nye widget-farve-presets (Grøn/Lilla/Orange/Rød/Teal)"
```

---

### Task 3: `WidgetColors.providers()` — vælg preset ud fra farvetema + tema-tilstand

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/common/WidgetColors.kt`
- Test: `app/src/test/java/dk/akait/hawidgets/widget/common/WidgetColorsTest.kt`

**Interfaces:**
- Consumes: `presetFor(colorTheme: String): WidgetColorPreset` (Task 2); `SecureStore.COLOR_BLUE`, `THEME_LIGHT`, `THEME_DARK`, `THEME_SYSTEM` (Task 1 + eksisterende); `SecureStore.get(context).widgetColorTheme` (Task 1).
- Produces: `internal enum class ColorMode { DYNAMIC, FORCED_LIGHT, FORCED_DARK, SYSTEM_PAIR }`; `internal fun resolveColorMode(colorTheme: String, themeMode: String): ColorMode` — begge rent Kotlin, testbare uden Context.

- [ ] **Step 1: Skriv den fejlende test**

Opret `app/src/test/java/dk/akait/hawidgets/widget/common/WidgetColorsTest.kt`:

```kotlin
package dk.akait.hawidgets.widget.common

import dk.akait.hawidgets.data.SecureStore
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetColorsTest {

    @Test fun blueSystemUsesDynamicColors() {
        assertEquals(
            ColorMode.DYNAMIC,
            resolveColorMode(SecureStore.COLOR_BLUE, SecureStore.THEME_SYSTEM)
        )
    }

    @Test fun blueLightUsesForcedLight() {
        assertEquals(
            ColorMode.FORCED_LIGHT,
            resolveColorMode(SecureStore.COLOR_BLUE, SecureStore.THEME_LIGHT)
        )
    }

    @Test fun blueDarkUsesForcedDark() {
        assertEquals(
            ColorMode.FORCED_DARK,
            resolveColorMode(SecureStore.COLOR_BLUE, SecureStore.THEME_DARK)
        )
    }

    @Test fun greenSystemUsesSystemPairNotDynamic() {
        // Farvetema-valget skal vinde over Android's dynamiske farve for alle temaer UNDTAGEN Blå.
        assertEquals(
            ColorMode.SYSTEM_PAIR,
            resolveColorMode(SecureStore.COLOR_GREEN, SecureStore.THEME_SYSTEM)
        )
    }

    @Test fun greenLightUsesForcedLight() {
        assertEquals(
            ColorMode.FORCED_LIGHT,
            resolveColorMode(SecureStore.COLOR_GREEN, SecureStore.THEME_LIGHT)
        )
    }

    @Test fun greenDarkUsesForcedDark() {
        assertEquals(
            ColorMode.FORCED_DARK,
            resolveColorMode(SecureStore.COLOR_GREEN, SecureStore.THEME_DARK)
        )
    }

    @Test fun unknownColorThemeWithSystemBehavesLikeNonBlue() {
        assertEquals(
            ColorMode.SYSTEM_PAIR,
            resolveColorMode("not-a-real-theme", SecureStore.THEME_SYSTEM)
        )
    }
}
```

- [ ] **Step 2: Kør testen for at bekræfte den fejler**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew :app:testDebugUnitTest --tests "dk.akait.hawidgets.widget.common.WidgetColorsTest"`
Expected: FAIL (kompileringsfejl — `ColorMode`/`resolveColorMode` findes ikke endnu)

- [ ] **Step 3: Implementér `resolveColorMode` + `ColorMode`, og omskriv `providers()`**

I `app/src/main/java/dk/akait/hawidgets/widget/common/WidgetColors.kt`, tilføj lige efter import-blokken (efter linje 13, før `object WidgetColors`):

```kotlin
/** Hvilken [ColorProviders]-konstruktion et (farvetema, tema-tilstand)-par resulterer i. */
internal enum class ColorMode { DYNAMIC, FORCED_LIGHT, FORCED_DARK, SYSTEM_PAIR }

/**
 * Ren beslutningsfunktion (ingen Context/Android-afhængighed, unit-testbar): kun Blå+System bevarer
 * den historiske Android-dynamiske Material You-farve. Enhver anden kombination bruger presettets
 * faste lys/mørk-par — farvetema-valget vinder altid over systemets dynamiske farve, jf. spec.
 */
internal fun resolveColorMode(colorTheme: String, themeMode: String): ColorMode = when {
    colorTheme == SecureStore.COLOR_BLUE && themeMode == SecureStore.THEME_SYSTEM -> ColorMode.DYNAMIC
    themeMode == SecureStore.THEME_LIGHT -> ColorMode.FORCED_LIGHT
    themeMode == SecureStore.THEME_DARK -> ColorMode.FORCED_DARK
    else -> ColorMode.SYSTEM_PAIR
}
```

Erstat herefter `providers()`-funktionen (linje 55-61 i den nuværende fil):

```kotlin
    /** [ColorProviders] svarende til det aktive farvetema + tema-tilstand. Se [resolveColorMode]
     * for selve beslutningslogikken (testet separat uden Context). */
    fun providers(context: Context): ColorProviders {
        val store = SecureStore.get(context)
        val preset = presetFor(store.widgetColorTheme)
        return when (resolveColorMode(store.widgetColorTheme, store.themeMode)) {
            ColorMode.DYNAMIC -> DynamicThemeColorProviders
            ColorMode.FORCED_LIGHT -> material3ColorProviders(light = preset.light, dark = preset.light)
            ColorMode.FORCED_DARK -> material3ColorProviders(light = preset.dark, dark = preset.dark)
            ColorMode.SYSTEM_PAIR -> material3ColorProviders(light = preset.light, dark = preset.dark)
        }
    }
```

De nu ubrugte `lightProviders`/`darkProviders`-private-vals (linje 50-53 i den nuværende fil) fjernes — deres rolle overtages af `presetFor(SecureStore.COLOR_BLUE)` + de nye `ColorMode`-grene.

Fjern desuden de to imports der nu er ubrugte i denne fil (linje 12-13, `HaWidgetsColorScheme`/`HaWidgetsDarkColorScheme` — de bruges kun i `presetFor()`'s fallback-gren i `WidgetColorPresets.kt` nu, ikke direkte i `WidgetColors.kt` længere):

```kotlin
import dk.akait.hawidgets.ui.theme.HaWidgetsColorScheme
import dk.akait.hawidgets.ui.theme.HaWidgetsDarkColorScheme
```

- [ ] **Step 4: Kør testen for at bekræfte den er grøn**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew :app:testDebugUnitTest --tests "dk.akait.hawidgets.widget.common.WidgetColorsTest"`
Expected: BUILD SUCCESSFUL, 7 tests passed

- [ ] **Step 5: Kør HELE test-suiten (regression-check for `providers()`-omskrivningen)**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle tests (inkl. de 25 eksisterende + de 15 nye fra Task 2+3) passerer

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/WidgetColors.kt app/src/test/java/dk/akait/hawidgets/widget/common/WidgetColorsTest.kt
git commit -m "feat: WidgetColors.providers() vælger farvetema-preset"
```

---

### Task 4: Bugfix — tændt chip's ring skal være `primary`, ikke `surfaceVariant`

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityRendering.kt:101`

**Interfaces:**
- Consumes: `GlanceTheme.colors` (uændret, allerede i scope i `surfaceFor()`).
- Produces: ingen ny signatur — kun ét literal-bytte i en eksisterende, allerede-brugt funktion.

Denne ændring er en Composable der læser `GlanceTheme.colors` — kan ikke unit-testes uden en Compose-testramme (findes ikke i dette projekt, jf. Task 2/3's begrundelse for hvorfor rene funktioner blev udtrukket der). Verificeres i stedet visuelt i Task 7's emulator-QA.

- [ ] **Step 1: Lav rettelsen**

Åbn `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityRendering.kt`. Find linje 101:

```kotlin
        active -> if (isChip) Surface(c.surfaceVariant, c.primary, c.onPrimary) else Surface(null, c.primary, c.onPrimary)
```

Erstat med:

```kotlin
        // v0.2.61: ring-farven for en TÆNDT chip er nu `primary` (samme som fyldet) i stedet for
        // `surfaceVariant` — ingen synlig kant på en tændt chip (kun SLUKKET chips har en synlig,
        // temafarvet ring). Tilbagevenden til v0.2.50-beslutningen, som en senere session ændrede.
        active -> if (isChip) Surface(c.primary, c.primary, c.onPrimary) else Surface(null, c.primary, c.onPrimary)
```

- [ ] **Step 2: Kompilér for at bekræfte syntaks**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityRendering.kt
git commit -m "fix: tændt sekundær-chip mister sin grå ring (ring = primary, ikke surfaceVariant)"
```

---

### Task 5: i18n — nye strenge (da/en/sv)

**Files:**
- Modify: `app/src/main/res/values/strings.xml:140`
- Modify: `app/src/main/res/values-da/strings.xml:140`
- Modify: `app/src/main/res/values-sv/strings.xml:140`

**Interfaces:**
- Produces: `R.string.widget_color_theme_label`, `R.string.color_theme_blue`, `R.string.color_theme_green`, `R.string.color_theme_purple`, `R.string.color_theme_orange`, `R.string.color_theme_red`, `R.string.color_theme_teal` — brugt af Task 6.

Ren ressource-tilføjelse, ikke unit-testbar. Verificeres via kompilering (resource-linking fejler hvis en fil mangler en nøgle andre refererer) og visuelt i Task 7's QA.

- [ ] **Step 1: Engelsk (default)**

I `app/src/main/res/values/strings.xml`, indsæt lige efter linje 140 (`<string name="theme_system">Follow system</string>`):

```xml
    <string name="widget_color_theme_label">Widget color</string>
    <string name="color_theme_blue">Blue</string>
    <string name="color_theme_green">Green</string>
    <string name="color_theme_purple">Purple</string>
    <string name="color_theme_orange">Orange</string>
    <string name="color_theme_red">Red</string>
    <string name="color_theme_teal">Teal</string>
```

- [ ] **Step 2: Dansk**

I `app/src/main/res/values-da/strings.xml`, indsæt lige efter linje 140 (`<string name="theme_system">Følg system</string>`):

```xml
    <string name="widget_color_theme_label">Farvetema</string>
    <string name="color_theme_blue">Blå</string>
    <string name="color_theme_green">Grøn</string>
    <string name="color_theme_purple">Lilla</string>
    <string name="color_theme_orange">Orange</string>
    <string name="color_theme_red">Rød</string>
    <string name="color_theme_teal">Teal</string>
```

- [ ] **Step 3: Svensk**

I `app/src/main/res/values-sv/strings.xml`, indsæt lige efter linje 140 (`<string name="theme_system">Följ systemet</string>`):

```xml
    <string name="widget_color_theme_label">Widgetfärg</string>
    <string name="color_theme_blue">Blå</string>
    <string name="color_theme_green">Grön</string>
    <string name="color_theme_purple">Lila</string>
    <string name="color_theme_orange">Orange</string>
    <string name="color_theme_red">Röd</string>
    <string name="color_theme_teal">Turkos</string>
```

- [ ] **Step 4: Kompilér for at bekræfte resource-linking**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-da/strings.xml app/src/main/res/values-sv/strings.xml
git commit -m "feat: lokaliserede strenge for widget-farvetema-vælger"
```

---

### Task 6: `MainActivity` — ny "Farvetema"-dropdown

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/MainActivity.kt`

**Interfaces:**
- Consumes: `SecureStore.widgetColorTheme` (Task 1), `presetFor(colorTheme).light.primary` (Task 2, til swatch-farve), `R.string.widget_color_theme_label` + 6 `color_theme_*`-strenge (Task 5), eksisterende `updateAllWidgets(context)`.
- Produces: ny privat Composable `ColorThemeRow(currentTheme: String, onSelect: (String) -> Unit)` — kaldes kun fra `SettingsSheet`, ingen andre forbrugere.

Composable UI, ikke unit-testbar uden instrumenteret test (projektet har ingen). Verificeres visuelt i Task 7's emulator-QA.

- [ ] **Step 1: Tilføj import**

I `app/src/main/java/dk/akait/hawidgets/MainActivity.kt`, tilføj til import-blokken (efter linje 26, `import androidx.compose.material.icons.filled.Palette`):

```kotlin
import androidx.compose.material.icons.filled.ColorLens
```

Tilføj også (efter linje 94, den sidste `dk.akait.hawidgets.widget.*`-import):

```kotlin
import dk.akait.hawidgets.widget.common.presetFor
```

- [ ] **Step 2: Tilføj `ColorThemeRow`-composable**

Indsæt lige efter `ThemeRow`-funktionen (efter linje 615, dvs. lige før kommentaren `/** Tving alle placerede...` på linje 617):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorThemeRow(currentTheme: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        SecureStore.COLOR_BLUE to stringResource(R.string.color_theme_blue),
        SecureStore.COLOR_GREEN to stringResource(R.string.color_theme_green),
        SecureStore.COLOR_PURPLE to stringResource(R.string.color_theme_purple),
        SecureStore.COLOR_ORANGE to stringResource(R.string.color_theme_orange),
        SecureStore.COLOR_RED to stringResource(R.string.color_theme_red),
        SecureStore.COLOR_TEAL to stringResource(R.string.color_theme_teal),
    )
    val selectedLabel = options.firstOrNull { it.first == currentTheme }?.second
        ?: stringResource(R.string.color_theme_blue)

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.widget_color_theme_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                selectedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (colorTheme, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(presetFor(colorTheme).light.primary)
                        )
                    },
                    onClick = {
                        onSelect(colorTheme)
                        expanded = false
                    }
                )
            }
        }
    }
}
```

- [ ] **Step 3: Wire ind i `SettingsSheet`**

I samme fil, i `SettingsSheet`-composablen: indsæt lige efter `ThemeRow`-kaldet slutter (efter linje 457, `}` der lukker `ThemeRow { ... }`-blokken, FØR `HorizontalDivider` på linje 459):

```kotlin

            var colorTheme by remember { mutableStateOf(store.widgetColorTheme) }
            ColorThemeRow(currentTheme = colorTheme) { theme ->
                store.widgetColorTheme = theme
                colorTheme = theme
                // Samme begrundelse som ThemeRow/LanguageRow (ADR-5): widgets observerer ikke
                // SecureStore reaktivt, så en eksplicit updateAll() er nødvendig. Ingen recreate()
                // her — farvetemaet påvirker KUN widgets, ikke app-UI'et (jf. spec).
                scope.launch { updateAllWidgets(context) }
            }
```

- [ ] **Step 4: Kompilér for at bekræfte syntaks**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/MainActivity.kt
git commit -m "feat: Farvetema-dropdown i Indstillinger"
```

---

### Task 7: Versionsbump, fuld build, unit-tests, emulator-QA

**Files:**
- Modify: `app/build.gradle.kts:16-17`

**Interfaces:**
- Consumes: alle foregående tasks.
- Produces: en installerbar debug-APK klar til emulator/device-QA.

- [ ] **Step 1: Bump version**

I `app/build.gradle.kts`, ret linje 16-17:

```kotlin
        versionCode = 61
        versionName = "0.2.61"
```

- [ ] **Step 2: Kør HELE test-suiten**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle tests grønne (25 eksisterende + 15 nye = 40)

- [ ] **Step 3: Byg debug-APK**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Installér på emulator (`pixel_test`) som reinstall**

Run: `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk` (juster device-id efter `adb devices`)
Expected: `Success`

- [ ] **Step 5: Emulator-QA — driv det faktiske flow**

Åbn appen → "Indstillinger" (bundsheet) → bekræft ny "Farvetema"-række vises under "Tema"-rækken, med en farve-prik pr. valgmulighed i dropdown'en. Vælg "Grøn" → bekræft en placeret entity-widget (fx en tændt lampe) skifter til grøn UDEN manuel opdatering. Skift tilbage til "Blå" + "Tema"=Følg system → bekræft ingen visuel ændring ift. før denne opgave (Android-dynamisk farve, uændret). Hvis en MultiEntityWidget med en tændt sekundær-chip er placeret: bekræft chippen ikke længere har en synlig grå ring (kun fyldfarven).

Virker noget ikke → tilbage til den relevante task, ret, gentag build+install+QA. Bliv i loopet til alt er grønt (jf. `CLAUDE.md`'s "Aldrig meld fikset uden bevis").

- [ ] **Step 6: Commit versionsbump**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version til 0.2.61 (widget-farvetemaer)"
```

**Bemærk:** Device-QA på fysisk enhed (Galaxy S23, `adb install -r`) udføres af brugeren efter denne plan er eksekveret — samme etablerede mønster som resten af projektets changelog.
