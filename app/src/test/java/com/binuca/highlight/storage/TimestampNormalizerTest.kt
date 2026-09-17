package com.binuca.highlight.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class TimestampNormalizerTest {
    @Test
    fun `normalizes each track against the shared clip start`() {
        val normalizer = TimestampNormalizer(18_000_000)

        assertEquals(0, normalizer.normalize(18_000_000))
        assertEquals(1_023_000, normalizer.normalize(19_023_000))
    }
}
