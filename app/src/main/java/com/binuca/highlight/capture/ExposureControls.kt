package com.binuca.highlight.capture

import java.util.Locale

data class ExposureControls(
    val index: Int = 0,
    val min: Int = 0,
    val max: Int = 0,
    val step: Float = 0f,
    val busy: Boolean = false,
    val revision: Int = 0,
    val direction: Int = 0,
) {
    val label: String get() = String.format(Locale.US, "%+.1f EV", index * step)
}
