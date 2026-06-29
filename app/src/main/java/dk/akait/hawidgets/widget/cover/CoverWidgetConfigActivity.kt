package dk.akait.hawidgets.widget.cover

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class CoverWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "cover"
    override val pickerTitle = "Vælg cover / persienne"
    override val domainIconResId = R.drawable.ic_cover
    override fun formatEntityState(state: String) = when (state) {
        "open" -> "Åben"
        "closed" -> "Lukket"
        "opening" -> "Åbner…"
        "closing" -> "Lukker…"
        else -> state
    }
}
