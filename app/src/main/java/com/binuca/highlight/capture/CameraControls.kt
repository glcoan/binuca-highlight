package com.binuca.highlight.capture

import java.util.Locale

data class ZoomShortcut(val id: String, val label: String, val lensId: String, val ratio: Float)

data class CameraControls(
    val shortcuts: List<ZoomShortcut> = emptyList(),
    val lensId: String = "default",
    val zoom: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val hasFlash: Boolean = false,
    val torchOn: Boolean = false,
    val lensScale: Float = 1f,
)

internal fun zoomStops(min: Float, max: Float): List<Float> =
    (listOf(min, 1f, 2f, 3f, 5f, 10f, max))
        .filter { it.isFinite() && it > 0 && it in min..max }
        .distinct().sorted()

fun zoomLabel(ratio: Float): String =
    String.format(Locale.US, "%.1f", ratio).removeSuffix(".0") + "×"

/** Every shortcut targets the active stream, using the slider's native zoom units. */
internal fun continuousZoomShortcuts(
    lensId: String, scale: Float, min: Float, max: Float, lensStops: List<Float>,
): List<ZoomShortcut> = (zoomStops(min * scale, max * scale) + lensStops)
    .filter { it.isFinite() && it >= min * scale && it <= max * scale }
    .sorted().distinctBy { zoomLabel(it) }
    .map { displayed ->
        ZoomShortcut("zoom-$displayed", zoomLabel(displayed), lensId, (displayed / scale).coerceIn(min, max))
    }
