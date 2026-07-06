package dk.akait.hawidgets.widget.multientity

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.EntityStateEntity
import dk.akait.hawidgets.data.db.MultiWidgetEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.DateTimeControlActivity
import dk.akait.hawidgets.widget.common.defaultShowValueFor
import dk.akait.hawidgets.widget.common.RangeControlActivity
import dk.akait.hawidgets.widget.common.TextControlActivity
import dk.akait.hawidgets.widget.common.RefreshEntityAction
import dk.akait.hawidgets.widget.common.ToggleEntityAction
import dk.akait.hawidgets.widget.common.TriggerEntityAction
import dk.akait.hawidgets.widget.common.UnconfiguredWidgetContent
import dk.akait.hawidgets.widget.common.domainIconResId
import dk.akait.hawidgets.widget.common.formatEntityState
import dk.akait.hawidgets.widget.common.friendlyNameFromJson
import dk.akait.hawidgets.widget.common.unitFromJson
import dk.akait.hawidgets.widget.common.isActiveState
import dk.akait.hawidgets.widget.common.isStale
import dk.akait.hawidgets.worker.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject

// Ramme rundt om hele slot-listen — tema-/dag-nat-baseret (IKKE længere en hardcodet grå
// literal, jf. v0.2.26-code-review-fund om kontrast-risiko ved at blande en fast farve med
// tema-baserede chip-farver). Lav alpha, så tapetet stadig anes svagt igennem — "transparent
// ramme" jf. brugerens eget ord, ikke en solid udfyldning.
private val FRAME_BACKGROUND = ColorProvider(
    day = Color(0x1F1C1B1F),
    night = Color(0x1FE6E1E5),
)
internal const val FRAME_PADDING_DP = 4
internal const val ROW_GAP_DP = 4
internal const val CHIP_GAP_DP = 4
internal const val REFRESH_STRIP_HEIGHT_DP = 24

class MultiEntityWidget : GlanceAppWidget() {

    // SizeMode.Exact: sikkert her fordi INGEN komponeret indhold læser LocalSize.current —
    // ramme, LazyColumn og rækker bruger alle almindelige fillMaxSize/fillMaxWidth-modifiers med
    // naturlig (wrap-content) rækkehøjde. Android komponerer under Exact altid BÅDE en portræt- og
    // en landskabs-udgave (RemoteViews(landscape, portrait)) og lader launcheren vælge ud fra
    // Configuration-orientering — på Galaxy S23 + Nova Launcher blev landskabs-udgaven konsekvent
    // vist SELV I PORTRÆT, men det er kun synligt/skadeligt når de to udgaver rent faktisk kan
    // afvige (dvs. når indhold afhænger af LocalSize). Genindfør IKKE en LocalSize-baseret
    // rækkehøjde uden at gen-teste dette scenarie på en Nova-enhed.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val db = AppDatabase.get(context)
        val initialConfig = db.multiWidgetDao().get(appWidgetId)
        val initialSlots = db.multiWidgetDao().getSlots(appWidgetId)
        // Preload states too (not just slots) — every entity tap re-invokes provideGlance
        // via WidgetUpdater.updateForEntity()'s explicit widget.update() call, on top of the
        // reactive Flow recomposition already triggered by the Room write. Without this preload,
        // that extra provideGlance call's first frame used an empty states map → every SlotRow
        // briefly rendered as "off"/"Henter status…" before the Flow's first emission caught up,
        // perceived as a visual "hop" on every click. Mirrors LightWidget's initialState preload.
        val initialStateIds = initialSlots.flatMap { it.allEntityIds() }.distinct()
        val initialStates = initialStateIds.associateWith { entityId -> db.entityStateDao().get(entityId) }

        provideContent {
            val viewState by combine(
                db.multiWidgetDao().observe(appWidgetId),
                db.multiWidgetDao().observeSlots(appWidgetId)
                    .flatMapLatest { slots -> statesFlow(db, slots).map { states -> slots to states } },
            ) { config, (slots, states) -> MultiWidgetViewState(config, slots, states) }
                .collectAsState(initial = MultiWidgetViewState(initialConfig, initialSlots, initialStates))
            val (config, slots, states) = viewState
            val showRefreshIcon = config?.showRefreshIcon ?: true

            GlanceTheme {
                if (slots.isEmpty()) {
                    UnconfiguredWidgetContent(
                        context, appWidgetId, MultiEntityWidgetConfigActivity::class.java, R.drawable.ic_multi_entity,
                    )
                } else {
                    MultiEntityContent(context, slots, states, showRefreshIcon)
                }
            }
        }
    }
}

private data class MultiWidgetViewState(
    val config: MultiWidgetEntity?,
    val slots: List<MultiWidgetSlotEntity>,
    val states: Map<String, EntityStateEntity?>,
)

