package com.binuca.highlight.capture

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.camera.core.SurfaceRequest
import androidx.camera.video.VideoOutput
import java.util.concurrent.Executor

class BufferedVideoOutput(
    private val quality: CaptureQuality,
    private val callbackExecutor: Executor,
    private val onSample: (EncodedSample) -> Unit,
    private val onFormat: (MediaFormat) -> Unit,
    private val onRotation: (Int) -> Unit,
    private val onError: (Throwable) -> Unit,
) : VideoOutput, AutoCloseable {
    private val codecThread = HandlerThread("Binuca-VideoEncoder").apply { start() }
    private val codecHandler = Handler(codecThread.looper)
    @Volatile private var codec: MediaCodec? = null
    @Volatile private var inputSurface: Surface? = null
    @Volatile private var closing = false

    override fun onSurfaceRequested(surfaceRequest: SurfaceRequest) {
        try {
            closeCodec()
            closing = false
            val size = surfaceRequest.resolution
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                size.width,
                size.height,
            ).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }
            encoder.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

                override fun onOutputBufferAvailable(
                    codec: MediaCodec,
                    index: Int,
                    info: MediaCodec.BufferInfo,
                ) {
                    try {
                        val output = codec.getOutputBuffer(index)
                        if (output != null && info.size > 0 &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            output.get(bytes)
                            onSample(
                                EncodedSample(
                                    EncodedTrack.VIDEO,
                                    info.presentationTimeUs,
                                    info.flags,
                                    bytes,
                                ),
                            )
                        }
                    } catch (error: Throwable) {
                        if (!closing) onError(error)
                    } finally {
                        runCatching { codec.releaseOutputBuffer(index, false) }
                    }
                }

                override fun onError(codec: MediaCodec, exception: MediaCodec.CodecException) {
                    if (!closing) onError(exception)
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    onFormat(format)
                }
            }, codecHandler)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            codec = encoder
            inputSurface = surface
            encoder.start()
            surfaceRequest.setTransformationInfoListener(callbackExecutor) { transformation ->
                onRotation(transformation.rotationDegrees)
            }
            surfaceRequest.provideSurface(surface, callbackExecutor) {
                if (inputSurface === surface) closeCodec()
            }
        } catch (error: Throwable) {
            surfaceRequest.willNotProvideSurface()
            onError(error)
        }
    }

    override fun close() {
        closeCodec()
        codecThread.quitSafely()
    }

    @Synchronized
    private fun closeCodec() {
        closing = true
        val oldCodec = codec
        codec = null
        runCatching { oldCodec?.stop() }
        runCatching { oldCodec?.release() }
        runCatching { inputSurface?.release() }
        inputSurface = null
    }
}

enum class CaptureQuality(val bitrate: Int, val targetSize: Size) {
    FULL_HD(8_000_000, Size(1920, 1080)),
    HD(4_000_000, Size(1280, 720)),
}
