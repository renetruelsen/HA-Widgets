# Widget-farvetemaer — design

**Status:** Godkendt af bruger 2026-07-09.
**Branch:** `feature/widget-color-themes`.

## Baggrund

Appen har i dag ét globalt tema-valg (`SecureStore.themeMode`: light/dark/system) der styrer lys/mørk
for både app-UI'et og alle Glance-widgets — men farven er altid den samme faste HA-blå
(`HaWidgetsColorScheme`/`HaWidgetsDarkColorScheme`, undtagen "system"-tilstand som bruger Android's
dynamiske Material You-farve). Bruger ønsker flere **farvetemaer** — udelukkende for widgets, ikke
for app-UI'et.

## Scope

- **Kun Glance-widgets** (`WidgetColors.kt`/`WidgetGlanceTheme`-forbrugere). App-UI'et
  (`MainActivity`, `HaWidgetsTheme`) er UDENFOR scope og forbliver fast blå uanset widget-farvetema.
- **Globalt valg**, ikke pr. widget. Ét dropdown-valg i `MainActivity` gælder alle placerede widgets
  (samme model som det eksisterende `themeMode`).
- **6 faste presets**, ingen fri farvevælger: Blå (nuværende/default), Grøn, Lilla, Orange, Rød, Teal.
- Ingen Room-migration — nyt valg gemmes i `SecureStore` (EncryptedSharedPreferences), samme lag som
  `themeMode`.

## Data

Ny property i `SecureStore.kt`, samme mønster som `themeMode`:

```kotlin
var widgetColorTheme: String
    get() = prefs.getString(KEY_WIDGET_COLOR_THEME, COLOR_BLUE) ?: COLOR_BLUE
    set(value) = prefs.edit().putString(KEY_WIDGET_COLOR_THEME, value).apply()

companion object {
    const val COLOR_BLUE = "blue"
    const val COLOR_GREEN = "green"
    const val COLOR_PURPLE = "purple"
    const val COLOR_ORANGE = "orange"
    const val COLOR_RED = "red"
    const val COLOR_TEAL = "teal"
}
```

## Farve-presets

Kun `primary`/`onPrimary` varierer pr. tema. `surfaceVariant`/`onSurfaceVariant`, error-familien,
`background`/`surface` er DELT på tværs af alle 6 presets (genbruger de eksisterende blå-neutrale
værdier fra `HaWidgetsColorScheme`/`HaWidgetsDarkColorScheme`) — "slukket"/"info"/"utilgængelig" ser
ens ud uanset valgt farvetema.

Godkendt via visuelt mockup (2026-07-09):

| Preset | Lys `primary` | Lys `onPrimary` | Mørk `primary` | Mørk `onPrimary` |
|---|---|---|---|---|
| Blå (uændret) | `#0B6FA4` | `#FFFFFF` | `#7FC3E8` | `#00344C` |
| Grøn | `#1E8E3E` | `#FFFFFF` | `#8FDB94` | `#00390D` |
| Lilla | `#6750A4` | `#FFFFFF` | `#D0BCFF` | `#381E72` |
| Orange | `#9C5700` | `#FFFFFF` | `#FFB870` | `#552F00` |
| Rød (rosa-rød) | `#C2185B` | `#FFFFFF` | `#FFB2C8` | `#5E1133` |
| Teal | `#00696B` | `#FFFFFF` | `#4FD8DA` | `#00373A` |

**Rød er bevidst rosa/magenta**, ikke ren rød — for at undgå at ligne den delte
`errorContainer`/`onErrorContainer`-farve (utilgængelig-tilstand), som allerede bruger den røde
familie (`#FFB4AB`/`#690005` i mørk).

**Kendt, accepteret interaktion:** climate-entiteter der faktisk varmer (`hvac_action == "heating"`)
vises altid med en FAST orange farve (`WidgetColors.heatingFill = #FF6D00`, v0.2.48/57-beslutning),
uanset valgt farvetema. Vælges "Orange"-temaet, kan en varmende climate-chip/-række derfor ligne
temaets almindelige tændt-farve. Accepteret — ingen ændring af `heatingFill`.

## Provider-valg (`WidgetColors.providers`)

```
if (colorTheme == BLUE && themeMode == SYSTEM):
    DynamicThemeColorProviders   # uændret adfærd, nul regression for eksisterende brugere
else:
    preset = presetFor(colorTheme)   # (lightScheme, darkScheme)
    when (themeMode):
        LIGHT  -> material3ColorProviders(light = preset.light, dark = preset.light)
        DARK   -> material3ColorProviders(light = preset.dark,  dark = preset.dark)
        SYSTEM -> material3ColorProviders(light = preset.light, dark = preset.dark)
                  # Android skifter selv via day/night-resource-qualifiers — ingen manuel
                  # dark-mode-detektion nødvendig, samme mekanisme som findes i dag for LIGHT/DARK.
```

## Nye/ændrede filer

- **`widget/common/WidgetColorPresets.kt`** (ny) — de 5 nye `ColorScheme`-par (Grøn/Lilla/Orange/Rød/
  Teal) som `lightColorScheme()`/`darkColorScheme()`, genbruger delte neutrale konstanter fra
  `ui/theme/Color.kt` (surfaceVariant/onSurfaceVariant/error-familie/background/surface).
