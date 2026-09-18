package com.binuca.highlight.ui

import org.junit.Assert.*
import org.junit.Test

class OrientationDetectionTest {
    @Test fun `detects both landscape and portrait positions`() {
        listOf(0, 10, 180, 350).forEach { assertEquals(false, detectedLandscape(it)) }
        listOf(90, 100, 270, 280).forEach { assertEquals(true, detectedLandscape(it)) }
    }
    @Test fun `ignores diagonal and unavailable readings`() {
        listOf(-1, 40, 135, 225, 315).forEach { assertNull(detectedLandscape(it)) }
    }
    @Test fun `supports naturally landscape devices`() {
        assertEquals(true, detectedLandscape(0, naturalLandscape = true))
        assertEquals(false, detectedLandscape(90, naturalLandscape = true))
    }
}
