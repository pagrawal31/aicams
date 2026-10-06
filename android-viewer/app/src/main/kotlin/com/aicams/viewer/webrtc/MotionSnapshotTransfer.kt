package com.aicams.viewer.webrtc

import java.util.Base64

internal data class MotionSnapshotWireMessage(
    val type: String,
    val id: String,
    val timestampMs: Long? = null,
    val totalChunks: Int? = null,
    val index: Int? = null,
    val data: String? = null
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("type", type)
        .put("id", id)
        .apply {
            timestampMs?.let { put("timestampMs", it) }
            totalChunks?.let { put("totalChunks", it) }
            index?.let { put("index", it) }
            data?.let { put("data", it) }
        }
}

/** Small JSON messages are chunked to avoid oversized WebRTC data-channel messages. */
internal object MotionSnapshotTransfer {
    const val EVENT_REQUEST = "snapshots_request"
    const val EVENT_START = "snapshot_start"
    const val EVENT_CHUNK = "snapshot_chunk"
    const val CHUNK_CHARACTERS = 8_000
    const val MAX_SNAPSHOTS = 3
    const val MAX_CHUNKS_PER_SNAPSHOT = 128
    const val MAX_SNAPSHOT_BYTES = 512 * 1024

    fun messages(snapshots: List<MotionSnapshot>): List<MotionSnapshotWireMessage> = buildList {
        snapshots.take(MAX_SNAPSHOTS).forEachIndexed { snapshotIndex, snapshot ->
            if (snapshot.jpegBytes.size > MAX_SNAPSHOT_BYTES) return@forEachIndexed
            val encoded = Base64.getEncoder().encodeToString(snapshot.jpegBytes)
            val chunks = encoded.chunked(CHUNK_CHARACTERS)
            if (chunks.size > MAX_CHUNKS_PER_SNAPSHOT) return@forEachIndexed
            val id = "${snapshot.timestampMs}-$snapshotIndex"
            add(MotionSnapshotWireMessage(EVENT_START, id, snapshot.timestampMs, chunks.size))
            chunks.forEachIndexed { chunkIndex, chunk ->
                add(MotionSnapshotWireMessage(EVENT_CHUNK, id, index = chunkIndex, data = chunk))
            }
        }
    }
}
