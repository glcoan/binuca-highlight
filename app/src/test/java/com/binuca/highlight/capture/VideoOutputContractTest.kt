package com.binuca.highlight.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoOutputContractTest {
    @Test
    fun `buffered output supplies its own media specification instead of null default`() {
        assertEquals(
            BufferedVideoOutput::class.java,
            BufferedVideoOutput::class.java.getMethod("getMediaSpec").declaringClass,
        )
    }
}
