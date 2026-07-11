# Widget active-styling redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Flyt aktiv-signalet i MultiEntityWidget fra fuld baggrundsfarve til ikon+status ("D"-stil), giv chips en subtilt mørkere flade, og bevar climate-varmer (orange) + gør utilgængelig nedtonet grå — struktureret til en fremtidig tema-editor.

**Architecture:** En ren, unit-testbar `resolveStyle(...)` afgør per (række/chip, tilstand) hvilke *semantiske* farveroller (`ColorRole`) baggrund/ikon/label/status skal have + om der vises border. En `@Composable` mapper oversætter roller → faktiske `ColorProvider`'e (fra `GlanceTheme.colors` + nye `WidgetColors`-værdier). `SlotRow`/`SecondaryChip` læser kun det flade resultat. Al betinget farvelogik bor ét sted (sømmen tema-editoren senere hænger på).

**Tech Stack:** Kotlin, Jetpack Glance, JUnit (rene funktions-tests), Room (uændret — ingen migration).

## Global Constraints

- **Ingen Room-migration, ingen config-UI, ingen ny data** — ren rendering-ændring.
- **Kun `MultiEntityWidget`** har entity-rækker efter v0.2.68-konvergeringen; ingen andre widgets berøres.
- **Tema-bevidst:** aktive farver (`primary`/`onPrimary`) læses fra `GlanceTheme.colors` (respekterer farvetema automatisk). Nye neutrale/faded-værdier er farve-neutrale, forskellige pr. lys/mørk, og SKAL følge `frameBackground`-mønstret (læs `SecureStore.themeMode`, vælg side ved tvunget lys/mørk) så de matcher `GlanceTheme` i tvungne temaer.
- **Ingen borders nu**, men `showBorder`-flag + 2-lags `StatefulSurface`-kapacitet BEVARES (drevet af flag der altid er `false`) → tema-editor tænder det uden ny layout-kode.
- **Climate-varmer (fuld orange `#FF6D00`) er UÆNDRET.**
- **Bump `versionCode`/`versionName` i `app/build.gradle.kts` FØR build** (næste efter v0.2.72), jf. projektkonvention.
- **QA:** emulator (`pixel_test`, ægte HA) laves som del af planen; **S23-device-QA laver brugeren selv** (skal IKKE udføres af agenten).
- Byg med JDK17: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew`.

---

## Filstruktur

- **Create** `app/src/main/java/dk/akait/hawidgets/widget/multientity/WidgetSlotStyle.kt` — pure `ColorRole`-enum, `StyleTokens`-data class, `resolveStyle(...)`-funktion. Ingen Android-imports.
- **Create** `app/src/test/java/dk/akait/hawidgets/widget/multientity/WidgetSlotStyleTest.kt` — unit-tests for `resolveStyle`.
- **Modify** `app/src/main/java/dk/akait/hawidgets/widget/common/WidgetColors.kt` — nye `chipBackground`, `chipDimText(context)`, `fadedContent(context)` + privat `themed(context, day, night)`-helper.
- **Modify** `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityRendering.kt` — `@Composable colorFor(role, context)`-mapper; `SlotRow` + `SecondaryChip` bruger `resolveStyle` og per-element-farver; fjern gamle `surfaceFor` + `Surface`-klasse; `StatefulSurface` border-drevet af flag.
- **Modify** `app/build.gradle.kts` — versions-bump.

---

## Task 1: Pure style-resolver + unit-tests

**Files:**
- Create: `app/src/main/java/dk/akait/hawidgets/widget/multientity/WidgetSlotStyle.kt`
- Test: `app/src/test/java/dk/akait/hawidgets/widget/multientity/WidgetSlotStyleTest.kt`

**Interfaces:**
- Produces:
  - `enum class ColorRole { PRIMARY, ON_PRIMARY, ROW_BG, CHIP_BG, NEUTRAL, CHIP_DIM, FADED, HEATING_BG, ON_HEATING }`
  - `data class StyleTokens(val bg: ColorRole, val icon: ColorRole, val label: ColorRole, val status: ColorRole, val showBorder: Boolean)`
  - `fun resolveStyle(isChip: Boolean, isActive: Boolean, isToggle: Boolean, heating: Boolean, unavailable: Boolean): StyleTokens`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/dk/akait/hawidgets/widget/multientity/WidgetSlotStyleTest.kt`:

