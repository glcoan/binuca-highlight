package com.binuca.highlight

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
            }
        }
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val readiness by viewModel.readiness.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val error by viewModel.engineError.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentSettings by rememberUpdatedState(settings)
    val saving by viewModel.savingCounts.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var zoomRatios by remember { mutableStateOf(listOf(1f)) }
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
    LaunchedEffect(sessionState) {
        if (sessionState == CameraSessionState.Ready) zoomRatios = viewModel.zoomRatios()
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { androidContext ->
                PreviewView(androidContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    viewModel.attachCamera(
                        lifecycleOwner,
                        this,
                        resolveDisplayRotation(display?.rotation),
                    )
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
            sessionState = sessionState,
            onSettings = { showSettings = true },
            onSwitchCamera = { viewModel.toggleCamera() },
            torchOn = torchOn,
            onTorch = {
                torchOn = !torchOn
                viewModel.setTorch(torchOn)
            },
            onExposure = viewModel::adjustExposure,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
        ) {
            if (zoomRatios.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    zoomRatios.forEach { ratio ->
                        Button(
                            onClick = { viewModel.setZoom(ratio) },
                            contentPadding = ButtonDefaults.ContentPadding,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.55f)),
                        ) { Text("${ratio}×", fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ClipDuration.entries.forEach { duration ->
                    HighlightButton(
                        duration = duration,
                        progress = readiness.progress(duration),
                        saving = (saving[duration] ?: 0) > 0,
                        onClick = { viewModel.requestCapture(duration, TriggerSource.SCREEN) },
                    )
                }
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
        )
    }
}

@Composable
private fun CameraTopBar(
    sessionState: CameraSessionState,
    onSettings: () -> Unit,
    onSwitchCamera: () -> Unit,
    torchOn: Boolean,
    onTorch: () -> Unit,
    onExposure: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.42f)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("BINUCA", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                when (sessionState) {
                    CameraSessionState.Preparing -> "Preparando câmera"
                    CameraSessionState.Buffering -> "Carregando buffer"
                    CameraSessionState.Ready -> "Pronto"
                    CameraSessionState.Recovering -> "Recuperando"
                    CameraSessionState.Stopped -> "Parado"
                },
                color = if (sessionState == CameraSessionState.Ready) Color(0xFF38D980) else Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
            IconButton({ onExposure(-1) }) { Text("−EV", fontSize = 11.sp) }
            IconButton({ onExposure(1) }) { Text("+EV", fontSize = 11.sp) }
            IconButton(onTorch) { Text(if (torchOn) "⚡" else "○", fontSize = 20.sp) }
            IconButton(onSwitchCamera) { Text("↻", fontSize = 24.sp) }
            IconButton(onSettings) { Text("⚙", fontSize = 22.sp) }
        }
    }
}

@Composable
private fun HighlightButton(
    duration: ClipDuration,
    progress: Float,
    saving: Boolean,
    onClick: () -> Unit,
) {
    val enabled = progress >= 1f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = if (enabled) Color(0xFF38D980) else Color.White.copy(alpha = 0.72f),
            trackColor = Color.Black.copy(alpha = 0.50f),
            strokeWidth = 5.dp,
        )
        Button(
            onClick = onClick,
            enabled = true,
            shape = CircleShape,
            contentPadding = ButtonDefaults.ContentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xD9146B40),
            ),
            modifier = Modifier.size(70.dp).alpha(if (enabled) 1f else 0.74f),
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("${duration.seconds} s", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF10231A)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Configurações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onDismiss) { Text("×", fontSize = 28.sp) }
            }
            Text("Duração do controle remoto")
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
            SettingSwitch("Som de confirmação", settings.soundEnabled, setSound)
            SettingSwitch("Vibração", settings.vibrationEnabled, setVibration)
            SettingSwitch("Confirmação falada", settings.voiceEnabled, setVoice)
            Text(
                "Voz gerada por IA da OpenAI",
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            Button(testRemote, modifier = Modifier.fillMaxWidth()) { Text("Testar controle remoto") }
            Text(
                "A Mi Band deve estar pareada como controle de câmera e enviar uma tecla de volume.",
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
