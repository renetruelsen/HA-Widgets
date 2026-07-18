package dk.rtr.hawidgets.widget.multientity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dk.rtr.hawidgets.R
import dk.rtr.hawidgets.widget.common.RANGE_VALUE_DOMAINS
import dk.rtr.hawidgets.widget.common.domainIconResId
import kotlinx.coroutines.flow.first

@Composable
internal fun actionLabel(action: String): String = when (action) {
    "TOGGLE" -> stringResource(R.string.action_toggle)
    "RANGE" -> stringResource(R.string.action_range)
    "TRIGGER" -> stringResource(R.string.action_trigger_long)
    "TEXT" -> stringResource(R.string.action_text)
    "DATETIME" -> stringResource(R.string.action_datetime)
    "HISTORY" -> stringResource(R.string.action_history)
    "OPEN_APP" -> stringResource(R.string.action_open_app)
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
    "HISTORY" -> stringResource(R.string.action_history_short)
    "OPEN_APP" -> stringResource(R.string.action_open_app)
    else -> stringResource(R.string.action_view_only_short)
}

/** Radio-række for meta-valget "Styr Home Assistant" vs. "Åbn app" i hoved-slottens Handling-sektion. */
@Composable
private fun ActionModeRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** "Vis rå skyder-værdi" (fx "45%"/"21.5°") i stedet for state-tekst — kun for domæner i
 * [RANGE_VALUE_DOMAINS] (light/cover/climate), uafhængig af valgt handling. */
