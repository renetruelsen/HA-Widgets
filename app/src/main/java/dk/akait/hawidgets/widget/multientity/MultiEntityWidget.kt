package dk.akait.hawidgets.widget.multientity

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.EntityStateEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.RangeControlActivity
import dk.akait.hawidgets.widget.common.RefreshEntityAction
import dk.akait.hawidgets.widget.common.ToggleEntityAction
import dk.akait.hawidgets.widget.common.TriggerEntityAction
import dk.akait.hawidgets.widget.common.UnconfiguredWidgetContent
import dk.akait.hawidgets.widget.common.WidgetCompactLayout
import dk.akait.hawidgets.widget.common.domainIconResId
import dk.akait.hawidgets.widget.common.formatEntityState
import dk.akait.hawidgets.widget.common.friendlyNameFromJson
import dk.akait.hawidgets.widget.common.isActiveState
import dk.akait.hawidgets.widget.common.isStale
import dk.akait.hawidgets.worker.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject

// Ramme rundt om slot-rækken: fast (ikke tema-baseret) grå, 50% alpha — bevidst literal farve
// jf. UX-spec, ikke GlanceTheme.colors.surfaceVariant (se docs/widget-settings-spec.md §6).
private val FRAME_BACKGROUND = ColorProvider(Color(0x80808080))
private const val FRAME_PADDING_DP = 4
private const val SLOT_GAP_DP = 4
private const val MAX_SLOT_SIZE_DP = 56
private const val MIN_SLOT_SIZE_DP = 48 // Android tap-target-minimum — se UX-review §6.

class MultiEntityWidget : GlanceAppWidget() {

    // SizeMode.Exact (ikke Responsive med diskrete buckets som lys/climate/cover) fordi
    // slot-boksene skal følge den faktiske tildelte bredde kontinuerligt (elastisk N-boks-række),
    // ikke springe mellem få faste layouts. Se docs/widget-settings-spec.md §6.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val db = AppDatabase.get(context)
        val initialSlots = db.multiWidgetDao().getSlots(appWidgetId)
        // Preload states too (not just slots) — every entity tap re-invokes provideGlance
        // via WidgetUpdater.updateForEntity()'s explicit widget.update() call, on top of the
        // reactive Flow recomposition already triggered by the Room write. Without this preload,
        // that extra provideGlance call's first frame used an empty states map → every SlotBox
        // briefly rendered as "off"/"Henter status…" before the Flow's first emission caught up,
        // perceived as a visual "hop" on every click. Mirrors LightWidget's initialState preload.
        val initialStateIds = initialSlots.flatMap { listOf(it.displayEntityId, it.actionEntityId) }.distinct()
        val initialStates = initialStateIds.associateWith { entityId -> db.entityStateDao().get(entityId) }

        provideContent {
            val viewState by db.multiWidgetDao().observeSlots(appWidgetId)
                .flatMapLatest { slots -> statesFlow(db, slots).map { states -> slots to states } }
                .collectAsState(initial = initialSlots to initialStates)
            val (slots, states) = viewState

            GlanceTheme {
                if (slots.isEmpty()) {
                    UnconfiguredWidgetContent(
                        context, appWidgetId, MultiEntityWidgetConfigActivity::class.java, R.drawable.ic_multi_entity,
                    )
                } else {
                    MultiEntityContent(context, appWidgetId, slots, states)
                }
            }
        }
    }
}

private fun statesFlow(
    db: AppDatabase,
    slots: List<MultiWidgetSlotEntity>,
): Flow<Map<String, EntityStateEntity?>> {
    val ids = slots.flatMap { listOf(it.displayEntityId, it.actionEntityId) }.distinct()
    if (ids.isEmpty()) return flowOf(emptyMap())
    val flows = ids.map { id -> db.entityStateDao().observe(id) }
    return combine(flows) { arr -> ids.zip(arr.toList()).toMap() }
}

/** Resultat af bredde-sizing: hvor mange slots der reelt vises, ved hvilken boksbredde,
 * og om resten skal samles i en "+N"-overflow-badge (se docs/widget-settings-spec.md §6).
 * Bokse bruger deres NATURLIGE størrelse (MAX_SLOT_SIZE_DP) og strækkes ikke for at udfylde
 * ekstra tildelt bredde — rammen hugger i stedet om boksene (se MultiEntityContent), så der
 * ikke opstår luft mellem bokse og ramme. Bredden skrumpes kun ned mod 48dp-gulvet hvis der
 * IKKE er plads til alle slots ved naturlig størrelse. Boks-HØJDEN beregnes separat i
 * [MultiEntityContent] (se computeBoxHeight) — bredde og højde er bevidst afkoblede. */
