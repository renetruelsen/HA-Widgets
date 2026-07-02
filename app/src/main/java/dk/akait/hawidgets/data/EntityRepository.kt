package dk.akait.hawidgets.data

import android.content.Context
import dk.akait.hawidgets.data.db.AppDatabase
import dk.akait.hawidgets.data.db.EntityStateEntity
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Eneste ejer af al HA-synkronisering (pull + push) og widget-fan-out.
 *
 * Vigtigt om udførelseskontekst:
 * - **Tryk-handleren** ([dk.akait.hawidgets.widget.light.ToggleLightAction]) kører i en
 *   BroadcastReceiver med hård ~10s ANR-grænse, MEN et tryk vækker appen straks (pålideligt,
 *   modsat baggrunds-WorkManager der udskydes i timevis på fx Samsung One UI). Derfor kører
 *   [command] netværket dér — men med kort-timeout-klient + hård [COMMAND_TIMEOUT_MS]-grænse.
 * - Vi bruger `turn_on`/`turn_off` (ikke `toggle`), så det optimistiske gæt ER det kommanderede
 *   resultat = sandheden. Ingen confirm-poll nødvendig.
 *
 * Room er sandhedskilden; efter enhver skrivning kaldes [WidgetUpdater] som fan-out
 * til alle widgets der viser entiteten.
 */
object EntityRepository {

    /** Hård loft på kommando-kald så broadcast-vinduet aldrig ANR'er. */
    private const val COMMAND_TIMEOUT_MS = 8_000L

    private fun client(context: Context): HaApiClient? {
        val store = SecureStore.get(context)
        val base = store.baseUrl ?: return null
        val token = store.token ?: return null
        return HaApiClient(base, token)
    }

    // ── Pull (baggrund, tålmodig klient) ────────────────────────────────────

    /** Pull: hent én entity fra HA, skriv til Room, fan-out. */
    suspend fun refresh(context: Context, entityId: String): EntityStateEntity? {
        val api = client(context) ?: return null
        val state = api.getState(entityId) ?: return null
        AppDatabase.get(context).entityStateDao().upsert(state)
        WidgetUpdater.updateForEntity(context, entityId)
        return state
    }

    /** Pull alle konfigurerede entiteter (SyncWorker). Returnerer false hvis nogen fejlede. */
    suspend fun refreshAll(context: Context): Boolean {
        val api = client(context) ?: return true
        val db = AppDatabase.get(context)
        val multiDao = db.multiWidgetDao()
        val ids = (
            db.entityWidgetDao().allEntityIds() +
                multiDao.allDisplayEntityIds() +
                multiDao.allActionEntityIds()
            ).distinct()
        var allOk = true
        for (id in ids) {
            val state = api.getState(id)
            if (state != null) {
                db.entityStateDao().upsert(state)
                WidgetUpdater.updateForEntity(context, id)
            } else {
                allOk = false
            }
        }
        return allOk
    }

    // ── Push (tryk → optimistisk + kort netværkskald, alt i broadcast) ───────

    /**
     * Lokal optimistisk opdatering — INGEN netværk. UI føles øjeblikkeligt.
     * Returnerer tidligere state. Kan også bruges separat.
     */
    suspend fun applyOptimistic(context: Context, entityId: String, targetState: String): EntityStateEntity? {
        val dao = AppDatabase.get(context).entityStateDao()
        val prev = dao.get(entityId) ?: return null
        dao.upsert(prev.copy(state = targetState, lastUpdated = System.currentTimeMillis()))
        WidgetUpdater.updateForEntity(context, entityId)
        return prev
    }

    /**
     * Kommando fra tryk-handler. Optimistisk visning straks, derefter ét kort service-kald
     * (kort-timeout-klient + [COMMAND_TIMEOUT_MS]-loft → aldrig ANR). Fejl → restaurér
     * [fromState] + markér stale (HA = facit, intet forkert gæt). Best-effort: ingen retry.
     */
    suspend fun command(
        context: Context,
        domain: String,
        service: String,
        entityId: String,
        targetState: String,
        fromState: String?,
    ): Boolean {
        applyOptimistic(context, entityId, targetState)
        val api = client(context) ?: return false
        val result = withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
            api.callService(domain, service, entityId, fast = true)
        }
        if (result !is HaApiClient.Result.Ok) {
            markStale(context, entityId, restoreState = fromState)
            return false
        }
        return true
    }

    /**
     * Markér entity stale (lastUpdated = 0 → widget viser "~" og deaktiverer tap).
     * [restoreState] != null sætter state tilbage til sidst-kendte rigtige værdi, så vi
     * ikke viser et forkert optimistisk gæt. Sandheden hentes ved næste sync/reconnect.
     */
    suspend fun markStale(context: Context, entityId: String, restoreState: String? = null) {
        val dao = AppDatabase.get(context).entityStateDao()
        val cur = dao.get(entityId) ?: return
        dao.upsert(cur.copy(state = restoreState ?: cur.state, lastUpdated = 0L))
        WidgetUpdater.updateForEntity(context, entityId)
    }
}
