package com.binuca.highlight.capture

import android.media.MediaCodec
import android.net.Uri
import java.nio.ByteBuffer

enum class EncodedTrack { VIDEO, AUDIO }

class EncodedSample(
    val track: EncodedTrack,
    val presentationTimeUs: Long,
    val flags: Int,
    data: ByteArray,
) {
    private val payload = data.copyOf()

    val size: Int get() = payload.size
    val isKeyFrame: Boolean get() = flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0

    fun readOnlyData(): ByteBuffer = ByteBuffer.wrap(payload).asReadOnlyBuffer()
}

enum class ClipDuration(val seconds: Int) {
    TEN_SECONDS(10),
    TWENTY_SECONDS(20),
    THIRTY_SECONDS(30);

    val durationUs: Long get() = seconds * 1_000_000L

    companion object {
        fun fromSeconds(seconds: Int): ClipDuration = entries.first { it.seconds == seconds }
    }
}

data class BufferReadiness(
    val availableUs: Long,
    val progress10: Float,
    val progress20: Float,
    val progress30: Float,
) {
    fun progress(duration: ClipDuration): Float = when (duration) {
        ClipDuration.TEN_SECONDS -> progress10
        ClipDuration.TWENTY_SECONDS -> progress20
        ClipDuration.THIRTY_SECONDS -> progress30
    }

    fun isReady(duration: ClipDuration): Boolean = availableUs >= duration.durationUs

    companion object {
        val Empty = BufferReadiness(0, 0f, 0f, 0f)
    }
}

enum class TriggerSource { SCREEN, REMOTE }

data class CaptureRequest(
    val duration: ClipDuration,
    val triggerTimeUs: Long,
    val source: TriggerSource,
)

sealed interface CaptureResult {
    data class Success(val uri: Uri, val actualDurationUs: Long) : CaptureResult
    data class Failure(val reason: String) : CaptureResult
}

enum class CameraSessionState { Preparing, Buffering, Ready, Recovering, Stopped }

data class CaptureSnapshot(
    val startTimeUs: Long,
    val endTimeUs: Long,
    val samples: List<EncodedSample>,
) {
    val actualDurationUs: Long get() = endTimeUs - startTimeUs
}

class SnapshotUnavailableException(message: String) : IllegalStateException(message)
