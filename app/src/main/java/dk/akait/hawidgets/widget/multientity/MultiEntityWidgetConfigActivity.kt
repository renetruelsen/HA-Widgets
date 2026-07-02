package dk.akait.hawidgets.widget.multientity

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.MultiWidgetEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.MULTI_ENTITY_DOMAINS
import dk.akait.hawidgets.widget.common.compatibleActionsFor
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

private enum class PickerTarget { DISPLAY, ACTION }

private data class SlotDraft(
    val displayEntity: HaApiClient.EntityBrief? = null,
    val actionEntity: HaApiClient.EntityBrief? = null,
    val action: String = "NONE",
    val label: String = "",
)

private sealed interface Step {
    data object ListScreen : Step
    data class SlotEditor(val editIndex: Int?, val draft: SlotDraft) : Step
    data class EntityPicker(val forTarget: PickerTarget, val editIndex: Int?, val draft: SlotDraft) : Step
}

private fun actionLabel(action: String): String = when (action) {
    "TOGGLE" -> "Slå til/fra"
    "RANGE" -> "Åbn skyder"
    "TRIGGER" -> "Udløs automatisering/script"
    else -> "Kun visning"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiEntityConfigScreen(appWidgetId: Int, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var titleInput by remember { mutableStateOf("") }
    var slots by remember { mutableStateOf<List<MultiWidgetSlotEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var allEntities by remember { mutableStateOf<List<HaApiClient.EntityBrief>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf<Step>(Step.ListScreen) }

    LaunchedEffect(Unit) {
        val store = SecureStore.get(context)
        if (!store.isConfigured) {
            loadError = "HA ikke forbundet. Åbn HA Widgets og forbind først."
            isLoading = false
            return@LaunchedEffect
        }
        val client = HaApiClient(store.baseUrl!!, store.token!!)
        allEntities = client.listStatesByDomains(MULTI_ENTITY_DOMAINS.toSet()).sortedBy { it.friendlyName }
        val db = AppDatabase.get(context)
        titleInput = db.multiWidgetDao().get(appWidgetId)?.title.orEmpty()
        slots = db.multiWidgetDao().getSlots(appWidgetId)
        isLoading = false
    }

    fun draftFromSlot(slot: MultiWidgetSlotEntity): SlotDraft {
        val display = allEntities.find { it.entityId == slot.displayEntityId }
            ?: HaApiClient.EntityBrief(slot.displayEntityId, slot.displayEntityId, "unknown", slot.displayDomain)
        val action = allEntities.find { it.entityId == slot.actionEntityId }
            ?: HaApiClient.EntityBrief(slot.actionEntityId, slot.actionEntityId, "unknown", slot.actionDomain)
        return SlotDraft(display, action, slot.action, slot.label)
    }

    fun saveSlot(editIndex: Int?, draft: SlotDraft) {
        val display = draft.displayEntity ?: return
        val action = draft.actionEntity ?: display
        val newSlot = MultiWidgetSlotEntity(
            appWidgetId = appWidgetId,
            slotIndex = editIndex ?: slots.size,
            displayEntityId = display.entityId,
            displayDomain = display.domain,
            actionEntityId = action.entityId,
            actionDomain = action.domain,
            action = draft.action,
            label = draft.label.trim(),
        )
        slots = if (editIndex == null) slots + newSlot
        else slots.toMutableList().also { it[editIndex] = newSlot }
        step = Step.ListScreen
    }

    when (val s = step) {
        Step.ListScreen -> ListScreen(
            titleInput = titleInput,
            onTitleChange = { titleInput = it },
            slots = slots,
            onAddSlot = { step = Step.EntityPicker(PickerTarget.DISPLAY, null, SlotDraft()) },
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
                    db.multiWidgetDao().upsert(MultiWidgetEntity(appWidgetId, titleInput.trim()))
                    db.multiWidgetDao().deleteAllSlots(appWidgetId)
                    slots.forEachIndexed { i, sl -> db.multiWidgetDao().upsertSlot(sl.copy(slotIndex = i)) }
                    SyncWorker.runNow(context)
                    SyncWorker.schedule(context)
                    onSaved()
                }
            },
        )

        is Step.EntityPicker -> EntityPickerSubScreen(
            title = if (s.forTarget == PickerTarget.DISPLAY) "Vælg entitet der skal vises" else "Vælg entitet der skal udløses",
            entities = allEntities,
            isLoading = isLoading,
            error = loadError,
            onSelected = { brief ->
                val updatedDraft = if (s.forTarget == PickerTarget.DISPLAY) {
                    s.draft.copy(displayEntity = brief, actionEntity = brief, action = "NONE")
                } else {
                    s.draft.copy(actionEntity = brief, action = "NONE")
                }
                step = Step.SlotEditor(s.editIndex, updatedDraft)
            },
            onBack = {
                step = if (s.draft.displayEntity == null) Step.ListScreen else Step.SlotEditor(s.editIndex, s.draft)
            },
        )

        is Step.SlotEditor -> SlotEditorScreen(
            draft = s.draft,
            onChangeDisplay = { step = Step.EntityPicker(PickerTarget.DISPLAY, s.editIndex, s.draft) },
            onChangeTarget = { step = Step.EntityPicker(PickerTarget.ACTION, s.editIndex, s.draft) },
            onActionChange = { newAction -> step = Step.SlotEditor(s.editIndex, s.draft.copy(action = newAction)) },
            onLabelChange = { newLabel -> step = Step.SlotEditor(s.editIndex, s.draft.copy(label = newLabel)) },
            onSave = { saveSlot(s.editIndex, s.draft) },
            onBack = { step = Step.ListScreen },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    titleInput: String,
    onTitleChange: (String) -> Unit,
    slots: List<MultiWidgetSlotEntity>,
    onAddSlot: () -> Unit,
    onEditSlot: (Int) -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onMoveSlot: (Int, Int) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Kombineret widget") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = titleInput,
                onValueChange = onTitleChange,
                label = { Text("Widget-titel (valgfrit)") },
                placeholder = { Text("f.eks. Mercedes") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.padding(8.dp))
            if (slots.isEmpty()) {
                Text(
                    "Ingen slots endnu — tryk \"Tilføj slot\" for at starte.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                slots.sortedBy { it.slotIndex }.forEachIndexed { index, slot ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(domainIconResId(slot.displayDomain)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(slot.label.ifEmpty { slot.displayEntityId }, style = MaterialTheme.typography.bodyLarge)
                            val actionSummary = if (slot.actionEntityId == slot.displayEntityId) {
                                actionLabel(slot.action)
                            } else {
                                "${actionLabel(slot.action)} → ${slot.actionEntityId}"
                            }
                            Text(actionSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onMoveSlot(index, -1) }, enabled = index > 0) { Text("↑") }
                        TextButton(onClick = { onMoveSlot(index, 1) }, enabled = index < slots.size - 1) { Text("↓") }
                        TextButton(onClick = { onEditSlot(index) }) { Text("Rediger") }
                        TextButton(onClick = { onRemoveSlot(index) }) { Text("Fjern") }
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.padding(8.dp))
            Button(onClick = onAddSlot, enabled = slots.size < 5, modifier = Modifier.fillMaxWidth()) {
                Text("+ Tilføj slot")
            }
            Spacer(Modifier.padding(4.dp))
            Button(onClick = onSave, enabled = slots.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Gem widget")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotEditorScreen(
    draft: SlotDraft,
    onChangeDisplay: () -> Unit,
    onChangeTarget: () -> Unit,
    onActionChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val display = draft.displayEntity ?: return
    val action = draft.actionEntity ?: display
    val compatible = compatibleActionsFor(action.domain)

    Scaffold(topBar = { TopAppBar(title = { Text("Tilpas handling") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(display.friendlyName, style = MaterialTheme.typography.titleMedium)
            Text(display.entityId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onChangeDisplay) { Text("Skift entitet") }
            Spacer(Modifier.padding(8.dp))
            OutlinedTextField(
                value = draft.label,
                onValueChange = { if (it.length <= 12) onLabelChange(it) },
                label = { Text("Kort label (valgfrit)") },
                placeholder = { Text("f.eks. Bad 1") },
                supportingText = { Text("Vises på widget i stedet for enhedsnavn. Maks 12 tegn.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.padding(12.dp))
            Text("Handling", style = MaterialTheme.typography.titleSmall)
            Text(
                if (action.entityId == display.entityId) "Mål: samme entitet" else "Mål: ${action.friendlyName}",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onChangeTarget) { Text("Skift mål") }
            compatible.forEach { actionType ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onActionChange(actionType) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = draft.action == actionType, onClick = { onActionChange(actionType) })
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel(actionType))
                }
            }
            Spacer(Modifier.padding(8.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Tilføj til widget") }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Annullér") }
        }
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
            navigationIcon = { TextButton(onClick = onBack) { Text("Tilbage") } },
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Søg…") },
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
                                SuggestionChip(onClick = {}, label = { Text(formatEntityState(brief.domain, brief.state)) })
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
