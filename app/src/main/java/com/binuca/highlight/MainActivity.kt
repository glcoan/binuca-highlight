package com.binuca.highlight

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.OrientationEventListener
import android.view.Surface as AndroidSurface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.displayCutoutPadding
import com.binuca.highlight.ui.detectedLandscape
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import com.binuca.highlight.capture.ExposureControls
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.ui.res.painterResource
import com.binuca.highlight.capture.zoomLabel
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.binuca.highlight.capture.BufferReadiness
import com.binuca.highlight.capture.CameraSessionState
import com.binuca.highlight.capture.CaptureQuality
import com.binuca.highlight.capture.ClipDuration
import com.binuca.highlight.capture.TriggerSource
import com.binuca.highlight.feedback.FeedbackController
import com.binuca.highlight.remote.RemoteTriggerGate
import com.binuca.highlight.settings.AppSettings
import com.binuca.highlight.ui.CameraEvent
import com.binuca.highlight.ui.CameraViewModel
import com.binuca.highlight.ui.resolveDisplayRotation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: CameraViewModel by viewModels()
    private val remoteGate = RemoteTriggerGate()
    private val orientationHandler = Handler(Looper.getMainLooper())
    private var orientationSensor: OrientationEventListener? = null
    private var pendingLandscape by mutableStateOf<Boolean?>(null)
    private var sensedLandscape: Boolean? = null
    private var ignoredLandscape: Boolean? = null
    private val proposeRotation = Runnable {
        val target = sensedLandscape
        if (target != null && target != isLandscape() && target != ignoredLandscape) {
            if (viewModel.readiness.value.availableUs == 0L) applyOrientation(target)
            else pendingLandscape = target
        }
    }

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun applyOrientation(landscape: Boolean) {
        pendingLandscape = null
        viewModel.stopCamera()
        requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        val displayRotation = windowManager.defaultDisplay.rotation
        val naturalLandscape = isLandscape() xor
            (displayRotation == AndroidSurface.ROTATION_90 || displayRotation == AndroidSurface.ROTATION_270)
        orientationSensor = object : OrientationEventListener(this) {
            override fun onOrientationChanged(degrees: Int) {
                val target = detectedLandscape(degrees, naturalLandscape)
                if (target == sensedLandscape) return
                sensedLandscape = target
                orientationHandler.removeCallbacks(proposeRotation)
                if (target == isLandscape()) {
                    ignoredLandscape = null
                    pendingLandscape = null
                } else if (target != null) {
                    orientationHandler.postDelayed(proposeRotation, 650)
                }
            }
        }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF38D980),
                    secondary = Color(0xFFD8B35B),
                    background = Color(0xFF06110B),
                    surface = Color(0xFF10231A),
                ),
            ) {
                BinucaApp(viewModel)
                pendingLandscape?.let { target ->
                    AlertDialog(
                        onDismissRequest = { ignoredLandscape = target; pendingLandscape = null },
                        title = { Text(if (target) "Mudar para horizontal?" else "Mudar para vertical?") },
                        text = { Text("Ao girar, o buffer será reiniciado e os botões de clipe carregarão novamente. Os clipes já salvos serão mantidos.") },
                        confirmButton = { TextButton(onClick = { applyOrientation(target) }) { Text("Girar e reiniciar") } },
                        dismissButton = { TextButton(onClick = { ignoredLandscape = target; pendingLandscape = null }) { Text("Manter orientação") } },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensedLandscape = null
        if (orientationSensor?.canDetectOrientation() == true) orientationSensor?.enable()
    }

    override fun onPause() {
        orientationSensor?.disable()
        orientationHandler.removeCallbacks(proposeRotation)
        super.onPause()
    }

    override fun onDestroy() {
        orientationSensor?.disable()
        orientationHandler.removeCallbacks(proposeRotation)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (remoteGate.accept(event.action, event.keyCode, event.repeatCount, event.eventTime)) {
            viewModel.requestRemoteCapture()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun BinucaApp(viewModel: CameraViewModel) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(hasPermissions(context)) }
    var requestedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        requestedOnce = true
        permissionsGranted = result[Manifest.permission.CAMERA] == true &&
            result[Manifest.permission.RECORD_AUDIO] == true
    }
    LaunchedEffect(Unit) {
        if (!permissionsGranted) launcher.launch(REQUIRED_PERMISSIONS)
    }
    if (permissionsGranted) {
        CameraScreen(viewModel)
    } else {
        PermissionScreen(
            permanentlyDenied = requestedOnce,
            requestAgain = { launcher.launch(REQUIRED_PERMISSIONS) },
            openSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
        )
    }
}

@Composable
private fun CameraScreen(viewModel: CameraViewModel) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val readiness by viewModel.readiness.collectAsState()
    val controls by viewModel.cameraControls.collectAsState()
    val exposure by viewModel.exposure.collectAsState()
    val error by viewModel.engineError.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentSettings by rememberUpdatedState(settings)
    val saving by viewModel.savingCounts.collectAsState()
    val cooldownSeconds by viewModel.captureCooldownSeconds.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    val feedback = remember { FeedbackController(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startCamera()
                Lifecycle.Event.ON_STOP -> viewModel.stopCamera()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.detachCamera()
            feedback.close()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is CameraEvent.Saved -> {
                    feedback.success(event.duration, currentSettings)
                    "Clipe de ${durationWord(event.duration)} segundos registrado com sucesso!"
                }
                is CameraEvent.Error -> event.message
                is CameraEvent.Wait -> event.message
            }
            banner = message
            launch {
                kotlinx.coroutines.delay(2_400)
                if (banner == message) banner = null
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).displayCutoutPadding()) {
        AndroidView(
            factory = { androidContext ->
                PreviewView(androidContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    doOnAttach {
                        viewModel.attachCamera(
                            lifecycleOwner,
                            this,
                            resolveDisplayRotation(display?.rotation),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ -> viewModel.zoomBy(zoom) }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> viewModel.focusAt(offset.x, offset.y) }
                },
        )

        CameraTopBar(
            onSettings = { showSettings = true },
            onSwitchCamera = { viewModel.toggleCamera() },
            torchOn = controls.torchOn,
            hasFlash = controls.hasFlash,
            onTorch = {
                viewModel.setTorch(!controls.torchOn)
            },
            onExposure = viewModel::adjustExposure,
            exposure = exposure,
            landscape = landscape,
            modifier = Modifier.align(if (landscape) Alignment.CenterStart else Alignment.TopCenter),
        )

        val zoomContent: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (controls.shortcuts.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.widthIn(max = 400.dp).padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.65f))
                        .horizontalScroll(rememberScrollState()).padding(6.dp),
                ) {
                    controls.shortcuts.forEach { shortcut ->
                        val selected = shortcut.lensId == controls.lensId && kotlin.math.abs(shortcut.ratio - controls.zoom) < 0.05f
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(if (selected) Color(0xFF38D980) else Color.White.copy(alpha = 0.08f))
                                .clickable(role = Role.Button) { viewModel.selectZoom(shortcut) }
                                .semantics { contentDescription = "Zoom ${shortcut.label}" },
                        ) {
                            Text(shortcut.label, color = if (selected) Color(0xFF06110B) else Color.White,
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
                if (controls.maxZoom > controls.minZoom) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 18.dp)) {
                        Text(zoomLabel(controls.zoom * controls.lensScale), color = Color.White, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                        Slider(value = controls.zoom.coerceIn(controls.minZoom, controls.maxZoom),
                            onValueChange = viewModel::setZoom, valueRange = controls.minZoom..controls.maxZoom,
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Ajuste contínuo de zoom" })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            }
        }
        val captureContent: @Composable () -> Unit = {
                ClipDuration.entries.forEach { duration ->
                    HighlightButton(
                        duration = duration,
                        progress = readiness.progress(duration),
                        saving = (saving[duration] ?: 0) > 0,
                        cooldownSeconds = cooldownSeconds,
                        onClick = { viewModel.requestCapture(duration, TriggerSource.SCREEN) },
                    )
                }
        }

        if (landscape) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.align(Alignment.CenterEnd).statusBarsPadding().navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
                captureContent()
            }
            Box(contentAlignment = Alignment.BottomCenter,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .padding(start = 84.dp, end = 116.dp, bottom = 10.dp)) {
                zoomContent()
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp)) {
                zoomContent()
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { captureContent() }
            }
        }

        banner?.let { message ->
            Surface(
                color = if (message.contains("sucesso")) Color(0xE6218A52) else Color(0xE6A33333),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            ) {
                Text(message, modifier = Modifier.padding(18.dp), textAlign = TextAlign.Center)
            }
        }
        error?.let { message ->
            Surface(
                color = Color(0xD9A33333),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 92.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text(message, Modifier.padding(12.dp)) }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            onDismiss = { showSettings = false },
            setDuration = viewModel::setRemoteDuration,
            setQuality = viewModel::setQuality,
            setSound = viewModel::setSound,
            setVibration = viewModel::setVibration,
            setVoice = viewModel::setVoice,
            testRemote = viewModel::requestRemoteCapture,
            exposure = exposure,
            resetExposure = viewModel::resetExposure,
        )
    }
}

