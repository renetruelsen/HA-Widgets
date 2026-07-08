package dk.akait.hawidgets.widget.multientity

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dk.akait.hawidgets.ui.theme.HaWidgetsTheme
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.MultiWidgetEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.MULTI_ENTITY_DOMAINS
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
            HaWidgetsTheme {
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

internal sealed interface PickerTarget {
    data object Display : PickerTarget
    data object Action : PickerTarget
    /** [index] = position in [SlotDraft.secondaryEntities] — may equal the list's current size
     * (adding a new one) or an existing index (replacing that chip's display entity). */
    data class SecondaryDisplay(val index: Int) : PickerTarget
    /** [index] = existing position whose action-mål (ikke visning) skal ændres. */
    data class SecondaryAction(val index: Int) : PickerTarget
}

internal sealed interface Step {
    data object ListScreen : Step
    data class SlotEditor(val editIndex: Int?, val draft: SlotDraft) : Step
    data class EntityPicker(val forTarget: PickerTarget, val editIndex: Int?, val draft: SlotDraft) : Step
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiEntityConfigScreen(appWidgetId: Int, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var slots by remember { mutableStateOf<List<MultiWidgetSlotEntity>>(emptyList()) }
    var showRefreshIcon by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var allEntities by remember { mutableStateOf<List<HaApiClient.EntityBrief>>(emptyList()) }
    // entityId → attributesJson (fra Room-cachen, samme kilde SyncWorker fylder widget-renderingen
    // fra) — bruges til isDateTimeLike-detektion (has_date/has_time/device_class) og live-preview
    // i "Ekstra info"/"Visning"-sektionerne. Genbruger eksisterende Room-infrastruktur i stedet for
    // at føje et nyt attributesJson-felt til HaApiClient.EntityBrief (uden for denne opgaves scope).
    var attrsByEntityId by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
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
        attrsByEntityId = allEntities.mapNotNull { entity ->
            db.entityStateDao().get(entity.entityId)?.attributesJson?.let { entity.entityId to it }
        }.toMap()
        isLoading = false
    }

    fun saveSlot(editIndex: Int?, draft: SlotDraft) {
        val newSlot = draft.toSlotEntity(appWidgetId, editIndex ?: slots.size) ?: return
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
            onEditSlot = { index -> step = Step.SlotEditor(index, draftFromSlot(slots[index], allEntities)) },
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
                    db.multiWidgetDao().upsert(MultiWidgetEntity(appWidgetId, showRefreshIcon = showRefreshIcon))
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
            attrsByEntityId = attrsByEntityId,
            onChangeDisplay = { step = Step.EntityPicker(PickerTarget.Display, s.editIndex, s.draft) },
            onChangeTarget = { step = Step.EntityPicker(PickerTarget.Action, s.editIndex, s.draft) },
            onActionChange = { newAction -> step = Step.SlotEditor(s.editIndex, s.draft.copy(action = newAction)) },
            onRangeInputModeChange = { mode -> step = Step.SlotEditor(s.editIndex, s.draft.copy(rangeInputMode = mode)) },
            onLabelChange = { newLabel -> step = Step.SlotEditor(s.editIndex, s.draft.copy(label = newLabel)) },
            onConfirmActionChange = { confirm -> step = Step.SlotEditor(s.editIndex, s.draft.copy(confirmAction = confirm)) },
            onDisplayPrecisionChange = { precision -> step = Step.SlotEditor(s.editIndex, s.draft.copy(displayPrecision = precision)) },
            onDatetimeFormatChange = { pattern -> step = Step.SlotEditor(s.editIndex, s.draft.copy(datetimeFormat = pattern)) },
            onAddSecondary = {
                step = Step.EntityPicker(PickerTarget.SecondaryDisplay(s.draft.secondaryEntities.size), s.editIndex, s.draft)
            },
            onRemoveSecondary = { index ->
                val updated = s.draft.secondaryEntities.filterIndexed { i, _ -> i != index }
                step = Step.SlotEditor(s.editIndex, s.draft.copy(secondaryEntities = updated))
            },
            onMoveSecondaryUp = { index ->
                val updated = s.draft.secondaryEntities.toMutableList()
                updated[index] = updated[index - 1].also { updated[index - 1] = updated[index] }
                step = Step.SlotEditor(s.editIndex, s.draft.copy(secondaryEntities = updated))
            },
            onMoveSecondaryDown = { index ->
                val updated = s.draft.secondaryEntities.toMutableList()
                updated[index] = updated[index + 1].also { updated[index + 1] = updated[index] }
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
            onSecondaryRangeInputModeChange = { index, mode ->
                val updated = s.draft.secondaryEntities.toMutableList()
                updated[index] = updated[index].copy(rangeInputMode = mode)
                step = Step.SlotEditor(s.editIndex, s.draft.copy(secondaryEntities = updated))
            },
            onSecondaryLabelChange = { index, newLabel ->
                val updated = s.draft.secondaryEntities.toMutableList()
                updated[index] = updated[index].copy(label = newLabel)
                step = Step.SlotEditor(s.editIndex, s.draft.copy(secondaryEntities = updated))
            },
            onSecondaryShowValueChange = { index, showValue ->
                val updated = s.draft.secondaryEntities.toMutableList()
                updated[index] = updated[index].copy(showValue = showValue)
                step = Step.SlotEditor(s.editIndex, s.draft.copy(secondaryEntities = updated))
            },
            onSecondaryConfirmActionChange = { index, confirm ->
                val updated = s.draft.secondaryEntities.toMutableList()
                updated[index] = updated[index].copy(confirmAction = confirm)
                step = Step.SlotEditor(s.editIndex, s.draft.copy(secondaryEntities = updated))
            },
            onSecondaryDisplayPrecisionChange = { index, precision ->
                val updated = s.draft.secondaryEntities.toMutableList()
                updated[index] = updated[index].copy(displayPrecision = precision)
                step = Step.SlotEditor(s.editIndex, s.draft.copy(secondaryEntities = updated))
            },
            onSecondaryDatetimeFormatChange = { index, pattern ->
                val updated = s.draft.secondaryEntities.toMutableList()
                updated[index] = updated[index].copy(datetimeFormat = pattern)
                step = Step.SlotEditor(s.editIndex, s.draft.copy(secondaryEntities = updated))
            },
            onSave = { saveSlot(s.editIndex, s.draft) },
            onBack = { step = Step.ListScreen },
        )
    }
}
