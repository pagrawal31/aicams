package com.aicams.viewer

import android.app.Application
import com.aicams.viewer.webrtc.WebRtcRuntime
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AICAMsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WebRtcRuntime.initialize(this)
    }
}