@Composable
private fun RangeValueToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.show_range_value_label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        }
        Text(
            stringResource(R.string.show_range_value_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlotEditorScreen(
    draft: SlotDraft,
    isEditing: Boolean,
    scrollToBottom: Boolean,
    attrsByEntityId: Map<String, String>,
    onChangeDisplay: () -> Unit,
    onRemoveMainEntity: () -> Unit,
    onChangeTarget: () -> Unit,
    onChooseApp: () -> Unit,
    onActionChange: (String) -> Unit,
    onRangeInputModeChange: (String?) -> Unit,
    onLabelChange: (String) -> Unit,
    onConfirmActionChange: (Boolean) -> Unit,
    onDisplayPrecisionChange: (Int?) -> Unit,
    onDatetimeFormatChange: (String?) -> Unit,
    onShowIconChange: (Boolean) -> Unit,
    onShowRangeValueChange: (Boolean) -> Unit,
    onAddSecondary: () -> Unit,
    onRemoveSecondary: (Int) -> Unit,
    onMoveSecondaryUp: (Int) -> Unit,
    onMoveSecondaryDown: (Int) -> Unit,
    onSecondaryChangeTarget: (Int) -> Unit,
    onSecondaryActionChange: (Int, String) -> Unit,
    onSecondaryRangeInputModeChange: (Int, String?) -> Unit,
    onSecondaryLabelChange: (Int, String) -> Unit,
    onSecondaryShowValueChange: (Int, Boolean) -> Unit,
    onSecondaryConfirmActionChange: (Int, Boolean) -> Unit,
    onSecondaryDisplayPrecisionChange: (Int, Int?) -> Unit,
    onSecondaryDatetimeFormatChange: (Int, String?) -> Unit,
    onSecondaryShowIconChange: (Int, Boolean) -> Unit,
    onSecondaryShowRangeValueChange: (Int, Boolean) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val display = draft.displayEntity
    val action = draft.actionEntity ?: display
    val context = LocalContext.current

    // "Åbn app": handlingen peger på en app, ikke en HA-entitet — mål-relateret UI (targetDiffers,
    // invalidTarget, "handl på anden entitet") er derfor undertrykt i denne tilstand.
    val isAppMode = draft.action == "OPEN_APP"
    val targetDiffers = display != null && !isAppMode && action != null && action.entityId != display.entityId
    val opts = action?.let { actionOptionsFor(it.domain) } ?: emptyList()
    val canControlHa = opts.isNotEmpty()
    val readOnly = opts.isEmpty()
    // Ugyldigt: bruger valgte et andet mål der ikke kan handles på (fx en sensor) → bloker gem.
    val invalidTarget = display != null && !isAppMode && targetDiffers && readOnly
    // App-handling valgt men ingen app endnu → bloker gem.
    val invalidAppChoice = isAppMode && draft.packageName == null
    val reactsToTap = draft.action != "NONE"
    // App'ens brugervendte navn (opslag via pakkenavn — synligt takket være <queries> launcher).
    val appLabel = draft.packageName?.let { pkg ->
        remember(pkg) {
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { pkg }
        }
    }
    // Husk seneste rigtige HA-handling (ikke NONE/OPEN_APP), så kontakt/meta-skift genopretter
    // brugerens valg (fx RANGE) i stedet for altid at nulstille til opts.first() (code-review-fund).
    // Nulstilles når mål/visning skifter (remember-keys), hvor opts alligevel genberegnes.
    var rememberedAction by remember(display?.entityId, action?.entityId) {
        mutableStateOf(draft.action.takeIf { it != "NONE" && it != "OPEN_APP" })
    }

    val scrollState = rememberScrollState()
    // Auto-scroll til den nyeste chip (brugerønske). Skærmen er en FRISK komposition hver gang den
    // genindtrædes efter EntityPicker (navigation mellem "when"-grene i Step smider den gamle
    // komposition væk) — den nye chip er derfor allerede med i `draft` ved allerførste komposition,
    // så en lokal størrelses-diff aldrig ser en vækst. `scrollToBottom` er derfor et eksplicit
    // signal fra navigations-callet i stedet.
    LaunchedEffect(Unit) {
        if (scrollToBottom) {
            // Vent til layoutet er målt (maxValue er 0 indtil layout har kørt) — ellers scroller
            // vi til 0 i stedet for den faktiske bund.
            snapshotFlow { scrollState.maxValue }.first { it > 0 }
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.slot_editor_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
        ) {
            if (display != null) {
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = { if (it.length <= 22) onLabelChange(it) },
                    label = { Text(stringResource(R.string.short_label_field)) },
                    placeholder = { Text(stringResource(R.string.short_label_placeholder)) },
                    supportingText = { Text(stringResource(R.string.short_label_supporting)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.padding(8.dp))
            }

            // ── VISNING ──
            SectionCard(title = stringResource(R.string.section_view)) {
                if (display == null) {
                    Text(
                        stringResource(R.string.chips_only_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.padding(top = 4.dp))
                    TextButton(onClick = onChangeDisplay, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.add_main_entity))
                    }
                } else {
                    Text(display.friendlyName, style = MaterialTheme.typography.titleMedium)
                    Text(display.entityId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.padding(top = 2.dp)) {
                        TextButton(onClick = onChangeDisplay, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.change_entity)) }
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = onRemoveMainEntity, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.remove_main_entity), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.show_icon), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Checkbox(checked = draft.showIcon, onCheckedChange = onShowIconChange)
                    }
                    ValueFormattingControls(
                        domain = display.domain,
                        attributesJson = attrsByEntityId[display.entityId],
                        currentState = display.state,
                        displayPrecision = draft.displayPrecision,
                        datetimeFormat = draft.datetimeFormat,
                        onDisplayPrecisionChange = onDisplayPrecisionChange,
                        onDatetimeFormatChange = onDatetimeFormatChange,
                    )
                    if (display.domain in RANGE_VALUE_DOMAINS) {
                        RangeValueToggle(checked = draft.showRangeValue, onCheckedChange = onShowRangeValueChange)
                    }
                }
            }
            Spacer(Modifier.padding(8.dp))

            // ── HANDLING ── (kun relevant med en hoved-entitet — chips-only rækker har ingen
            // klikbar hoved-række; hver chip har sin egen uafhængige handling i "Ekstra info".)
            if (display != null) {
                SectionCard(title = stringResource(R.string.section_action)) {
                    if (invalidTarget) {
                        Text(
                            stringResource(R.string.action_invalid_target),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        // "Reagér på tryk"-kontakt: kun når mål == visning. Ved andet mål er en
                        // handling altid underforstået, så kontakten (og dermed NONE) findes ikke.
                        // Vises nu OGSÅ for read-only visnings-entiteter (fx en sensor), fordi
                        // "Åbn app" er en gyldig handling uanset domæne.
                        if (!targetDiffers) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.reacts_on_tap), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Checkbox(
                                    checked = reactsToTap,
                                    onCheckedChange = { on ->
                                        onActionChange(
                                            when {
                                                !on -> "NONE"
                                                canControlHa -> rememberedAction?.takeIf { it in opts } ?: opts.first()
                                                else -> "OPEN_APP"
                                            }
                                        )
                                    },
                                )
                            }
                        }
                        val showActionChoice = targetDiffers || reactsToTap
                        if (showActionChoice) {
                            // Selve under-valget (HA-handlinger eller app-vælgeren) — hvad der vises
                            // afhænger af den aktuelle tilstand (isAppMode).
                            val subChoice: @Composable () -> Unit = {
                                if (isAppMode) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                appLabel ?: stringResource(R.string.no_app_selected),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            if (draft.packageName != null) {
                                                Text(
                                                    stringResource(R.string.selected_app),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        TextButton(onClick = onChooseApp) {
                                            Text(stringResource(if (draft.packageName == null) R.string.choose_app else R.string.change_app))
                                        }
                                    }
                                } else if (opts.size == 1) {
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
                                                .clickable(onClick = pick),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(selected = draft.action == actionType, onClick = pick)
                                            Spacer(Modifier.width(8.dp))
                                            Text(actionShortLabel(actionType))
                                        }
                                    }
                                }
                            }
                            // Rykket ind med en lodret streg, så det tydeligt hører til meta-valget ovenfor.
                            val indentedSubChoice: @Composable () -> Unit = {
                                Row(modifier = Modifier.height(IntrinsicSize.Min).padding(start = 14.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) { subChoice() }
                                }
                            }
                            // Meta-valg: styr Home Assistant vs. åbn en app. Under-valget foldes ud LIGE
                            // under sit eget meta-punkt. Kun vist når HA-styring overhovedet er mulig for
                            // målet; ellers er "Åbn app" det eneste (og underforståede) valg.
                            if (canControlHa) {
                                ActionModeRow(
                                    selected = !isAppMode,
                                    label = stringResource(R.string.action_control_ha),
                                    onClick = {
                                        val ha = rememberedAction?.takeIf { it in opts } ?: opts.first()
                                        rememberedAction = ha
                                        onActionChange(ha)
                                    },
                                )
                                if (!isAppMode) indentedSubChoice()
                                ActionModeRow(
                                    selected = isAppMode,
                                    label = stringResource(R.string.action_open_app_option),
                                    onClick = { onActionChange("OPEN_APP") },
                                )
                                if (isAppMode) indentedSubChoice()
                            } else {
                                subChoice()
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
                    if (draft.action == "TOGGLE" || draft.action == "TRIGGER") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.confirm_action_switch), modifier = Modifier.weight(1f))
                            Checkbox(checked = draft.confirmAction, onCheckedChange = onConfirmActionChange)
                        }
                    }
                    if (draft.action == "RANGE") {
                        RangeInputModeControl(
                            selected = draft.rangeInputMode,
                            onSelected = onRangeInputModeChange,
                        )
                    }
                    if (targetDiffers && action != null) {
                        Text(
                            stringResource(R.string.target_label, action.friendlyName),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    // "Handl på en anden entitet" giver ikke mening i app-tilstand (handlingen peger
                    // på en app) — skjult dér.
                    if (!isAppMode) {
                        TextButton(onClick = onChangeTarget, contentPadding = PaddingValues(0.dp)) {
                            Text(if (targetDiffers) stringResource(R.string.choose_other_target) else stringResource(R.string.act_on_other_entity))
                        }
                    }
                }
                Spacer(Modifier.padding(8.dp))
            }

            // ── EKSTRA INFO ── (altid tilgængelig — også for chips-only rækker)
            SectionCard(title = stringResource(R.string.extra_info_section, draft.secondaryEntities.size, MAX_SECONDARY_ENTITIES)) {
                draft.secondaryEntities.forEachIndexed { index, secondary ->
                    SecondaryEntityRow(
                        secondary = secondary,
                        attrsByEntityId = attrsByEntityId,
                        canMoveUp = index > 0,
                        canMoveDown = index < draft.secondaryEntities.size - 1,
                        onRemove = { onRemoveSecondary(index) },
                        onMoveUp = { onMoveSecondaryUp(index) },
                        onMoveDown = { onMoveSecondaryDown(index) },
                        onChangeTarget = { onSecondaryChangeTarget(index) },
                        onActionChange = { newAction -> onSecondaryActionChange(index, newAction) },
                        onRangeInputModeChange = { mode -> onSecondaryRangeInputModeChange(index, mode) },
                        onLabelChange = { newLabel -> onSecondaryLabelChange(index, newLabel) },
                        onShowValueChange = { showValue -> onSecondaryShowValueChange(index, showValue) },
                        onConfirmActionChange = { confirm -> onSecondaryConfirmActionChange(index, confirm) },
                        onDisplayPrecisionChange = { precision -> onSecondaryDisplayPrecisionChange(index, precision) },
                        onDatetimeFormatChange = { pattern -> onSecondaryDatetimeFormatChange(index, pattern) },
                        onShowIconChange = { showIcon -> onSecondaryShowIconChange(index, showIcon) },
                        onShowRangeValueChange = { show -> onSecondaryShowRangeValueChange(index, show) },
                    )
                    if (index < draft.secondaryEntities.size - 1) Spacer(Modifier.padding(vertical = 4.dp))
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
            Button(
                onClick = onSave,
                enabled = !invalidTarget && !invalidAppChoice && (display != null || draft.secondaryEntities.isNotEmpty()),
                modifier = Modifier.fillMaxWidth(),
            ) {
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onChangeTarget: () -> Unit,
    onActionChange: (String) -> Unit,
    onRangeInputModeChange: (String?) -> Unit,
    onLabelChange: (String) -> Unit,
    onShowValueChange: (Boolean) -> Unit,
    onConfirmActionChange: (Boolean) -> Unit,
    onDisplayPrecisionChange: (Int?) -> Unit,
    onDatetimeFormatChange: (String?) -> Unit,
    onShowIconChange: (Boolean) -> Unit,
    onShowRangeValueChange: (Boolean) -> Unit,
) {
    val display = secondary.displayEntity
    val action = secondary.actionEntity
    val targetDiffers = action.entityId != display.entityId
    val opts = actionOptionsFor(action.domain)
    val readOnly = opts.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
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
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.cd_move_up, display.friendlyName),
                    tint = if (canMoveUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_move_down, display.friendlyName),
                    tint = if (canMoveDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_remove, display.friendlyName), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
        OutlinedTextField(
            value = secondary.label,
            onValueChange = { if (it.length <= 22) onLabelChange(it) },
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
        if (display.domain in RANGE_VALUE_DOMAINS) {
            RangeValueToggle(checked = secondary.showRangeValue, onCheckedChange = onShowRangeValueChange)
        }
        Spacer(Modifier.padding(top = 6.dp))
        Text(stringResource(R.string.section_action), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        val reactsToTap = secondary.action != "NONE"
        if (readOnly) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    stringResource(R.string.action_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onChangeTarget, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.change)) }
            }
        } else {
            // "Reagérer på tryk"-kontakt for chippen — samme mønster som hoved-entiteten
            // (kun når mål == visning; targetDiffers indebærer altid en handling). Uden denne
            // kontakt var en chips gemte action="NONE" (fra FØR domænet fik nogen handling,
            // fx device_tracker/sensor før HISTORY) umuligt at opgradere: opts.size==1-grenen
            // viste kun en informativ auto-tekst uden forbindelse til den faktisk gemte værdi.
            if (!targetDiffers) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.reacts_on_tap), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = reactsToTap,
                        onCheckedChange = { on -> onActionChange(if (on) opts.first() else "NONE") },
                    )
                }
            }
            val showActionChoice = targetDiffers || reactsToTap
            if (showActionChoice) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (targetDiffers) {
                            Text(stringResource(R.string.target_label, action.friendlyName), style = MaterialTheme.typography.bodySmall)
                        }
                        if (opts.size == 1) {
                            Text(stringResource(R.string.on_tap_label, actionShortLabel(opts[0])), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = onChangeTarget, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.change)) }
                }
                if (opts.size > 1) {
                    opts.forEach { actionType ->
                        val pick = { onActionChange(actionType) }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = pick),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = secondary.action == actionType, onClick = pick)
                            Spacer(Modifier.width(6.dp))
                            Text(actionShortLabel(actionType), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.slot_view_only_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onChangeTarget, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.change)) }
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
                stringResource(R.string.show_icon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Checkbox(checked = secondary.showIcon, onCheckedChange = onShowIconChange)
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
            Checkbox(checked = secondary.showValue, onCheckedChange = onShowValueChange)
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
                Checkbox(checked = secondary.confirmAction, onCheckedChange = onConfirmActionChange)
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
