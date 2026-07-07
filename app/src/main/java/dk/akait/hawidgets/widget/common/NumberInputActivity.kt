package dk.akait.hawidgets.widget.common

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.ui.theme.HaWidgetsTheme
import dk.akait.hawidgets.R
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * "Indtast værdi"-alternativet til [RangeControlActivity]s skyder for RANGE-handlinger (Task 13,
 * del A — kun brugt af MultiEntityWidget når slot/chip har rangeInputMode == "FIELD").
 *
 * Følger samme dialog-Activity-mønster som [TextControlActivity]/[RangeControlActivity] (translucent
 * tema, Material3 Surface-dialog). Numerisk tekstfelt valideret mod [EXTRA_MIN_VALUE]/[EXTRA_MAX_VALUE],
 * Gem/Annullér. Afsender NØJAGTIG samme service-kald som skyderen via den delte [sendRangeValue] —
 * ingen tredje kopi af domain→service-mappingen.
 */
class NumberInputActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ENTITY_ID = "entity_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_CURRENT_VALUE = "current_value" // Double
        const val EXTRA_MIN_VALUE = "min_value" // Double
        const val EXTRA_MAX_VALUE = "max_value" // Double
        const val EXTRA_UNIT_SUFFIX = "unit_suffix"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: return finish()
        val label = intent.getStringExtra(EXTRA_LABEL) ?: entityId
        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: return finish()
        val current = intent.getDoubleExtra(EXTRA_CURRENT_VALUE, 0.0)
        val rawMin = intent.getDoubleExtra(EXTRA_MIN_VALUE, 0.0)
        val rawMax = intent.getDoubleExtra(EXTRA_MAX_VALUE, 100.0)
        // Guard mod omvendt/ugyldigt range (samme sikring som RangeControlActivity).
        val min = if (rawMin < rawMax) rawMin else 0.0
        val max = if (rawMin < rawMax) rawMax else 100.0
        val unitSuffix = intent.getStringExtra(EXTRA_UNIT_SUFFIX).orEmpty()

        setContent {
            HaWidgetsTheme {
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                    val scope = rememberCoroutineScope()
                    var text by remember { mutableStateOf(trimNumber(current)) }
                    var busy by remember { mutableStateOf(false) }

                    val parsed = text.trim().replace(',', '.').toDoubleOrNull()
                    val inRange = parsed != null && parsed in min..max
                    val outOfRangeMsg = stringResource(R.string.number_input_out_of_range, trimNumber(min), trimNumber(max))

                    fun save() {
                        val value = parsed ?: return
                        scope.launch {
                            busy = true
                            val ok = sendRangeValue(applicationContext, domain, entityId, value)
                            busy = false
                            if (ok) finish() else showActionError(applicationContext)
                        }
                    }

                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            isError = text.isNotBlank() && !inRange,
                            suffix = if (unitSuffix.isNotEmpty()) { { Text(unitSuffix) } } else null,
                            supportingText = { if (text.isNotBlank() && !inRange) Text(outOfRangeMsg) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(onClick = { finish() }, enabled = !busy) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(Modifier.padding(4.dp))
                            Button(onClick = { save() }, enabled = !busy && inRange) {
                                Text(stringResource(R.string.save))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Vis en Double uden overflødige decimaler (23.0 → "23", 21.5 → "21.5"); Locale.ROOT → altid '.'. */
private fun trimNumber(value: Double): String =
    String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.').ifEmpty { "0" }