```kotlin
package dk.akait.hawidgets.widget.multientity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetSlotStyleTest {

    private fun row(active: Boolean = false, heating: Boolean = false, unavailable: Boolean = false) =
        resolveStyle(isChip = false, isActive = active, isToggle = false, heating = heating, unavailable = unavailable)

    private fun chip(active: Boolean = false, toggle: Boolean = false, heating: Boolean = false, unavailable: Boolean = false) =
        resolveStyle(isChip = true, isActive = active, isToggle = toggle, heating = heating, unavailable = unavailable)

    // --- HOVED-RÆKKE (row) ---
    @Test fun rowActiveColorsIconAndStatusKeepsLabelNeutral() {
        val t = row(active = true)
        assertEquals(ColorRole.ROW_BG, t.bg)
        assertEquals(ColorRole.PRIMARY, t.icon)
        assertEquals(ColorRole.NEUTRAL, t.label)
        assertEquals(ColorRole.PRIMARY, t.status)
        assertFalse(t.showBorder)
    }

    @Test fun rowInactiveIsAllNeutral() {
        val t = row(active = false)
        assertEquals(ColorRole.ROW_BG, t.bg)
        assertEquals(ColorRole.NEUTRAL, t.icon)
        assertEquals(ColorRole.NEUTRAL, t.label)
        assertEquals(ColorRole.NEUTRAL, t.status)
    }

    @Test fun rowHeatingIsFullOrange() {
        val t = row(active = true, heating = true)
        assertEquals(ColorRole.HEATING_BG, t.bg)
        assertEquals(ColorRole.ON_HEATING, t.icon)
        assertEquals(ColorRole.ON_HEATING, t.label)
        assertEquals(ColorRole.ON_HEATING, t.status)
    }

    @Test fun rowUnavailableIsFadedOnNeutralBg() {
        val t = row(unavailable = true)
        assertEquals(ColorRole.ROW_BG, t.bg)
        assertEquals(ColorRole.FADED, t.icon)
        assertEquals(ColorRole.FADED, t.label)
        assertEquals(ColorRole.FADED, t.status)
    }

    // --- CHIP ---
    @Test fun chipToggleActiveIsFullPrimary() {
        val t = chip(active = true, toggle = true)
        assertEquals(ColorRole.PRIMARY, t.bg)
        assertEquals(ColorRole.ON_PRIMARY, t.icon)
        assertEquals(ColorRole.ON_PRIMARY, t.label)
        assertEquals(ColorRole.ON_PRIMARY, t.status)
    }

    @Test fun chipToggleOffIsDimOnChipBg() {
        val t = chip(active = false, toggle = true)
        assertEquals(ColorRole.CHIP_BG, t.bg)
        assertEquals(ColorRole.CHIP_DIM, t.icon)
        assertEquals(ColorRole.CHIP_DIM, t.label)
        assertEquals(ColorRole.CHIP_DIM, t.status)
    }

    @Test fun chipActiveNonToggleColorsIconAndStatusDimsLabel() {
        val t = chip(active = true, toggle = false)
        assertEquals(ColorRole.CHIP_BG, t.bg)
        assertEquals(ColorRole.PRIMARY, t.icon)
        assertEquals(ColorRole.CHIP_DIM, t.label)
        assertEquals(ColorRole.PRIMARY, t.status)
    }

    @Test fun chipInfoIsAllDimOnChipBg() {
        val t = chip(active = false, toggle = false)
        assertEquals(ColorRole.CHIP_BG, t.bg)
        assertEquals(ColorRole.CHIP_DIM, t.icon)
    }

    @Test fun chipHeatingIsFullOrange() {
        val t = chip(active = true, heating = true)
        assertEquals(ColorRole.HEATING_BG, t.bg)
        assertEquals(ColorRole.ON_HEATING, t.status)
    }

    @Test fun chipUnavailableIsFadedOnChipBg() {
        val t = chip(unavailable = true)
        assertEquals(ColorRole.CHIP_BG, t.bg)
        assertEquals(ColorRole.FADED, t.icon)
    }

    // --- PRIORITET ---
    @Test fun unavailableWinsOverActive() {
        val t = chip(active = true, toggle = true, unavailable = true)
        assertEquals(ColorRole.FADED, t.icon)
    }

    @Test fun heatingWinsOverActive() {
        val t = row(active = true, heating = true)
        assertEquals(ColorRole.HEATING_BG, t.bg)
    }

    @Test fun borderIsAlwaysFalseInThisVersion() {
        assertFalse(row(active = true).showBorder)
        assertFalse(chip(active = true, toggle = true).showBorder)
        assertFalse(chip(unavailable = true).showBorder)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew testDebugUnitTest --tests "dk.akait.hawidgets.widget.multientity.WidgetSlotStyleTest"`
