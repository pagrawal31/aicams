package com.aicams.viewer.utils

object Constants {
    // API Configuration

    // below is for emulator
    // const val API_BASE_URL = "http://10.0.2.2:8000"

    // below is for real device
    const val API_BASE_URL = "http://192.168.29.102:8000"
    const val P2P_DEVICE_ID = "aicams-phone-camera"
    
    const val COORDINATOR_BASE_URL = "http://10.0.2.2:3000"
    const val SIGNALING_URL = "ws://10.0.2.2:8000"
    const val API_TIMEOUT_SECONDS = 30L
    
    // WebRTC Configuration
    val STUN_SERVERS: List<String> = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302"
    )

    val TURN_SERVERS: List<String> = emptyList()
    
    // Video constraints
    const val VIDEO_WIDTH = 1920
    const val VIDEO_HEIGHT = 1080
    const val VIDEO_FPS = 30
    
    // Timeouts
    const val CONNECTION_TIMEOUT_SECONDS = 10L
    const val STREAM_TIMEOUT_SECONDS = 30L
    
    // Preferences
    const val PREF_AUTH_TOKEN = "auth_token"
    const val PREF_USER_ID = "user_id"
    const val PREF_USER_EMAIL = "user_email"
    const val PREF_REMEMBER_ME = "remember_me"
    
    // Navigation
    const val NAV_ROUTE_LOGIN = "login"
    const val NAV_ROUTE_DASHBOARD = "dashboard"
    const val NAV_ROUTE_CAMERA = "camera"
    const val NAV_ROUTE_EVENTS = "events"
    
    // mDNS Configuration
    const val MDNS_SERVICE_TYPE = "_camera-stream._tcp"
    const val MDNS_DISCOVERY_TIMEOUT_SECONDS = 10L
}
