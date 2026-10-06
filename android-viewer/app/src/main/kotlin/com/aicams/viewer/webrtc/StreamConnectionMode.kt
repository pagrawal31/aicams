package com.aicams.viewer.webrtc

enum class StreamConnectionMode {
    RELAY_VIA_LAPTOP,
    P2P_VIA_LAPTOP_SIGNALING,
    P2P_LOCAL_WIFI
}

internal interface SignalingTransport {
    fun send(message: org.json.JSONObject): Boolean
    fun close()
}