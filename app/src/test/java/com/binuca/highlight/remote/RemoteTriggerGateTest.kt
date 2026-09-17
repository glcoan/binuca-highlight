package com.binuca.highlight.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTriggerGateTest {
    private val gate = RemoteTriggerGate(debounceMs = 700)

    @Test
    fun `accepts only initial key down from supported camera controls`() {
        assertTrue(gate.accept(action = 0, keyCode = 24, repeatCount = 0, eventTimeMs = 1_000))
        assertFalse(gate.accept(action = 0, keyCode = 24, repeatCount = 1, eventTimeMs = 2_000))
        assertFalse(gate.accept(action = 1, keyCode = 25, repeatCount = 0, eventTimeMs = 3_000))
        assertFalse(gate.accept(action = 0, keyCode = 4, repeatCount = 0, eventTimeMs = 4_000))
    }

    @Test
    fun `debounces volume pairs emitted by one wearable click`() {
        assertTrue(gate.accept(0, 24, 0, 10_000))
        assertFalse(gate.accept(0, 25, 0, 10_400))
        assertTrue(gate.accept(0, 27, 0, 10_701))
    }
}
