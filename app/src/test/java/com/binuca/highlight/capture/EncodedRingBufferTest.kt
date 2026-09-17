package com.binuca.highlight.capture

import android.media.MediaCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncodedRingBufferTest {
    @Test
    fun `readiness uses the common audio and video interval`() {
        val buffer = EncodedRingBuffer(retentionUs = 31_000_000)

        buffer.append(sample(EncodedTrack.VIDEO, 0, keyFrame = true))
        buffer.append(sample(EncodedTrack.AUDIO, 500_000))
        buffer.append(sample(EncodedTrack.VIDEO, 20_000_000, keyFrame = true))
        buffer.append(sample(EncodedTrack.AUDIO, 12_500_000))

        val readiness = buffer.readiness()

        assertEquals(12_000_000, readiness.availableUs)
        assertEquals(1f, readiness.progress10, 0.001f)
        assertEquals(0.6f, readiness.progress20, 0.001f)
        assertEquals(0.4f, readiness.progress30, 0.001f)
        assertTrue(readiness.isReady(ClipDuration.TEN_SECONDS))
        assertFalse(readiness.isReady(ClipDuration.TWENTY_SECONDS))
    }

    @Test
    fun `samples older than retention are discarded while memory remains bounded`() {
        val buffer = EncodedRingBuffer(retentionUs = 31_000_000)

        for (second in 0..70) {
            buffer.append(sample(EncodedTrack.VIDEO, second * 1_000_000L, keyFrame = true))
            buffer.append(sample(EncodedTrack.AUDIO, second * 1_000_000L))
        }

        val samples = buffer.samplesForTesting()
        assertTrue(samples.all { it.presentationTimeUs >= 39_000_000L })
        assertEquals(64, samples.size)
        assertEquals(31_000_000L, buffer.readiness().availableUs)
    }

    @Test
    fun `late samples older than the retention window are discarded immediately`() {
        val buffer = EncodedRingBuffer(retentionUs = 31_000_000)
        buffer.append(sample(EncodedTrack.VIDEO, 70_000_000, keyFrame = true))
        buffer.append(sample(EncodedTrack.AUDIO, 70_000_000))

        buffer.append(sample(EncodedTrack.AUDIO, 1_000_000))

        assertTrue(buffer.samplesForTesting().all { it.presentationTimeUs >= 39_000_000 })
    }

    @Test
    fun `snapshot starts at keyframe before requested boundary and ends at trigger`() {
        val buffer = EncodedRingBuffer(retentionUs = 31_000_000)
        for (second in 0..30) {
            buffer.append(sample(EncodedTrack.VIDEO, second * 1_000_000L, keyFrame = second % 2 == 0))
            buffer.append(sample(EncodedTrack.AUDIO, second * 1_000_000L))
        }

        val snapshot = buffer.snapshot(durationUs = 10_000_000, triggerTimeUs = 29_500_000)

        assertEquals(18_000_000L, snapshot.startTimeUs)
        assertEquals(29_000_000L, snapshot.endTimeUs)
        assertEquals(11_000_000L, snapshot.actualDurationUs)
        assertTrue(snapshot.samples.first { it.track == EncodedTrack.VIDEO }.isKeyFrame)
        assertTrue(snapshot.samples.all { it.presentationTimeUs in 18_000_000L..29_000_000L })
    }

    @Test
    fun `consecutive overlapping snapshots do not consume buffered samples`() {
        val buffer = EncodedRingBuffer(retentionUs = 31_000_000)
        for (second in 0..30) {
            buffer.append(sample(EncodedTrack.VIDEO, second * 1_000_000L, keyFrame = true))
            buffer.append(sample(EncodedTrack.AUDIO, second * 1_000_000L))
        }

        val first = buffer.snapshot(20_000_000, 30_000_000)
        val second = buffer.snapshot(10_000_000, 30_000_000)

        assertEquals(42, first.samples.size)
        assertEquals(22, second.samples.size)
        assertEquals(62, buffer.samplesForTesting().size)
        assertTrue(first.samples.any { firstSample -> second.samples.any { it === firstSample } })
    }

    private fun sample(track: EncodedTrack, ptsUs: Long, keyFrame: Boolean = false) =
        EncodedSample(
            track = track,
            presentationTimeUs = ptsUs,
            flags = if (keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
            data = byteArrayOf((ptsUs / 1_000_000).toByte()),
        )
}
