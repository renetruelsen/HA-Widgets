package dk.akait.hawidgets.widget.common

import android.content.Context
import android.widget.Toast
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore

/**
 * Delt fejl-feedback for kontrol-dialogerne (RangeControl/TextControl/NumberInput/
 * DateTimeControl/ConfirmAction) — vises når det underliggende HA callService-kald
 * fejler. Ingen tilsvarende succes-toast: succes signaleres af dialogens egen lukning/
 * opdaterede state.
 */
fun showActionError(context: Context) {
    Toast.makeText(context, R.string.action_failed, Toast.LENGTH_SHORT).show()
}

/** Delt HA-klient-opslag for kontrol-dialogerne — bygger en [HaApiClient] fra [SecureStore],
 * eller null hvis appen (usandsynligt herfra, da dialogerne kun åbnes når appen er forbundet)
 * ikke har en gemt URL/token. Erstatter det tidligere kopierede SecureStore+HaApiClient-
 * konstruktionsmønster i RangeControlActivity/TextControlActivity/DateTimeControlActivity/
 * RangeService (v0.2.45-oprydning, jf. v0.2.34-fund #7). */
fun resolveHaApiClient(context: Context): HaApiClient? {
    val store = SecureStore.get(context.applicationContext)
    val base = store.baseUrl ?: return null
    val token = store.token ?: return null
    return HaApiClient(base, token)
}
