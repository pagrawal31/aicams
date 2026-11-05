package com.aicams.viewer.webrtc

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import com.aicams.viewer.utils.Constants
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** WebSocket signaling only; audio/video remain on the peer-to-peer WebRTC connection. */
internal class P2PSignalingClient(
    baseUrl: String,
    role: String,
    deviceId: String,
    private val initialMessage: JSONObject? = null,
    private val onMessage: (JSONObject) -> Unit,
    private val onState: (String) -> Unit
) : SignalingTransport {
    private val closed = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    private val reconnectScheduled = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)
    private val outboundMessages = ConcurrentLinkedQueue<String>()
    private val reconnectExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "AICAMsSignalingReconnect").apply { isDaemon = true }
    }
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val request: Request
    @Volatile private var webSocket: WebSocket? = null

    init {
        val wsUrl = toWebSocketUrl(baseUrl)
        val encodedRole = URLEncoder.encode(role, StandardCharsets.UTF_8.toString())
        val encodedDeviceId = URLEncoder.encode(deviceId, StandardCharsets.UTF_8.toString())
        request = Request.Builder()
            .url("$wsUrl/p2p/signaling?role=$encodedRole&deviceId=$encodedDeviceId")
            .apply {
                Constants.SIGNALING_TOKEN.takeIf(String::isNotBlank)?.let { token ->
                    header("Authorization", "Bearer $token")
                }
            }
            .build()
        connect()
    }

    private fun connect() {
        if (closed.get()) return
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closed.get()) {
                    webSocket.close(1000, "screen stopped")
                    return
                }
                val activeSocket = this@P2PSignalingClient.webSocket
                if (activeSocket != null && activeSocket !== webSocket) {
                    webSocket.close(1000, "replaced connection")
                    return
                }
                this@P2PSignalingClient.webSocket = webSocket
                connected.set(true)
                reconnectAttempts.set(0)
                reconnectScheduled.set(false)
                onState("signaling connected")
                initialMessage?.let { webSocket.send(it.toString()) }
                flushOutboundMessages(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (closed.get()) return
                try {
                    onMessage(JSONObject(text))
                } catch (_: Exception) {
                    onState("invalid signaling message")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleDisconnect(webSocket, "signaling disconnected; reconnecting")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect(webSocket, "signaling disconnected; reconnecting")
            }
        })
        webSocket = socket
    }

    private fun handleDisconnect(disconnectedSocket: WebSocket, state: String) {
        if (closed.get() || webSocket !== disconnectedSocket) return
        connected.set(false)
        webSocket = null
        onState(state)
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (closed.get() || !reconnectScheduled.compareAndSet(false, true)) return
        val attempt = reconnectAttempts.getAndIncrement().coerceAtMost(5)
        val delaySeconds = (1L shl attempt).coerceAtMost(MAX_RECONNECT_DELAY_SECONDS)
        reconnectExecutor.schedule({
            reconnectScheduled.set(false)
            connect()
        }, delaySeconds, TimeUnit.SECONDS)
    }

    private fun flushOutboundMessages(socket: WebSocket) {
        while (!closed.get() && connected.get()) {
            val message = outboundMessages.peek() ?: return
            if (!socket.send(message)) return
            outboundMessages.poll()
        }
    }

    override fun send(message: JSONObject): Boolean {
        if (closed.get()) return false
        if (outboundMessages.size >= MAX_QUEUED_MESSAGES) outboundMessages.poll()
        outboundMessages.add(message.toString())
        webSocket?.takeIf { connected.get() }?.let(::flushOutboundMessages)
        return true
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        connected.set(false)
        webSocket?.close(1000, "screen stopped")
        webSocket = null
        outboundMessages.clear()
        reconnectExecutor.shutdownNow()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    companion object {
        private const val MAX_RECONNECT_DELAY_SECONDS = 30L
        private const val MAX_QUEUED_MESSAGES = 100

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
