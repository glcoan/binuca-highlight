package com.binuca.highlight.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean

class AacAudioEncoder(
    private val onSample: (EncodedSample) -> Unit,
    private val onFormat: (MediaFormat) -> Unit,
    private val onError: (Throwable) -> Unit,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var codec: MediaCodec? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (!running.compareAndSet(false, true)) return
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            running.set(false)
            throw IllegalStateException("O microfone não informou um buffer válido")
        }
        try {
            val audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minBuffer * 4)
                .build()
            recorder = audioRecord
            check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "Não foi possível abrir o microfone" }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec = encoder
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBuffer * 2)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            recorder = audioRecord
            codec = encoder
            audioRecord.startRecording()
            check(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Não foi possível iniciar a gravação do microfone"
            }
            worker = Thread({ encodeLoop(audioRecord, encoder) }, "Binuca-AudioEncoder").apply { start() }
        } catch (error: Throwable) {
            running.set(false)
            closeResources()
            throw error
        }
    }

    private fun encodeLoop(audioRecord: AudioRecord, encoder: MediaCodec) {
        var submittedFrames = 0L
        val firstPtsUs = System.nanoTime() / 1_000L
        val info = MediaCodec.BufferInfo()
        try {
            while (running.get()) {
                val inputIndex = encoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val input = encoder.getInputBuffer(inputIndex) ?: continue
                    input.clear()
                    val bytesRead = audioRecord.read(input, input.capacity(), AudioRecord.READ_BLOCKING)
                    if (bytesRead > 0) {
                        val ptsUs = firstPtsUs + submittedFrames * 1_000_000L / SAMPLE_RATE
                        submittedFrames += bytesRead / BYTES_PER_FRAME
                        encoder.queueInputBuffer(inputIndex, 0, bytesRead, ptsUs, 0)
                    } else {
                        check(!running.get()) { "Falha ao ler o microfone (código $bytesRead)" }
                        break
                    }
                }
                drainEncoder(encoder, info)
            }
        } catch (error: Throwable) {
            if (running.get()) onError(error)
        } finally {
            closeResources()
        }
    }

    private fun drainEncoder(encoder: MediaCodec, info: MediaCodec.BufferInfo) {
        while (true) {
            when (val outputIndex = encoder.dequeueOutputBuffer(info, 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormat(encoder.outputFormat)
                else -> if (outputIndex >= 0) {
                    val output = encoder.getOutputBuffer(outputIndex)
                    if (output != null && info.size > 0 &&
                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        output.get(bytes)
                        onSample(EncodedSample(EncodedTrack.AUDIO, info.presentationTimeUs, info.flags, bytes))
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

    override fun close() {
        if (!running.getAndSet(false)) return
        runCatching { recorder?.stop() }
        worker?.interrupt()
        if (Thread.currentThread() !== worker) runCatching { worker?.join(1_000) }
        closeResources()
    }

    @Synchronized
    private fun closeResources() {
        runCatching { recorder?.release() }
        recorder = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        worker = null
    }

    companion object {
        private const val SAMPLE_RATE = 48_000
        private const val BYTES_PER_FRAME = 2
    }
}
