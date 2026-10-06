package com.aicams.viewer

import com.aicams.viewer.webrtc.WebRTCClient
import org.junit.Assert.assertEquals
import org.junit.Test

class WebRTCClientTest {
    @Test
    fun `offer url should be built from camera base url`() {
        assertEquals("http://10.0.2.2:8000/offer", WebRTCClient.buildOfferUrl("http://10.0.2.2:8000"))
    }

    @Test
    fun `camera host should resolve to emulator host when using local preview`() {
        assertEquals("http://10.0.2.2:8000", WebRTCClient.resolveCameraBaseUrl("10.0.2.2"))
    }
}
