package dk.akait.hawidgets.widget.multientity

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.MultiWidgetEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.MULTI_ENTITY_DOMAINS
import dk.akait.hawidgets.widget.common.compatibleActionsFor
import dk.akait.hawidgets.widget.common.defaultShowValueFor
import dk.akait.hawidgets.widget.common.domainIconResId
import dk.akait.hawidgets.widget.common.formatEntityState
import dk.akait.hawidgets.worker.SyncWorker
import kotlinx.coroutines.launch

class MultiEntityWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent {
            MaterialTheme {
                MultiEntityConfigScreen(
                    appWidgetId = appWidgetId,
                    onSaved = {
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                        finish()
                    },
                )
            }
        }
    }
}

private sealed interface PickerTarget {
    data object Display : PickerTarget
    data object Action : PickerTarget
    /** [index] = position in [SlotDraft.secondaryEntities] — may equal the list's current size
     * (adding a new one) or an existing index (replacing that chip's display entity). */
    data class SecondaryDisplay(val index: Int) : PickerTarget
    /** [index] = existing position whose action-mål (ikke visning) skal ændres. */
    data class SecondaryAction(val index: Int) : PickerTarget
}

/** Samme visning/handling-uafhængighed som [SlotDraft] selv — se docs/widget-settings-spec.md §9. */
private data class SecondarySlotDraft(
    val displayEntity: HaApiClient.EntityBrief,
    val actionEntity: HaApiClient.EntityBrief,
    val action: String,
    /** Vis værditekst (ikke kun ikon) på chippen — brugervalgt, default via [defaultShowValueFor]. */
    val showValue: Boolean = defaultShowValueFor(action),
)

private data class SlotDraft(
    val displayEntity: HaApiClient.EntityBrief? = null,
    val actionEntity: HaApiClient.EntityBrief? = null,
    val action: String = "NONE",
    val label: String = "",
    val secondaryEntities: List<SecondarySlotDraft> = emptyList(),
)

private const val MAX_SECONDARY_ENTITIES = 3

private sealed interface Step {
    data object ListScreen : Step
    data class SlotEditor(val editIndex: Int?, val draft: SlotDraft) : Step
    data class EntityPicker(val forTarget: PickerTarget, val editIndex: Int?, val draft: SlotDraft) : Step
}

@Composable
private fun actionLabel(action: String): String = when (action) {
    "TOGGLE" -> stringResource(R.string.action_toggle)
    "RANGE" -> stringResource(R.string.action_range)
    "TRIGGER" -> stringResource(R.string.action_trigger_long)
    "TEXT" -> stringResource(R.string.action_text)
    "DATETIME" -> stringResource(R.string.action_datetime)
    else -> stringResource(R.string.action_view_only)
}

/** Kort variant til radios + auto-linjen i SlotEditorScreen (undgår den lange TRIGGER-tekst). */
@Composable
private fun actionShortLabel(action: String): String = when (action) {
    "TOGGLE" -> stringResource(R.string.action_toggle)
    "RANGE" -> stringResource(R.string.action_range)
    "TRIGGER" -> stringResource(R.string.action_trigger_short)
    "TEXT" -> stringResource(R.string.action_text)
    "DATETIME" -> stringResource(R.string.action_datetime)
    else -> stringResource(R.string.action_view_only_short)
}

/** Handlings-typer (ex. NONE) et domæne understøtter som action-mål. Tom = read-only. */
private fun actionOptionsFor(domain: String): List<String> =
    compatibleActionsFor(domain).filter { it != "NONE" }

/** Default-handling når et mål (eller ny visnings-entitet) vælges: første rigtige handling, ellers
 * NONE for read-only domæner. Bruges til at snap'e action ved mål-skift, så UI aldrig står med en
 * handling målet ikke understøtter. */
