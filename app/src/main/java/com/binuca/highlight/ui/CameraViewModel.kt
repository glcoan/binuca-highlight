package com.binuca.highlight.ui

import android.annotation.SuppressLint
import android.app.Application
import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.binuca.highlight.capture.CameraCaptureEngine
import com.binuca.highlight.capture.CaptureQuality
import com.binuca.highlight.capture.CaptureRequest
import com.binuca.highlight.capture.CaptureResult
import com.binuca.highlight.capture.ClipDuration
import com.binuca.highlight.capture.TriggerSource
import com.binuca.highlight.settings.AppSettings
import com.binuca.highlight.settings.SettingsRepository
import com.binuca.highlight.storage.HighlightMuxer
import com.binuca.highlight.storage.SerializedSaveQueue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = CameraCaptureEngine(application)
    private val settingsRepository = SettingsRepository(application)
    private val muxer = HighlightMuxer(application.contentResolver)

    val readiness = engine.readiness
    val sessionState = engine.sessionState
    val engineError = engine.errorMessage
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings(),
    )
    private val _events = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    private val _savingCounts = MutableStateFlow<Map<ClipDuration, Int>>(emptyMap())
    val savingCounts = _savingCounts.asStateFlow()
    private val saveQueue = SerializedSaveQueue<SaveJob>(viewModelScope) { job ->
        val result = muxer.write(job.snapshot, job.formats, job.request.duration)
        decrementSaving(job.request.duration)
        when (result) {
            is CaptureResult.Success -> _events.emit(CameraEvent.Saved(job.request.duration, result))
            is CaptureResult.Failure -> _events.emit(CameraEvent.Error(result.reason))
        }
    }

    private var lifecycleOwner: LifecycleOwner? = null
    @SuppressLint("StaticFieldLeak")
    private var previewView: PreviewView? = null
    private var rotation: Int = Surface.ROTATION_0
    private var frontCamera = false
    private var started = false
    private var activeQuality = CaptureQuality.FULL_HD

    init {
        viewModelScope.launch {
            settings.map { it.quality }.distinctUntilChanged().collect { persistedQuality ->
                if (started && persistedQuality != activeQuality) {
                    activeQuality = persistedQuality
                    engine.restart(quality = persistedQuality)
                }
            }
        }
    }

    fun attachCamera(owner: LifecycleOwner, view: PreviewView, targetRotation: Int) {
        lifecycleOwner = owner
        previewView = view
        rotation = targetRotation
        startCamera()
    }

    fun startCamera() {
        val owner = lifecycleOwner ?: return
        val view = previewView ?: return
        if (started) return
        started = true
        activeQuality = settings.value.quality
        engine.start(owner, view, activeQuality, frontCamera, rotation)
    }

    fun stopCamera() {
        started = false
        engine.stop()
    }

    fun detachCamera() {
        started = false
        engine.detach()
        lifecycleOwner = null
        previewView = null
    }

    fun requestCapture(duration: ClipDuration, source: TriggerSource) {
        val frozen = runCatching { engine.captureSnapshot(duration) }.getOrElse { error ->
            _events.tryEmit(CameraEvent.Wait(error.message ?: "O buffer ainda não está pronto"))
            return
        }
        incrementSaving(duration)
        val accepted = saveQueue.submit(
            SaveJob(
                request = CaptureRequest(duration, frozen.first.endTimeUs, source),
                snapshot = frozen.first,
                formats = frozen.second,
            ),
        )
        if (!accepted) {
            decrementSaving(duration)
            _events.tryEmit(CameraEvent.Error("A fila de salvamento foi encerrada"))
        }
    }

    fun requestRemoteCapture() = requestCapture(settings.value.remoteDuration, TriggerSource.REMOTE)

    fun toggleCamera() {
        frontCamera = !frontCamera
        engine.restart(useFrontCamera = frontCamera)
    }

    fun setQuality(value: CaptureQuality) {
        viewModelScope.launch { settingsRepository.setQuality(value) }
        activeQuality = value
        engine.restart(quality = value)
    }

    fun setRemoteDuration(value: ClipDuration) = viewModelScope.launch {
        settingsRepository.setRemoteDuration(value)
    }

    fun setSound(value: Boolean) = viewModelScope.launch { settingsRepository.setSound(value) }
    fun setVibration(value: Boolean) = viewModelScope.launch { settingsRepository.setVibration(value) }
    fun setVoice(value: Boolean) = viewModelScope.launch { settingsRepository.setVoice(value) }
    fun zoomBy(scale: Float) = engine.zoomBy(scale)
    fun setZoom(ratio: Float) = engine.setZoomRatio(ratio)
    fun zoomRatios(): List<Float> = engine.availableZoomRatios()
    fun focusAt(x: Float, y: Float) = engine.focusAt(x, y)
    fun setTorch(enabled: Boolean) = engine.setTorch(enabled)
    fun hasTorch(): Boolean = engine.hasTorch()
    fun adjustExposure(delta: Int) = engine.adjustExposure(delta)

    private fun incrementSaving(duration: ClipDuration) {
        _savingCounts.value = _savingCounts.value.toMutableMap().apply {
            this[duration] = (this[duration] ?: 0) + 1
        }
    }

    private fun decrementSaving(duration: ClipDuration) {
        _savingCounts.value = _savingCounts.value.toMutableMap().apply {
            val next = (this[duration] ?: 1) - 1
            if (next <= 0) remove(duration) else this[duration] = next
        }
    }

    override fun onCleared() {
        saveQueue.close()
        engine.close()
        super.onCleared()
    }
}

private data class SaveJob(
    val request: CaptureRequest,
    val snapshot: com.binuca.highlight.capture.CaptureSnapshot,
    val formats: com.binuca.highlight.capture.CaptureFormats,
)

sealed interface CameraEvent {
    data class Saved(val duration: ClipDuration, val result: CaptureResult.Success) : CameraEvent
    data class Error(val message: String) : CameraEvent
    data class Wait(val message: String) : CameraEvent
}
