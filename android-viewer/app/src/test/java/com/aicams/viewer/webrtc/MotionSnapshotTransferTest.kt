package com.aicams.viewer.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class MotionSnapshotTransferTest {
    @Test
    fun chunksSnapshotIntoBoundedMessagesAndPreservesPayload() {
        val bytes = ByteArray(25_000) { (it % 251).toByte() }
        val messages = MotionSnapshotTransfer.messages(listOf(MotionSnapshot(1234L, bytes)))

        assertEquals(MotionSnapshotTransfer.EVENT_START, messages.first().type)
        val chunks = messages.drop(1)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.type == MotionSnapshotTransfer.EVENT_CHUNK })
        assertTrue(chunks.all { it.data.orEmpty().length <= MotionSnapshotTransfer.CHUNK_CHARACTERS })
        val restored = Base64.getDecoder().decode(chunks.joinToString("") { it.data.orEmpty() })
        assertTrue(bytes.contentEquals(restored))
    }

    @Test
    fun skipsSnapshotsLargerThanConfiguredBound() {
        val tooLarge = ByteArray(MotionSnapshotTransfer.MAX_SNAPSHOT_BYTES + 1)
        assertTrue(MotionSnapshotTransfer.messages(listOf(MotionSnapshot(1L, tooLarge))).isEmpty())
    }
}