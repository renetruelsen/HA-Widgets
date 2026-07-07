package dk.akait.hawidgets.widget.multientity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.formatDateTimeState
import dk.akait.hawidgets.widget.common.isDateTimeLike
import dk.akait.hawidgets.widget.common.isRawValueDomain
import org.json.JSONObject
import java.util.Locale

/** Precision-dropdown (Auto/0/1/2) for rå numeriske domæner + frit datoformat-felt (m. live
 * preview) for datetime-agtige domæner (v0.3.0, C2) — vises for hoved-entitetens VISNING-sektion
 * OG hver sekundær-chips visnings-entitet. Ingen kontrol vises for domæner med en fast tekst-tabel
 * i formatEntityState (fx light/switch) — de har intet tal/dato at formattere. [attributesJson]
 * kommer fra Room-cachen (samme kilde widget-renderingen selv bruger); kan være null hvis
 * entiteten endnu ikke er synket — kontrollerne skjules da for datetime-agtige domæner (kan ikke
 * afgøre has_date/has_time), men precision-dropdown for almindelige sensor/number-domæner vises
 * stadig (afhænger ikke af attrs). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ValueFormattingControls(
    domain: String,
    attributesJson: String?,
    currentState: String,
    displayPrecision: Int?,
    datetimeFormat: String?,
    onDisplayPrecisionChange: (Int?) -> Unit,
    onDatetimeFormatChange: (String?) -> Unit,
) {
    if (!isRawValueDomain(domain)) return
    val dateTimeLike = isDateTimeLike(domain, attributesJson)

    if (dateTimeLike) {
        var pattern by remember(datetimeFormat) { mutableStateOf(datetimeFormat ?: "") }
        val locale = Locale.getDefault()
        val attrs = attributesJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val hasDate = attrs?.optBoolean("has_date", true) ?: true
        val hasTime = attrs?.optBoolean("has_time", true) ?: true
        Spacer(Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = pattern,
            onValueChange = { newValue ->
                pattern = newValue
                onDatetimeFormatChange(newValue.ifBlank { null })
            },
            label = { Text(stringResource(R.string.datetime_format_label)) },
            supportingText = { Text(stringResource(R.string.datetime_format_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        // Live preview — bruger den faktiske aktuelle state, så brugeren straks ser resultatet af
        // et frit mønster (og at et ugyldigt mønster falder trygt tilbage til auto, jf. Task 2).
        val preview = formatDateTimeState(currentState, pattern.ifBlank { null }, hasDate, hasTime, locale)
        Text(
            preview,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    } else {
        var expanded by remember { mutableStateOf(false) }
        val options: List<Int?> = listOf(null, 0, 1, 2)
        val autoLabel = stringResource(R.string.precision_auto)
        fun optionLabel(value: Int?) = value?.toString() ?: autoLabel

        Spacer(Modifier.padding(top = 8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = optionLabel(displayPrecision),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.display_precision_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(value)) },
                        onClick = {
                            onDisplayPrecisionChange(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** RANGE input-tilstand: «Skyder» (skyder-dialog) eller «Indtast værdi» (talfelt-dialog) — Task 13,
 * del A. Vises kun når handlingen er RANGE, både for hoved-slotten og hver sekundær-chip. Sentinel:
 * null = "SLIDER" (uændret default); "FIELD" = talfelt. «Skyder» gemmer null, så en slot der aldrig
 * har rørt kontrollen forbliver present som "ingen override" i DB'en. */
@Composable
internal fun RangeInputModeControl(selected: String?, onSelected: (String?) -> Unit) {
    // null/"SLIDER" behandles ens (skyder). Kun "FIELD" er felt-tilstand.
    val isField = selected == "FIELD"
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            stringResource(R.string.range_input_mode_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        val options = listOf(false to R.string.range_input_mode_slider, true to R.string.range_input_mode_field)
        options.forEach { (fieldValue, labelRes) ->
            val pick = { onSelected(if (fieldValue) "FIELD" else null) }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = pick).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isField == fieldValue, onClick = pick)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(labelRes), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
