package dk.akait.hawidgets.widget.sensor

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class SensorWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "sensor"
    override fun pickerTitle() = getString(R.string.picker_title_sensor)
    override val domainIconResId = R.drawable.ic_sensor
}
