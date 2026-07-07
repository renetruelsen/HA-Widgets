package dk.akait.hawidgets.widget.cover

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class CoverWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "cover"
    override fun pickerTitle() = getString(R.string.picker_title_cover)
    override val domainIconResId = R.drawable.ic_cover
    override fun formatEntityState(state: String) = when (state) {
        "open" -> getString(R.string.state_open)
        "closed" -> getString(R.string.state_closed)
        "opening" -> getString(R.string.state_opening)
        "closing" -> getString(R.string.state_closing)
        else -> state
    }
}
