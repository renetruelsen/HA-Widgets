package dk.akait.hawidgets.widget.binarysensor

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class BinarySensorWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "binary_sensor"
    override val pickerTitle = "Vælg binær sensor"
    override val domainIconResId = R.drawable.ic_binary_sensor
    override fun formatEntityState(state: String) = when (state) {
        "on" -> "Aktiv"
        "off" -> "Inaktiv"
        else -> state
    }
}
