package dk.akait.hawidgets.widget.climate

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class ClimateWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "climate"
    override val pickerTitle = "Vælg klimastyring"
    override val domainIconResId = R.drawable.ic_climate
    override fun formatEntityState(state: String) = when (state) {
        "heat" -> "Opvarmning"
        "cool" -> "Køling"
        "auto", "heat_cool" -> "Auto"
        "dry" -> "Affugtning"
        "fan_only" -> "Ventilator"
        "off" -> "Slukket"
        else -> state
    }
}
