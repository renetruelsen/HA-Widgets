package dk.akait.hawidgets.widget.binarysensor

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class BinarySensorWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "binary_sensor"
    override fun pickerTitle() = getString(R.string.picker_title_binary_sensor)
    override val domainIconResId = R.drawable.ic_binary_sensor
    override fun formatEntityState(state: String) = when (state) {
        "on" -> getString(R.string.state_active)
        "off" -> getString(R.string.state_inactive)
        else -> state
    }
}