/** Alle entity-id'er en slot kan referere (visning/handling for hoved-entiteten + op til 3
 * sekundær-chips) — bruges til at afgøre hvilke entiteter der skal observeres/præloades. */
internal fun MultiWidgetSlotEntity.allEntityIds(): List<String> = listOfNotNull(
    displayEntityId, actionEntityId,
    secondary1DisplayEntityId, secondary1ActionEntityId,
    secondary2DisplayEntityId, secondary2ActionEntityId,
    secondary3DisplayEntityId, secondary3ActionEntityId,
)

private fun statesFlow(
    db: AppDatabase,
    slots: List<MultiWidgetSlotEntity>,
): Flow<Map<String, EntityStateEntity?>> {
    val ids = slots.flatMap { it.allEntityIds() }.distinct()
    if (ids.isEmpty()) return flowOf(emptyMap())
    val flows = ids.map { id -> db.entityStateDao().observe(id) }
    return combine(flows) { arr -> ids.zip(arr.toList()).toMap() }
}

/** En konfigureret sekundær-chip klar til rendering — null hvis den slot-plads er tom. */
private data class SecondaryChipData(
    val displayEntityId: String,
    val displayDomain: String,
    val actionEntityId: String,
    val actionDomain: String,
    val action: String,
    val showValue: Boolean,
)

private fun secondaryChipData(
    displayId: String?, displayDomain: String?,
    actionId: String?, actionDomain: String?,
    action: String?, showValue: Boolean?,
): SecondaryChipData? {
    if (displayId == null || displayDomain == null || actionId == null || actionDomain == null || action == null) return null
    return SecondaryChipData(displayId, displayDomain, actionId, actionDomain, action, showValue ?: defaultShowValueFor(action))
}

private fun MultiWidgetSlotEntity.secondaryChips(): List<SecondaryChipData> = listOfNotNull(
    secondaryChipData(secondary1DisplayEntityId, secondary1DisplayDomain, secondary1ActionEntityId, secondary1ActionDomain, secondary1Action, secondary1ShowValue),
    secondaryChipData(secondary2DisplayEntityId, secondary2DisplayDomain, secondary2ActionEntityId, secondary2ActionDomain, secondary2Action, secondary2ShowValue),
    secondaryChipData(secondary3DisplayEntityId, secondary3DisplayDomain, secondary3ActionEntityId, secondary3ActionDomain, secondary3Action, secondary3ShowValue),
)

@Composable
private fun MultiEntityContent(
    context: Context,
    slots: List<MultiWidgetSlotEntity>,
    states: Map<String, EntityStateEntity?>,
    showRefreshIcon: Boolean,
) {
    val sorted = slots.sortedBy { it.slotIndex }

    // Rammen fylder hele det tildelte areal (fillMaxSize) — ved oversize efterlades tomrum
    // under listen/stripen i stedet for at strække indholdet (bevidst accepteret, se
    // brainstorm-konklusionen). LazyColumn scroller allerede ved undersize/overflow.
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(FRAME_BACKGROUND)
            .cornerRadius(16.dp)
            .padding(FRAME_PADDING_DP.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(sorted, itemId = { it.slotIndex.toLong() }) { slot ->
                    Column {
                        SlotRow(context, slot, states)
                        Spacer(modifier = GlanceModifier.height(ROW_GAP_DP.dp))
                    }
                }
            }
            if (showRefreshIcon) {
                RefreshStrip(context)
            }
        }
    }
}

