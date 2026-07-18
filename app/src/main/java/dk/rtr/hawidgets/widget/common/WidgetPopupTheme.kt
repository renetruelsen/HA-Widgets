package dk.rtr.hawidgets.widget.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dk.rtr.hawidgets.data.SecureStore

/**
 * Theme-wrapper for widget-tryk-popups (Range/Text/DateTime/NumberInput/ConfirmAction). I modsætning
 * til [dk.rtr.hawidgets.ui.theme.HaWidgetsTheme] (app-UI, altid fast blå) følger denne
 * [SecureStore.widgetColorTheme] ligesom selve widgetten (WidgetGlanceTheme) — disse popups er reelt
 * widget-interaktion, ikke app-UI, så de skal matche den farve brugeren ser på hjemskærmen.
 */
@Composable
fun WidgetPopupTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = SecureStore.get(context)
    val dark = when (store.themeMode) {
        SecureStore.THEME_DARK -> true
        SecureStore.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val preset = presetFor(store.widgetColorTheme)
    MaterialTheme(
        colorScheme = if (dark) preset.dark else preset.light,
        content = content,
    )
}
