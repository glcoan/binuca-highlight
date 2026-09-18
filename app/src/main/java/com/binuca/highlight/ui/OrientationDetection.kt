package com.binuca.highlight.ui

internal fun detectedLandscape(degrees: Int, naturalLandscape: Boolean = false): Boolean? {
    if (degrees !in 0..359) return null
    return when {
        degrees <= 20 || degrees >= 340 || degrees in 160..200 -> naturalLandscape
        degrees in 70..110 || degrees in 250..290 -> !naturalLandscape
        else -> null
    }
}
