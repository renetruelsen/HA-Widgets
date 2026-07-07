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