Expected: FAIL — `Unresolved reference: resolveStyle` / `ColorRole`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/dk/akait/hawidgets/widget/multientity/WidgetSlotStyle.kt`:

```kotlin
package dk.akait.hawidgets.widget.multientity

/**
 * Semantiske farveroller for en widget-række/chip. Oversættes til faktiske Glance-[ColorProvider]'e
 * i MultiEntityRendering.colorFor(). At holde resolveren i rene roller gør den unit-testbar UDEN
 * Android/Glance, og giver ét sted en fremtidig tema-editor kan bytte farve-sættet ud.
 */
enum class ColorRole { PRIMARY, ON_PRIMARY, ROW_BG, CHIP_BG, NEUTRAL, CHIP_DIM, FADED, HEATING_BG, ON_HEATING }

/** Fladt styling-resultat for én række/chip: hvilken rolle hvert visuelt element får. */
data class StyleTokens(
    val bg: ColorRole,
    val icon: ColorRole,
    val label: ColorRole,
    val status: ColorRole,
    val showBorder: Boolean,
)

/**
 * Ren beslutningsfunktion for active-styling (v0.2.73-redesign, se
 * docs/superpowers/specs/2026-07-11-widget-active-styling-design.md).
 *
 * - Hoved-slot aktiv = "D": ikon+status primary, label neutral, neutral baggrund.
 * - Chip TOGGLE tændt = fuld primary; øvrige aktive chips = D på chip-flade; inaktiv/info = dæmpet.
 * - Climate varmer = fuld orange (højeste prioritet efter unavailable).
 * - Utilgængelig = nedtonet grå indhold på neutral baggrund.
 *
 * [showBorder] er altid false i denne version; 2-lags-border-kapaciteten bevares til en fremtidig
 * tema-editor (så resolveren blot skal begynde at sætte flaget).
 */
