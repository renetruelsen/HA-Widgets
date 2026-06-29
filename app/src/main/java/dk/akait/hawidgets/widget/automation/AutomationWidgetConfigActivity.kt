package dk.akait.hawidgets.widget.automation

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class AutomationWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "automation"
    override val pickerTitle = "Vælg automatisering"
    override val domainIconResId = R.drawable.ic_automation
    override fun formatEntityState(state: String) = when (state) {
        "on" -> "Aktiv"
        "off" -> "Deaktiveret"
        else -> state
    }
}
