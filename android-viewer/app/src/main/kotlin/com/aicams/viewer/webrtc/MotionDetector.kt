package com.aicams.viewer.webrtc

/**
 * Detects meaningful frame-to-frame luminance changes from a low-resolution sample.
 * Returns a new stable motion state only when it changes; the first sample is the baseline.
 */
internal class MotionDetector(
    private val pixelDifferenceThreshold: Int = 20,
    private val changedPixelRatioThreshold: Float = 0.05f,
    private val averageDifferenceThreshold: Float = 7f,
    private val framesToStart: Int = 1,
    private val framesToStop: Int = 3
) {
    private var previousSample: ByteArray? = null
    @Volatile private var motionDetected = false
    private var positiveFrames = 0
    private var negativeFrames = 0

    fun isMotionDetected(): Boolean = motionDetected

    fun update(sample: ByteArray): Boolean? {
        val previous = previousSample
        previousSample = sample.copyOf()
        if (previous == null || previous.size != sample.size || sample.isEmpty()) return null

        var changedPixels = 0
        var totalDifference = 0
        for (index in sample.indices) {
            val currentLuma = sample[index].toInt() and 0xff
            val previousLuma = previous[index].toInt() and 0xff
            val difference = kotlin.math.abs(currentLuma - previousLuma)
            totalDifference += difference
            if (difference >= pixelDifferenceThreshold) changedPixels++
        }

        val changedRatio = changedPixels.toFloat() / sample.size
        val averageDifference = totalDifference.toFloat() / sample.size
        val frameHasMotion = changedRatio >= changedPixelRatioThreshold &&
            averageDifference >= averageDifferenceThreshold

        if (frameHasMotion) {
            positiveFrames++
            negativeFrames = 0
        } else {
            negativeFrames++
            positiveFrames = 0
        }

        if (!motionDetected && positiveFrames >= framesToStart) {
            motionDetected = true
            negativeFrames = 0
            return true
        }
        if (motionDetected && negativeFrames >= framesToStop) {
            motionDetected = false
            positiveFrames = 0
            return false
        }
        return null
    }
}