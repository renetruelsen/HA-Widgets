package dk.akait.hawidgets.widget.multientity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.AppSettingsHint
import dk.akait.hawidgets.widget.common.domainIconResId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListScreen(
    slots: List<MultiWidgetSlotEntity>,
    showRefreshIcon: Boolean,
    onShowRefreshIconChange: (Boolean) -> Unit,
    onAddSlot: () -> Unit,
    onEditSlot: (Int) -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onMoveSlot: (Int, Int) -> Unit,
    onSave: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.multi_entity_config_title)) },
                actions = {
                    IconButton(onClick = onOpenAppSettings) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.settings_in_app_hint))
                    }
                    dk.akait.hawidgets.transfer.TransferOverflowMenu(onExport = onExport, onImport = onImport)
                },
            )
        },
        bottomBar = { AppSettingsHint(onOpenSettings = onOpenAppSettings) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (slots.isEmpty()) {
                Text(
                    stringResource(R.string.multi_entity_empty_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(6.dp))
                // Import er ellers gemt i ⋮-menuen; her i tom-tilstanden — hvor en bruger der lige
                // har placeret en widget står — gøres den åbenlys ved siden af "Tilføj slot".
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_config))
                }
            } else {
                slots.sortedBy { it.slotIndex }.forEachIndexed { index, slot ->
                    SlotCard(
                        slot = slot,
                        canMoveUp = index > 0,
                        canMoveDown = index < slots.size - 1,
                        onClick = { onEditSlot(index) },
                        onRemove = { onRemoveSlot(index) },
                        onMoveUp = { onMoveSlot(index, -1) },
                        onMoveDown = { onMoveSlot(index, 1) },
                    )
                    Spacer(Modifier.padding(vertical = 4.dp))
                }
            }
            Spacer(Modifier.padding(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.multi_entity_show_refresh_icon), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = showRefreshIcon, onCheckedChange = onShowRefreshIconChange)
            }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = onAddSlot, enabled = slots.size < 5, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.add_slot))
            }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = onSave, enabled = slots.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_widget))
            }
        }
    }
}

/** Kort pr. slot: klikbart (svagt tonet) hovedindhold m/navn på egen fuld-bredde række
 * (1 linje + ellipsis) og chevron → åbner editor, efterfulgt af en bund-række med
 * ↑/↓/slet som separate 48dp-ikonknapper. Alle sekundær-entiteter listes altid fuldt
 * synligt — ingen skjult "+N"-optælling. */
@Composable
private fun SlotCard(
    slot: MultiWidgetSlotEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val name = slot.label.ifEmpty { slot.displayEntityId }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
                .clickable(onClickLabel = stringResource(R.string.cd_edit), role = Role.Button, onClick = onClick)
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(domainIconResId(slot.displayDomain)),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.cd_edit),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            val actionSummary = if (slot.actionEntityId == slot.displayEntityId) {
                actionLabel(slot.action)
            } else {
                stringResource(R.string.label_with_target, actionLabel(slot.action), slot.actionEntityId)
            }
            Text(
                actionSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 30.dp, top = 2.dp),
            )
            val secondarySummaries = secondarySlotSummaries(slot)
            if (secondarySummaries.isNotEmpty()) {
                Column(modifier = Modifier.padding(start = 30.dp, top = 6.dp)) {
                    secondarySummaries.forEach { (iconResId, text) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Icon(
                                painter = painterResource(iconResId),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.cd_move_up, name),
                    tint = if (canMoveUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_move_down, name),
                    tint = if (canMoveDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_remove, name), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** (ikon, "navn — handlingslabel") for hver konfigureret sekundær-chip på sloten. */
@Composable
private fun secondarySlotSummaries(slot: MultiWidgetSlotEntity): List<Pair<Int, String>> {
    @Composable
    fun summaryFor(
        displayId: String?, displayDomain: String?, actionId: String?, action: String?,
    ): Pair<Int, String>? {
        if (displayId == null || displayDomain == null || action == null) return null
        val label = if (actionId != null && actionId != displayId) {
            stringResource(R.string.label_dash_with_target, displayId, actionShortLabel(action), actionId)
        } else {
            stringResource(R.string.label_dash, displayId, actionShortLabel(action))
        }
        return domainIconResId(displayDomain) to label
    }
    return listOfNotNull(
        summaryFor(slot.secondary1DisplayEntityId, slot.secondary1DisplayDomain, slot.secondary1ActionEntityId, slot.secondary1Action),
        summaryFor(slot.secondary2DisplayEntityId, slot.secondary2DisplayDomain, slot.secondary2ActionEntityId, slot.secondary2Action),
        summaryFor(slot.secondary3DisplayEntityId, slot.secondary3DisplayDomain, slot.secondary3ActionEntityId, slot.secondary3Action),
        summaryFor(slot.secondary4DisplayEntityId, slot.secondary4DisplayDomain, slot.secondary4ActionEntityId, slot.secondary4Action),
    )
}