- **`widget/common/WidgetColors.kt`** — `providers()` udvidet med preset-opslag (se ovenfor).
- **`data/SecureStore.kt`** — ny `widgetColorTheme`-property + 6 konstanter.
- **`MainActivity.kt`** — ny "Farvetema"-dropdown-række, samme mønster som eksisterende `ThemeRow`
  (`ColorThemeRow`), med en lille farvet prik pr. valgmulighed (bruger presettets lys-`primary` som
  swatch-farve). Kalder `updateAllWidgets()` ved skift (samme mønster som tema-/sprogvalg).
- **`res/values*/strings.xml`** (da/en/sv) — nyt label + 6 preset-navne.
- **`widget/multientity/MultiEntityRendering.kt`** — bugfix i `surfaceFor()`: TÆNDT chip's ring-farve
  ændres fra `c.surfaceVariant` (grå) til `c.primary` (samme som fyldet) — se nedenfor.

## Chip/række border-matrix

Række og chip farvelægges 100% uafhængigt af hinanden — hver læser kun sin EGEN action-måls
tilstand (v0.2.29: en chip kan pege på en anden entitet end rækken). Rækkens farve påvirker aldrig
chippens, og omvendt. **Rækken har ALDRIG en ring, uanset tilstand** — kun fyld-/tekstfarven skifter.
Kun chips kan have en ring, og kun når chippens EGET mål er et on/off-domæne.

| Element | Tilstand | Fyld | Tekst/ikon | Ring |
|---|---|---|---|---|
| Række | Utilgængelig | `errorContainer` | `onErrorContainer` | Ingen |
| Række | Varmer nu (climate) | `heatingFill` (fast `#FF6D00`) | `onHeating` (hvid) | Ingen |
| Række | Info-domæne (sensor/number/scene/script) | `surfaceVariant` | `onSurfaceVariant` | Ingen |
| Række | On/off-domæne, TÆNDT | `primary` | `onPrimary` | Ingen |
| Række | On/off-domæne, SLUKKET | `surfaceVariant` | `onSurfaceVariant` | Ingen |
| Chip | Utilgængelig | `errorContainer` | `onErrorContainer` | Ingen |
| Chip | Varmer nu (climate) | `heatingFill` | `onHeating` | Ingen |
| Chip | Info-domæne | `surfaceVariant` | `onSurfaceVariant` | Ingen |
| Chip | On/off-domæne, TÆNDT | `primary` | `onPrimary` | **`primary`** (samme som fyld → ingen synlig kant) |
| Chip | On/off-domæne, SLUKKET | `surfaceVariant` | `onSurfaceVariant` | **`primary`** (temafarvet, synlig kant mod grå fyld) |

Kombinationseksempler (slot × chip, uafhængige — enhver kombination af de to kolonner er gyldig):
slot TÆNDT + chip TÆNDT, slot TÆNDT + chip SLUKKET, slot SLUKKET + chip TÆNDT, slot SLUKKET + chip
SLUKKET, slot Info + chip TÆNDT/SLUKKET, osv. — se tabellen ovenfor, kombinér frit pr. element.

**Bugfix inkluderet i denne opgave:** `surfaceFor()`'s TÆNDT+chip-gren ændres fra
`Surface(c.surfaceVariant, c.primary, c.onPrimary)` til `Surface(c.primary, c.primary, c.onPrimary)`
— en tilbagevenden til v0.2.50-beslutningen ("chip-ring fjernet helt" for aktive chips), som en
udokumenteret session (v0.2.51/56) tilsyneladende ændrede uden at opdatere `CLAUDE.md`. Kun SLUKKET
chips beholder en synlig, temafarvet ring herefter.

## MainActivity UI

Ny `ColorThemeRow` (composable), placeret lige under den eksisterende `ThemeRow` (Lys/Mørk/System) i
"Indstillinger"-sektionen. Samme dropdown-mønster (`ExposedDropdownMenuBox`/`DropdownMenuItem`) som
`ThemeRow`/`LanguageRow`. Hver menu-item viser en lille farvet cirkel (presettets lys-`primary`) +
det lokaliserede navn. Persisterer til `SecureStore.widgetColorTheme`, kalder
`scope.launch { updateAllWidgets(context) }` ved valg (matcher eksisterende `ThemeRow`/`LanguageRow`-
adfærd).

## i18n

Nye strenge (da/en/sv):
- `widget_color_theme_label` ("Farvetema"/"Widget color"/"Widgetfärg")
- `color_theme_blue`, `color_theme_green`, `color_theme_purple`, `color_theme_orange`,
  `color_theme_red`, `color_theme_teal`

## Test

Unit-test af `WidgetColors.providers()`-valglogikken: matrix af `colorTheme × themeMode` →
forventet provider-identitet (Blå+System → `DynamicThemeColorProviders`; alle andre kombinationer →
korrekt lys/mørk-scheme-par), samme ånd som eksisterende 25 unit-tests i projektet.

Manuel QA (emulator + device, jf. projektets faste workflow i `CLAUDE.md`): skift farvetema i
`MainActivity`, bekræft alle placerede widgets skifter farve uden manuel opdatering
(`updateAllWidgets()`), bekræft Blå+System stadig følger Android's dynamiske farve (ingen
regression), bekræft TÆNDT chip ikke længere har synlig ring, bekræft SLUKKET chip's ring følger det
valgte tema.

## Non-goals

- Fri farvevælger (custom hex/color picker) — kun de 6 faste presets.
- Per-widget farvetema — kun ét globalt valg.
- App-UI-farve (MainActivity osv.) — forbliver fast blå.
- Ændring af `heatingFill`/`frameBackground`/`refreshOverlay` — disse forbliver faste,
  tema-uafhængige (semantiske) farver, uændret af denne opgave.
