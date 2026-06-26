package dk.akait.hawidgets.data

import dk.akait.hawidgets.data.db.EntityStateEntity
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
    )

    private val base get() = baseUrl.trimEnd('/')
    private val authHeader get() = "Bearer $token"

    suspend fun checkConnection(): Result = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/api/")
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
            .get()
            .build()
        try {
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> Result.Ok
                    401 -> Result.Error("Token afvist (401). Tjek dit long-lived token.")
                    else -> Result.Error("Uventet svar fra HA: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Result.Error("Kunne ikke nå HA: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun getState(entityId: String): EntityStateEntity? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/api/states/$entityId")
            .header("Authorization", authHeader)
            .get()
            .build()
        try {
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
            null
        }
    }

    suspend fun callService(
        domain: String,
        service: String,
        entityId: String,
        extraData: Map<String, Any> = emptyMap(),
    ): Result = withContext(Dispatchers.IO) {
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
        try {
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.Ok
                else Result.Error("HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Henter alle states og filtrerer på domæne — bruges til config-skærm. */
    suspend fun listStatesByDomain(domain: String): List<EntityBrief> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/api/states")
            .header("Authorization", authHeader)
            .get()
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val array = JSONArray(body)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val id = obj.getString("entity_id")
                        if (!id.startsWith("$domain.")) continue
                        val attrs = obj.optJSONObject("attributes")
                        add(EntityBrief(
                            entityId = id,
                            friendlyName = attrs?.optString("friendly_name")?.ifEmpty { null } ?: id,
                            state = obj.getString("state"),
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
