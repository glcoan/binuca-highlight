package com.binuca.highlight.storage

import com.binuca.highlight.capture.EncodedSample
import com.binuca.highlight.capture.EncodedTrack
import org.junit.Assert.*
import org.junit.Test

class MuxerSampleBufferTest {
    private fun sample(vararg bytes: Byte) = EncodedSample(EncodedTrack.VIDEO, 0, 0, bytes)

    @Test
    fun `copies immutable heap sample into native accessible direct memory`() {
        val sample = sample(1, 2, 3)
        val original = sample.readOnlyData()
        assertFalse(original.isDirect)
        assertFalse(original.hasArray())
        val buffer = MuxerSampleBuffer(3).prepare(sample)
        assertTrue(buffer.isDirect)
        assertEquals(0, buffer.position())
        assertEquals(3, buffer.limit())
        val bytes = ByteArray(3)
        buffer.get(bytes)
        assertArrayEquals(byteArrayOf(1, 2, 3), bytes)
        buffer.put(0, 99.toByte())
        assertEquals(1.toByte(), sample.readOnlyData().get(0))
    }

    @Test
    fun `reuses buffer and excludes trailing bytes when next packet is smaller`() {
        val scratch = MuxerSampleBuffer(4)
        val first = scratch.prepare(sample(1, 2, 3, 4))
        first.position(4)
        val second = scratch.prepare(sample(8, 9))
        assertSame(first, second)
        assertEquals(0, second.position())
        assertEquals(2, second.limit())
        val bytes = ByteArray(second.remaining())
        second.get(bytes)
        assertArrayEquals(byteArrayOf(8, 9), bytes)
    }

    @Test
    fun `overlapping saves do not modify retained samples`() {
        val shared = sample(4, 5, 6)
        val first = MuxerSampleBuffer(3).prepare(shared)
        first.put(0, 0.toByte())
        val next = MuxerSampleBuffer(3).prepare(shared)
        assertEquals(4.toByte(), next.get())
        assertEquals(4.toByte(), shared.readOnlyData().get())
    }
}
