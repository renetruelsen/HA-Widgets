package dk.akait.hawidgets.widget.switchwidget

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class SwitchWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "switch"
    override val pickerTitle = "Vælg kontakt"
    override val domainIconResId = R.drawable.ic_switch
    override fun formatEntityState(state: String) = when (state) {
        "on" -> "Tændt"
        "off" -> "Slukket"
        else -> state
    }
}
