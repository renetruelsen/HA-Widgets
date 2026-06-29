package dk.akait.hawidgets.widget.weather

import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.BaseEntityPickerActivity

class WeatherWidgetConfigActivity : BaseEntityPickerActivity() {
    override val domain = "weather"
    override val pickerTitle = "Vælg vejrstation"
    override val domainIconResId = R.drawable.ic_weather
}
