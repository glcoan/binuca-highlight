package com.binuca.highlight.capture

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Range
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraCaptureEngine(
    private val context: Context,
) : AutoCloseable {
    private val ringBuffer = EncodedRingBuffer()
    private val formats = CaptureFormatRegistry()
    private val codecExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _readiness = MutableStateFlow(BufferReadiness.Empty)
    val readiness: StateFlow<BufferReadiness> = _readiness.asStateFlow()
    private val _sessionState = MutableStateFlow(CameraSessionState.Stopped)
    val sessionState: StateFlow<CameraSessionState> = _sessionState.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoOutput: BufferedVideoOutput? = null
    private var audioEncoder: AacAudioEncoder? = null
    private var owner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var quality = CaptureQuality.FULL_HD
    private var useFrontCamera = false
    private var targetRotation = 0
    private var active = false
    private var recoveryAttempted = false

    fun start(
        lifecycleOwner: LifecycleOwner,
        view: PreviewView,
        quality: CaptureQuality,
        useFrontCamera: Boolean,
        targetRotation: Int,
    ) {
        this.owner = lifecycleOwner
        previewView = view
        this.quality = quality
        this.useFrontCamera = useFrontCamera
        this.targetRotation = targetRotation
        active = true
        recoveryAttempted = false
        resetBuffer()
        bindSession()
    }

    fun restart(quality: CaptureQuality = this.quality, useFrontCamera: Boolean = this.useFrontCamera) {
        this.quality = quality
        this.useFrontCamera = useFrontCamera
        resetBuffer()
        releaseSession()
        if (active) bindSession()
    }

    @SuppressLint("RestrictedApi")
    private fun bindSession() {
        val lifecycleOwner = owner ?: return
        val view = previewView ?: return
        _sessionState.value = CameraSessionState.Preparing
        _errorMessage.value = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                if (!active) return@addListener
                val cameraProvider = future.get()
                provider = cameraProvider
                cameraProvider.unbindAll()
                val preview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build()
                    .also { it.setSurfaceProvider(view.surfaceProvider) }
                val output = BufferedVideoOutput(
                    quality = quality,
                    callbackExecutor = codecExecutor,
                    onSample = ::onEncodedSample,
                    onFormat = formats::setVideo,
                    onRotation = formats::setRotation,
                    onError = ::handleSessionError,
                )
                videoOutput = output
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            quality.targetSize,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build()
                val videoCapture = VideoCapture.Builder(output)
                    .setResolutionSelector(resolutionSelector)
                    .setTargetFrameRate(Range(30, 30))
                    .setTargetRotation(targetRotation)
                    .build()
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
                audioEncoder = AacAudioEncoder(
                    onSample = ::onEncodedSample,
                    onFormat = formats::setAudio,
                    onError = ::handleSessionError,
                ).also { it.start() }
                _sessionState.value = CameraSessionState.Buffering
            } catch (error: Throwable) {
                handleSessionError(error)
            }
        }, mainExecutor)
    }

    private fun onEncodedSample(sample: EncodedSample) {
        ringBuffer.append(sample)
        val value = ringBuffer.readiness()
        _readiness.value = value
        _sessionState.value = if (value.isReady(ClipDuration.TEN_SECONDS)) {
            CameraSessionState.Ready
        } else {
            CameraSessionState.Buffering
        }
    }

    fun captureSnapshot(duration: ClipDuration): Pair<CaptureSnapshot, CaptureFormats> {
        if (!readiness.value.isReady(duration)) {
            val missingSeconds = ((duration.durationUs - readiness.value.availableUs + 999_999) / 1_000_000)
            throw SnapshotUnavailableException("Aguarde $missingSeconds segundos")
        }
        val captureFormats = formats.current()
            ?: throw SnapshotUnavailableException("Os codecs ainda estão preparando o arquivo")
        return ringBuffer.snapshot(duration.durationUs, Long.MAX_VALUE) to captureFormats
    }

    fun setZoomRatio(ratio: Float) {
        val zoom = camera?.cameraInfo?.zoomState?.value ?: return
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio))
    }

    fun zoomBy(scaleFactor: Float) {
        val zoom = camera?.cameraInfo?.zoomState?.value ?: return
        setZoomRatio(zoom.zoomRatio * scaleFactor)
    }

    fun availableZoomRatios(): List<Float> {
        val zoom = camera?.cameraInfo?.zoomState?.value ?: return listOf(1f)
        return listOf(0.5f, 1f, 2f, 3f, 5f, 10f)
            .filter { it in zoom.minZoomRatio..zoom.maxZoomRatio }
            .ifEmpty { listOf(zoom.minZoomRatio) }
    }

    fun focusAt(x: Float, y: Float) {
        val view = previewView ?: return
        val point = view.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun setTorch(enabled: Boolean) {
        if (camera?.cameraInfo?.hasFlashUnit() == true) camera?.cameraControl?.enableTorch(enabled)
    }

    fun hasTorch(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    fun adjustExposure(delta: Int) {
        val state = camera?.cameraInfo?.exposureState ?: return
        val next = (state.exposureCompensationIndex + delta).coerceIn(
            state.exposureCompensationRange.lower,
            state.exposureCompensationRange.upper,
        )
        camera?.cameraControl?.setExposureCompensationIndex(next)
    }

    fun stop() {
        active = false
        releaseSession()
        resetBuffer()
        _sessionState.value = CameraSessionState.Stopped
    }

    fun detach() {
        stop()
        owner = null
        previewView = null
    }

    private fun handleSessionError(error: Throwable) {
        mainHandler.post {
            if (!active) return@post
            _errorMessage.value = error.message ?: "Falha na câmera ou no microfone"
            if (!recoveryAttempted) {
                recoveryAttempted = true
                _sessionState.value = CameraSessionState.Recovering
                releaseSession()
                resetBuffer()
                mainHandler.postDelayed({ if (active) bindSession() }, 1_000)
            } else {
                _sessionState.value = CameraSessionState.Stopped
            }
        }
    }

    private fun resetBuffer() {
        ringBuffer.clear()
        formats.clear()
        _readiness.value = BufferReadiness.Empty
    }

    private fun releaseSession() {
        runCatching { provider?.unbindAll() }
        camera = null
        audioEncoder?.close()
        audioEncoder = null
        videoOutput?.close()
        videoOutput = null
    }

    override fun close() {
        detach()
        codecExecutor.shutdownNow()
    }
}
