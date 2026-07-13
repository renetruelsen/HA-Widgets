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
import dk.akait.hawidgets.data.WIDGET_ORPHAN_GRACE_MILLIS
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.MultiWidgetEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.transfer.ConfirmReplaceDialog
import dk.akait.hawidgets.transfer.ImportError
import dk.akait.hawidgets.transfer.ImportPickerDialog
import dk.akait.hawidgets.transfer.ImportPickerItem
import dk.akait.hawidgets.transfer.TRANSFER_IMPORT_MIME_TYPES
import dk.akait.hawidgets.transfer.TransferConfig
import dk.akait.hawidgets.transfer.WidgetTransferIo
import dk.akait.hawidgets.transfer.importErrorMessage
import dk.akait.hawidgets.transfer.multiTransferConfig
import dk.akait.hawidgets.transfer.rememberImportLauncher
import dk.akait.hawidgets.transfer.singleConfigBundle
import dk.akait.hawidgets.widget.common.AppSettingsHint
import dk.akait.hawidgets.widget.common.MULTI_ENTITY_DOMAINS
import dk.akait.hawidgets.widget.common.domainIconResId
import dk.akait.hawidgets.widget.common.NotConnectedGate
import dk.akait.hawidgets.widget.common.rememberResumeTick
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
    /** App-vælger for "Åbn app"-handlingen (kun hoved-slotten). */
    data class AppPicker(val editIndex: Int?, val draft: SlotDraft) : Step
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
    var notConnected by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    // Import/eksport-tilstand: importChoices = filens multi-configs (vises i vælgeren);
    // pendingImport = den valgte config, der afventer bekræftelse før den overskriver.
    var importChoices by remember { mutableStateOf<List<TransferConfig.Multi>?>(null) }
    var pendingImport by remember { mutableStateOf<TransferConfig.Multi?>(null) }
    // Recovery: soft-slettede (fjernede) widgets inden for grace-perioden, som kan gendannes til
    // DENNE widget. recoverChoices = åben gendan-vælger (genbruger samme picker/confirm/apply-flow).
    var recoverable by remember { mutableStateOf<List<TransferConfig.Multi>>(emptyList()) }
    var recoverChoices by remember { mutableStateOf<List<TransferConfig.Multi>?>(null) }
    val importLauncher = rememberImportLauncher { bundle ->
        val multi = bundle.multiConfigs
        if (multi.isEmpty()) {
            android.widget.Toast.makeText(
                context, importErrorMessage(context, ImportError.NoMatchingType), android.widget.Toast.LENGTH_LONG
            ).show()
        } else {
            importChoices = multi
        }
    }

    val resumeTick = rememberResumeTick()
    LaunchedEffect(resumeTick) {
        val store = SecureStore.get(context)
        if (!store.isConfigured) {
            notConnected = true
            isLoading = false
            return@LaunchedEffect
        }
        notConnected = false
        if (loaded) return@LaunchedEffect
        try {
            val client = HaApiClient(store.baseUrl!!, store.token!!)
            allEntities = client.listStatesByDomains(MULTI_ENTITY_DOMAINS.toSet()).sortedBy { it.friendlyName }
            val db = AppDatabase.get(context)
            slots = db.multiWidgetDao().getSlots(appWidgetId)
            showRefreshIcon = db.multiWidgetDao().get(appWidgetId)?.showRefreshIcon ?: true
            attrsByEntityId = allEntities.mapNotNull { entity ->
                db.entityStateDao().get(entity.entityId)?.attributesJson?.let { entity.entityId to it }
            }.toMap()
            // Soft-slettede widgets (fjernet, endnu ikke purged) inden for grace-perioden — kan
            // gendannes til denne widget. Ekskludér den aktuelle widget selv (defensivt).
            val now = System.currentTimeMillis()
            recoverable = db.multiWidgetDao().getSoftDeleted()
                .filter { it.appWidgetId != appWidgetId && it.removedAt != null && now - it.removedAt!! <= WIDGET_ORPHAN_GRACE_MILLIS }
                .map { multiTransferConfig(db.multiWidgetDao().getSlots(it.appWidgetId), it.showRefreshIcon) }
            loadError = null
            loaded = true
        } catch (e: Exception) {
            loadError = context.getString(R.string.load_entities_error, e.message ?: "")
        }
        isLoading = false
    }

    fun saveSlot(editIndex: Int?, draft: SlotDraft) {
        val newSlot = draft.toSlotEntity(appWidgetId, editIndex ?: slots.size) ?: return
        slots = if (editIndex == null) slots + newSlot
        else slots.toMutableList().also { it[editIndex] = newSlot }
        step = Step.ListScreen
    }

    if (notConnected) {
        NotConnectedGate(onOpenApp = {
            context.startActivity(Intent(context, dk.akait.hawidgets.MainActivity::class.java))
        })
        return
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
            onOpenAppSettings = {
                context.startActivity(
                    Intent(context, dk.akait.hawidgets.MainActivity::class.java)
                        .putExtra(dk.akait.hawidgets.MainActivity.EXTRA_OPEN_SETTINGS, true)
                )
            },
            onExport = {
                WidgetTransferIo.shareBundle(
                    context, singleConfigBundle(multiTransferConfig(slots, showRefreshIcon))
                )
            },
            onImport = { importLauncher.launch(TRANSFER_IMPORT_MIME_TYPES) },
            onRecover = if (recoverable.isNotEmpty()) ({ recoverChoices = recoverable }) else null,
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

        is Step.AppPicker -> AppPickerScreen(
            onSelected = { pkg ->
                step = Step.SlotEditor(s.editIndex, s.draft.copy(action = "OPEN_APP", packageName = pkg))
            },
            onBack = { step = Step.SlotEditor(s.editIndex, s.draft) },
        )

        is Step.SlotEditor -> SlotEditorScreen(
            draft = s.draft,
            isEditing = s.editIndex != null,
            attrsByEntityId = attrsByEntityId,
            onChangeDisplay = { step = Step.EntityPicker(PickerTarget.Display, s.editIndex, s.draft) },
            onChangeTarget = { step = Step.EntityPicker(PickerTarget.Action, s.editIndex, s.draft) },
            onChooseApp = { step = Step.AppPicker(s.editIndex, s.draft) },
            onActionChange = { newAction -> step = Step.SlotEditor(s.editIndex, s.draft.copy(action = newAction)) },
            onRangeInputModeChange = { mode -> step = Step.SlotEditor(s.editIndex, s.draft.copy(rangeInputMode = mode)) },
            onLabelChange = { newLabel -> step = Step.SlotEditor(s.editIndex, s.draft.copy(label = newLabel)) },
            onConfirmActionChange = { confirm -> step = Step.SlotEditor(s.editIndex, s.draft.copy(confirmAction = confirm)) },
            onDisplayPrecisionChange = { precision -> step = Step.SlotEditor(s.editIndex, s.draft.copy(displayPrecision = precision)) },
            onDatetimeFormatChange = { pattern -> step = Step.SlotEditor(s.editIndex, s.draft.copy(datetimeFormat = pattern)) },
            onShowIconChange = { showIcon -> step = Step.SlotEditor(s.editIndex, s.draft.copy(showIcon = showIcon)) },
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
            onSecondaryShowIconChange = { index, showIcon ->
                val updated = s.draft.secondaryEntities.toMutableList()
                updated[index] = updated[index].copy(showIcon = showIcon)
                step = Step.SlotEditor(s.editIndex, s.draft.copy(secondaryEntities = updated))
            },
            onSave = { saveSlot(s.editIndex, s.draft) },
            onBack = { step = Step.ListScreen },
        )
    }

    // Delt: multi-config → picker-post med rigtige navne (slot-label > friendly name > entity_id,
    // maks 2 + "+N") + domæne-ikon. Bruges af BÅDE fil-import og gendan-fjernet.
    fun multiPickerItems(configs: List<TransferConfig.Multi>): List<ImportPickerItem> = configs.map { multi ->
        val names = multi.slots.map { slot ->
            slot.label.ifBlank {
                allEntities.firstOrNull { it.entityId == slot.displayEntityId }?.friendlyName ?: slot.displayEntityId
            }
        }
        val title = when {
            names.isEmpty() -> multi.label
            names.size <= 2 -> names.joinToString(", ")
            else -> names.take(2).joinToString(", ") + " +${names.size - 2}"
        }
        ImportPickerItem(
            title = title,
            subtitle = context.getString(R.string.import_item_multi_subtitle, multi.slots.size),
            iconResId = domainIconResId(multi.slots.firstOrNull()?.displayDomain ?: ""),
        )
    }

    // Import: vælger over filens multi-configs → bekræft → erstat den nuværende in-memory opsætning.
    importChoices?.let { choices ->
        ImportPickerDialog(
            items = multiPickerItems(choices),
            onPick = { index -> pendingImport = choices[index]; importChoices = null },
            onDismiss = { importChoices = null },
        )
    }
    // Gendan fjernet: samme picker/bekræft/anvend-flow, blot fyldt fra soft-slettede DB-configs.
    recoverChoices?.let { choices ->
        ImportPickerDialog(
            items = multiPickerItems(choices),
            onPick = { index -> pendingImport = choices[index]; recoverChoices = null },
            onDismiss = { recoverChoices = null },
            titleRes = R.string.recover_removed_widget,
            countRes = R.plurals.recover_count,
        )
    }
    pendingImport?.let { chosen ->
        ConfirmReplaceDialog(
            onConfirm = {
                // Sæt den aktuelle widgets appWidgetId + fortløbende slotIndex på de importerede slots
                // (afsenderens appWidgetId/placeholder-0 kasseres). Gemmes via det eksisterende "Gem"-flow.
                slots = chosen.slots.mapIndexed { i, sl -> sl.copy(appWidgetId = appWidgetId, slotIndex = i) }
                showRefreshIcon = chosen.showRefreshIcon
                pendingImport = null
                android.widget.Toast.makeText(
                    context, context.getString(R.string.import_success), android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { pendingImport = null },
        )
    }
}
