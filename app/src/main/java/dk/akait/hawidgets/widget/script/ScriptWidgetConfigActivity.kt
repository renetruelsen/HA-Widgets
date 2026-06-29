package dk.akait.hawidgets.widget.script

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class ScriptWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "script"
    override val pickerTitle = "Vælg script"
    override val domainIconResId = R.drawable.ic_script
    override fun formatEntityState(state: String) = when (state) {
        "on" -> "Kører"
        "off" -> "Klar"
        else -> state
    }
}
