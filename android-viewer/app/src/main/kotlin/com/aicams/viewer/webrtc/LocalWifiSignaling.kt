package com.aicams.viewer.webrtc

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AICAMsLocalSignaling"
private const val SERVICE_TYPE = "_aicams-p2p._tcp."
private const val SERVICE_NAME = "AICAMsCamera"

/** Camera-side local signaling: advertises a TCP JSON-lines socket via Android NSD/mDNS. */
internal class LocalWifiSignalingServer(
    context: Context,
    private val onMessage: (JSONObject) -> Unit,
    private val onState: (String) -> Unit
) : SignalingTransport {
    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val running = AtomicBoolean(false)
    private val outbound = LinkedBlockingQueue<String>()
    private val writerExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AICAMsLocalSignalServerWriter").apply { isDaemon = true }
    }
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var clientSocket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        Thread({
            try {
                val listener = ServerSocket(0)
                listener.reuseAddress = true
                serverSocket = listener
                registerService(listener.localPort)
                onState("advertising local camera on Wi-Fi")
                while (running.get()) {
                    val socket = listener.accept()
                    if (!running.get()) {
                        socket.close()
                        break
                    }
                    if (clientSocket?.isConnected == true && clientSocket?.isClosed == false) {
                        socket.close()
                        continue
                    }
                    outbound.clear()
                    clientSocket = socket
                    writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                    writerExecutor.execute { drainOutbound(socket) }
                    onState("local viewer connected")
                    readMessages(socket)
                    if (clientSocket === socket) {
                        clientSocket = null
                        writer = null
                        onState("advertising local camera on Wi-Fi")
                    }
                }
            } catch (error: Exception) {
                if (running.get()) {
                    Log.e(TAG, "Local signaling server failed", error)
                    onState("local signaling error: ${error.message ?: "server failed"}")
                }
            } finally {
                closeSockets()
            }
        }, "AICAMsLocalSignalServer").apply { isDaemon = true }.start()
    }

    private fun registerService(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Registered ${serviceInfo.serviceName} ${serviceInfo.serviceType}:${serviceInfo.port}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD registration failed: $errorCode")
                onState("local discovery failed ($errorCode)")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD unregistration failed: $errorCode")
            }
        }
        registrationListener = listener
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun readMessages(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            while (running.get() && !socket.isClosed) {
                val line = reader.readLine() ?: break
                try {
                    onMessage(JSONObject(line))
                } catch (error: Exception) {
                    Log.w(TAG, "Ignoring malformed local signaling message", error)
                }
            }
        } catch (error: Exception) {
            if (running.get()) Log.w(TAG, "Local viewer socket ended", error)
        } finally {
            try { socket.close() } catch (_: Exception) { }
        }
    }

    override fun send(message: JSONObject): Boolean {
        if (!running.get() || writer == null) return false
        outbound.offer(message.toString())
        try { writerExecutor.execute { clientSocket?.let(::drainOutbound) } }
        catch (_: Exception) { return false }
        return true
    }

    private fun drainOutbound(target: Socket) {
        while (running.get() && clientSocket === target && !target.isClosed) {
            val text = try { outbound.poll(500, TimeUnit.MILLISECONDS) }
            catch (_: InterruptedException) { return } ?: continue
            val currentWriter = writer ?: return
            try {
                currentWriter.write(text)
                currentWriter.newLine()
                currentWriter.flush()
            } catch (error: Exception) {
                Log.w(TAG, "Could not send local signaling message", error)
                return
            }
        }
    }

    override fun close() {
        if (!running.getAndSet(false)) return
        registrationListener?.let { listener ->
            try { nsdManager.unregisterService(listener) } catch (_: Exception) { }
        }
        writerExecutor.shutdownNow()
        outbound.clear()
        closeSockets()
    }

    private fun closeSockets() {
        try { clientSocket?.close() } catch (_: Exception) { }
        try { serverSocket?.close() } catch (_: Exception) { }
        clientSocket = null
        writer = null
        serverSocket = null
    }
}

