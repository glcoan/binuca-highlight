package com.binuca.highlight.capture

import java.util.ArrayDeque

class EncodedRingBuffer(
    private val retentionUs: Long = 31_000_000L,
) {
    private val samples = ArrayDeque<EncodedSample>()
    private var latestTimestampUs = Long.MIN_VALUE

    @Synchronized
    fun append(sample: EncodedSample) {
        require(sample.presentationTimeUs >= 0) { "Timestamp must be non-negative" }
        samples.addLast(sample)
        latestTimestampUs = maxOf(latestTimestampUs, sample.presentationTimeUs)
        evictExpired()
    }

    @Synchronized
    fun readiness(): BufferReadiness {
        val video = bounds(EncodedTrack.VIDEO) ?: return BufferReadiness.Empty
        val audio = bounds(EncodedTrack.AUDIO) ?: return BufferReadiness.Empty
        val commonStart = maxOf(video.first, audio.first)
        val commonEnd = minOf(video.second, audio.second)
        val availableUs = (commonEnd - commonStart).coerceAtLeast(0L).coerceAtMost(retentionUs)
        return BufferReadiness(
            availableUs = availableUs,
            progress10 = progress(availableUs, ClipDuration.TEN_SECONDS),
            progress20 = progress(availableUs, ClipDuration.TWENTY_SECONDS),
            progress30 = progress(availableUs, ClipDuration.THIRTY_SECONDS),
        )
    }

    @Synchronized
    fun snapshot(durationUs: Long, triggerTimeUs: Long): CaptureSnapshot {
        require(durationUs > 0) { "Duration must be positive" }
        val latestVideo = samples.lastOrNull {
            it.track == EncodedTrack.VIDEO && it.presentationTimeUs <= triggerTimeUs
        } ?: throw SnapshotUnavailableException("Ainda não há vídeo disponível")
        val latestAudio = samples.lastOrNull {
            it.track == EncodedTrack.AUDIO && it.presentationTimeUs <= triggerTimeUs
        } ?: throw SnapshotUnavailableException("Ainda não há áudio disponível")
        val endUs = minOf(latestVideo.presentationTimeUs, latestAudio.presentationTimeUs)
        val requestedStartUs = endUs - durationUs
        val startUs = samples.lastOrNull {
            it.track == EncodedTrack.VIDEO && it.isKeyFrame &&
                it.presentationTimeUs <= requestedStartUs
        }?.presentationTimeUs
            ?: throw SnapshotUnavailableException("Aguarde o próximo quadro-chave")
        val snapshotSamples = samples
            .filter { it.presentationTimeUs in startUs..endUs }
            .sortedWith(compareBy<EncodedSample> { it.presentationTimeUs }.thenBy { it.track.ordinal })
        if (snapshotSamples.none { it.track == EncodedTrack.AUDIO } ||
            snapshotSamples.none { it.track == EncodedTrack.VIDEO }
        ) {
            throw SnapshotUnavailableException("Áudio e vídeo ainda não estão sincronizados")
        }
        return CaptureSnapshot(startUs, endUs, snapshotSamples)
    }

    @Synchronized
    fun clear() {
        samples.clear()
        latestTimestampUs = Long.MIN_VALUE
    }

    @Synchronized
    internal fun samplesForTesting(): List<EncodedSample> = samples.toList()

    private fun evictExpired() {
        if (latestTimestampUs == Long.MIN_VALUE) return
        val cutoffUs = latestTimestampUs - retentionUs
        samples.removeIf { it.presentationTimeUs < cutoffUs }
    }

    private fun bounds(track: EncodedTrack): Pair<Long, Long>? {
        var earliest = Long.MAX_VALUE
        var latest = Long.MIN_VALUE
        samples.forEach { sample ->
            if (sample.track == track) {
                earliest = minOf(earliest, sample.presentationTimeUs)
                latest = maxOf(latest, sample.presentationTimeUs)
            }
        }
        return if (latest == Long.MIN_VALUE) null else earliest to latest
    }

    private fun progress(availableUs: Long, duration: ClipDuration): Float =
        (availableUs.toDouble() / duration.durationUs).coerceIn(0.0, 1.0).toFloat()
}