@Composable
private fun RefreshStrip(context: Context) {
    // Hele rækken (ikke kun ikonet) er klikbar — et 16dp-bredt hit-areal alene ville være for
    // lille at ramme pålideligt i en kun 24dp høj bjælke.
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(REFRESH_STRIP_HEIGHT_DP.dp)
            .clickable(actionRunCallback<RefreshEntityAction>(actionParametersOf())),
        horizontalAlignment = Alignment.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = context.getString(R.string.multi_entity_refresh_all),
            modifier = GlanceModifier.size(16.dp).padding(end = 4.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun SlotRow(
    context: Context,
    slot: MultiWidgetSlotEntity,
    states: Map<String, EntityStateEntity?>,
) {
    val displayState = states[slot.displayEntityId]
    val actionState = states[slot.actionEntityId]
    val isUnavailable = displayState?.state == "unavailable"
    val isActive = displayState != null && isActiveState(slot.displayDomain, displayState.state)

    val bgColor = when {
        isUnavailable -> GlanceTheme.colors.errorContainer
        isActive -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.surfaceVariant
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        isActive -> GlanceTheme.colors.onPrimary
        else -> GlanceTheme.colors.onSurfaceVariant
    }

    val label = slot.label.ifEmpty {
        friendlyNameFromJson(displayState?.attributesJson ?: "{}") ?: slot.displayEntityId
    }
    val statusBase = formatEntityState(
        slot.displayDomain, displayState?.state,
        displayState?.attributesJson?.let { unitFromJson(it) },
    )
    val statusText = if (displayState != null && displayState.isStale()) "$statusBase ~" else statusBase

    val rowModifier = clickModifier(
        context = context,
        base = GlanceModifier.fillMaxWidth().background(bgColor).cornerRadius(12.dp).padding(8.dp),
        action = slot.action,
        actionEntityId = slot.actionEntityId,
        actionDomain = slot.actionDomain,
        refreshEntityId = slot.displayEntityId,
        rangeLabel = label,
        actionState = actionState,
    )

    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(domainIconResId(slot.displayDomain)),
            contentDescription = label,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(label, style = TextStyle(color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Text(statusText, style = TextStyle(color = contentColor, fontSize = 11.sp), maxLines = 1)
        }
        val chips = slot.secondaryChips()
        if (chips.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                chips.forEachIndexed { index, chip ->
                    if (index > 0) Spacer(modifier = GlanceModifier.width(CHIP_GAP_DP.dp))
                    SecondaryChip(context, chip, states)
                }
            }
        }
    }
}

@Composable
private fun SecondaryChip(
    context: Context,
    chip: SecondaryChipData,
    states: Map<String, EntityStateEntity?>,
) {
    val displayState = states[chip.displayEntityId]
    val actionState = states[chip.actionEntityId]
    val isUnavailable = displayState?.state == "unavailable"
    val isActive = displayState != null && isActiveState(chip.displayDomain, displayState.state)
    val isInfo = chip.action == "NONE"
    // Brugervalgt i config-UI'en (default via defaultShowValueFor, se secondaryChipData) —
    // ikke hardcodet til handlingstypen, så brugeren selv kan vise/skjule værditeksten pr. chip.
    val showsValueText = chip.showValue

    val bgColor = when {
        isUnavailable -> GlanceTheme.colors.errorContainer
        isInfo -> GlanceTheme.colors.surfaceVariant
        isActive -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.surfaceVariant
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        isInfo -> GlanceTheme.colors.onSurfaceVariant
        isActive -> GlanceTheme.colors.onPrimary
        else -> GlanceTheme.colors.onSurfaceVariant
    }
    val label = friendlyNameFromJson(displayState?.attributesJson ?: "{}") ?: chip.displayEntityId

    // Eksplicit 48dp højde — Android-tilgængelighedens tap-target-minimum. Uden dette var
    // chippens reelle klik-areal kun icon+padding høj (~22dp), da den (i modsætning til
    // hoved-rækken) ikke får sin højde "gratis" fra en flerlinjet label-kolonne ved siden af.
    val chipModifier = clickModifier(
        context = context,
        base = GlanceModifier.background(bgColor).cornerRadius(10.dp).height(48.dp).padding(horizontal = 6.dp),
        action = chip.action,
        actionEntityId = chip.actionEntityId,
        actionDomain = chip.actionDomain,
        refreshEntityId = chip.displayEntityId,
        rangeLabel = label,
        actionState = actionState,
    )

    Row(modifier = chipModifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(domainIconResId(chip.displayDomain)),
            contentDescription = label,
            modifier = GlanceModifier.size(14.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
        if (showsValueText) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = formatEntityState(
                    chip.displayDomain, displayState?.state,
                    displayState?.attributesJson?.let { unitFromJson(it) },
                ),
                style = TextStyle(color = contentColor, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

/** Fælles klik-håndtering for både hoved-rækken og sekundær-chips: NONE → opdatér kun
 * [refreshEntityId]; ellers TOGGLE/RANGE/TRIGGER på ([actionEntityId], [actionDomain]) — kan
 * være en anden entitet end den der vises (se designbeslutning i docs/widget-settings-spec.md §9). */
private fun clickModifier(
    context: Context,
    base: GlanceModifier,
    action: String,
    actionEntityId: String,
    actionDomain: String,
    refreshEntityId: String,
    rangeLabel: String,
    actionState: EntityStateEntity?,
): GlanceModifier {
    if (action == "NONE") {
        return base.clickable(
            actionRunCallback<RefreshEntityAction>(
                actionParametersOf(RefreshEntityAction.entityIdKey to refreshEntityId)
            )
        )
    }
    if (actionState == null || actionState.state == "unavailable") return base
    return when (action) {
        "TOGGLE" -> base.clickable(
            actionRunCallback<ToggleEntityAction>(
                actionParametersOf(
                    ToggleEntityAction.entityIdKey to actionEntityId,
                    ToggleEntityAction.domainKey to actionDomain,
                )
            )
        )
        "RANGE" -> {
            val attrs = try { JSONObject(actionState.attributesJson) } catch (_: Exception) { JSONObject() }
            val intent = Intent(context, RangeControlActivity::class.java).apply {
                putExtra(RangeControlActivity.EXTRA_ENTITY_ID, actionEntityId)
                putExtra(RangeControlActivity.EXTRA_LABEL, rangeLabel)
                putExtra(RangeControlActivity.EXTRA_DOMAIN, actionDomain)
                putExtra(RangeControlActivity.EXTRA_IS_ON, actionState.state != "off" && actionState.state != "closed")
                // Sendes som præcise (decimal) værdier — number/input_number kan have en
                // fraktioneret state/step (fx 21.5), som ikke må afrundes væk.
                putExtra(RangeControlActivity.EXTRA_CURRENT_VALUE_PRECISE, rangeCurrentValue(actionDomain, actionState, attrs))
                putExtra(RangeControlActivity.EXTRA_MIN_VALUE_PRECISE, rangeMin(actionDomain, attrs))
                putExtra(RangeControlActivity.EXTRA_MAX_VALUE_PRECISE, rangeMax(actionDomain, attrs))
                if (actionDomain == "number" || actionDomain == "input_number") {
                    putExtra(RangeControlActivity.EXTRA_UNIT_SUFFIX, attrs.optString("unit_of_measurement", ""))
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            base.clickable(actionStartActivity(intent))
        }
        "TEXT" -> {
            val attrs = try { JSONObject(actionState.attributesJson) } catch (_: Exception) { JSONObject() }
            val intent = Intent(context, TextControlActivity::class.java).apply {
                putExtra(TextControlActivity.EXTRA_ENTITY_ID, actionEntityId)
                putExtra(TextControlActivity.EXTRA_LABEL, rangeLabel)
                putExtra(TextControlActivity.EXTRA_CURRENT_VALUE, actionState.state)
                putExtra(TextControlActivity.EXTRA_MAX_LENGTH, attrs.optInt("max", 255))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            base.clickable(actionStartActivity(intent))
        }
        "DATETIME" -> {
            val attrs = try { JSONObject(actionState.attributesJson) } catch (_: Exception) { JSONObject() }
            val intent = Intent(context, DateTimeControlActivity::class.java).apply {
                putExtra(DateTimeControlActivity.EXTRA_ENTITY_ID, actionEntityId)
                putExtra(DateTimeControlActivity.EXTRA_HAS_DATE, attrs.optBoolean("has_date", true))
                putExtra(DateTimeControlActivity.EXTRA_HAS_TIME, attrs.optBoolean("has_time", true))
                putExtra(DateTimeControlActivity.EXTRA_CURRENT_VALUE, actionState.state)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            base.clickable(actionStartActivity(intent))
        }
        else -> { // "TRIGGER"
            val service = when (actionDomain) {
                "automation" -> "trigger"
                "input_button" -> "press"
                else -> "turn_on" // scene, script
            }
            base.clickable(
                actionRunCallback<TriggerEntityAction>(
                    actionParametersOf(
                        TriggerEntityAction.entityIdKey to actionEntityId,
                        TriggerEntityAction.domainKey to actionDomain,
                        TriggerEntityAction.serviceKey to service,
                    )
                )
            )
        }
    }
}

private fun rangeCurrentValue(domain: String, state: EntityStateEntity, attrs: JSONObject): Double = when (domain) {
    "light" -> attrs.optInt("brightness", 255).let { (it * 100 / 255).coerceIn(0, 100) }.toDouble()
    "cover" -> attrs.optInt("current_position", if (state.state == "open") 100 else 0).toDouble()
    "climate" -> attrs.optInt("temperature", 20).toDouble()
    // Bevarer decimaler (fx 21.5) i stedet for at afrunde til et heltal — number/input_number
    // kan have en fraktioneret step.
    "number", "input_number" -> state.state.toDoubleOrNull() ?: 0.0
    else -> 0.0
}

private fun rangeMin(domain: String, attrs: JSONObject): Double = when (domain) {
    "climate" -> attrs.optInt("min_temp", 16).toDouble()
    "number", "input_number" -> attrs.optDouble("min", 0.0)
    else -> 1.0
}

private fun rangeMax(domain: String, attrs: JSONObject): Double = when (domain) {
    "climate" -> attrs.optInt("max_temp", 30).toDouble()
    "number", "input_number" -> attrs.optDouble("max", 100.0)
    else -> 100.0
}

// Bevarer oprindeligt class-/filnavn for bagudkompatibilitet med allerede placerede widgets
// (v0.2.27: revert fra 4 varianter til én — se docs/widget-settings-spec.md §8).
class MultiEntityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MultiEntityWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
