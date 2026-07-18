package dk.rtr.hawidgets.data

import dk.rtr.hawidgets.data.db.EntityStateEntity
import dk.rtr.hawidgets.logging.RemoteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HaApiClient(
    private val baseUrl: String,
    private val token: String,
) {
    sealed interface Result {
        data object Ok : Result
        data class Error(val message: String) : Result
    }

    data class EntityBrief(
        val entityId: String,
        val friendlyName: String,
        val state: String,
        val domain: String = entityId.substringBefore('.'),
        val unit: String? = null,
    )

    private val base get() = baseUrl.trimEnd('/')
    private val authHeader get() = "Bearer $token"

    suspend fun checkConnection(): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$base/api/")
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> Result.Ok
                    401 -> Result.Error("Token afvist (401). Tjek dit long-lived token.")
                        .also { RemoteLogger.w("HA", it.message) }
                    else -> Result.Error("Uventet svar fra HA: HTTP ${response.code}")
                        .also { RemoteLogger.w("HA", it.message) }
                }
            }
        } catch (e: Exception) {
            val msg = "Kunne ikke nå HA: ${e.message ?: e.javaClass.simpleName}"
            RemoteLogger.w("HA", msg)
            Result.Error(msg)
        }
    }

    suspend fun getState(entityId: String): EntityStateEntity? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$base/api/states/$entityId")
                .header("Authorization", authHeader)
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                EntityStateEntity(
                    entityId = json.getString("entity_id"),
                    state = json.getString("state"),
                    attributesJson = json.optJSONObject("attributes")?.toString() ?: "{}",
                    lastUpdated = System.currentTimeMillis(),
                )
            }
        } catch (e: Exception) {
            RemoteLogger.w("HA", "getState($entityId) failed: ${e.message ?: e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Kald en service. [fast] = brug kort-timeout-klienten (kommando-path, så vi
     * aldrig hænger i et ANR-følsomt vindue). Default bruger baggrunds-klienten.
     */
    suspend fun callService(
        domain: String,
        service: String,
        entityId: String,
        extraData: Map<String, Any> = emptyMap(),
        fast: Boolean = false,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("entity_id", entityId)
                extraData.forEach { (k, v) -> put(k, v) }
            }
            val body = bodyJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$base/api/services/$domain/$service")
                .header("Authorization", authHeader)
                .post(body)
                .build()
            (if (fast) httpFast else http).newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.Ok
                else Result.Error("HTTP ${response.code}").also {
                    RemoteLogger.w("HA", "callService $domain.$service failed: ${it.message}")
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            RemoteLogger.w("HA", "callService $domain.$service exception: $msg")
            Result.Error(msg)
        }
    }

    /** Henter alle states og filtrerer på ét domæne — bruges til enkelt-entity widget-config. */
    suspend fun listStatesByDomain(domain: String): List<EntityBrief> = listStatesByDomains(setOf(domain))

    /** Henter alle states og filtrerer på flere domæner — bruges til multi-entity widget-config. */
    suspend fun listStatesByDomains(domains: Set<String>): List<EntityBrief> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$base/api/states")
                .header("Authorization", authHeader)
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val array = JSONArray(body)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val id = obj.getString("entity_id")
                        val entityDomain = id.substringBefore('.')
                        if (entityDomain !in domains) continue
                        val attrs = obj.optJSONObject("attributes")
                        add(EntityBrief(
                            entityId = id,
                            friendlyName = attrs?.optString("friendly_name")?.ifEmpty { null } ?: id,
                            state = obj.getString("state"),
                            domain = entityDomain,
                            unit = attrs?.optString("unit_of_measurement")?.ifEmpty { null },
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            RemoteLogger.w("HA", "listStatesByDomains failed: ${e.message ?: e.javaClass.simpleName}")
            emptyList()
        }
    }

    companion object {
        /** Baggrunds-klient: tålmodig (sync, config-load). */
        private val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        /** Kommando-klient: kort timeout så tryk aldrig hænger. */
        private val httpFast = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }
}