/** Viewer-side NSD discovery and TCP signaling client. Messages queue until discovery/connect completes. */
internal class LocalWifiSignalingClient(
    context: Context,
    initialMessage: JSONObject? = null,
    private val onMessage: (JSONObject) -> Unit,
    private val onState: (String) -> Unit
) : SignalingTransport {
    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val running = AtomicBoolean(true)
    private val outbound = LinkedBlockingQueue<String>()
    private val writerExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AICAMsLocalSignalClientWriter").apply { isDaemon = true }
    }
    private val viewerSessionId = UUID.randomUUID().toString()
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var discoveryListener: NsdManager.DiscoveryListener? = null
    @Volatile private var resolving = false

    init {
        initialMessage?.let { outbound.offer(it.toString()) }
        discover()
    }

    private fun discover() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                onState("searching for camera on local Wi-Fi")
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!running.get() || resolving) return
                if (!serviceInfo.serviceType.startsWith(SERVICE_TYPE.removeSuffix(".")) || serviceInfo.serviceName != SERVICE_NAME) return
                resolving = true
                try {
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            resolving = false
                            onState("camera discovery resolve failed ($errorCode)")
                        }
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            connect(info)
                        }
                    })
                } catch (error: Exception) {
                    resolving = false
                    onState("camera discovery failed: ${error.message ?: "resolve failed"}")
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName == SERVICE_NAME) onState("local camera disappeared")
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onState("local discovery failed ($errorCode)")
                try { nsdManager.stopServiceDiscovery(this) } catch (_: Exception) { }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discoveryListener = listener
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: Exception) {
            onState("could not start local discovery: ${error.message ?: "unknown error"}")
        }
    }

    private fun connect(info: NsdServiceInfo) {
        Thread({
            try {
                onState("connecting to ${info.serviceName} on local Wi-Fi")
                val target = Socket()
                target.connect(InetSocketAddress(info.host, info.port), CONNECT_TIMEOUT_MS)
                if (!running.get()) {
                    target.close()
                    return@Thread
                }
                socket = target
                writer = BufferedWriter(OutputStreamWriter(target.getOutputStream(), Charsets.UTF_8))
                try { discoveryListener?.let(nsdManager::stopServiceDiscovery) } catch (_: Exception) { }
                onState("local signaling connected")
                scheduleFlush()
                readMessages(target)
            } catch (error: Exception) {
                if (running.get()) onState("local signaling error: ${error.message ?: "connection failed"}")
            } finally {
                try { socket?.close() } catch (_: Exception) { }
                socket = null
                writer = null
            }
        }, "AICAMsLocalSignalClient").apply { isDaemon = true }.start()
    }

    private fun readMessages(target: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(target.getInputStream(), Charsets.UTF_8))
            while (running.get() && !target.isClosed) {
                val line = reader.readLine() ?: break
                try { onMessage(JSONObject(line)) }
                catch (error: Exception) { Log.w(TAG, "Ignoring malformed local signaling message", error) }
            }
        } catch (error: Exception) {
            if (running.get()) Log.w(TAG, "Local camera socket ended", error)
        }
    }

    override fun send(message: JSONObject): Boolean {
        if (!running.get()) return false
        outbound.offer(message.toString())
        scheduleFlush()
        return true
    }

    private fun scheduleFlush() {
        try { writerExecutor.execute(::flushQueuedMessages) }
        catch (_: Exception) { }
    }

    private fun flushQueuedMessages() {
        if (writer == null) return
        while (running.get()) {
            val message = outbound.poll() ?: break
            val currentWriter = writer ?: run {
                outbound.offer(message)
                return
            }
            try {
                currentWriter.write(message)
                currentWriter.newLine()
                currentWriter.flush()
            } catch (error: Exception) {
                Log.w(TAG, "Could not send local signaling message", error)
                outbound.offer(message)
                return
            }
        }
    }

    override fun close() {
        if (!running.getAndSet(false)) return
        discoveryListener?.let { listener ->
            try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) { }
        }
            writerExecutor.shutdownNow()
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        writer = null
        outbound.clear()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000
    }
}
