package com.binuca.highlight.capture

import android.media.MediaFormat

data class CaptureFormats(
    val video: MediaFormat,
    val audio: MediaFormat,
    val rotationDegrees: Int,
)

class CaptureFormatRegistry {
    @Volatile private var videoFormat: MediaFormat? = null
    @Volatile private var audioFormat: MediaFormat? = null
    @Volatile private var rotationDegrees: Int = 0

    fun setVideo(format: MediaFormat) {
        videoFormat = format
    }

    fun setAudio(format: MediaFormat) {
        audioFormat = format
    }

    fun setRotation(degrees: Int) {
        rotationDegrees = degrees
    }

    fun current(): CaptureFormats? {
        val video = videoFormat ?: return null
        val audio = audioFormat ?: return null
        return CaptureFormats(video, audio, rotationDegrees)
    }

    fun clear() {
        videoFormat = null
        audioFormat = null
        rotationDegrees = 0
    }
}
