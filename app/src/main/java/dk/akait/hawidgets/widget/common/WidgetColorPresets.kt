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
