package dk.akait.hawidgets.widget.sensor

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class SensorWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "sensor"
    override val pickerTitle = "Vælg sensor"
    override val domainIconResId = R.drawable.ic_sensor
}
