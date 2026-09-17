package com.binuca.highlight.ui

import android.view.Surface

internal fun resolveDisplayRotation(rotation: Int?): Int = rotation ?: Surface.ROTATION_0
