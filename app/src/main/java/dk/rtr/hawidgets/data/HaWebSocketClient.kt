package dk.rtr.hawidgets.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class DashboardInfo(val urlPath: String, val title: String)

/**
 * Minimal HA WebSocket client used to fetch the list of dashboards for the
 * widget configuration picker.
 */
class HaWebSocketClient(
    baseUrl: String,
    private val token: String,
) {
    private val wsUrl = baseUrl.trimEnd('/')
        .replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://") + "/api/websocket"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Returns the user dashboards plus a synthetic default ("Overblik"). */
    suspend fun listDashboards(): Result<List<DashboardInfo>> =
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url(wsUrl).build()
            var resumed = false
            val ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val msg = JSONObject(text)
                        when (msg.optString("type")) {
                            "auth_required" -> webSocket.send(
                                JSONObject().put("type", "auth").put("access_token", token).toString()
                            )
                            "auth_invalid" -> finish(webSocket, Result.failure(IllegalStateException("auth_invalid")))
                            "auth_ok" -> webSocket.send(
                                JSONObject().put("id", 1).put("type", "lovelace/dashboards/list").toString()
                            )
                            "result" -> if (msg.optInt("id") == 1) {
                                if (msg.optBoolean("success")) {
                                    finish(webSocket, Result.success(parse(msg.optJSONArray("result"))))
                                } else {
                                    finish(webSocket, Result.failure(IllegalStateException("list failed")))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        finish(webSocket, Result.failure(e))
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    finish(webSocket, Result.failure(t))
                }

                private fun finish(webSocket: WebSocket, result: Result<List<DashboardInfo>>) {
                    if (resumed) return
                    resumed = true
                    webSocket.close(1000, null)
                    if (cont.isActive) cont.resume(result)
                }
            })
            cont.invokeOnCancellation { ws.cancel() }
        }

    private fun parse(arr: JSONArray?): List<DashboardInfo> {
        val list = mutableListOf(DashboardInfo("lovelace", "Overblik (standard)"))
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val d = arr.optJSONObject(i) ?: continue
                val urlPath = d.optString("url_path", "")
                if (urlPath.isNotBlank()) {
                    list.add(DashboardInfo(urlPath, d.optString("title", urlPath)))
                }
            }
        }
        return list
    }
}
