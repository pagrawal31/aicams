package com.aicams.viewer.webrtc

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** WebSocket signaling only; audio/video remain on the peer-to-peer WebRTC connection. */
internal class P2PSignalingClient(
    baseUrl: String,
    role: String,
    deviceId: String,
    private val initialMessage: JSONObject? = null,
    private val onMessage: (JSONObject) -> Unit,
    private val onState: (String) -> Unit
) : SignalingTransport {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val webSocket: WebSocket

    init {
        val wsUrl = toWebSocketUrl(baseUrl)
        val encodedRole = URLEncoder.encode(role, StandardCharsets.UTF_8.toString())
        val encodedDeviceId = URLEncoder.encode(deviceId, StandardCharsets.UTF_8.toString())
        val request = Request.Builder()
            .url("$wsUrl/p2p/signaling?role=$encodedRole&deviceId=$encodedDeviceId")
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onState("signaling connected")
                initialMessage?.let { webSocket.send(it.toString()) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    onMessage(JSONObject(text))
                } catch (_: Exception) {
                    onState("invalid signaling message")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onState("signaling error: ${t.message ?: "connection failed"}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onState("signaling closed")
            }
        })
    }

    override fun send(message: JSONObject): Boolean = webSocket.send(message.toString())

    override fun close() {
        webSocket.close(1000, "screen stopped")
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        fun toWebSocketUrl(baseUrl: String): String {
            val normalized = baseUrl.trimEnd('/')
            return when {
                normalized.startsWith("https://") -> "wss://${normalized.removePrefix("https://") }"
                normalized.startsWith("http://") -> "ws://${normalized.removePrefix("http://") }"
                normalized.startsWith("wss://") || normalized.startsWith("ws://") -> normalized
                else -> "ws://$normalized"
            }
        }
    }
}
