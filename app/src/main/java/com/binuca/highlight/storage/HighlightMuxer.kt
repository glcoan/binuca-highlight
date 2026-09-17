package com.binuca.highlight.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.media.MediaCodec
import android.media.MediaMuxer
import android.os.Environment
import android.provider.MediaStore
import com.binuca.highlight.capture.CaptureFormats
import com.binuca.highlight.capture.CaptureResult
import com.binuca.highlight.capture.CaptureSnapshot
import com.binuca.highlight.capture.ClipDuration
import com.binuca.highlight.capture.EncodedTrack
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HighlightMuxer(
    private val resolver: ContentResolver,
    private val fileNamer: HighlightFileNamer = HighlightFileNamer(),
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun write(
        snapshot: CaptureSnapshot,
        formats: CaptureFormats,
        requestedDuration: ClipDuration,
    ): CaptureResult = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileNamer.create(Instant.now(clock), requestedDuration))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/Binuca Highlight",
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: return@withContext CaptureResult.Failure("Não foi possível criar o arquivo na Galeria")
        try {
            resolver.openFileDescriptor(uri, "w", null).use { descriptor ->
                checkNotNull(descriptor) { "Não foi possível abrir o arquivo do highlight" }
                val muxer = MediaMuxer(descriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                try {
                    val videoTrack = muxer.addTrack(formats.video)
                    val audioTrack = muxer.addTrack(formats.audio)
                    muxer.setOrientationHint(formats.rotationDegrees)
                    muxer.start()
                    val normalizer = TimestampNormalizer(snapshot.startTimeUs)
                    val info = MediaCodec.BufferInfo()
                    snapshot.samples.forEach { sample ->
                        val flags = sample.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG.inv()
                        info.set(0, sample.size, normalizer.normalize(sample.presentationTimeUs), flags)
                        muxer.writeSampleData(
                            if (sample.track == EncodedTrack.VIDEO) videoTrack else audioTrack,
                            sample.readOnlyData(),
                            info,
                        )
                    }
                    muxer.stop()
                } finally {
                    muxer.release()
                }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            CaptureResult.Success(uri, snapshot.actualDurationUs)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            CaptureResult.Failure(error.message ?: "Falha ao salvar o highlight")
        }
    }
}