private data class SlotLayout(val boxWidth: Dp, val visibleSlots: Int, val overflowCount: Int)

private fun computeSlotLayout(availableWidth: Dp, slotCount: Int): SlotLayout {
    if (slotCount <= 0) return SlotLayout(MAX_SLOT_SIZE_DP.dp, 0, 0)
    val usable = availableWidth - (FRAME_PADDING_DP * 2).dp
    val naturalWidthNeeded = (MAX_SLOT_SIZE_DP * slotCount + SLOT_GAP_DP * (slotCount - 1)).dp
    if (usable >= naturalWidthNeeded) {
        // Nok plads til alle ved naturlig 56dp-størrelse — ingen grund til at strække dem bredere.
        return SlotLayout(MAX_SLOT_SIZE_DP.dp, slotCount, 0)
    }
    val idealBox = (usable - ((slotCount - 1) * SLOT_GAP_DP).dp) / slotCount
    if (idealBox >= MIN_SLOT_SIZE_DP.dp) {
        return SlotLayout(idealBox, slotCount, 0)
    }
    // Ikke plads til alle ved 48dp-tilgængelighedsgulvet — vis så mange som får plads ved 48dp,
    // og saml resten i én overflow-badge (aldrig under 48dp, jf. UX-review).
    val perSlot = MIN_SLOT_SIZE_DP.dp + SLOT_GAP_DP.dp
    val fitAtMin = ((usable + SLOT_GAP_DP.dp) / perSlot).toInt().coerceAtLeast(1)
    if (fitAtMin >= slotCount) return SlotLayout(MIN_SLOT_SIZE_DP.dp, slotCount, 0)
    val visible = (fitAtMin - 1).coerceAtLeast(1)
    return SlotLayout(MIN_SLOT_SIZE_DP.dp, visible, slotCount - visible)
}

/** Boks-højden fylder ALTID den tildelte højde (intet loft) — kun et 48dp-gulv for
 * tilgængelighed. I modsætning til bredden behøver højden ikke være kvadratisk med boksen;
 * en højere-end-bred boks er accepteret for at undgå lodret luft i rammen. */
private fun computeBoxHeight(availableHeight: Dp): Dp =
    (availableHeight - (FRAME_PADDING_DP * 2).dp).coerceAtLeast(MIN_SLOT_SIZE_DP.dp)

@Composable
private fun MultiEntityContent(
    context: Context,
    appWidgetId: Int,
    slots: List<MultiWidgetSlotEntity>,
    states: Map<String, EntityStateEntity?>,
) {
    val sorted = slots.sortedBy { it.slotIndex }
    val size = LocalSize.current
    val layout = computeSlotLayout(size.width, sorted.size)
    val boxHeight = computeBoxHeight(size.height)

    // Ydre Box centrerer den faktiske ramme inden i widgettens fulde tildelte areal — rammen
    // selv har INGEN bredde-modifier, så den krymper til sit indhold (boksene) i stedet for at
    // fylde hele den tildelte bredde. Det fjerner det vandrette "luft" mellem bokse og ramme,
    // når launcheren giver mere plads end de N boksers naturlige bredde kræver.
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = GlanceModifier
                .background(FRAME_BACKGROUND)
                .cornerRadius(16.dp)
                .padding(FRAME_PADDING_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row {
                sorted.take(layout.visibleSlots).forEachIndexed { index, slot ->
                    if (index > 0) Spacer(modifier = GlanceModifier.width(SLOT_GAP_DP.dp))
                    SlotBox(context, slot, states[slot.displayEntityId], states[slot.actionEntityId], layout.boxWidth, boxHeight)
                }
                if (layout.overflowCount > 0) {
                    if (layout.visibleSlots > 0) Spacer(modifier = GlanceModifier.width(SLOT_GAP_DP.dp))
                    OverflowBadge(context, appWidgetId, layout.overflowCount, layout.boxWidth, boxHeight)
                }
            }
        }
    }
}

