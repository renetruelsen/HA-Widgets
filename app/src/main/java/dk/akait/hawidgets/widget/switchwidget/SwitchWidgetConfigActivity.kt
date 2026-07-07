package dk.akait.hawidgets.widget.switchwidget

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class SwitchWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "switch"
    override fun pickerTitle() = getString(R.string.picker_title_switch)
    override val domainIconResId = R.drawable.ic_switch
    override fun formatEntityState(state: String) = when (state) {
        "on" -> getString(R.string.state_on)
        "off" -> getString(R.string.state_off)
        else -> state
    }
}