@Composable
private fun CameraTopBar(
    onSettings: () -> Unit,
    onSwitchCamera: () -> Unit,
    torchOn: Boolean,
    hasFlash: Boolean,
    onTorch: () -> Unit,
    onExposure: (Int) -> Unit,
    exposure: ExposureControls,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val buttons: @Composable () -> Unit = {
        ExposureButton(-1, exposure) { onExposure(-1) }
        ExposureButton(1, exposure) { onExposure(1) }
        CameraControlIcon(R.drawable.ic_flash, if (torchOn) "Desligar lanterna" else "Ligar lanterna", onTorch, hasFlash, torchOn)
        CameraControlIcon(R.drawable.ic_switch_camera, "Alternar câmera frontal e traseira", onSwitchCamera)
        CameraControlIcon(R.drawable.ic_settings, "Configurações", onSettings)
    }
    if (landscape) {
        Column(modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            buttons()
        }
    } else {
        Row(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            buttons()
        }
    }
}

@Composable
private fun ExposureButton(direction: Int, exposure: ExposureControls, onClick: () -> Unit) {
    var confirmed by remember { mutableStateOf(false) }
    LaunchedEffect(exposure.revision) {
        confirmed = exposure.revision > 0 && (exposure.direction == direction || exposure.direction == 0)
        if (confirmed) {
            kotlinx.coroutines.delay(650)
            confirmed = false
        }
    }
    val enabled = !exposure.busy && if (direction < 0) exposure.index > exposure.min else exposure.index < exposure.max
    IconButton(onClick, enabled = enabled,
        modifier = Modifier.size(48.dp).background(if (confirmed) Color(0xFF38D980) else Color.Black.copy(alpha = 0.6f), CircleShape)
            .semantics { contentDescription = (if (direction < 0) "Diminuir exposição" else "Aumentar exposição") + ", ${exposure.label}" }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val color = if (confirmed) Color(0xFF06110B) else Color.White.copy(alpha = if (enabled) 1f else 0.4f)
            Text(if (direction < 0) "−EV" else "+EV", color = color, fontSize = 12.sp)
            Text(exposure.label.removeSuffix(" EV"), color = color, fontSize = 10.sp)
        }
    }
}

