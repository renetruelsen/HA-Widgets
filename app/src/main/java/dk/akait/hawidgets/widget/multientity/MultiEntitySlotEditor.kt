package dk.akait.hawidgets.widget.multientity

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.R
import dk.akait.hawidgets.widget.common.domainIconResId

@Composable
internal fun actionLabel(action: String): String = when (action) {
    "TOGGLE" -> stringResource(R.string.action_toggle)
    "RANGE" -> stringResource(R.string.action_range)
    "TRIGGER" -> stringResource(R.string.action_trigger_long)
    "TEXT" -> stringResource(R.string.action_text)
    "DATETIME" -> stringResource(R.string.action_datetime)
    else -> stringResource(R.string.action_view_only)
}

/** Kort variant til radios + auto-linjen i SlotEditorScreen (undgår den lange TRIGGER-tekst). */
@Composable
internal fun actionShortLabel(action: String): String = when (action) {
    "TOGGLE" -> stringResource(R.string.action_toggle)
    "RANGE" -> stringResource(R.string.action_range)
    "TRIGGER" -> stringResource(R.string.action_trigger_short)
    "TEXT" -> stringResource(R.string.action_text)
    "DATETIME" -> stringResource(R.string.action_datetime)
    else -> stringResource(R.string.action_view_only_short)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlotEditorScreen(
    draft: SlotDraft,
    isEditing: Boolean,
    attrsByEntityId: Map<String, String>,
    onChangeDisplay: () -> Unit,
    onChangeTarget: () -> Unit,
    onActionChange: (String) -> Unit,
    onRangeInputModeChange: (String?) -> Unit,
    onLabelChange: (String) -> Unit,
    onConfirmActionChange: (Boolean) -> Unit,
    onDisplayPrecisionChange: (Int?) -> Unit,
    onDatetimeFormatChange: (String?) -> Unit,
    onAddSecondary: () -> Unit,
    onRemoveSecondary: (Int) -> Unit,
    onSecondaryChangeTarget: (Int) -> Unit,
    onSecondaryActionChange: (Int, String) -> Unit,
    onSecondaryRangeInputModeChange: (Int, String?) -> Unit,
    onSecondaryLabelChange: (Int, String) -> Unit,
    onSecondaryShowValueChange: (Int, Boolean) -> Unit,
    onSecondaryConfirmActionChange: (Int, Boolean) -> Unit,
    onSecondaryDisplayPrecisionChange: (Int, Int?) -> Unit,
    onSecondaryDatetimeFormatChange: (Int, String?) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val display = draft.displayEntity ?: return
    val action = draft.actionEntity ?: display

    val targetDiffers = action.entityId != display.entityId
    val opts = actionOptionsFor(action.domain)
    val readOnly = opts.isEmpty()
    // Ugyldigt: bruger valgte et andet mål der ikke kan handles på (fx en sensor) → bloker gem.
    val invalidTarget = targetDiffers && readOnly
    val reactsToTap = draft.action != "NONE"
    // Husk seneste rigtige handlings-valg, så kontakt FRA→TIL genopretter brugerens valg (fx
    // RANGE) i stedet for altid at nulstille til opts.first() (code-review-fund). Nulstilles
    // når mål/visning skifter (remember-keys), hvor opts alligevel genberegnes.
    var rememberedAction by remember(display.entityId, action.entityId) {
        mutableStateOf(draft.action.takeIf { it != "NONE" })
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.slot_editor_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = draft.label,
                onValueChange = { if (it.length <= 12) onLabelChange(it) },
                label = { Text(stringResource(R.string.short_label_field)) },
                placeholder = { Text(stringResource(R.string.short_label_placeholder)) },
                supportingText = { Text(stringResource(R.string.short_label_supporting)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.padding(8.dp))

            // ── VISNING ──
            SectionCard(title = stringResource(R.string.section_view)) {
                Text(display.friendlyName, style = MaterialTheme.typography.titleMedium)
                Text(display.entityId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onChangeDisplay, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.change_entity)) }
                ValueFormattingControls(
                    domain = display.domain,
                    attributesJson = attrsByEntityId[display.entityId],
                    currentState = display.state,
                    displayPrecision = draft.displayPrecision,
                    datetimeFormat = draft.datetimeFormat,
                    onDisplayPrecisionChange = onDisplayPrecisionChange,
                    onDatetimeFormatChange = onDatetimeFormatChange,
                )
            }
            Spacer(Modifier.padding(8.dp))

            // ── HANDLING ──
            SectionCard(title = stringResource(R.string.section_action)) {
                when {
                    invalidTarget -> {
                        Text(
                            stringResource(R.string.action_invalid_target),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    readOnly -> {
                        Text(
                            stringResource(R.string.action_read_only),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        // "Reagér på tryk"-kontakt: kun når mål == visning. Ved andet mål er en
                        // handling altid underforstået, så kontakten (og dermed NONE) findes ikke.
                        if (!targetDiffers) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.reacts_on_tap), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = reactsToTap,
                                    onCheckedChange = { on ->
                                        onActionChange(
                                            if (on) rememberedAction?.takeIf { it in opts } ?: opts.first()
                                            else "NONE"
                                        )
                                    },
                                )
                            }
                        }
                        val showActionChoice = targetDiffers || reactsToTap
                        if (showActionChoice) {
                            if (opts.size == 1) {
                                Text(
                                    stringResource(R.string.on_tap_label, actionShortLabel(opts[0])),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            } else {
                                opts.forEach { actionType ->
                                    val pick = {
                                        rememberedAction = actionType
                                        onActionChange(actionType)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = pick)
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(selected = draft.action == actionType, onClick = pick)
                                        Spacer(Modifier.width(8.dp))
                                        Text(actionShortLabel(actionType))
                                    }
                                }
                            }
                        } else {
                            // Kontakt FRA (mål == visning): slotten viser kun status.
                            Text(
                                stringResource(R.string.slot_view_only_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
                if (draft.action == "TOGGLE" || draft.action == "TRIGGER") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.confirm_action_switch), modifier = Modifier.weight(1f))
                        Switch(checked = draft.confirmAction, onCheckedChange = onConfirmActionChange)
                    }
                }
                if (draft.action == "RANGE") {
                    RangeInputModeControl(
                        selected = draft.rangeInputMode,
                        onSelected = onRangeInputModeChange,
                    )
                }
                if (targetDiffers) {
                    Text(
                        stringResource(R.string.target_label, action.friendlyName),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                TextButton(onClick = onChangeTarget, contentPadding = PaddingValues(0.dp)) {
                    Text(if (targetDiffers) stringResource(R.string.choose_other_target) else stringResource(R.string.act_on_other_entity))
                }
            }
            Spacer(Modifier.padding(8.dp))

            // ── EKSTRA INFO ──
            SectionCard(title = stringResource(R.string.extra_info_section, draft.secondaryEntities.size, MAX_SECONDARY_ENTITIES)) {
                draft.secondaryEntities.forEachIndexed { index, secondary ->
                    SecondaryEntityRow(
                        secondary = secondary,
                        attrsByEntityId = attrsByEntityId,
                        onRemove = { onRemoveSecondary(index) },
                        onChangeTarget = { onSecondaryChangeTarget(index) },
                        onActionChange = { newAction -> onSecondaryActionChange(index, newAction) },
                        onRangeInputModeChange = { mode -> onSecondaryRangeInputModeChange(index, mode) },
                        onLabelChange = { newLabel -> onSecondaryLabelChange(index, newLabel) },
                        onShowValueChange = { showValue -> onSecondaryShowValueChange(index, showValue) },
                        onConfirmActionChange = { confirm -> onSecondaryConfirmActionChange(index, confirm) },
                        onDisplayPrecisionChange = { precision -> onSecondaryDisplayPrecisionChange(index, precision) },
                        onDatetimeFormatChange = { pattern -> onSecondaryDatetimeFormatChange(index, pattern) },
                    )
                    if (index < draft.secondaryEntities.size - 1) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                if (draft.secondaryEntities.size in 1 until MAX_SECONDARY_ENTITIES) {
                    Text(
                        stringResource(R.string.extra_info_room_for_more, MAX_SECONDARY_ENTITIES - draft.secondaryEntities.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                } else if (draft.secondaryEntities.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 4.dp))
                }
                TextButton(
                    onClick = onAddSecondary,
                    enabled = draft.secondaryEntities.size < MAX_SECONDARY_ENTITIES,
                    contentPadding = PaddingValues(0.dp),
                ) { Text(stringResource(R.string.add_extra_entity)) }
            }
            Spacer(Modifier.padding(12.dp))

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.padding(2.dp))
            Button(onClick = onSave, enabled = !invalidTarget, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (isEditing) R.string.update_slot else R.string.add_to_widget))
            }
        }
    }
}

