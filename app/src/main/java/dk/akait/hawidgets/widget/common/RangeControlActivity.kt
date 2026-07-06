package dk.akait.hawidgets.widget.common

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.ui.theme.HaWidgetsTheme
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.EntityRepository
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore
import kotlinx.coroutines.launch

class RangeControlActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ENTITY_ID = "entity_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_CURRENT_VALUE = "current_value"
        const val EXTRA_IS_ON = "is_on"
        const val EXTRA_MIN_VALUE = "min_value"
        const val EXTRA_MAX_VALUE = "max_value"
        /** Actual measured temperature for climate domain. Int.MIN_VALUE = not provided. */
        const val EXTRA_ACTUAL_TEMP = "actual_temp"
        /** Valgfri unit-override til value-label (fx "W", "kWh") — bruges af number-domain. Tom/null = domain-default (%). */
        const val EXTRA_UNIT_SUFFIX = "unit_suffix"
        /** Præcise (decimal) current/min/max-værdier — bruges af number/input_number, hvis
         * entiteten har en fraktioneret state/step (fx 21.5). Når til stede, foretrækkes disse
         * frem for de heltals-baserede EXTRA_CURRENT_VALUE/MIN/MAX-værdier ovenfor. */
        const val EXTRA_CURRENT_VALUE_PRECISE = "current_value_precise"
        const val EXTRA_MIN_VALUE_PRECISE = "min_value_precise"
        const val EXTRA_MAX_VALUE_PRECISE = "max_value_precise"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: return finish()
        val label = intent.getStringExtra(EXTRA_LABEL) ?: entityId
        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: return finish()
        val isOnInitial = intent.getBooleanExtra(EXTRA_IS_ON, true)
        val actualTemp = intent.getIntExtra(EXTRA_ACTUAL_TEMP, Int.MIN_VALUE)
        val unitSuffixOverride = intent.getStringExtra(EXTRA_UNIT_SUFFIX)

        val rawInitialValue = if (intent.hasExtra(EXTRA_CURRENT_VALUE_PRECISE)) {
            intent.getDoubleExtra(EXTRA_CURRENT_VALUE_PRECISE, 100.0)
        } else {
            intent.getIntExtra(EXTRA_CURRENT_VALUE, 100).toDouble()
        }
        val rawMinValue = if (intent.hasExtra(EXTRA_MIN_VALUE_PRECISE)) {
            intent.getDoubleExtra(EXTRA_MIN_VALUE_PRECISE, 1.0)
        } else {
            intent.getIntExtra(EXTRA_MIN_VALUE, 1).toDouble()
        }
        val rawMaxValue = if (intent.hasExtra(EXTRA_MAX_VALUE_PRECISE)) {
            intent.getDoubleExtra(EXTRA_MAX_VALUE_PRECISE, 100.0)
        } else {
            intent.getIntExtra(EXTRA_MAX_VALUE, 100).toDouble()
        }
        // Guard mod ugyldigt/omvendt range (min >= max) fra en entitets attributter — Slider
        // kaster IllegalArgumentException på et sådant valueRange, og coerceIn nedenfor ville
        // ligeledes crashe. Falder tilbage til et sikkert 0..100-standardinterval.
        val minValue = if (rawMinValue < rawMaxValue) rawMinValue else 0.0
        val maxValue = if (rawMinValue < rawMaxValue) rawMaxValue else 100.0
        val initialValue = rawInitialValue

        setContent {
            HaWidgetsTheme {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 6.dp,
                ) {
                    val scope = rememberCoroutineScope()
                    var sliderValue by remember { mutableFloatStateOf(initialValue.toFloat().coerceIn(minValue.toFloat(), maxValue.toFloat())) }
                    var isOn by remember { mutableStateOf(isOnInitial) }
                    var busy by remember { mutableStateOf(false) }

                    fun sendRangeCommand(value: Double) {
                        scope.launch {
                            val store = SecureStore.get(applicationContext)
                            val base = store.baseUrl ?: return@launch
                            val token = store.token ?: return@launch
                            val api = HaApiClient(base, token)
                            when (domain) {
                                "light" -> api.callService(
                                    "light", "turn_on", entityId,
                                    extraData = mapOf("brightness" to (value.toInt() * 255 / 100).coerceIn(1, 255))
                                )
                                "cover" -> api.callService(
                                    "cover", "set_cover_position", entityId,
                                    extraData = mapOf("position" to value.toInt())
                                )
                                "climate" -> api.callService(
                                    "climate", "set_temperature", entityId,
                                    extraData = mapOf("temperature" to value.toInt())
                                )
                                // number/input_number kan have en fraktioneret step (fx 0.5) — send den
                                // fulde decimalværdi i stedet for at afrunde til et heltal.
                                "number" -> api.callService(
                                    "number", "set_value", entityId,
                                    extraData = mapOf("value" to value)
                                )
                                "input_number" -> api.callService(
                                    "input_number", "set_value", entityId,
                                    extraData = mapOf("value" to value)
                                )
                            }
                            EntityRepository.refresh(applicationContext, entityId)
                        }
                    }

                    fun sendToggle() {
                        scope.launch {
                            busy = true
                            val store = SecureStore.get(applicationContext)
                            val base = store.baseUrl ?: run { busy = false; return@launch }
                            val token = store.token ?: run { busy = false; return@launch }
                            val api = HaApiClient(base, token)
                            when (domain) {
                                "light" -> if (isOn) {
                                    api.callService("light", "turn_off", entityId)
                                } else {
                                    api.callService("light", "turn_on", entityId)
                                }
                                "cover" -> if (isOn) {
                                    api.callService("cover", "close_cover", entityId)
                                } else {
                                    api.callService("cover", "open_cover", entityId)
                                }
                                "climate" -> if (isOn) {
                                    api.callService("climate", "turn_off", entityId)
                                } else {
                                    api.callService("climate", "turn_on", entityId)
                                }
                            }
                            isOn = !isOn
                            EntityRepository.refresh(applicationContext, entityId)
                            busy = false
                        }
                    }

                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        if (domain == "climate" && actualTemp != Int.MIN_VALUE) {
                            Text(
                                "Aktuel temperatur: ${actualTemp}°C",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        val toggleLabel = when {
                            domain == "cover" && isOn -> "Luk helt"
                            domain == "cover" -> "Åbn helt"
                            isOn -> "Sluk"
                            else -> "Tænd"
                        }
                        val valueLabel = when (domain) {
                            "cover" -> "Position"
                            "climate" -> "Temperatur"
                            "number", "input_number" -> "Værdi"
                            else -> "Lysstyrke"
                        }
                        val unitSuffix = unitSuffixOverride ?: when (domain) {
                            "climate" -> "°C"
                            else -> "%"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val displayValue = if (domain == "number" || domain == "input_number") {
                                formatPreciseValue(sliderValue)
                            } else {
                                sliderValue.toInt().toString()
                            }
                            Text(
                                "$valueLabel: $displayValue$unitSuffix",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (domain != "number" && domain != "input_number") {
                                OutlinedButton(onClick = { sendToggle() }, enabled = !busy) {
                                    Text(toggleLabel)
                                }
                            }
                        }

                        // −/+ trin-knapper flankerer slideren (Task 13, variant B). Trin-størrelsen
                        // afledes af range-bredden (stepFor); et tryk snapper til nærmeste trin og
                        // flytter ét trin (stepValue). Både knap og direkte slider-træk går gennem
                        // SAMME præcise Double-værdi til sendRangeCommand — number/input_number
                        // bevarer dermed decimaler (v0.2.34) uændret.
                        val step = stepFor(minValue, maxValue)
                        val controlsEnabled = domain == "number" || domain == "input_number" || isOn
                        fun applyStep(direction: Int) {
                            val next = stepValue(sliderValue.toDouble(), direction, step, minValue, maxValue)
                            sliderValue = next.toFloat()
                            sendRangeCommand(next)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { applyStep(-1) }, enabled = controlsEnabled) {
                                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.range_step_down))
                            }
                            Slider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                onValueChangeFinished = { sendRangeCommand(sliderValue.toDouble()) },
                                valueRange = minValue.toFloat()..maxValue.toFloat(),
                                enabled = controlsEnabled,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { applyStep(+1) }, enabled = controlsEnabled) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.range_step_up))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Viser op til 2 decimaler for number/input_number, uden overflødige nuller (23 i stedet for
 * 23.00, 21.5 i stedet for 21.50) — bevarer den præcision brugeren rent faktisk har konfigureret
 * (fx en step på 0.5), i stedet for at afrunde til nærmeste heltal. */
private fun formatPreciseValue(value: Float): String {
    // Locale.ROOT tvinger '.' som decimal-separator uanset enhedens sprogindstilling (fx dansk
    // bruger ','), så trimEnd('.') nedenfor rammer korrekt i stedet for at efterlade "38,".
    val rounded = String.format(java.util.Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
    return rounded.ifEmpty { "0" }
}
