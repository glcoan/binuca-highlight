package com.binuca.highlight.storage

import com.binuca.highlight.capture.ClipDuration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightFileNamerTest {
    @Test
    fun `uses local timestamp and requested duration`() {
        val namer = HighlightFileNamer(ZoneId.of("America/Sao_Paulo"))

        val name = namer.create(
            instant = Instant.parse("2026-09-17T17:30:52Z"),
            duration = ClipDuration.TWENTY_SECONDS,
        )

        assertEquals("Binuca_Highlight_20260917_143052_20s.mp4", name)
    }
}
