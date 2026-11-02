package com.aicams.viewer.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraBatterySettingsTest {
    @Test
    fun `individual defaults stay inactive until battery saver is enabled`() {
        val settings = CameraBatterySettings()

        assertFalse(settings.useReducedVideoQuality)
        assertFalse(settings.useLowPowerWhileWaiting)
        assertFalse(settings.suppressLocalPreview)
        assertFalse(settings.startWithMicrophoneOff)
        assertFalse(settings.permitScreenSleep)
        assertFalse(settings.capVideoBitrate)
    }

    @Test
    fun `enabling battery saver activates selected defaults`() {
        val settings = CameraBatterySettings(enabled = true)

        assertTrue(settings.useReducedVideoQuality)
        assertTrue(settings.useLowPowerWhileWaiting)
        assertTrue(settings.suppressLocalPreview)
        assertTrue(settings.startWithMicrophoneOff)
        assertTrue(settings.permitScreenSleep)
        assertTrue(settings.capVideoBitrate)
    }

    @Test
    fun `individual features remain independently optional`() {
        val settings = CameraBatterySettings(
            enabled = true,
            reduceVideoQuality = false,
            disableLocalPreview = false,
            limitVideoBitrate = false
        )

        assertFalse(settings.useReducedVideoQuality)
        assertFalse(settings.suppressLocalPreview)
        assertFalse(settings.capVideoBitrate)
        assertTrue(settings.useLowPowerWhileWaiting)
        assertTrue(settings.startWithMicrophoneOff)
    }
}