/** Mini-udgave af hoved-entitetens VISNING/HANDLING-mønster, for én sekundær info/handlings-chip.
 * Målet for handlingen kan afvige fra visnings-entiteten (samme uafhængighed som hoved-slottet) —
 * "Skift" åbner picker'en filtreret til domæner der understøtter mindst én handling, så
 * visning≠handling+read-only (invalidTarget) aldrig kan opstå her (i modsætning til hoved-
 * entitetens ældre "Handl på en anden enhed"-flow, som IKKE filtrerer og derfor stadig kan). */
@Composable
private fun SecondaryEntityRow(
    secondary: SecondarySlotDraft,
    attrsByEntityId: Map<String, String>,
    onRemove: () -> Unit,
    onChangeTarget: () -> Unit,
    onActionChange: (String) -> Unit,
    onRangeInputModeChange: (String?) -> Unit,
    onLabelChange: (String) -> Unit,
    onShowValueChange: (Boolean) -> Unit,
    onConfirmActionChange: (Boolean) -> Unit,
    onDisplayPrecisionChange: (Int?) -> Unit,
    onDatetimeFormatChange: (String?) -> Unit,
) {
    val display = secondary.displayEntity
    val action = secondary.actionEntity
    val targetDiffers = action.entityId != display.entityId
    val opts = actionOptionsFor(action.domain)
    val readOnly = opts.isEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(domainIconResId(display.domain)),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.section_view), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(display.friendlyName, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
        OutlinedTextField(
            value = secondary.label,
            onValueChange = { if (it.length <= 12) onLabelChange(it) },
            label = { Text(stringResource(R.string.chip_label_field)) },
            supportingText = { Text(stringResource(R.string.chip_label_supporting)) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        ValueFormattingControls(
            domain = display.domain,
            attributesJson = attrsByEntityId[display.entityId],
            currentState = display.state,
            displayPrecision = secondary.displayPrecision,
            datetimeFormat = secondary.datetimeFormat,
            onDisplayPrecisionChange = onDisplayPrecisionChange,
            onDatetimeFormatChange = onDatetimeFormatChange,
        )
        Spacer(Modifier.padding(top = 6.dp))
        Text(stringResource(R.string.section_action), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                if (readOnly) {
                    Text(
                        stringResource(R.string.action_read_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (targetDiffers) {
                        Text(stringResource(R.string.target_label, action.friendlyName), style = MaterialTheme.typography.bodySmall)
                    }
                    if (opts.size == 1) {
                        Text(stringResource(R.string.on_tap_label, actionShortLabel(opts[0])), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            TextButton(onClick = onChangeTarget, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.change)) }
        }
        if (!readOnly && opts.size > 1) {
            opts.forEach { actionType ->
                val pick = { onActionChange(actionType) }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = pick).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = secondary.action == actionType, onClick = pick)
                    Spacer(Modifier.width(6.dp))
                    Text(actionShortLabel(actionType), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (!readOnly && secondary.action == "RANGE") {
            RangeInputModeControl(
                selected = secondary.rangeInputMode,
                onSelected = onRangeInputModeChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.show_value_on_chip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = secondary.showValue, onCheckedChange = onShowValueChange)
        }
        if (!readOnly && (secondary.action == "TOGGLE" || secondary.action == "TRIGGER")) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.confirm_action_switch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = secondary.confirmAction, onCheckedChange = onConfirmActionChange)
            }
        }
    }
}

/** Lys indrammet sektion med overskrift — bruges til «Visning» og «Handling». */
@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        content()
    }
}
