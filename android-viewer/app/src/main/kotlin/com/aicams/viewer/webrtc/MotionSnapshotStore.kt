package com.aicams.viewer.webrtc

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.webrtc.VideoFrame
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

internal data class MotionSnapshot(val timestampMs: Long, val jpegBytes: ByteArray)

/** Keeps only a few low-resolution motion-event JPEGs in app-private storage. */
internal class MotionSnapshotStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "motion_snapshots").apply { mkdirs() }
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AICAMsSnapshotStore").apply { isDaemon = true }
    }

    init {
        pruneOldFiles()
    }

    fun capture(frame: VideoFrame, onSaved: (MotionSnapshot) -> Unit = {}) {
        frame.retain()
        executor.execute {
            try {
                createThumbnailJpeg(frame)?.let { bytes ->
                    val timestamp = System.currentTimeMillis()
                    File(directory, "$timestamp.jpg").writeBytes(bytes)
                    pruneOldFiles()
                    Log.i(TAG, "Saved motion snapshot timestamp=$timestamp bytes=${bytes.size}")
                    onSaved(MotionSnapshot(timestamp, bytes))
                }
            } catch (error: Exception) {
                Log.w(TAG, "Could not save motion snapshot", error)
            } finally {
                frame.release()
            }
        }
    }

    @Synchronized
    fun latest(limit: Int = MAX_SNAPSHOTS): List<MotionSnapshot> {
        pruneOldFiles()
        return directory.listFiles { file -> file.extension == "jpg" }
            .orEmpty()
            .sortedByDescending { it.nameWithoutExtension.toLongOrNull() ?: 0L }
            .take(limit.coerceIn(0, MAX_SNAPSHOTS))
            .mapNotNull { file ->
                val timestamp = file.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
                try { MotionSnapshot(timestamp, file.readBytes()) }
                catch (error: Exception) {
                    Log.w(TAG, "Could not read snapshot ${file.name}", error)
                    null
                }
            }
    }

    @Synchronized
    private fun pruneOldFiles() {
        val files = directory.listFiles { file -> file.extension == "jpg" }.orEmpty()
            .sortedByDescending { it.nameWithoutExtension.toLongOrNull() ?: 0L }
        val oldestAllowed = System.currentTimeMillis() - MAX_AGE_MS
        files.forEachIndexed { index, file ->
            val timestamp = file.nameWithoutExtension.toLongOrNull() ?: 0L
            if (index >= MAX_SNAPSHOTS || timestamp < oldestAllowed) file.delete()
        }
    }

    private fun createThumbnailJpeg(frame: VideoFrame): ByteArray? {
        val buffer = frame.buffer.toI420() ?: return null
        try {
            val outWidth = minOf(THUMBNAIL_WIDTH, buffer.width).coerceAtLeast(1)
            val outHeight = minOf(THUMBNAIL_HEIGHT, buffer.height).coerceAtLeast(1)
            val pixels = IntArray(outWidth * outHeight)
            val yPlane = buffer.dataY
            val uPlane = buffer.dataU
            val vPlane = buffer.dataV
            for (y in 0 until outHeight) {
                val sourceY = y * buffer.height / outHeight
                for (x in 0 until outWidth) {
                    val sourceX = x * buffer.width / outWidth
                    val luminance = (yPlane.get(sourceY * buffer.strideY + sourceX).toInt() and 0xff) - 16
                    val u = (uPlane.get((sourceY / 2) * buffer.strideU + sourceX / 2).toInt() and 0xff) - 128
                    val v = (vPlane.get((sourceY / 2) * buffer.strideV + sourceX / 2).toInt() and 0xff) - 128
                    val yValue = luminance.coerceAtLeast(0)
                    val red = ((298 * yValue + 409 * v + 128) shr 8).coerceIn(0, 255)
                    val green = ((298 * yValue - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
                    val blue = ((298 * yValue + 516 * u + 128) shr 8).coerceIn(0, 255)
                    pixels[y * outWidth + x] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }
            val bitmap = Bitmap.createBitmap(pixels, outWidth, outHeight, Bitmap.Config.ARGB_8888)
            return ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                bitmap.recycle()
                output.toByteArray()
            }
        } finally {
            buffer.release()
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private companion object {
        const val TAG = "AICAMsMotionSnapshot"
        const val MAX_SNAPSHOTS = 3
        const val MAX_AGE_MS = 24L * 60L * 60L * 1000L
        const val THUMBNAIL_WIDTH = 320
        const val THUMBNAIL_HEIGHT = 180
        const val JPEG_QUALITY = 55
    }
}
