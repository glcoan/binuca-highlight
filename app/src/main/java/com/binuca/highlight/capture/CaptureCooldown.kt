package com.binuca.highlight.capture

/** Shared across durations and trigger sources. Rejected attempts never extend the deadline. */
class CaptureCooldown(private val nowMs: () -> Long) {
    private var blockedUntilMs = 0L

    fun remainingMs(): Long = (blockedUntilMs - nowMs()).coerceAtLeast(0L)

    fun onCaptureAccepted() {
        blockedUntilMs = nowMs() + 5_000L
    }
}
