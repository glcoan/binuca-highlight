package com.binuca.highlight.storage

import com.binuca.highlight.capture.EncodedSample
import java.nio.ByteBuffer

/** One native-compatible scratch buffer per save; never exposes the ring's payload. */
internal class MuxerSampleBuffer(capacity: Int) {
    private val buffer = ByteBuffer.allocateDirect(capacity)

    fun prepare(sample: EncodedSample): ByteBuffer {
        require(sample.size <= buffer.capacity()) { "Sample exceeds muxer buffer capacity" }
        buffer.clear()
        buffer.put(sample.readOnlyData())
        buffer.flip()
        return buffer
    }
}
