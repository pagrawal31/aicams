package com.aicams.viewer.webrtc

import android.content.Context
import android.os.BatteryManager
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import org.webrtc.PeerConnection
import org.webrtc.RTCStatsCollectorCallback
import org.webrtc.RTCStatsReport
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Periodically logs process/device resource readings and WebRTC RTP traffic counters. */
internal class ResourceDiagnostics(
    context: Context,
    private val tag: String,
    private val role: String
) {
    private val appContext = context.applicationContext
    private val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "AICAMsResourceDiagnostics-$role").apply { isDaemon = true }
    }

    @Volatile private var running = false
    @Volatile private var connectionProvider: (() -> PeerConnection?)? = null
    @Volatile private var modeProvider: (() -> String)? = null
    private var previousSentBytes: Long? = null
    private var previousReceivedBytes: Long? = null
    private var previousStatsTimeMs: Long? = null

    fun start(
        connectionProvider: () -> PeerConnection?,
        modeProvider: () -> String
    ) {
        if (running) return
        this.connectionProvider = connectionProvider
        this.modeProvider = modeProvider
        running = true
        executor.scheduleAtFixedRate(::sample, SAMPLE_INTERVAL_SECONDS, SAMPLE_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    private fun sample() {
        if (!running) return
        try {
            val processPssKb = Debug.getPss()
            val runtime = Runtime.getRuntime()
            val javaHeapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MEGABYTE
            val batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val batteryCurrentMicroamps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val batteryCurrentText = if (batteryCurrentMicroamps == Int.MIN_VALUE) "unavailable" else "${batteryCurrentMicroamps}uA"

            Log.i(
                tag,
                "resource role=$role mode=${modeProvider?.invoke() ?: "unknown"} " +
                    "processPss=${processPssKb}KB javaHeap=${javaHeapUsedMb}MB " +
                    "deviceBattery=${batteryPercent.takeIf { it >= 0 }?.let { "${it}%" } ?: "unavailable"} " +
                    "deviceBatteryCurrent=$batteryCurrentText"
            )

            val connection = connectionProvider?.invoke() ?: return
            connection.getStats(object : RTCStatsCollectorCallback {
                override fun onStatsDelivered(report: RTCStatsReport) {
                    if (!running) return
                    logWebRtcStats(report)
                }
            })
        } catch (error: Exception) {
            Log.w(tag, "Resource sampling failed", error)
        }
    }

    @Synchronized
    private fun logWebRtcStats(report: RTCStatsReport) {
        var sentBytes = 0L
        var receivedBytes = 0L
        var hasSentCounter = false
        var hasReceivedCounter = false

        report.statsMap.values.forEach { stat ->
            val members = stat.members
            when (stat.type) {
                "outbound-rtp" -> {
                    (members["bytesSent"] as? Number)?.let {
                        sentBytes += it.toLong()
                        hasSentCounter = true
                    }
                }
                "inbound-rtp" -> {
                    (members["bytesReceived"] as? Number)?.let {
                        receivedBytes += it.toLong()
                        hasReceivedCounter = true
                    }
                }
            }
        }

        val nowMs = SystemClock.elapsedRealtime()
        val previousTime = previousStatsTimeMs
        val elapsedSeconds = if (previousTime != null && nowMs > previousTime) {
            (nowMs - previousTime) / 1000.0
        } else {
            null
        }
        val txKbps = if (elapsedSeconds != null && hasSentCounter && previousSentBytes != null && sentBytes >= previousSentBytes!!) {
            (sentBytes - previousSentBytes!!) * 8.0 / elapsedSeconds / 1000.0
        } else {
            null
        }
        val rxKbps = if (elapsedSeconds != null && hasReceivedCounter && previousReceivedBytes != null && receivedBytes >= previousReceivedBytes!!) {
            (receivedBytes - previousReceivedBytes!!) * 8.0 / elapsedSeconds / 1000.0
        } else {
            null
        }

        if (hasSentCounter) previousSentBytes = sentBytes
        if (hasReceivedCounter) previousReceivedBytes = receivedBytes
        previousStatsTimeMs = nowMs

        Log.i(
            tag,
            "webrtc role=$role tx=${if (hasSentCounter) "${sentBytes}B" else "n/a"}" +
                "${txKbps?.let { " (${"%.1f".format(it)}kbps)" } ?: ""} " +
                "rx=${if (hasReceivedCounter) "${receivedBytes}B" else "n/a"}" +
                "${rxKbps?.let { " (${"%.1f".format(it)}kbps)" } ?: ""}"
        )
    }

    fun stop() {
        running = false
        connectionProvider = null
        modeProvider = null
        executor.shutdownNow()
    }

    private companion object {
        const val SAMPLE_INTERVAL_SECONDS = 10L
        const val BYTES_PER_MEGABYTE = 1024.0 * 1024.0
    }
}
