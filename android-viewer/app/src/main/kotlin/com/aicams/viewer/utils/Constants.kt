package com.aicams.viewer.utils

import com.aicams.viewer.BuildConfig

object Constants {
    // API Configuration

    // below is for emulator
    // const val API_BASE_URL = "http://10.0.2.2:8000"

    // Supplied at build time through AICAMS_* environment variables or Gradle properties.
    val API_BASE_URL: String = BuildConfig.SIGNALING_BASE_URL.trimEnd('/')
    val P2P_DEVICE_ID: String = BuildConfig.CAMERA_DEVICE_ID
    val SIGNALING_TOKEN: String = BuildConfig.SIGNALING_TOKEN
    
    const val COORDINATOR_BASE_URL = "http://10.0.2.2:3000"
    const val SIGNALING_URL = "ws://10.0.2.2:8000"
    const val API_TIMEOUT_SECONDS = 30L
    
    // WebRTC Configuration
    val STUN_SERVERS: List<String> = BuildConfig.STUN_URLS
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { url -> if (url.startsWith("stun:") || url.startsWith("stuns:")) url else "stun:$url" }

    val TURN_SERVERS: List<String> = BuildConfig.TURN_URLS
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
    val TURN_USERNAME: String = BuildConfig.TURN_USERNAME
    val TURN_PASSWORD: String = BuildConfig.TURN_PASSWORD
    
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
