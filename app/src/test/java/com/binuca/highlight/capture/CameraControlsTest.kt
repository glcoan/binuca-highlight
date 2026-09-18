package com.binuca.highlight.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CameraControlsTest {
    @Test fun `shortcuts stay on active wide stream and match slider units`() {
        val shortcuts = continuousZoomShortcuts("wide", 0.6f, 1f, 20f, listOf(0.6f, 1f, 2.5f))
        assertEquals(listOf("0.6×", "1×", "2×", "2.5×", "3×", "5×", "10×", "12×"), shortcuts.map { it.label })
        shortcuts.forEach {
            assertEquals("wide", it.lensId)
            assertEquals(it.label, zoomLabel(it.ratio * 0.6f))
        }
        assertEquals(1f / 0.6f, shortcuts.first { it.label == "1×" }.ratio, 0.0001f)
    }

    @Test fun `does not include a shortcut requiring a lens switch outside slider range`() {
        val shortcuts = continuousZoomShortcuts("default", 1f, 1f, 10f, listOf(0.6f, 2.5f, 20f))
        assertFalse(shortcuts.any { it.label == "0.6×" || it.label == "20×" })
        assertEquals(2.5f, shortcuts.first { it.label == "2.5×" }.ratio)
    }
    @Test fun `includes device ultra wide before standard zoom and full maximum`() {
        assertEquals(listOf(0.6f, 1f, 2f, 3f, 5f, 10f, 30f), zoomStops(0.6f, 30f))
    }

    @Test fun `does not advertise unsupported zoom and removes duplicate bounds`() {
        assertEquals(listOf(1f, 2f, 3f, 5f, 10f), zoomStops(1f, 10f))
        assertEquals(listOf(1f), zoomStops(1f, 1f))
        assertFalse(zoomStops(1f, 4f).contains(0.5f))
    }

    @Test fun `formats compact zoom labels`() {
        assertEquals("1×", zoomLabel(1f))
        assertEquals("0.6×", zoomLabel(0.6f))
    }
}
