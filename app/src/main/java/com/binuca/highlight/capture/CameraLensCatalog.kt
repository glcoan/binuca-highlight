package com.binuca.highlight.capture

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlin.math.atan

internal data class CameraLens(
    val id: String,
    val selector: CameraSelector,
    val scale: Float,
)

/** Only uses cameras exposed by the public CameraX/Camera2 APIs. */
@SuppressLint("UnsafeOptInUsageError", "RestrictedApi")
internal fun discoverLenses(provider: ProcessCameraProvider, front: Boolean): List<CameraLens> {
    val defaultSelector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    val primary = provider.getCameraInfo(defaultSelector)
    val primaryId = Camera2CameraInfo.from(primary).cameraId
    fun angle(info: CameraInfo): Double? = runCatching {
        val camera2 = Camera2CameraInfo.from(info)
        val sensor = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return@runCatching null
        val focal = camera2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: return@runCatching null
        if (focal <= 0) null else 2 * atan(sensor.width.toDouble() / (2 * focal))
    }.getOrNull()
    val primaryAngle = angle(primary)
    val result = mutableListOf(CameraLens("default", defaultSelector, 1f))
    val seen = mutableSetOf(primaryId)
    defaultSelector.filter(provider.availableCameraInfos).forEach { logical ->
        val logicalId = Camera2CameraInfo.from(logical).cameraId
        fun add(info: CameraInfo, physical: Boolean) {
            runCatching {
                val id = Camera2CameraInfo.from(info).cameraId
                if (id in seen) return@runCatching
                val fov = angle(info) ?: return@runCatching
                val scale = (primaryAngle?.div(fov) ?: info.intrinsicZoomRatio.toDouble()).toFloat()
                if (!scale.isFinite() || scale <= 0 || kotlin.math.abs(scale - 1f) < 0.08f) return@runCatching
                val builder = CameraSelector.Builder().addCameraFilter { infos ->
                    infos.filter { Camera2CameraInfo.from(it).cameraId == logicalId }
                }
                if (physical) builder.setPhysicalCameraId(id)
                result.add(CameraLens(id, builder.build(), scale))
                seen.add(id)
            }
        }
        add(logical, false)
        runCatching { logical.physicalCameraInfos }.getOrDefault(emptySet()).forEach { add(it, true) }
    }
    return result.sortedBy { it.scale }
}
