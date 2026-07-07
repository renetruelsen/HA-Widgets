package dk.akait.hawidgets.widget.script

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class ScriptWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "script"
    override fun pickerTitle() = getString(R.string.picker_title_script)
    override val domainIconResId = R.drawable.ic_script
    override fun formatEntityState(state: String) = when (state) {
        "on" -> getString(R.string.state_running)
        "off" -> getString(R.string.state_ready)
        else -> state
    }
}
