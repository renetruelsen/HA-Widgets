package dk.akait.hawidgets.widget.climate

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class ClimateWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "climate"
    override fun pickerTitle() = getString(R.string.picker_title_climate)
    override val domainIconResId = R.drawable.ic_climate
    override fun formatEntityState(state: String) = when (state) {
        "heat" -> getString(R.string.climate_heat)
        "cool" -> getString(R.string.climate_cool)
        "auto", "heat_cool" -> getString(R.string.climate_auto)
        "dry" -> getString(R.string.climate_dry)
        "fan_only" -> getString(R.string.climate_fan_only)
        "off" -> getString(R.string.state_off)
        else -> state
    }
}
