package dk.akait.hawidgets.widget.common

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: return finish()
        val label = intent.getStringExtra(EXTRA_LABEL) ?: entityId
        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: return finish()
        val initialValue = intent.getIntExtra(EXTRA_CURRENT_VALUE, 100)
        val isOnInitial = intent.getBooleanExtra(EXTRA_IS_ON, true)

        setContent {
            MaterialTheme {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 6.dp,
                ) {
                    val scope = rememberCoroutineScope()
                    var sliderValue by remember { mutableFloatStateOf(initialValue.toFloat()) }
                    var isOn by remember { mutableStateOf(isOnInitial) }
                    var busy by remember { mutableStateOf(false) }

                    fun sendRangeCommand(value: Int) {
                        scope.launch {
                            val store = SecureStore.get(applicationContext)
                            val base = store.baseUrl ?: return@launch
                            val token = store.token ?: return@launch
                            val api = HaApiClient(base, token)
                            when (domain) {
                                "light" -> api.callService(
                                    "light", "turn_on", entityId,
                                    extraData = mapOf("brightness" to (value * 255 / 100).coerceIn(1, 255))
                                )
                                "cover" -> api.callService(
                                    "cover", "set_cover_position", entityId,
                                    extraData = mapOf("position" to value)
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
                            if (domain == "light") {
                                if (isOn) {
                                    api.callService("light", "turn_off", entityId)
                                } else {
                                    api.callService("light", "turn_on", entityId)
                                }
                            } else if (domain == "cover") {
                                if (isOn) {
                                    api.callService("cover", "close_cover", entityId)
                                } else {
                                    api.callService("cover", "open_cover", entityId)
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

                        val toggleLabel = when {
                            domain == "cover" && isOn -> "Luk"
                            domain == "cover" -> "Åbn"
                            isOn -> "Sluk"
                            else -> "Tænd"
                        }
                        val valueLabel = when (domain) {
                            "cover" -> "Position"
                            else -> "Lysstyrke"
                        }
                        val unitSuffix = "%"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$valueLabel: ${sliderValue.toInt()}$unitSuffix",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            OutlinedButton(onClick = { sendToggle() }, enabled = !busy) {
                                Text(toggleLabel)
                            }
                        }

                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = { sendRangeCommand(sliderValue.toInt()) },
                            valueRange = 1f..100f,
                            enabled = isOn,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Luk") }
                    }
                }
            }
        }
    }
}
