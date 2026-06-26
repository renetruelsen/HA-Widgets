package dk.akait.hawidgets.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Thin REST client for Home Assistant. M1 only needs a connection check. */
class HaApiClient(
    private val baseUrl: String,
    private val token: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    sealed interface Result {
        data object Ok : Result
        data class Error(val message: String) : Result
    }

    /** Validates base URL + token by hitting the authenticated `/api/` root. */
    suspend fun checkConnection(): Result = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
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
}
