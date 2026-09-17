package com.binuca.highlight.storage

import com.binuca.highlight.capture.ClipDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HighlightFileNamer(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(zoneId)

    fun create(instant: Instant, duration: ClipDuration): String =
        "Binuca_Highlight_${formatter.format(instant)}_${duration.seconds}s.mp4"
}