@Composable
private fun CameraControlIcon(icon: Int, label: String, onClick: () -> Unit, enabled: Boolean = true, selected: Boolean = false) {
    IconButton(onClick, enabled = enabled,
        modifier = Modifier.size(48.dp).background(if (selected) Color(0xFF38D980) else Color.Black.copy(alpha = 0.6f), CircleShape)) {
        Icon(painterResource(icon), label, modifier = Modifier.size(24.dp),
            tint = if (!enabled) Color.White.copy(alpha = 0.3f) else if (selected) Color(0xFF06110B) else Color.White)
    }
}

@Composable
private fun HighlightButton(
    duration: ClipDuration,
    progress: Float,
    saving: Boolean,
    cooldownSeconds: Int,
    onClick: () -> Unit,
) {
    val enabled = progress >= 1f && cooldownSeconds == 0
    val ballColor = when (duration) {
        ClipDuration.TEN_SECONDS -> Color(0xFFE5AD16)
        ClipDuration.TWENTY_SECONDS -> Color(0xFF2374D5)
        ClipDuration.THIRTY_SECONDS -> Color(0xFFD83240)
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = if (enabled) Color(0xFF38D980) else Color.White.copy(alpha = 0.72f),
            trackColor = Color.Black.copy(alpha = 0.50f),
            strokeWidth = 5.dp,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(70.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(ballColor, ballColor, Color(0xFF161B22))))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                .clickable(enabled = cooldownSeconds == 0, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "Salvar últimos ${duration.seconds} segundos" },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(Color(0xFFFFF9E9), CircleShape),
            ) {
                Text(if (cooldownSeconds > 0) "${cooldownSeconds}s" else duration.seconds.toString(), color = Color(0xFF15191E), fontWeight = FontWeight.Black, fontSize = 23.sp)
            }
            if (saving) {
                CircularProgressIndicator(Modifier.size(58.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    settings: AppSettings,
    onDismiss: () -> Unit,
    setDuration: (ClipDuration) -> Unit,
    setQuality: (CaptureQuality) -> Unit,
    setSound: (Boolean) -> Unit,
    setVibration: (Boolean) -> Unit,
    setVoice: (Boolean) -> Unit,
    testRemote: () -> Unit,
    exposure: ExposureControls,
    resetExposure: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF10231A)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Configurações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
            }
            Text("Exposição: ${exposure.label}", modifier = Modifier.padding(top = 8.dp))
            Button(resetExposure, enabled = exposure.index != 0 && !exposure.busy,
                modifier = Modifier.fillMaxWidth()) { Text("Restaurar exposição padrão (0 EV)") }
            Text("Clipe padrão pelo botão de captura")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClipDuration.entries.forEach { duration ->
                    FilterChip(
                        selected = settings.remoteDuration == duration,
                        onClick = { setDuration(duration) },
                        label = { Text("${duration.seconds} s") },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Qualidade (reinicia o buffer)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.quality == CaptureQuality.FULL_HD,
                    onClick = { setQuality(CaptureQuality.FULL_HD) },
                    label = { Text("1080p") },
                )
                FilterChip(
                    selected = settings.quality == CaptureQuality.HD,
                    onClick = { setQuality(CaptureQuality.HD) },
                    label = { Text("720p") },
                )
            }
            SettingSwitch("Som de caixa registradora", settings.soundEnabled, setSound)
            SettingSwitch("Vibração", settings.vibrationEnabled, setVibration)
            SettingSwitch("Confirmação falada", settings.voiceEnabled, setVoice)
            Text("Som e voz usam o volume de mídia. Com ambos ativados, tocam juntos.",
                color = Color.White.copy(alpha = 0.64f), fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            Button(testRemote, modifier = Modifier.fillMaxWidth()) { Text("Testar controle remoto") }
            Text(
                "Use o controle de câmera do relógio conectado por Bluetooth para registrar um clipe. O dispositivo precisa enviar uma ação do botão de captura compatível com o aplicativo.",
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun PermissionScreen(
    permanentlyDenied: Boolean,
    requestAgain: () -> Unit,
    openSettings: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp),
        ) {
            Text("Binuca Highlight", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            Text(
                "Câmera e microfone são necessários para manter o buffer de vídeo com áudio.",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(if (permanentlyDenied) openSettings else requestAgain) {
                Text(if (permanentlyDenied) "Abrir configurações" else "Permitir câmera e microfone")
            }
        }
    }
}

private fun hasPermissions(context: android.content.Context): Boolean = REQUIRED_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun durationWord(duration: ClipDuration): String = when (duration) {
    ClipDuration.TEN_SECONDS -> "dez"
    ClipDuration.TWENTY_SECONDS -> "vinte"
    ClipDuration.THIRTY_SECONDS -> "trinta"
}

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
)