private fun defaultActionFor(domain: String): String =
    actionOptionsFor(domain).firstOrNull() ?: "NONE"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiEntityConfigScreen(appWidgetId: Int, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var slots by remember { mutableStateOf<List<MultiWidgetSlotEntity>>(emptyList()) }
    var showRefreshIcon by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var allEntities by remember { mutableStateOf<List<HaApiClient.EntityBrief>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf<Step>(Step.ListScreen) }
    val haNotConnectedError = stringResource(R.string.ha_not_connected_error)

    LaunchedEffect(Unit) {
        val store = SecureStore.get(context)
        if (!store.isConfigured) {
            loadError = haNotConnectedError
            isLoading = false
            return@LaunchedEffect
        }
        val client = HaApiClient(store.baseUrl!!, store.token!!)
        allEntities = client.listStatesByDomains(MULTI_ENTITY_DOMAINS.toSet()).sortedBy { it.friendlyName }
        val db = AppDatabase.get(context)
        slots = db.multiWidgetDao().getSlots(appWidgetId)
        showRefreshIcon = db.multiWidgetDao().get(appWidgetId)?.showRefreshIcon ?: true
        isLoading = false
    }

    fun entityOrPlaceholder(entityId: String, domain: String) =
        allEntities.find { it.entityId == entityId }
            ?: HaApiClient.EntityBrief(entityId, entityId, "unknown", domain)

    fun secondaryDraftFrom(
        displayId: String?, displayDomain: String?,
        actionId: String?, actionDomain: String?,
        action: String?, showValue: Boolean?,
    ): SecondarySlotDraft? {
        if (displayId == null || displayDomain == null || actionId == null || actionDomain == null || action == null) return null
        return SecondarySlotDraft(
            displayEntity = entityOrPlaceholder(displayId, displayDomain),
            actionEntity = entityOrPlaceholder(actionId, actionDomain),
            action = action,
            showValue = showValue ?: defaultShowValueFor(action),
        )
    }

    fun draftFromSlot(slot: MultiWidgetSlotEntity): SlotDraft {
        val display = entityOrPlaceholder(slot.displayEntityId, slot.displayDomain)
        val actionEntity = entityOrPlaceholder(slot.actionEntityId, slot.actionDomain)
        // Normalisér gammelt data: en slot gemt med action=NONE men et ANDET mål var muligt i den
        // gamle UI, men er ugyldigt i den nye model (mål ≠ visning ⇒ altid en handling, ingen
        // "Kun visning"-radio at vælge). Snap til første gyldige handling, så en radio er valgt.
        val targetDiffers = actionEntity.entityId != display.entityId
        val opts = actionOptionsFor(actionEntity.domain)
        val normalizedAction = if (targetDiffers && slot.action !in opts) {
            opts.firstOrNull() ?: slot.action
        } else {
            slot.action
        }
        val secondaries = listOfNotNull(
            secondaryDraftFrom(
                slot.secondary1DisplayEntityId, slot.secondary1DisplayDomain,
                slot.secondary1ActionEntityId, slot.secondary1ActionDomain, slot.secondary1Action,
                slot.secondary1ShowValue,
            ),
            secondaryDraftFrom(
                slot.secondary2DisplayEntityId, slot.secondary2DisplayDomain,
                slot.secondary2ActionEntityId, slot.secondary2ActionDomain, slot.secondary2Action,
                slot.secondary2ShowValue,
            ),
            secondaryDraftFrom(
                slot.secondary3DisplayEntityId, slot.secondary3DisplayDomain,
                slot.secondary3ActionEntityId, slot.secondary3ActionDomain, slot.secondary3Action,
                slot.secondary3ShowValue,
            ),
        )
        return SlotDraft(display, actionEntity, normalizedAction, slot.label, secondaries)
    }

    fun saveSlot(editIndex: Int?, draft: SlotDraft) {
        val display = draft.displayEntity ?: return
        val action = draft.actionEntity ?: display
        val sec = draft.secondaryEntities
        val newSlot = MultiWidgetSlotEntity(
            appWidgetId = appWidgetId,
            slotIndex = editIndex ?: slots.size,
            displayEntityId = display.entityId,
            displayDomain = display.domain,
            actionEntityId = action.entityId,
            actionDomain = action.domain,
            action = draft.action,
            label = draft.label.trim(),
            secondary1DisplayEntityId = sec.getOrNull(0)?.displayEntity?.entityId,
            secondary1DisplayDomain = sec.getOrNull(0)?.displayEntity?.domain,
            secondary1ActionEntityId = sec.getOrNull(0)?.actionEntity?.entityId,
            secondary1ActionDomain = sec.getOrNull(0)?.actionEntity?.domain,
            secondary1Action = sec.getOrNull(0)?.action,
            secondary1ShowValue = sec.getOrNull(0)?.showValue,
            secondary2DisplayEntityId = sec.getOrNull(1)?.displayEntity?.entityId,
            secondary2DisplayDomain = sec.getOrNull(1)?.displayEntity?.domain,
            secondary2ActionEntityId = sec.getOrNull(1)?.actionEntity?.entityId,
            secondary2ActionDomain = sec.getOrNull(1)?.actionEntity?.domain,
            secondary2Action = sec.getOrNull(1)?.action,
            secondary2ShowValue = sec.getOrNull(1)?.showValue,
            secondary3DisplayEntityId = sec.getOrNull(2)?.displayEntity?.entityId,
            secondary3DisplayDomain = sec.getOrNull(2)?.displayEntity?.domain,
            secondary3ActionEntityId = sec.getOrNull(2)?.actionEntity?.entityId,
            secondary3ActionDomain = sec.getOrNull(2)?.actionEntity?.domain,
            secondary3Action = sec.getOrNull(2)?.action,
            secondary3ShowValue = sec.getOrNull(2)?.showValue,
        )
        slots = if (editIndex == null) slots + newSlot
        else slots.toMutableList().also { it[editIndex] = newSlot }
        step = Step.ListScreen
    }

    when (val s = step) {
        Step.ListScreen -> ListScreen(
            slots = slots,
            showRefreshIcon = showRefreshIcon,
            onShowRefreshIconChange = { showRefreshIcon = it },
            onAddSlot = { step = Step.EntityPicker(PickerTarget.Display, null, SlotDraft()) },
            onEditSlot = { index -> step = Step.SlotEditor(index, draftFromSlot(slots[index])) },
            onRemoveSlot = { index ->
                slots = slots.filterIndexed { i, _ -> i != index }.mapIndexed { i, sl -> sl.copy(slotIndex = i) }
            },
            onMoveSlot = { index, delta ->
                val target = index + delta
                if (target in slots.indices) {
                    val mutable = slots.toMutableList()
                    val a = mutable[index]
                    val b = mutable[target]
                    mutable[index] = b.copy(slotIndex = index)
                    mutable[target] = a.copy(slotIndex = target)
                    slots = mutable.sortedBy { it.slotIndex }
                }
            },
            onSave = {
                scope.launch {
                    val db = AppDatabase.get(context)
                    db.multiWidgetDao().upsert(MultiWidgetEntity(appWidgetId, "", showRefreshIcon = showRefreshIcon))
                    db.multiWidgetDao().deleteAllSlots(appWidgetId)
                    slots.forEachIndexed { i, sl -> db.multiWidgetDao().upsertSlot(sl.copy(slotIndex = i)) }
                    SyncWorker.runNow(context)
                    SyncWorker.schedule(context)
                    onSaved()
                }
            },
        )

        is Step.EntityPicker -> EntityPickerSubScreen(
            title = when (s.forTarget) {
                PickerTarget.Display, is PickerTarget.SecondaryDisplay -> stringResource(R.string.picker_target_display_title)
                PickerTarget.Action, is PickerTarget.SecondaryAction -> stringResource(R.string.picker_target_action_title)
            },
            entities = when (s.forTarget) {
                // Handlings-mål-valg filtreres til domæner der understøtter mindst én handling —
                // undgår at brugeren kan vælge et read-only mål (fx en sensor), som ellers ville
                // kræve en efterfølgende fejlmelding.
                PickerTarget.Action, is PickerTarget.SecondaryAction -> allEntities.filter { actionOptionsFor(it.domain).isNotEmpty() }
                PickerTarget.Display, is PickerTarget.SecondaryDisplay -> allEntities
            },
            isLoading = isLoading,
            error = loadError,
            onSelected = { brief ->
                // Snap action til domænets default (opts[0], ellers NONE) — både når visnings-
                // entiteten og når action-målet vælges. Sikrer at action altid er gyldig for det
                // valgte måls domæne (ingen "Åbn skyder" på en kontakt osv.).
                val updatedDraft = when (val target = s.forTarget) {
                    PickerTarget.Display ->
                        s.draft.copy(displayEntity = brief, actionEntity = brief, action = defaultActionFor(brief.domain))
                    PickerTarget.Action ->
                        s.draft.copy(actionEntity = brief, action = defaultActionFor(brief.domain))
                    is PickerTarget.SecondaryDisplay -> {
                        val newSecondary = SecondarySlotDraft(brief, brief, defaultActionFor(brief.domain))
                        val updated = s.draft.secondaryEntities.toMutableList()
                        if (target.index < updated.size) updated[target.index] = newSecondary else updated.add(newSecondary)
                        s.draft.copy(secondaryEntities = updated)
                    }
                    is PickerTarget.SecondaryAction -> {
                        val updated = s.draft.secondaryEntities.toMutableList()
                        updated.getOrNull(target.index)?.let { existing ->
                            updated[target.index] = existing.copy(actionEntity = brief, action = defaultActionFor(brief.domain))
                        }
                        s.draft.copy(secondaryEntities = updated)
                    }
                }
                step = Step.SlotEditor(s.editIndex, updatedDraft)
            },
            onBack = {
                step = if (s.draft.displayEntity == null) Step.ListScreen else Step.SlotEditor(s.editIndex, s.draft)
            },
        )

        is Step.SlotEditor -> SlotEditorScreen(
            draft = s.draft,
            isEditing = s.editIndex != null,
            onChangeDisplay = { step = Step.EntityPicker(PickerTarget.Display, s.editIndex, s.draft) },
            onChangeTarget = { step = Step.EntityPicker(PickerTarget.Action, s.editIndex, s.draft) },
            onActionChange = { newAction -> step = Step.SlotEditor(s.editIndex, s.draft.copy(action = newAction)) },
            onLabelChange = { newLabel -> step = Step.SlotEditor(s.editIndex, s.draft.copy(label = newLabel)) },
            onAddSecondary = {
                step = Step.EntityPicker(PickerTarget.SecondaryDisplay(s.draft.secondaryEntities.size), s.editIndex, s.draft)
            },
            onRemoveSecondary = { index ->
                val updated = s.draft.secondaryEntities.filterIndexed { i, _ -> i != index }
                step = Step.SlotEditor(s.editIndex, s.draft.copy(secondaryEntities = updated))
            },
            onSecondaryChangeTarget = { index ->
                step = Step.EntityPicker(PickerTarget.SecondaryAction(index), s.editIndex, s.draft)
            },
            onSecondaryActionChange = { index, newAction ->
                val updated = s.draft.secondaryEntities.toMutableList()
                updated[index] = updated[index].copy(action = newAction)
                step = Step.SlotEditor(s.editIndex, s.draft.copy(secondaryEntities = updated))
            },
            onSecondaryShowValueChange = { index, showValue ->
                val updated = s.draft.secondaryEntities.toMutableList()
                updated[index] = updated[index].copy(showValue = showValue)
                step = Step.SlotEditor(s.editIndex, s.draft.copy(secondaryEntities = updated))
            },
            onSave = { saveSlot(s.editIndex, s.draft) },
            onBack = { step = Step.ListScreen },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    slots: List<MultiWidgetSlotEntity>,
    showRefreshIcon: Boolean,
    onShowRefreshIconChange: (Boolean) -> Unit,
    onAddSlot: () -> Unit,
    onEditSlot: (Int) -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onMoveSlot: (Int, Int) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.multi_entity_config_title)) }) }) { padding ->
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

/** Kort pr. slot: klikbart (svagt tonet) hovedindhold m/chevron → åbner editor, smal
 * fjern-søjle, og en ↑/↓-sorteringssøjle der fylder kortets fulde højde. Alle sekundær-
 * entiteter listes altid fuldt synligt — ingen skjult "+N"-optælling. */
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp)),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
                .clickable(onClick = onClick)
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(domainIconResId(slot.displayDomain)),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(slot.label.ifEmpty { slot.displayEntityId }, style = MaterialTheme.typography.bodyLarge)
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
                    Text(actionSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.width(36.dp).fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    .then(if (canMoveUp) Modifier.clickable(onClick = onMoveUp) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.cd_move_up),
                    tint = if (canMoveUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    .then(if (canMoveDown) Modifier.clickable(onClick = onMoveDown) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_move_down),
                    tint = if (canMoveDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                )
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotEditorScreen(
    draft: SlotDraft,
    isEditing: Boolean,
    onChangeDisplay: () -> Unit,
    onChangeTarget: () -> Unit,
    onActionChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onAddSecondary: () -> Unit,
    onRemoveSecondary: (Int) -> Unit,
    onSecondaryChangeTarget: (Int) -> Unit,
    onSecondaryActionChange: (Int, String) -> Unit,
    onSecondaryShowValueChange: (Int, Boolean) -> Unit,
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
                        onRemove = { onRemoveSecondary(index) },
                        onChangeTarget = { onSecondaryChangeTarget(index) },
                        onActionChange = { newAction -> onSecondaryActionChange(index, newAction) },
                        onShowValueChange = { showValue -> onSecondaryShowValueChange(index, showValue) },
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
    onRemove: () -> Unit,
    onChangeTarget: () -> Unit,
    onActionChange: (String) -> Unit,
    onShowValueChange: (Boolean) -> Unit,
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
    }
}

/** Lys indrammet sektion med overskrift — bruges til «Visning» og «Handling». */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityPickerSubScreen(
    title: String,
    entities: List<HaApiClient.EntityBrief>,
    isLoading: Boolean,
    error: String?,
    onSelected: (HaApiClient.EntityBrief) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val filtered = entities.filter {
                        query.isBlank() ||
                            it.friendlyName.contains(query, ignoreCase = true) ||
                            it.entityId.contains(query, ignoreCase = true)
                    }
                    LazyColumn {
                        items(filtered) { brief ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelected(brief) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(domainIconResId(brief.domain)),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (brief.state == "on") MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(brief.friendlyName, style = MaterialTheme.typography.bodyLarge)
                                    Text(brief.entityId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                SuggestionChip(onClick = {}, label = { Text(formatEntityState(brief.domain, brief.state, brief.unit)) })
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
