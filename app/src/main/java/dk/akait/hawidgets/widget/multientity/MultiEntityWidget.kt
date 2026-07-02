package dk.akait.hawidgets.widget.multientity

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
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

private const val SLOT_SIZE_DP = 56

class MultiEntityWidget : GlanceAppWidget() {

    // resizeMode="none" i widget-info.xml → fast størrelse, ingen responsive buckets nødvendige.
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val db = AppDatabase.get(context)
        val initialTitle = db.multiWidgetDao().get(appWidgetId)?.title.orEmpty()
        val initialSlots = db.multiWidgetDao().getSlots(appWidgetId)
        // Preload states too (not just title+slots) — every entity tap re-invokes provideGlance
        // via WidgetUpdater.updateForEntity()'s explicit widget.update() call, on top of the
        // reactive Flow recomposition already triggered by the Room write. Without this preload,
        // that extra provideGlance call's first frame used an empty states map → every SlotBox
        // briefly rendered as "off"/"Henter status…" before the Flow's first emission caught up,
        // perceived as a visual "hop" on every click. Mirrors LightWidget's initialState preload.
        val initialStateIds = initialSlots.flatMap { listOf(it.displayEntityId, it.actionEntityId) }.distinct()
        val initialStates = initialStateIds.associateWith { entityId -> db.entityStateDao().get(entityId) }

        provideContent {
            val viewState by db.multiWidgetDao().observe(appWidgetId)
                .combine(db.multiWidgetDao().observeSlots(appWidgetId)) { cfg, slots ->
                    (cfg?.title.orEmpty()) to slots
                }
                .flatMapLatest { (title, slots) ->
                    statesFlow(db, slots).map { states -> Triple(title, slots, states) }
                }
                .collectAsState(
                    initial = Triple(initialTitle, initialSlots, initialStates)
                )
            val (title, slots, states) = viewState

            GlanceTheme {
                if (slots.isEmpty()) {
                    UnconfiguredWidgetContent(
                        context, appWidgetId, MultiEntityWidgetConfigActivity::class.java, R.drawable.ic_multi_entity,
                    )
                } else {
                    MultiEntityContent(context, title, slots, states)
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

@Composable
private fun MultiEntityContent(
    context: Context,
    title: String,
    slots: List<MultiWidgetSlotEntity>,
    states: Map<String, EntityStateEntity?>,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            for (slot in slots.sortedBy { it.slotIndex }) {
                SlotBox(context, slot, states[slot.displayEntityId], states[slot.actionEntityId])
            }
        }
    }
}

@Composable
private fun SlotBox(
    context: Context,
    slot: MultiWidgetSlotEntity,
    displayState: EntityStateEntity?,
    actionState: EntityStateEntity?,
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

    val baseModifier = GlanceModifier.size(SLOT_SIZE_DP.dp).background(bgColor).cornerRadius(16.dp)
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

class MultiEntityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MultiEntityWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