@Composable
private fun OverflowBadge(context: Context, appWidgetId: Int, count: Int, boxWidth: Dp, boxHeight: Dp) {
    // Badgen skal give ADGANG til de skjulte slots, ikke kun tælle dem — tryk åbner config,
    // hvor alle slots kan ses/redigeres (code-review-fund: uklikkelig badge = uopnåelige
    // entiteter indtil manuel resize).
    val intent = Intent(context, MultiEntityWidgetConfigActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    Box(
        modifier = GlanceModifier.width(boxWidth).height(boxHeight)
            .background(GlanceTheme.colors.surfaceVariant).cornerRadius(16.dp)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$count",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun SlotBox(
    context: Context,
    slot: MultiWidgetSlotEntity,
    displayState: EntityStateEntity?,
    actionState: EntityStateEntity?,
    boxWidth: Dp,
    boxHeight: Dp,
) {
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
    val statusBase = formatEntityState(slot.displayDomain, displayState?.state)
    val statusText = if (displayState != null && displayState.isStale()) "$statusBase ~" else statusBase

    val baseModifier = GlanceModifier.width(boxWidth).height(boxHeight).background(bgColor).cornerRadius(16.dp)
    val modifier = slotClickModifier(context, baseModifier, slot, actionState)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        WidgetCompactLayout(domainIconResId(slot.displayDomain), label, statusText, contentColor)
    }
}

private fun slotClickModifier(
    context: Context,
    base: GlanceModifier,
    slot: MultiWidgetSlotEntity,
    actionState: EntityStateEntity?,
): GlanceModifier {
    if (slot.action == "NONE") {
        return base.clickable(
            actionRunCallback<RefreshEntityAction>(
                actionParametersOf(RefreshEntityAction.entityIdKey to slot.displayEntityId)
            )
        )
    }
    if (actionState == null || actionState.state == "unavailable") return base
    return when (slot.action) {
        "TOGGLE" -> base.clickable(
            actionRunCallback<ToggleEntityAction>(
                actionParametersOf(
                    ToggleEntityAction.entityIdKey to slot.actionEntityId,
                    ToggleEntityAction.domainKey to slot.actionDomain,
                )
            )
        )
        "RANGE" -> {
            val attrs = try { JSONObject(actionState.attributesJson) } catch (_: Exception) { JSONObject() }
            val intent = Intent(context, RangeControlActivity::class.java).apply {
                putExtra(RangeControlActivity.EXTRA_ENTITY_ID, slot.actionEntityId)
                putExtra(RangeControlActivity.EXTRA_LABEL, slot.label.ifEmpty { slot.actionEntityId })
                putExtra(RangeControlActivity.EXTRA_DOMAIN, slot.actionDomain)
                putExtra(RangeControlActivity.EXTRA_CURRENT_VALUE, rangeCurrentValue(slot.actionDomain, actionState, attrs))
                putExtra(RangeControlActivity.EXTRA_IS_ON, actionState.state != "off" && actionState.state != "closed")
                putExtra(RangeControlActivity.EXTRA_MIN_VALUE, rangeMin(slot.actionDomain, attrs))
                putExtra(RangeControlActivity.EXTRA_MAX_VALUE, rangeMax(slot.actionDomain, attrs))
                if (slot.actionDomain == "number") {
                    putExtra(RangeControlActivity.EXTRA_UNIT_SUFFIX, attrs.optString("unit_of_measurement", ""))
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            base.clickable(actionStartActivity(intent))
        }
        else -> { // "TRIGGER"
            val service = if (slot.actionDomain == "automation") "trigger" else "turn_on"
            base.clickable(
                actionRunCallback<TriggerEntityAction>(
                    actionParametersOf(
                        TriggerEntityAction.entityIdKey to slot.actionEntityId,
                        TriggerEntityAction.domainKey to slot.actionDomain,
                        TriggerEntityAction.serviceKey to service,
                    )
                )
            )
        }
    }
}

private fun rangeCurrentValue(domain: String, state: EntityStateEntity, attrs: JSONObject): Int = when (domain) {
    "light" -> attrs.optInt("brightness", 255).let { (it * 100 / 255).coerceIn(0, 100) }
    "cover" -> attrs.optInt("current_position", if (state.state == "open") 100 else 0)
    "climate" -> attrs.optInt("temperature", 20)
    "number" -> state.state.toDoubleOrNull()?.toInt() ?: 0
    else -> 0
}

private fun rangeMin(domain: String, attrs: JSONObject): Int = when (domain) {
    "climate" -> attrs.optInt("min_temp", 16)
    "number" -> attrs.optDouble("min", 0.0).toInt()
    else -> 1
}

private fun rangeMax(domain: String, attrs: JSONObject): Int = when (domain) {
    "climate" -> attrs.optInt("max_temp", 30)
    "number" -> attrs.optDouble("max", 100.0).toInt()
    else -> 100
}

// 4 receivers, samme MultiEntityWidget()-instans — kun manifest-registrering + XML-footprint
// (multi_entity_2/3/4_widget_info.xml) differentierer varianterne. MultiEntityWidgetReceiver
// bevarer sit oprindelige class-/filnavn (bliver de facto "5-plads") for bagudkompatibilitet
// med allerede placerede widgets. Se docs/widget-settings-spec.md §6.
class MultiEntityWidget2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MultiEntityWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}

class MultiEntityWidget3Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MultiEntityWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}

class MultiEntityWidget4Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MultiEntityWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}

class MultiEntityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MultiEntityWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
