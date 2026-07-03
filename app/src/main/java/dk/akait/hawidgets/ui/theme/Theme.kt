package dk.akait.hawidgets.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun HaWidgetsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HaWidgetsColorScheme, content = content)
}
