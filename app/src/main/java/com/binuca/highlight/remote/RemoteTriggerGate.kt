package com.binuca.highlight.remote

class RemoteTriggerGate(
    private val debounceMs: Long = 700L,
) {
    private var lastAcceptedAtMs = Long.MIN_VALUE

    @Synchronized
    fun accept(action: Int, keyCode: Int, repeatCount: Int, eventTimeMs: Long): Boolean {
        if (action != ACTION_DOWN || repeatCount != 0 || keyCode !in SUPPORTED_KEYS) return false
        if (lastAcceptedAtMs != Long.MIN_VALUE && eventTimeMs - lastAcceptedAtMs <= debounceMs) {
            return false
        }
        lastAcceptedAtMs = eventTimeMs
        return true
    }

    companion object {
        private const val ACTION_DOWN = 0
        private val SUPPORTED_KEYS = setOf(24, 25, 27)
    }
}
