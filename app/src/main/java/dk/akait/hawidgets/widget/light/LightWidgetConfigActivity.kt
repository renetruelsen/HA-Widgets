package dk.akait.hawidgets.widget.light

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class LightWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "light"
    override fun pickerTitle() = getString(R.string.picker_title_light)
    override val domainIconResId = R.drawable.ic_lightbulb
    override fun formatEntityState(state: String) = when (state) {
        "on" -> getString(R.string.state_on)
        "off" -> getString(R.string.state_off)
        else -> state
    }
}
