package dk.akait.hawidgets.widget.multientity

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.EntityStateEntity
import dk.akait.hawidgets.data.db.MultiWidgetEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.UnconfiguredWidgetContent
import dk.akait.hawidgets.widget.common.WidgetGlanceTheme
import dk.akait.hawidgets.worker.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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

            WidgetGlanceTheme(context) {
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

// Bevarer oprindeligt class-/filnavn for bagudkompatibilitet med allerede placerede widgets
// (v0.2.27: revert fra 4 varianter til én — se docs/widget-settings-spec.md §8).
class MultiEntityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MultiEntityWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SyncWorker.runNow(context)
    }
}
