package dk.akait.hawidgets.widget.light

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class LightWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "light"
    override val pickerTitle = "Vælg lyskilde"
    override val domainIconResId = R.drawable.ic_lightbulb
    override fun formatEntityState(state: String) = when (state) {
        "on" -> "Tændt"
        "off" -> "Slukket"
        else -> state
    }
}
