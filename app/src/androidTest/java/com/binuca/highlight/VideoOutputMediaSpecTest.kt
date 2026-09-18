package com.binuca.highlight

import android.annotation.SuppressLint
import androidx.camera.video.MediaSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.binuca.highlight.capture.BufferedVideoOutput
import com.binuca.highlight.capture.CaptureQuality
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("RestrictedApi")
@RunWith(AndroidJUnit4::class)
class VideoOutputMediaSpecTest {
    @Test
    fun bothQualitiesProvideNonNullMp4SpecificationBeforeBinding() {
        CaptureQuality.entries.forEach { quality ->
            BufferedVideoOutput(quality, Executor { it.run() }, {}, {}, {}, { throw it }).use { output ->
                val spec = output.mediaSpec.fetchData().get()
                assertNotNull(spec)
                assertEquals(MediaSpec.OUTPUT_FORMAT_MPEG_4, spec!!.outputFormat)
                assertEquals(quality.bitrate, spec.videoSpec.bitrate)
                assertEquals(30, spec.videoSpec.encodeFrameRate)
                assertEquals(true, output.isSourceStreamRequired.fetchData().get())
            }
        }
    }
}
