package com.binuca.highlight.storage

class TimestampNormalizer(
    private val clipStartUs: Long,
) {
    fun normalize(presentationTimeUs: Long): Long =
        (presentationTimeUs - clipStartUs).coerceAtLeast(0L)
}
