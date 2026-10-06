package com.aicams.viewer.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MotionDetectorTest {
    @Test
    fun detectsSceneChangeAndClearsAfterQuietFrames() {
        val detector = MotionDetector()
        val stillScene = ByteArray(100) { 100.toByte() }
        val changedScene = stillScene.copyOf().apply {
            for (index in 0 until 30) this[index] = 180.toByte()
        }

        assertNull(detector.update(stillScene))
        assertEquals(true, detector.update(changedScene))
        assertNull(detector.update(changedScene))
        assertNull(detector.update(changedScene))
        assertEquals(false, detector.update(changedScene))
    }

    @Test
    fun ignoresSmallPixelNoise() {
        val detector = MotionDetector()
        val stillScene = ByteArray(100) { 100.toByte() }
        val noisyScene = stillScene.copyOf().apply { this[0] = 220.toByte() }

        assertNull(detector.update(stillScene))
        assertNull(detector.update(noisyScene))
    }
}