package org.aiguardian.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GuardianHome()
            }
        }
    }
}

@Composable
private fun GuardianHome() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraRunning by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var ttsReady by remember { mutableStateOf(false) }
    val ttsEngine = remember { mutableStateOf<TextToSpeech?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermission = granted
        cameraRunning = granted
        cameraError = if (granted) null else "Camera permission is required to start live video."
    }

    DisposableEffect(context) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine.language = Locale.US
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                ttsReady = true
            }
        }
        ttsEngine.value = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            ttsEngine.value = null
            ttsReady = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF101820)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AI Guardian",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Text(
                text = "Live visual awareness assistant",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB8C7D1)
            )

            if (cameraRunning && cameraPermission) {
                CameraPreview(
                    lifecycleOwner = lifecycleOwner,
                    onError = { cameraError = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .semantics { contentDescription = "Live phone camera preview" }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF1C2A35), RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Camera is stopped",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Start the camera when you are ready. Video is processed on this phone in this initial app shell.",
                        color = Color(0xFFCFD8DC)
                    )
                }
            }

            cameraError?.let {
                Text(text = it, color = Color(0xFFFFB4AB))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .semantics { contentDescription = if (cameraRunning) "Stop live camera" else "Start live camera" },
                    onClick = {
                        if (cameraRunning) {
                            cameraRunning = false
                            cameraError = null
                        } else if (cameraPermission) {
                            cameraRunning = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                ) {
                    Text(if (cameraRunning) "Stop camera" else "Start camera")
                }

                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .semantics { contentDescription = "Test spoken audio through the selected Android audio output" },
                    enabled = ttsReady,
                    onClick = {
                        ttsEngine.value?.speak(
                            "AI Guardian audio test. If you hear this in your Bluetooth headset, audio output is working.",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "guardian-audio-test"
                        )
                    }
                ) {
                    Text("Test voice")
                }
            }

            Text(
                text = if (ttsReady) {
                    "Voice ready • Android system audio output"
                } else {
                    "Preparing voice engine…"
                },
                color = Color(0xFFB8C7D1),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Experimental awareness aid only. Do not rely on it for navigation, road crossing, or collision avoidance.",
                color = Color(0xFFFFD6A5),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var previewUseCase: Preview? = null

        cameraProviderFuture.addListener({
            try {
                provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                previewUseCase = preview
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )
            } catch (error: Exception) {
                onError("Could not start camera: ${error.localizedMessage ?: "unknown camera error"}")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            previewUseCase?.let { useCase -> provider?.unbind(useCase) }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
            .background(Color.Black, RoundedCornerShape(20.dp))
    )
}
