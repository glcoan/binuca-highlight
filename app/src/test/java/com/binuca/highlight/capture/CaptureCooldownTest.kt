package com.binuca.highlight.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureCooldownTest {
    @Test fun `first capture is allowed and next is allowed exactly five seconds later`() {
        var now = 100L
        val cooldown = CaptureCooldown { now }
        assertEquals(0L, cooldown.remainingMs())
        cooldown.onCaptureAccepted()
        assertEquals(5_000L, cooldown.remainingMs())
        now += 4_999
        assertEquals(1L, cooldown.remainingMs())
        now += 1
        assertEquals(0L, cooldown.remainingMs())
    }

    @Test fun `all durations and sources share the same deadline without extending it`() {
        var now = 0L
        val cooldown = CaptureCooldown { now }
        cooldown.onCaptureAccepted()
        for (duration in ClipDuration.entries) {
            for (source in TriggerSource.entries) {
                now += 100
                assertEquals(5_000L - now, cooldown.remainingMs())
            }
        }
        now = 5_000
        assertEquals(0L, cooldown.remainingMs())
        cooldown.onCaptureAccepted()
        assertEquals(5_000L, cooldown.remainingMs())
    }
}
