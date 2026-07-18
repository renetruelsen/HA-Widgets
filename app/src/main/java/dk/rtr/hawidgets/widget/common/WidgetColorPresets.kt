package dk.rtr.hawidgets.widget.common

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dk.rtr.hawidgets.R
import dk.rtr.hawidgets.data.SecureStore
import dk.rtr.hawidgets.ui.theme.HaBlueDarkBackground
import dk.rtr.hawidgets.ui.theme.HaBlueDarkError
import dk.rtr.hawidgets.ui.theme.HaBlueDarkErrorContainer
import dk.rtr.hawidgets.ui.theme.HaBlueDarkOnBackground
import dk.rtr.hawidgets.ui.theme.HaBlueDarkOnError
import dk.rtr.hawidgets.ui.theme.HaBlueDarkOnErrorContainer
import dk.rtr.hawidgets.ui.theme.HaBlueDarkOnSurface
import dk.rtr.hawidgets.ui.theme.HaBlueDarkOnSurfaceVariant
import dk.rtr.hawidgets.ui.theme.HaBlueDarkSurface
import dk.rtr.hawidgets.ui.theme.HaBlueDarkSurfaceVariant
import dk.rtr.hawidgets.ui.theme.HaBlueOnSurfaceVariant
import dk.rtr.hawidgets.ui.theme.HaBlueSurfaceVariant
import dk.rtr.hawidgets.ui.theme.HaWidgetsColorScheme
import dk.rtr.hawidgets.ui.theme.HaWidgetsDarkColorScheme

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

// Blå genbruger app-UI'ets eksisterende (historiske, eneste) skema uændret — ingen sharedLight/Dark,
// så farve-parity med tiden før farvetema-featuren er bit-for-bit garanteret.
internal val BluePreset = WidgetColorPreset(HaWidgetsColorScheme, HaWidgetsDarkColorScheme)

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

/** Ét valgbart widget-farvetema: nøgle (persisteret i [SecureStore.widgetColorTheme]),
 * label-ressource (til pickeren) og farve-preset. */
internal data class WidgetColorTheme(
    val key: String,
    @StringRes val labelRes: Int,
    val preset: WidgetColorPreset,
)

/**
 * ÉN kilde til sandhed for de valgbare farvetemaer — læst af BÅDE settings-pickeren
 * (`MainActivity.ColorThemeRow`) og [presetFor]. Tilføj/fjern et tema ét sted her, så picker og
 * resolver aldrig kan divergere. Rækkefølgen er visningsrækkefølgen i dropdown'en (Blå først).
 */
internal val WIDGET_COLOR_THEMES: List<WidgetColorTheme> = listOf(
    WidgetColorTheme(SecureStore.COLOR_BLUE, R.string.color_theme_blue, BluePreset),
    WidgetColorTheme(SecureStore.COLOR_GREEN, R.string.color_theme_green, GreenPreset),
    WidgetColorTheme(SecureStore.COLOR_PURPLE, R.string.color_theme_purple, PurplePreset),
    WidgetColorTheme(SecureStore.COLOR_ORANGE, R.string.color_theme_orange, OrangePreset),
    WidgetColorTheme(SecureStore.COLOR_RED, R.string.color_theme_red, RedPreset),
    WidgetColorTheme(SecureStore.COLOR_TEAL, R.string.color_theme_teal, TealPreset),
)

/** [colorTheme] → dets [WidgetColorPreset]. Ukendt/uventet værdi falder sikkert tilbage til Blå
 * (samme defensive mønster som v0.2.13's sprog-dropdown-fix — undgår en kastet exception). */
internal fun presetFor(colorTheme: String): WidgetColorPreset =
    WIDGET_COLOR_THEMES.firstOrNull { it.key == colorTheme }?.preset ?: BluePreset