fun resolveStyle(
    isChip: Boolean,
    isActive: Boolean,
    isToggle: Boolean,
    heating: Boolean,
    unavailable: Boolean,
): StyleTokens {
    val bgNeutral = if (isChip) ColorRole.CHIP_BG else ColorRole.ROW_BG
    // "dæmpet" er CHIP_DIM på en chip, men almindelig NEUTRAL på hoved-rækken (rækken har ikke en
    // subtilt-mørkere flade at dæmpe imod).
    val dim = if (isChip) ColorRole.CHIP_DIM else ColorRole.NEUTRAL
    return when {
        unavailable -> StyleTokens(bgNeutral, ColorRole.FADED, ColorRole.FADED, ColorRole.FADED, false)
        heating -> StyleTokens(ColorRole.HEATING_BG, ColorRole.ON_HEATING, ColorRole.ON_HEATING, ColorRole.ON_HEATING, false)
        isChip && isToggle && isActive ->
            StyleTokens(ColorRole.PRIMARY, ColorRole.ON_PRIMARY, ColorRole.ON_PRIMARY, ColorRole.ON_PRIMARY, false)
        isActive -> StyleTokens(bgNeutral, ColorRole.PRIMARY, dim, ColorRole.PRIMARY, false)
        else -> StyleTokens(bgNeutral, dim, dim, dim, false)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew testDebugUnitTest --tests "dk.akait.hawidgets.widget.multientity.WidgetSlotStyleTest"`
Expected: PASS (14 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/multientity/WidgetSlotStyle.kt app/src/test/java/dk/akait/hawidgets/widget/multientity/WidgetSlotStyleTest.kt
git commit -m "feat: pure resolveStyle for widget active-styling (D-stil, chip, hvac, faded)"
```

---

## Task 2: Nye farve-værdier i WidgetColors

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/common/WidgetColors.kt`

**Interfaces:**
- Consumes: intet fra tidligere tasks.
- Produces:
  - `val WidgetColors.chipBackground: ColorProvider` (subtil mørk scrim, begge temaer)
  - `fun WidgetColors.chipDimText(context: Context): ColorProvider`
  - `fun WidgetColors.fadedContent(context: Context): ColorProvider`

- [ ] **Step 1: Tilføj værdierne**

I `app/src/main/java/dk/akait/hawidgets/widget/common/WidgetColors.kt`, tilføj inde i `object WidgetColors` (efter `onHeating`-linjen). Bemærk `Color` er allerede importeret (`androidx.compose.ui.graphics.Color`), og `Context` (`android.content.Context`):

```kotlin
    /**
     * Chip-flade: subtilt MØRKERE end hoved-rækkens neutrale baggrund, så en chip løfter sig visuelt
     * fra rækken (de deler ellers samme neutrale farve og flyder sammen). Realiseret som en let mørk
     * scrim (samme værdi i begge temaer) der lægges OVEN PÅ rækkens baggrund → adapterer automatisk
     * til den underliggende farve, inkl. Blå+system's dynamiske Material You-flade. Fast (ikke
     * konfigurerbar) i v0.2.73; en fremtidig tema-editor kan gøre den bruger-styret.
     */
    val chipBackground: ColorProvider = ColorProvider(Color(0x22000000))

    /** Dæmpet ("mørkere hvid") tekst/ikon på en chip — nedtonet ift. rækkens onSurfaceVariant, så
     * chip-indhold underordner sig hoved-rækken. Fast lys/mørk-par, tvunget-tema-bevidst. */
    fun chipDimText(context: Context): ColorProvider =
        themed(context, day = Color(0xFF4A535B), night = Color(0xFFAEB6BA))

    /** Nedtonet grå for en UTILGÆNGELIG entitet (v0.2.73: erstatter den fulde røde error-container
     * — en offline entitet er ikke en fejl brugeren skal rette). Fast lys/mørk-par. */
    fun fadedContent(context: Context): ColorProvider =
        themed(context, day = Color(0xFF9AA1A8), night = Color(0xFF7A8085))

    /** Fælles lys/mørk-vælger: tvinger en side ved forced light/dark (så farven matcher
     * GlanceTheme, som også tvinges), ellers en dynamisk day/night-provider. Samme mønster som
     * [frameBackground]/[refreshOverlay]. */
    private fun themed(context: Context, day: Color, night: Color): ColorProvider =
        when (SecureStore.get(context).themeMode) {
            SecureStore.THEME_LIGHT -> ColorProvider(day)
            SecureStore.THEME_DARK -> ColorProvider(night)
            else -> androidx.glance.color.ColorProvider(day = day, night = night)
        }
```

- [ ] **Step 2: Byg**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (kun præeksisterende `ExperimentalCoroutinesApi`-warning).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/common/WidgetColors.kt
git commit -m "feat: chipBackground/chipDimText/fadedContent farver til active-styling"
```

---

## Task 3: Rolle→farve-mapper + SlotRow bruger resolveStyle

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityRendering.kt` (SlotRow ~281-363; tilføj `colorFor` ved siden af `surfaceFor`)

**Interfaces:**
- Consumes: `resolveStyle`, `StyleTokens`, `ColorRole` (Task 1); `WidgetColors.chipBackground/chipDimText/fadedContent` (Task 2).
- Produces: `@Composable private fun colorFor(role: ColorRole, context: Context): ColorProvider` (bruges også af Task 4).

Denne task lader `surfaceFor`/`Surface`/`StatefulSurface` stå urørt endnu (fjernes i Task 5) — `SlotRow` skiftes til den nye model med en midlertidig lokal border-fri render, så den kan QA'es isoleret.

- [ ] **Step 1: Tilføj rolle→farve-mapperen**

I `MultiEntityRendering.kt`, tilføj efter `surfaceFor`-funktionen (omkring linje 106):

```kotlin
/** Oversætter en semantisk [ColorRole] til en faktisk Glance-[ColorProvider]. Aktive farver læses
 * fra GlanceTheme (respekterer farvetema); neutrale/faded/chip-flader fra WidgetColors. */
@Composable
private fun colorFor(role: ColorRole, context: Context): ColorProvider = when (role) {
    ColorRole.PRIMARY -> GlanceTheme.colors.primary
    ColorRole.ON_PRIMARY -> GlanceTheme.colors.onPrimary
    ColorRole.ROW_BG -> GlanceTheme.colors.surfaceVariant
    ColorRole.NEUTRAL -> GlanceTheme.colors.onSurfaceVariant
    ColorRole.CHIP_BG -> WidgetColors.chipBackground
    ColorRole.CHIP_DIM -> WidgetColors.chipDimText(context)
    ColorRole.FADED -> WidgetColors.fadedContent(context)
    ColorRole.HEATING_BG -> WidgetColors.heatingFill
    ColorRole.ON_HEATING -> WidgetColors.onHeating
}
```

- [ ] **Step 2: Omskriv SlotRow til resolveStyle + per-element-farver**

Erstat hele `SlotRow`-funktionen (linje ~281-363) med:

```kotlin
@Composable
private fun SlotRow(
    context: Context,
    slot: MultiWidgetSlotEntity,
    states: Map<String, EntityStateEntity?>,
) {
    val displayState = states[slot.displayEntityId]
    val actionState = states[slot.actionEntityId]
    val isUnavailable = displayState?.state == "unavailable" ||
        (slot.action != "NONE" && actionState?.state == "unavailable")
    val isActive = displayState != null && isActiveState(slot.displayDomain, displayState.state)
    val heating = isHeating(slot.displayDomain, displayState) ||
        (slot.action != "NONE" && isHeating(slot.actionDomain, actionState))

    val tokens = resolveStyle(
        isChip = false,
        isActive = isActive,
        isToggle = false,
        heating = heating,
        unavailable = isUnavailable,
    )
    val bgColor = colorFor(tokens.bg, context)
    val iconColor = colorFor(tokens.icon, context)
    val labelColor = colorFor(tokens.label, context)
    val statusColor = colorFor(tokens.status, context)

    val label = slot.label.ifEmpty {
        friendlyNameFromJson(displayState?.attributesJson ?: "{}") ?: slot.displayEntityId
    }
    val statusBase = displayValueFor(context, slot.displayDomain, displayState, slot.displayPrecision, slot.datetimeFormat)
    val statusText = if (displayState != null && displayState.isStale()) "$statusBase ~" else statusBase

    val chips = slot.secondaryChips()
    val rowContent: @Composable () -> Unit = {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (slot.showIcon) {
                Image(
                    provider = ImageProvider(domainIconResId(slot.displayDomain)),
                    contentDescription = label,
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = ColorFilter.tint(iconColor),
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(label, style = TextStyle(color = labelColor, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text(statusText, style = TextStyle(color = statusColor, fontSize = 11.sp), maxLines = 1)
            }
            if (chips.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    chips.forEachIndexed { index, chip ->
                        if (index > 0) Spacer(modifier = GlanceModifier.width(CHIP_GAP_DP.dp))
                        SecondaryChip(context, chip, states)
                    }
                }
            }
        }
    }

    fun withClick(base: GlanceModifier) = clickModifier(
        context = context,
        base = base,
        action = slot.action,
        actionEntityId = slot.actionEntityId,
        actionDomain = slot.actionDomain,
        refreshEntityId = slot.displayEntityId,
        rangeLabel = label,
        actionState = actionState,
        confirmAction = slot.confirmAction,
        rangeInputMode = slot.rangeInputMode,
        packageName = slot.actionPackageName,
    )

    // Border altid false i v0.2.73 → altid ét fyldt lag. StatefulSurface bevares (Task 5) til den
    // fremtidige border-kapacitet; her renderes det enkle lag direkte.
    Box(
        modifier = withClick(
            GlanceModifier.fillMaxWidth().background(bgColor).cornerRadius(ROW_CORNER_DP.dp).padding(ROW_SINGLE_PAD_DP.dp),
        ),
        contentAlignment = Alignment.Center,
    ) { rowContent() }
}
```

- [ ] **Step 3: Byg**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (`SecondaryChip` bruger stadig gammel `surfaceFor` — det er OK indtil Task 4.)

- [ ] **Step 4: Emulator-QA (visuel)**

Installer og verificér hoved-rækkerne i BEGGE temaer:

```bash
<SDK>/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Med en placeret multi-widget mod ægte HA, bekræft på `pixel_test`:
- Aktiv on/off-række (fx et tændt lys): neutral baggrund, ikon+status i primary-farve, navn neutralt (IKKE fuld baggrund længere).
- Inaktiv række: alt neutralt.
- Climate der varmer: fuld orange, hvid tekst.
- Utilgængelig entitet: nedtonet grå indhold, neutral baggrund (IKKE rød).
- Skift app-tema (Indstillinger → Tema → Lys/Mørk) og gentag — farverne skal matche temaet.

Virker det ikke → tilbage til Step 2. Bliv i loopet til grønt.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityRendering.kt
git commit -m "feat: SlotRow bruger resolveStyle (D-stil, orange, faded) + colorFor-mapper"
```

---

## Task 4: SecondaryChip bruger resolveStyle

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityRendering.kt` (SecondaryChip ~366-455)

**Interfaces:**
- Consumes: `resolveStyle`, `colorFor` (Task 3), `WidgetColors.chipBackground` (Task 2).

- [ ] **Step 1: Omskriv SecondaryChip**

Erstat hele `SecondaryChip`-funktionen med:

```kotlin
@Composable
private fun SecondaryChip(
    context: Context,
    chip: SecondaryChipData,
    states: Map<String, EntityStateEntity?>,
) {
    val displayState = states[chip.displayEntityId]
    val actionState = states[chip.actionEntityId]
    val isUnavailable = displayState?.state == "unavailable" ||
        (chip.action != "NONE" && actionState?.state == "unavailable")
    val stateful = chip.action != "NONE" && hasOnOffState(chip.actionDomain)
    val isActive = stateful && actionState != null && isActiveState(chip.actionDomain, actionState.state)
    val heating = isHeating(chip.displayDomain, displayState) ||
        (chip.action != "NONE" && isHeating(chip.actionDomain, actionState))

    val tokens = resolveStyle(
        isChip = true,
        isActive = isActive,
        isToggle = chip.action == "TOGGLE",
        heating = heating,
        unavailable = isUnavailable,
    )
    val bgColor = colorFor(tokens.bg, context)
    val iconColor = colorFor(tokens.icon, context)
    val labelColor = colorFor(tokens.label, context)
    val valueColor = colorFor(tokens.status, context)

    val labelText = chip.label
    val valueText = if (chip.showValue) {
        displayValueFor(context, chip.displayDomain, displayState, chip.displayPrecision, chip.datetimeFormat)
    } else null

    val hasText = labelText.isNotEmpty() || valueText != null
    val chipContent: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (chip.showIcon) {
                Image(
                    provider = ImageProvider(domainIconResId(chip.displayDomain)),
                    contentDescription = labelText.ifEmpty { chip.displayEntityId },
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(iconColor),
                )
                if (hasText) Spacer(modifier = GlanceModifier.width(4.dp))
            }
            if (hasText) {
                Column {
                    if (labelText.isNotEmpty()) {
                        Text(labelText, style = TextStyle(color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                    if (valueText != null) {
                        Text(valueText, style = TextStyle(color = valueColor, fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
        }
    }

    fun withClick(base: GlanceModifier) = clickModifier(
        context = context,
        base = base,
        action = chip.action,
        actionEntityId = chip.actionEntityId,
        actionDomain = chip.actionDomain,
        refreshEntityId = chip.displayEntityId,
        rangeLabel = labelText.ifEmpty { chip.displayEntityId },
        actionState = actionState,
        confirmAction = chip.confirmAction,
        rangeInputMode = chip.rangeInputMode,
    )

    // Asymmetrisk padding når ikon vises (v0.2.60): 6dp før ikon / 8dp efter tekst; symmetrisk 8dp
    // uden ikon (v0.2.59). Border altid false i v0.2.73 → ét fyldt lag.
    val chipPad = if (chip.showIcon) {
        GlanceModifier.padding(start = CHIP_SINGLE_H_PAD_DP.dp, end = CHIP_SINGLE_H_PAD_END_DP.dp)
    } else {
        GlanceModifier.padding(horizontal = CHIP_SINGLE_H_PAD_NO_ICON_DP.dp)
    }
    Box(
        modifier = withClick(
            GlanceModifier.height(48.dp).background(bgColor).cornerRadius(CHIP_CORNER_DP.dp).then(chipPad),
        ),
        contentAlignment = Alignment.Center,
    ) { chipContent() }
}
```

- [ ] **Step 2: Byg**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Emulator-QA (visuel)**

Installer (`adb install -r ...`) og bekræft chips i BEGGE temaer på `pixel_test`:
- TOGGLE-chip tændt: fuld primary baggrund, onPrimary indhold.
- TOGGLE-chip slukket / info-chip: subtilt mørkere flade end rækken, dæmpet grå tekst.
- Aktiv ikke-toggle chip (fx device_tracker "hjemme" med historik-handling): mørkere flade, ikon+værdi i primary, label dæmpet.
- Chip på utilgængelig entitet: nedtonet grå.
- Chip løfter sig synligt fra hoved-rækken (adskillelse virker).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityRendering.kt
git commit -m "feat: SecondaryChip bruger resolveStyle (subtil chip-flade, ingen border)"
```

---

## Task 5: Fjern død kode + bevar border-kapacitet i StatefulSurface

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityRendering.kt`

Efter Task 3+4 er `surfaceFor` og `Surface`-klassen ubrugte. `StatefulSurface` er også ubrugt, men skal BEVARES (border-kapaciteten til tema-editoren) — omdøbt/dokumenteret så den ikke ser død ud.

- [ ] **Step 1: Fjern `surfaceFor` + `Surface`-klassen**

Slet `private class Surface(...)` (linje ~85) og hele `@Composable private fun surfaceFor(...)` (linje ~87-106). Behold `isHeating` (bruges stadig).

- [ ] **Step 2: Bevar StatefulSurface som fremtidig border-kapacitet**

Erstat `StatefulSurface`-doc-kommentaren + signatur, så det er klart den er bevaret bevidst. Ret funktionen til at tage flade farver + `showBorder`/`borderColor` i stedet for den slettede `Surface`-type:

```kotlin
/**
 * BEVARET til en fremtidig tema-editor (chip-border-toggle). Renderer et lag med [bg] enten som ét
 * fyldt lag ([showBorder] == false) eller to lag (ring i [borderColor] om fyldet). I v0.2.73 kalder
 * INGEN denne funktion — [resolveStyle] returnerer altid showBorder == false, og SlotRow/SecondaryChip
 * renderer det enkle lag direkte. Når editoren tænder borders, ruter de to render-steder herigennem.
 */
@Composable
private fun StatefulSurface(
    bg: ColorProvider,
    borderColor: ColorProvider?,
    showBorder: Boolean,
    cornerDp: Int,
    outerBase: GlanceModifier,
    innerBase: GlanceModifier,
    ringInnerPad: GlanceModifier,
    singlePad: GlanceModifier,
    makeClickable: (GlanceModifier) -> GlanceModifier,
    content: @Composable () -> Unit,
) {
    if (showBorder && borderColor != null) {
        Box(
            modifier = makeClickable(
                outerBase.background(borderColor).cornerRadius(cornerDp.dp).padding(SURFACE_BORDER_DP.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = innerBase.background(bg)
                    .cornerRadius((cornerDp - SURFACE_BORDER_DP).dp).then(ringInnerPad),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    } else {
        Box(
            modifier = makeClickable(
                outerBase.background(bg).cornerRadius(cornerDp.dp).then(singlePad),
            ),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
```

Note: `StatefulSurface` vil nu udløse en "unused"-warning. Tilføj `@Suppress("unused")` på funktionen med en kommentar om at den er bevaret bevidst. Konstanter `ROW_INNER_PAD_DP`, `CHIP_INNER_H_PAD_DP`, `CHIP_INNER_H_PAD_END_DP`, `CHIP_INNER_H_PAD_NO_ICON_DP`, `SURFACE_BORDER_DP` bruges kun af den bevarede `StatefulSurface`; behold dem (tilføj `@Suppress("unused")` hvor compileren klager).

- [ ] **Step 3: Byg + kør alle unit-tests**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL; alle tests grønne (55 eksisterende + 14 nye = 69).

- [ ] **Step 4: Emulator-QA (regression)**

Installer og bekræft at ALT stadig renderer korrekt (ingen visuel regression fra Task 3/4): fuld tilstands-gennemgang i begge temaer på en placeret multi-widget. Bekræft ingen crash i logcat.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityRendering.kt
git commit -m "refactor: fjern gammel surfaceFor/Surface, bevar StatefulSurface til border-kapacitet"
```

---

## Task 6: Versions-bump + endelig QA-gate

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Bump version**

I `app/build.gradle.kts`, find `versionCode`/`versionName` (i dag svarende til v0.2.72) og hæv til næste (`versionName = "0.2.73"`, `versionCode` +1).

- [ ] **Step 2: Clean build + alle tests**

Run: `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew clean assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL; alle 69 tests grønne.

- [ ] **Step 3: Fuld emulator-QA i begge temaer**

Installer (`adb install -r ...`) på `pixel_test` mod ægte HA. Gennemgå HELE beslutningstabellen fra spec'en i BÅDE lyst og mørkt tema:
- Aktiv D-række · inaktiv · info-domæne (sensor) · climate-varmer (orange) · utilgængelig (faded)
- Chip: TOGGLE tændt (fuld) · TOGGLE slukket/info (dæmpet) · aktiv ikke-toggle (D) · chip-adskillelse synlig · utilgængelig (faded)
- Skift tema undervejs og bekræft alle farver følger med. Ingen crash i logcat.

- [ ] **Step 4: Commit + CLAUDE.md changelog**

Opdater `CLAUDE.md`'s status-sektion med en v0.2.73-post (kort: hvad ændrede sig, QA-status = emulator grøn / S23 afventer bruger, spec+plan-stier). Så:

```bash
git add app/build.gradle.kts CLAUDE.md
git commit -m "chore: bump v0.2.73 (widget active-styling redesign) + changelog"
```

- [ ] **Step 5: Overdrag til bruger for S23-QA**

Meld til brugeren: emulator-QA grøn, versionsnavn v0.2.73 (build-nr), klar til S23-device-QA (som brugeren selv udfører). Kør `code-review` inden merge til main.

---

## Self-Review-noter (udført)

- **Spec-dækning:** D-stil (Task 3), chip-flade+dæmpet (Task 2+4), TOGGLE-chip fuld (Task 1+4), climate-orange (Task 1, uændrede farver), utilgængelig faded (Task 1+2), ingen borders + bevaret kapacitet (Task 1+5), begge temaer (Task 2's `themed`-helper + colorFor via GlanceTheme), tema-editor-søm (StyleTokens/colorFor/StatefulSurface). Alle dækket.
- **Parkeret (ikke i denne plan, egne opgaver):** skydende værdier, tema-editor, chip-adskillelse pr. row-entity.
- **Type-konsistens:** `resolveStyle`/`StyleTokens`/`ColorRole` bruges identisk i Task 1/3/4; `colorFor` defineres i Task 3, bruges i Task 4.
