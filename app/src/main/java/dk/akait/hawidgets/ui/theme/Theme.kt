package dk.akait.hawidgets.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dk.akait.hawidgets.data.SecureStore

// Static scheme (not dynamic/Material You) so branding stays consistent across
// devices and OS versions — minSdk 26 doesn't support dynamic color below API 31
// anyway.
val HaWidgetsColorScheme = lightColorScheme(
    primary = HaBluePrimary,
    onPrimary = HaBlueOnPrimary,
    primaryContainer = HaBluePrimaryContainer,
    onPrimaryContainer = HaBlueOnPrimaryContainer,
    secondaryContainer = HaBlueSurfaceVariant,
    onSecondaryContainer = HaBlueOnSurfaceVariant,
    surfaceVariant = HaBlueSurfaceVariant,
    onSurfaceVariant = HaBlueOnSurfaceVariant,
)

// Dark counterpart, mirroring the same brand-blue family with a lightened primary for
// contrast against dark surfaces (see Color.kt for rationale on the non-primary values).
val HaWidgetsDarkColorScheme = darkColorScheme(
    primary = HaBlueDarkPrimary,
    onPrimary = HaBlueDarkOnPrimary,
    primaryContainer = HaBlueDarkPrimaryContainer,
    onPrimaryContainer = HaBlueDarkOnPrimaryContainer,
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

/**
 * App-theme wrapper for every Compose Activity. The colour scheme is chosen from the
 * user's global theme-mode preference ([SecureStore.themeMode]):
 *  - "light"  → always the light brand scheme
 *  - "dark"   → always the dark brand scheme
 *  - "system" → follow the OS night setting ([isSystemInDarkTheme]) — pixel-identical to
 *               the historical default behaviour.
 *
 * The preference is read at composition time, so an Activity that calls [android.app.Activity.recreate]
 * after the user changes the mode (see MainActivity) will pick up the new scheme. The
 * `HaWidgetsTheme { content }` call signature is intentionally unchanged so all existing
 * call sites keep working.
 */
@Composable
fun HaWidgetsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode = SecureStore.get(context).themeMode
    val dark = when (mode) {
        SecureStore.THEME_DARK -> true
        SecureStore.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) HaWidgetsDarkColorScheme else HaWidgetsColorScheme,
        content = content,
    )
}
