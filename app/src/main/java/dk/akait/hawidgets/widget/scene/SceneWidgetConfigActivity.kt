package dk.akait.hawidgets.widget.scene

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class SceneWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "scene"
    override val pickerTitle = "Vælg scene"
    override val domainIconResId = R.drawable.ic_scene
    override fun formatEntityState(state: String) = "Aktiver"
}
