package com.binuca.highlight.ui

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayRotationResolverTest {
    @Test
    fun `falls back to zero rotation before preview is attached`() {
        assertEquals(Surface.ROTATION_0, resolveDisplayRotation(null))
    }

    @Test
    fun `preserves rotation from an attached display`() {
        assertEquals(Surface.ROTATION_90, resolveDisplayRotation(Surface.ROTATION_90))
    }
}
