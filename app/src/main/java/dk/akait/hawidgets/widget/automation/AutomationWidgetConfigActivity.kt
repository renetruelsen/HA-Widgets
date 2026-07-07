package dk.akait.hawidgets.widget.automation

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class AutomationWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "automation"
    override fun pickerTitle() = getString(R.string.picker_title_automation)
    override val domainIconResId = R.drawable.ic_automation
    override fun formatEntityState(state: String) = when (state) {
        "on" -> getString(R.string.state_active)
        "off" -> getString(R.string.state_deactivated)
        else -> state
    }
}
