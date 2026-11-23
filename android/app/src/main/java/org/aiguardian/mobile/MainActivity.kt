package org.aiguardian.mobile

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.draw.clipToBounds
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.OutputTransform
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.Date
import java.util.concurrent.Executors

data class DetectionHistoryEntry(
    val id: Long,
    val timestamp: Long,
    val durationSeconds: Long,
    val objectCounts: Map<String, Int>,
    val note: String = ""
) {
    val totalObjectTypes: Int
        get() = objectCounts.size

    val totalObjects: Int
        get() = objectCounts.values.sum()
}

private const val HISTORY_PREFS = "guardian_history"
private const val HISTORY_KEY = "sessions"
private const val MAX_HISTORY_ENTRIES = 100

private fun loadDetectionHistory(context: Context): List<DetectionHistoryEntry> {
    return try {
        val preferences = context.getSharedPreferences(
            HISTORY_PREFS,
            Context.MODE_PRIVATE
        )

        val json = preferences.getString(HISTORY_KEY, "[]") ?: "[]"
        val array = JSONArray(json)

        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val countsJson = item.getJSONObject("objectCounts")
                val counts = mutableMapOf<String, Int>()

                val keys = countsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    counts[key] = countsJson.getInt(key)
                }

                add(
                    DetectionHistoryEntry(
                        id = item.getLong("id"),
                        timestamp = item.getLong("timestamp"),
                        durationSeconds = item.getLong("durationSeconds"),
                        objectCounts = counts,
                        note = item.optString("note", "")
                    )
                )
            }
        }
    } catch (exception: Exception) {
        emptyList()
    }
}

private fun saveDetectionHistory(
    context: Context,
    entries: List<DetectionHistoryEntry>
) {
    val array = JSONArray()

    entries.take(MAX_HISTORY_ENTRIES).forEach { entry ->
        val countsJson = JSONObject()

        entry.objectCounts.forEach { (label, count) ->
            countsJson.put(label, count)
        }

        val item = JSONObject().apply {
            put("id", entry.id)
            put("timestamp", entry.timestamp)
            put("durationSeconds", entry.durationSeconds)
            put("objectCounts", countsJson)
            put("note", entry.note)
        }

        array.put(item)
    }

    context.getSharedPreferences(
        HISTORY_PREFS,
        Context.MODE_PRIVATE
    ).edit()
        .putString(HISTORY_KEY, array.toString())
        .apply()
}

internal val Navy: Color
    @Composable get() = LocalGuardianPalette.current.background

internal val Panel: Color
    @Composable get() = LocalGuardianPalette.current.surface

internal val PanelLight: Color
    @Composable get() = LocalGuardianPalette.current.surfaceVariant

internal val Cyan: Color
    @Composable get() = LocalGuardianPalette.current.primary

internal val Green: Color
    @Composable get() = LocalGuardianPalette.current.secondary

internal val Muted: Color
    @Composable get() = LocalGuardianPalette.current.muted

internal val Warning: Color
    @Composable get() = LocalGuardianPalette.current.warning

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            val preferences = remember {
                getSharedPreferences("guardian_settings", Context.MODE_PRIVATE)
            }

            var themeMode by remember {
                mutableStateOf(
                    preferences.getString("theme_mode", "System Default")
                        ?: "System Default"
                )
            }

            GuardianTheme(themeMode = themeMode) {
                GuardianHome(
                    themeMode = themeMode,
                    onThemeModeChange = { selectedMode ->
                        themeMode = selectedMode
                        preferences.edit()
                            .putString("theme_mode", selectedMode)
                            .apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun GuardianHome(
    themeMode: String,
    onThemeModeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Keep the LiteRT-LM engine alive across scene requests. Reinitializing a
    // 500M-parameter VLM for every button press adds avoidable startup latency.
    val vlmSceneAnalyzer = remember(context.applicationContext) {
        VlmSceneAnalyzer(context.applicationContext)
    }

    DisposableEffect(vlmSceneAnalyzer) {
        onDispose {
            vlmSceneAnalyzer.close()
        }
    }

    // Persistent application preferences.
    val preferences = remember {
        context.getSharedPreferences(
            "guardian_settings",
            Context.MODE_PRIVATE
        )
    }

    var selectedTab by remember { mutableStateOf("Home") }

    var interfaceLanguage by remember {
        mutableStateOf(
            preferences.getString("interface_language", "English")
                ?: "English"
        )
    }

    var speechLanguage by remember {
        mutableStateOf(
            preferences.getString("speech_language", "English (US)")
                ?: "English (US)"
        )
    }

    var speechRate by remember {
        mutableFloatStateOf(
            preferences.getFloat("speech_rate", 1.0f)
        )
    }

    var voiceAnnouncements by remember {
        mutableStateOf(
            preferences.getBoolean("voice_announcements", true)
        )
    }


    var lastAnnouncementTime by remember {
        mutableLongStateOf(0L)
    }

    var announcedSpatialItems by remember {
    mutableStateOf<Set<String>>(emptySet())
}
    var cameraRunning by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraPreviewView by remember {
        mutableStateOf<PreviewView?>(null)
    }

    fun captureCameraSnapshot(): Bitmap? {
        val preview = cameraPreviewView ?: return null

        if (preview.width <= 0 || preview.height <= 0) {
            return null
        }

        return try {
            val previewBitmap = preview.bitmap ?: return null
            previewBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } catch (error: Exception) {
            Log.e(
                "GuardianSceneFlow",
                "Could not create independent camera snapshot",
                error
            )
            null
        }
    }

        // On-demand VLM scene understanding state.
    var sceneAnalyzing by remember { mutableStateOf(false) }
    var sceneDescription by remember { mutableStateOf("") }

    // Incremented whenever the camera session stops.
    // Prevents an old inference result from being published.
    var cameraSessionId by remember { mutableLongStateOf(0L) }

    var yoloAnalyzerReleased by remember {
    mutableStateOf(false)
}
    var pendingSceneSnapshot by remember {
    mutableStateOf<Bitmap?>(null)
}

var sceneInferenceStarted by remember {
    mutableStateOf(false)
}
    var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var detectionFrameSequence by remember { mutableLongStateOf(0L) }
    val obstacleStabilityTracker = remember { ObstacleStabilityTracker() }
    var historyEntries by remember {
    mutableStateOf(loadDetectionHistory(context))
}

var editingHistoryEntry by remember {
    mutableStateOf<DetectionHistoryEntry?>(null)
}

var historyNote by remember {
    mutableStateOf("")
}

var deletingHistoryEntry by remember {
    mutableStateOf<DetectionHistoryEntry?>(null)
}

var showClearHistoryConfirmation by remember {
    mutableStateOf(false)
}

fun updateHistoryNote(entry: DetectionHistoryEntry, note: String) {
    val updatedEntries = historyEntries.map { current ->
        if (current.id == entry.id) {
            current.copy(note = note.trim())
        } else {
            current
        }
    }

    historyEntries = updatedEntries
    saveDetectionHistory(context, updatedEntries)
}

fun deleteHistoryEntry(entry: DetectionHistoryEntry) {
    val updatedEntries = historyEntries.filterNot {
        it.id == entry.id
    }

    historyEntries = updatedEntries
    saveDetectionHistory(context, updatedEntries)
}

fun clearDetectionHistory() {
    historyEntries = emptyList()
    saveDetectionHistory(context, emptyList())
}

var sessionStartedAt by remember {
    mutableLongStateOf(0L)
}

var sessionPeakCounts by remember {
    mutableStateOf<Map<String, Int>>(emptyMap())
}
    var inferenceMs by remember { mutableLongStateOf(0L) }
    var analysisReady by remember { mutableStateOf(false) }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }


var speechLanguageStatus by remember {
    mutableStateOf("Checking speech language...")
}

var ttsReady by remember { mutableStateOf(false) }

val voiceAssistant = remember(context) {
    VoiceAssistant(
        context = context,
        onReadyChanged = { ready ->
            ttsReady = ready
            if (!ready) {
                speechLanguageStatus =
                    "Speech engine initialization failed. Check your installed text-to-speech service."
            }
        },
        onUtteranceEvent = { utteranceId, completed ->
            if (
                !completed &&
                utteranceId.startsWith("guardian-spatial-announcement")
            ) {
                Log.w(
                    "GuardianVoice",
                    "Spatial announcement playback failed; allowing retry"
                )
                announcedSpatialItems = emptySet()
            }
        }
    )
}

    var testing by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf("AI model is ready.") }

    DisposableEffect(activity, cameraRunning) {
        val window = activity?.window

        if (window != null) {
            val controller = WindowInsetsControllerCompat(
                window, window.decorView
            )

            WindowCompat.setDecorFitsSystemWindows(
                window, !cameraRunning
            )

            if (cameraRunning) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            if (window != null) {
                WindowInsetsControllerCompat(
                    window, window.decorView
                ).show(WindowInsetsCompat.Type.systemBars())

                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        cameraRunning = granted
        cameraError = if (granted) null else
            "Camera permission is required."
    }

    LaunchedEffect(cameraRunning) {
    if (cameraRunning) {
        sessionStartedAt = System.currentTimeMillis()
        sessionPeakCounts = emptyMap()
    } else {
        if (sessionStartedAt > 0L) {
            val endedAt = System.currentTimeMillis()

            if (sessionPeakCounts.isNotEmpty()) {
                val entry = DetectionHistoryEntry(
                    id = sessionStartedAt,
                    timestamp = sessionStartedAt,
                    durationSeconds = (
                        (endedAt - sessionStartedAt) / 1000L
                    ).coerceAtLeast(0L),
                    objectCounts = sessionPeakCounts
                )

                historyEntries = (
                    listOf(entry) + historyEntries
                ).take(MAX_HISTORY_ENTRIES)

                saveDetectionHistory(context, historyEntries)
            }

            sessionStartedAt = 0L
            sessionPeakCounts = emptyMap()
        }
    }
}

    DisposableEffect(voiceAssistant) {
        onDispose {
            voiceAssistant.close()
            ttsReady = false
        }
    }

    LaunchedEffect(ttsReady, speechLanguage, speechRate) {
        if (ttsReady) {
            speechLanguageStatus = voiceAssistant.configure(
                speechLanguage = speechLanguage,
                speechRate = speechRate
            )
        }
    }

    fun startCamera() {
        cameraError = null
        if (permissionGranted) {
            cameraRunning = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun stopCamera() {
    voiceAssistant.stop()
    obstacleStabilityTracker.reset()
    announcedSpatialItems = emptySet()
    detectionFrameSequence++

    cameraSessionId++

    // Recycle a snapshot only if VLM has not started using it.
    if (!sceneInferenceStarted) {
        pendingSceneSnapshot?.let { snapshot ->
            if (!snapshot.isRecycled) {
                snapshot.recycle()
            }
        }
        pendingSceneSnapshot = null
    }

    yoloAnalyzerReleased = false
    sceneAnalyzing = false
    cameraRunning = false

    detections = emptyList()
    inferenceMs = 0L
    analysisReady = false
    cameraError = null
}

    fun describeScene() {
    if (!cameraRunning || sceneAnalyzing) return

    val snapshot = captureCameraSnapshot()

    if (snapshot == null) {
        sceneDescription = "Camera image is not ready. Please try again."
        return
    }

    // Reset the previous release signal before requesting YOLO shutdown.
    yoloAnalyzerReleased = false

    // Retain the captured image until YOLO has been released.
    pendingSceneSnapshot?.let { previous ->
        if (!previous.isRecycled) previous.recycle()
    }

    pendingSceneSnapshot = snapshot
    sceneDescription = "Waiting for camera analysis to stop..."
    sceneAnalyzing = true
}


LaunchedEffect(
    sceneAnalyzing,
    yoloAnalyzerReleased,
    sceneInferenceStarted
) {
    val snapshot = pendingSceneSnapshot
        ?: return@LaunchedEffect

    if (!sceneAnalyzing || !yoloAnalyzerReleased) {
        return@LaunchedEffect
    }

    // Prevent duplicate VLM inference.
    if (sceneInferenceStarted) {
        Log.w("GuardianSceneFlow", "Duplicate inference prevented")
        return@LaunchedEffect
    }

    sceneInferenceStarted = true
    val requestSession = cameraSessionId

    Log.i(
        "GuardianSceneFlow",
        "Starting VLM request. Session=$requestSession"
    )

    // Run independently of this LaunchedEffect's lifecycle.
    scope.launch {
        try {
            val result = withContext(Dispatchers.IO) {
                try {
                    Result.success(
                        vlmSceneAnalyzer.describeScene(snapshot)
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }

            Log.i(
                "GuardianSceneFlow",
                "Inference returned. Session=$requestSession"
            )

            if (cameraRunning && cameraSessionId == requestSession) {
                result.fold(
                    onSuccess = { description ->
                        sceneDescription = description.ifBlank {
                            "The scene could not be described clearly."
                        }

                        Log.i(
                            "GuardianSceneFlow",
                            "Description published. Length=${sceneDescription.length}"
                        )

                        Log.i(
    "GuardianVoice",
    "Scene speech check: announcements=$voiceAnnouncements, ttsReady=$ttsReady, textLength=${sceneDescription.length}"
)

if (voiceAnnouncements && ttsReady) {
    voiceAssistant.speak(
        sceneDescription,
        flushQueue = true,
        utteranceId = "guardian-scene-description"
    )
} else {
    Log.w(
        "GuardianVoice",
        "Scene speech skipped: voice announcements disabled or TTS not ready"
    )
}
                    },
                    onFailure = { error ->
                        sceneDescription =
                            "Scene description unavailable: " +
                            (error.message ?: "Unknown error")

                        Log.e(
                            "GuardianSceneFlow",
                            "Inference failed",
                            error
                        )
                    }
                )
            } else {
                Log.w(
                    "GuardianSceneFlow",
                    "Result rejected. CameraRunning=$cameraRunning, " +
                        "CurrentSession=$cameraSessionId, " +
                        "RequestSession=$requestSession"
                )
            }
        } catch (cancelled: CancellationException) {
            Log.w(
                "GuardianSceneFlow",
                "VLM coroutine cancelled",
                cancelled
            )

            if (cameraRunning && cameraSessionId == requestSession) {
                sceneDescription = "Scene description was interrupted. Please try again."
            }

            throw cancelled
        } finally {
            if (!snapshot.isRecycled) {
                snapshot.recycle()
            }

            if (pendingSceneSnapshot === snapshot) {
                pendingSceneSnapshot = null
            }

            sceneInferenceStarted = false

            if (cameraRunning && cameraSessionId == requestSession) {
                sceneAnalyzing = false
            }

            Log.i(
                "GuardianSceneFlow",
                "VLM request cleanup completed. Session=$requestSession"
            )
        }
    }
}

    fun runTest(message: String, task: () -> String) {
        if (testing) return

        testing = true
        testStatus = message

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { task() }
            }

            testStatus = result.fold(
                onSuccess = { it },
                onFailure = {
                    "Test failed: ${it.message ?: "Unknown error"}"
                }
            )
            testing = false
        }
    }

    val objectSummary = remember(detections) {
    detections.groupBy { it.label }
        .entries
        .sortedBy { it.key }
        .joinToString(" | ") { entry ->

            val positions = entry.value.mapNotNull { detection ->

                val width = detection.frameWidth
                val height = detection.frameHeight

                if (width <= 0 || height <= 0) {
                    return@mapNotNull null
                }

                val centerX = (
                    detection.boundingBox.left +
                    detection.boundingBox.right
                ) / 2f

                val centerY = (
                    detection.boundingBox.top +
                    detection.boundingBox.bottom
                ) / 2f

                val normalizedX = centerX / width
                val normalizedY = centerY / height

                val horizontalPosition = when {
                    normalizedX < 0.33f -> "front-left"
                    normalizedX > 0.67f -> "front-right"
                    else -> "front-center"
                }

                val verticalPosition = when {
                    normalizedY < 0.33f -> "upper"
                    normalizedY > 0.67f -> "lower"
                    else -> "middle"
                }

                "$horizontalPosition ($verticalPosition)"
            }.distinct()

            if (positions.isEmpty()) {
                "${entry.key}: ${entry.value.size}"
            } else {
                "${entry.key}: ${positions.joinToString(", ")}"
            }
        }
}

    // Announce only obstacles confirmed across multiple camera-analysis frames.
LaunchedEffect(
    detectionFrameSequence,
    voiceAnnouncements,
    ttsReady,
    speechLanguage
) {
    val prioritizedObstacles = PathAwareObstacleAnalyzer.analyze(detections)
    val currentSpatialItems = prioritizedObstacles
        .map { obstacle ->
            obstacle.detection.label to obstacle.direction
        }
        .distinct()

    val currentKeys = currentSpatialItems.map { (label, direction) ->
        "$label|$direction"
    }.toSet()

    val stableKeys = obstacleStabilityTracker.update(
        currentKeys = currentKeys,
        frameId = detectionFrameSequence,
        nowMs = SystemClock.elapsedRealtime()
    )

    val stableSpatialItems = currentSpatialItems.filter { (label, direction) ->
        "$label|$direction" in stableKeys
    }

    Log.d(
        "GuardianVoice",
        "Camera speech evaluation: detections=" + detections.size +
            ", prioritized=" + currentSpatialItems.size +
            ", temporallyStable=" + stableSpatialItems.size +
            ", enabled=" + voiceAnnouncements +
            ", ttsReady=" + ttsReady
    )

    if (!voiceAnnouncements) {
        announcedSpatialItems = emptySet()
        return@LaunchedEffect
    }

    if (currentKeys.isEmpty()) {
        announcedSpatialItems = emptySet()
        return@LaunchedEffect
    }

    announcedSpatialItems = announcedSpatialItems.intersect(currentKeys)

    if (!ttsReady || stableSpatialItems.isEmpty()) {
        return@LaunchedEffect
    }

    val newItems = stableSpatialItems.filter { (label, direction) ->
        "$label|$direction" !in announcedSpatialItems
    }
    if (newItems.isEmpty()) {
        return@LaunchedEffect
    }

    val currentTime = System.currentTimeMillis()
    if (currentTime - lastAnnouncementTime < 3000L) {
        return@LaunchedEffect
    }

    val spokenMessage = newItems
        .sortedBy { it.first }
        .joinToString(" ") { (label, direction) ->
            SpeechLocalizer.localize(
                label = label,
                direction = direction,
                language = speechLanguage
            )
        }

    val accepted = voiceAssistant.speak(
        spokenMessage,
        flushQueue = true,
        utteranceId = "guardian-spatial-announcement"
    )

    if (accepted) {
        announcedSpatialItems =
            announcedSpatialItems + newItems.map { (label, direction) ->
                "$label|$direction"
            }
        lastAnnouncementTime = currentTime
    } else {
        Log.w(
            "GuardianVoice",
            "Spatial announcement was rejected; it will be retried on a later camera frame"
        )
    }
}

    Surface(
    modifier = Modifier.fillMaxSize(),
    color = Navy
) {
    Column(modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                cameraRunning && permissionGranted -> {
                    CameraScreen(
                        lifecycleOwner = lifecycleOwner,
                        detections = detections,
                        inferenceMs = inferenceMs,
                        analysisReady = analysisReady,
                        objectSummary = objectSummary,
                        cameraError = cameraError,
                        onError = { cameraError = it },
                        onDetections = { found, duration, _ ->
    detections = found
    detectionFrameSequence++
    inferenceMs = duration
    analysisReady = true

    if (sessionStartedAt > 0L && found.isNotEmpty()) {
        val frameCounts = found
            .groupingBy { it.label }
            .eachCount()

        sessionPeakCounts = sessionPeakCounts.toMutableMap().apply {
            frameCounts.forEach { (label, count) ->
                this[label] = maxOf(this[label] ?: 0, count)
            }
        }
    }
},                      sceneAnalyzing = sceneAnalyzing,
                        sceneDescription = sceneDescription,
                        onDescribeScene = ::describeScene,
                        onStop = ::stopCamera,
                        onVoice = {
                            if (voiceAnnouncements) {
                                voiceAssistant.speak(
                                    "AI Guardian is running. Visual awareness is active.",
                                    flushQueue = true,
                                    utteranceId = "guardian-live-status"
                                )
                            }
                        },
                        voiceReady = ttsReady,
                        onPreviewViewChanged = { view ->
                            cameraPreviewView = view
                        },
                        onYoloAnalyzerReleased = {
                            yoloAnalyzerReleased = true
                        }
                    )
                }

                selectedTab == "Settings" -> {
                    SettingsScreen(
                        interfaceLanguage = interfaceLanguage,
                        speechLanguage = speechLanguage,
                        speechRate = speechRate,
                        voiceAnnouncements = voiceAnnouncements,
                        speechLanguageStatus = speechLanguageStatus,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onInterfaceLanguageChange = {
                            interfaceLanguage = it
                            preferences.edit()
                                .putString("interface_language", it)
                                .apply()
                        },
                        onSpeechLanguageChange = {
                            speechLanguage = it
                            preferences.edit()
                                .putString("speech_language", it)
                                .apply()
                        },
                        onSpeechRateChange = {
                            speechRate = it
                            preferences.edit()
                                .putFloat("speech_rate", it)
                                .apply()
                        },
                        onVoiceAnnouncementsChange = {
                            voiceAnnouncements = it
                            preferences.edit()
                                .putBoolean("voice_announcements", it)
                                .apply()
                        },
                        onBack = {
                            selectedTab = "Home"
                        }
                    )
                }


selectedTab == "History" -> {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // HEADER
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "YOUR ACTIVITY",
                color = Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )

            Text(
                "Detection History",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                "Review your recent AI vision sessions and detected objects.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }

        // SUMMARY
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                Triple("SESSIONS", historyEntries.size.toString(), Cyan),
                Triple(
                    "OBJECT TYPES",
                    historyEntries.sumOf { it.totalObjectTypes }.toString(),
                    MaterialTheme.colorScheme.onSurface
                ),
                Triple(
                    "PEAK OBJECTS",
                    historyEntries.sumOf { it.totalObjects }.toString(),
                    Color(0xFFFE2C55)
                )
            ).forEach { item ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Panel)
                        .padding(horizontal = 12.dp, vertical = 14.dp),
verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        item.first,
                        color = Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )

                    Text(
                        item.second,
                        color = item.third,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // EMPTY STATE
        if (historyEntries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Panel)
                    .padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "History is empty",
                    tint = Cyan,
                    modifier = Modifier.size(54.dp)
                )

                Text(
                    "Nothing here yet",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Your camera detection sessions will appear here after AI Guardian detects objects.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {

            // HISTORY ACTIONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        "RECENT SESSIONS",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Text(
                        "${historyEntries.size} saved session(s)",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }

                TextButton(
                    onClick = {
                        showClearHistoryConfirmation = true
                    }
                ) {
                    Text(
                        "CLEAR ALL",
                        color = Color(0xFFFE2C55),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // SESSION CARDS
            historyEntries.forEach { entry ->

                val dateText = SimpleDateFormat(
                    "dd MMM yyyy  •  hh:mm a",
                    Locale.getDefault()
                ).format(Date(entry.timestamp))

                val durationText = if (entry.durationSeconds < 60) {
                    "${entry.durationSeconds} sec"
                } else {
                    "${entry.durationSeconds / 60} min ${entry.durationSeconds % 60} sec"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Panel)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {

                    // SESSION HEADER
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                "VISION SESSION",
                                color = Cyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )

                            Text(
                                dateText,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(PanelLight)
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "SAVED",
                                color = Cyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // SESSION METRICS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        listOf(
                            Triple("DURATION", durationText, Cyan),
                            Triple("OBJECT TYPES", entry.totalObjectTypes.toString(), MaterialTheme.colorScheme.onSurface),
                            Triple("PEAK OBJECTS", entry.totalObjects.toString(), Color(0xFFFE2C55))
                        ).forEach { metric ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PanelLight)
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    metric.first,
                                    color = Muted,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )

                                Text(
                                    metric.second,
                                    color = metric.third,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // USER NOTE
                    if (entry.note.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PanelLight)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                "SESSION NOTE",
                                color = Cyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                entry.note,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // DETECTED OBJECTS
                    if (entry.objectCounts.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            "DETECTED OBJECTS",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )

                        entry.objectCounts.entries
                            .sortedByDescending { it.value }
                            .forEach { (label, count) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(PanelLight)
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        label.replaceFirstChar { it.uppercase() },
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Text(
                                        "Peak: $count",
                                        color = Cyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                    }

                    // CRUD ACTIONS
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                historyNote = entry.note
                                editingHistoryEntry = entry
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Cyan
                            )
                        ) {
                            Text(
                                "EDIT NOTE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                deletingHistoryEntry = entry
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFE2C55)
                            )
                        ) {
                            Text(
                                "DELETE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Text(
            "AI GUARDIAN  •  YOUR ACTIVITY, YOUR CONTROL",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            color = Muted,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}



selectedTab == "Learn" -> {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "LEARN • EXPLORE • GET STARTED",
                color = Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )

            Text(
                "Learning Hub",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )

            Text(
                "Discover how AI Guardian helps you understand your surroundings.",
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }

        // Introduction card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "YOUR AI COMPANION",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                Text(
                    "See the world through AI.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 29.sp
                )

                Text(
                    "AI Guardian uses your phone camera and an on-device AI model to identify supported objects and provide spoken awareness of detections.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 21.sp
                )

                Surface(
                    color = PanelLight,
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Text(
                        "POWERED BY YOLOv8n",
                        color = Cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 7.dp
                        )
                    )
                }
            }
        }

        // Getting started
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "GETTING STARTED",
                color = Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Text(
                "Your first session in four steps",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            val steps = listOf(
                Triple("01", "Open Home", "Start from the Home screen."),
                Triple("02", "Start Live Vision", "Activate the camera and allow camera access when requested."),
                Triple("03", "Explore your surroundings", "Point the camera towards objects you want the AI to identify."),
                Triple("04", "Listen to feedback", "Use voice announcements to hear supported object detections.")
            )

            steps.forEach { step ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Panel,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = PanelLight,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                step.first,
                                color = Cyan,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 11.dp
                                )
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                step.second,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                step.third,
                                color = Muted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        // Available capabilities
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "CURRENT CAPABILITIES",
                color = Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Panel,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(19.dp),
                    verticalArrangement = Arrangement.spacedBy(17.dp)
                ) {
                    val capabilities = listOf(
                        "AI object detection" to "Identifies supported objects using YOLOv8n.",
                        "On-device processing" to "Runs the detection model locally on your phone.",
                        "Voice announcements" to "Provides spoken feedback using Android Text-to-Speech.",
                        "Detection history" to "Stores summaries of detection sessions on your device."
                    )

                    capabilities.forEachIndexed { index, capability ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "✓",
                                    color = Cyan,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    capability.first,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.1.sp
                                )
                            }

                            Text(
                                capability.second,
                                color = Muted,
                                fontSize = 12.sp,
                                lineHeight = 19.sp
                            )
                        }

                        if (index != capabilities.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }

        // Accessibility guidance
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "ACCESSIBILITY",
                color = Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Panel,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(19.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Make the experience comfortable",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        "• Select your preferred speech language in Settings.\n\n" +
                        "• Adjust speech speed for comfortable listening.\n\n" +
                        "• Enable or disable voice announcements.\n\n" +
                        "• Review previous detection sessions in History.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 23.sp
                    )
                }
            }
        }

        // Future roadmap
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "WHAT'S COMING NEXT",
                color = Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            val plannedFeatures = listOf(
                "Scene understanding",
                "Advanced obstacle awareness",
                "Text reading and OCR",
                "Indoor and outdoor assistance",
                "Traffic and road-crossing research"
            )

            plannedFeatures.forEach { feature ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Panel,
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 17.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            feature,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                "PLANNED",
                                color = Warning,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(
                                    horizontal = 9.dp,
                                    vertical = 6.dp
                                )
                            )
                        }
                    }
                }
            }
        }

        // Safety notice
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(19.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "IMPORTANT SAFETY INFORMATION",
                    color = Warning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Text(
                    "AI Guardian is an experimental visual-awareness assistant. Object detection does not establish whether a path or road is safe. Never rely on this app alone for road crossing, navigation, obstacle avoidance or collision prevention. Continue using established mobility techniques and appropriate assistance.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 21.sp
                )
            }
        }

        Text(
            "AI GUARDIAN • BUILT FOR ACCESSIBILITY",
            color = Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
}

                else -> {
                    HomeScreen(
                        cameraError = cameraError,
                        voiceReady = ttsReady,
                        testing = testing,
                        testStatus = testStatus,
                        onStartCamera = ::startCamera,
                        onVoice = {
                            voiceAssistant.speak(
                                "AI Guardian audio test. Audio output is working.",
                                flushQueue = true,
                                utteranceId = "guardian-audio-test"
                            )
                        },
                        onModelTest = {
                            runTest("Running ONNX model test...") {
                                YoloDetector(context).use {
                                    it.runSmokeTest()
                                }
                            }
                        },
                        onImageTest = {
                            runTest("Analyzing sample image...") {
                                runBusImageTest(context)
                            }
                        },
                    )
                }
            }
        }


        if (!cameraRunning) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 3.dp,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                val tabs = listOf(
                    "Home",
                    "History",
                    "Learn",
                    "Settings"
                )

                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            selectedTab = tab
                        },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    "Home" -> Icons.Filled.Home
                                    "History" -> Icons.Filled.History
                                    "Learn" -> Icons.Filled.School
                                    else -> Icons.Filled.Settings
                                },
                                contentDescription = "$tab tab",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                                maxLines = 1
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cyan,
                            selectedTextColor = Cyan,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                            indicatorColor = PanelLight
                        )
                    )
                }
            }
        }

    }
}

if (editingHistoryEntry != null) {
    AlertDialog(
        onDismissRequest = {
            editingHistoryEntry = null
        },
        title = {
            Text("Edit Session Note")
        },
        text = {
            OutlinedTextField(
                value = historyNote,
                onValueChange = {
                    historyNote = it
                },
                label = {
                    Text("Personal Note")
                },
                placeholder = {
                    Text("Add a note about this session")
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val entry = editingHistoryEntry

                    if (entry != null) {
                        updateHistoryNote(entry, historyNote)
                    }

                    editingHistoryEntry = null
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    editingHistoryEntry = null
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

if (deletingHistoryEntry != null) {
    AlertDialog(
        onDismissRequest = {
            deletingHistoryEntry = null
        },
        title = {
            Text("Delete Session?")
        },
        text = {
            Text("This session will be permanently removed from your history.")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val entry = deletingHistoryEntry

                    if (entry != null) {
                        deleteHistoryEntry(entry)
                    }

                    deletingHistoryEntry = null
                }
            ) {
                Text(
                    "Delete",
                    color = Color(0xFFFF6B6B)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    deletingHistoryEntry = null
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

if (showClearHistoryConfirmation) {
    AlertDialog(
        onDismissRequest = {
            showClearHistoryConfirmation = false
        },
        title = {
            Text("Clear All History?")
        },
        text = {
            Text("All saved detection sessions will be permanently deleted.")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clearDetectionHistory()
                    showClearHistoryConfirmation = false
                }
            ) {
                Text(
                    "Clear All",
                    color = Color(0xFFFF6B6B)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    showClearHistoryConfirmation = false
                }
            ) {
                Text("Cancel")
            }
        }
    )
}
}

@Composable
private fun HomeScreen(
    cameraError: String?,
    voiceReady: Boolean,
    testing: Boolean,
    testStatus: String,
    onStartCamera: () -> Unit,
    onVoice: () -> Unit,
    onModelTest: () -> Unit,
    onImageTest: () -> Unit,
) {
    val backgroundColor = Navy
    val cardColor = Panel
    val secondaryText = Muted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {

        // BRAND HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "AI GUARDIAN",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )

                Text(
                    "INTELLIGENT ASSISTIVE TECHNOLOGY",
                    color = Cyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
        }

        // WELCOME SECTION
        Column(
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                "SEE YOUR WORLD.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 29.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.8).sp
            )

            Text(
                "UNDERSTAND MORE.",
                color = Cyan,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.8).sp
            )

            Text(
                "An intelligent visual companion designed to make your surroundings more understandable through AI.",
                color = secondaryText,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }

        // LIVE CAMERA FEATURE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            PanelLight,
                            Panel,
                            Panel
                        )
                    )
                )
                .padding(21.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Cyan)
                )

                Text(
                    "AI LIVE VISION",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    "ON-DEVICE AI",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Your surroundings,\nmade more understandable.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 31.sp
            )

            Text(
                "Use real-time camera analysis to identify supported objects and receive spoken assistance.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 21.sp
            )

            Button(
                onClick = onStartCamera,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    "START LIVE CAMERA",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp
                )
            }

            if (!cameraError.isNullOrBlank()) {
                Text(
                    cameraError,
                    modifier = Modifier.semantics {
                        contentDescription = "Camera error. $cameraError"
                    },
                    color = Warning,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // AI SYSTEM OVERVIEW
        Column(
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Text(
                "SYSTEM OVERVIEW",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoTile(
                    modifier = Modifier.weight(1f),
                    title = "AI ENGINE",
                    value = "YOLOv8n",
                    detail = "ONNX Runtime"
                )

                InfoTile(
                    modifier = Modifier.weight(1f),
                    title = "PROCESSING",
                    value = "On-device",
                    detail = "Local inference"
                )
            }

            Text(
                text = "Visual detection runs locally on your device.",
                color = secondaryText,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }

        // VOICE ASSISTANT
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(cardColor)
                .padding(19.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription =
                        "Voice assistant. Spoken assistance and feedback. " +
                        "Status: ${if (voiceReady) "ready" else "preparing"}."
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(PanelLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "♪",
                        color = Cyan,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "VOICE ASSISTANT",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Spoken assistance and feedback",
                        color = secondaryText,
                        fontSize = 12.sp
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (voiceReady) PanelLight
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (voiceReady) "READY" else "PREPARING",
                            color = if (voiceReady) Green else Warning,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Button(
                onClick = onVoice,
                enabled = voiceReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = secondaryText
                )
            ) {
                Text(
                    "TEST VOICE ASSISTANT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // ADDITIONAL CAPABILITIES
        Column(
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Text(
                "ASSISTIVE CAPABILITIES",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            val features = listOf(
                "Text Reading" to "Read printed information using OCR",
                "Scene Understanding" to "Understand the surrounding environment",
                "Navigation Assistance" to "Future indoor and outdoor guidance",
                "Road Crossing Support" to "Long-term research and safety validation"
            )


            features.forEachIndexed { index, feature ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardColor)
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PanelLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "%02d".format(index + 1),
                            color = Cyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = feature.first,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = feature.second,
                            color = secondaryText,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "PLANNED",
                            color = Warning,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

        }

        // SAFETY INFORMATION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                "SAFETY INFORMATION",
                color = Warning,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp
            )

            Text(
                "AI Guardian is currently an experimental awareness aid. Do not rely on it for independent navigation, road crossing or collision avoidance. Continue using established mobility techniques and appropriate human assistance.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                lineHeight = 19.sp
            )
        }

        // FOOTER
        Text(
            "AI GUARDIAN  •  BUILT FOR ACCESSIBILITY",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            color = secondaryText,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            letterSpacing = 0.7.sp
        )
    }
}

@Composable
private fun InfoTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    detail: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(title, color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun CameraScreen(
    lifecycleOwner: LifecycleOwner,
    detections: List<Detection>,
    inferenceMs: Long,
    analysisReady: Boolean,
    objectSummary: String,
    cameraError: String?,
    onError: (String) -> Unit,
    onDetections: (List<Detection>, Long, OutputTransform) -> Unit,
    sceneAnalyzing: Boolean,
    sceneDescription: String,
    onDescribeScene: () -> Unit,
    onStop: () -> Unit,
    onVoice: () -> Unit,
    voiceReady: Boolean,
    onPreviewViewChanged: (PreviewView?) -> Unit,
    onYoloAnalyzerReleased: () -> Unit

) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full-screen camera preview keeps the detected scene unobstructed.
        CameraPreview(
            lifecycleOwner = lifecycleOwner,
            detections = detections,
            onError = onError,
            onDetections = onDetections,
            onPreviewViewChanged = onPreviewViewChanged,
            analysisEnabled = !sceneAnalyzing,
            onYoloAnalyzerReleased = onYoloAnalyzerReleased,
            modifier = Modifier.fillMaxSize()
        )

        // Compact top bar with an accessible overflow menu.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiveBrand()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DetectionPill(detections.size, analysisReady)

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xD9091926)),
                    ) {
                        Text(
                            text = "⋮",
                            modifier = Modifier.semantics {
                                contentDescription = "Camera options"
                            },
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(if (showDetails) "Hide scene details" else "Show scene details")
                            },
                            onClick = {
                                showDetails = !showDetails
                                menuExpanded = false
                            }
                        )

                            DropdownMenuItem(
                            text = {
                                Text(
                                    if (sceneAnalyzing) {
                                        "Analyzing scene..."
                                    } else {
                                        "Describe scene"
                                    }
                                )
                            },
                            enabled = !sceneAnalyzing,
                            onClick = {
                                menuExpanded = false
                                onDescribeScene()
                            }
                        )

                        if (voiceReady) {
                            DropdownMenuItem(
                                text = { Text("Test voice") },
                                onClick = {
                                    menuExpanded = false
                                    onVoice()
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Stop camera",
                                    color = Color(0xFFE53955),
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onStop()
                            }
                        )
                    }
                }
            }
        }

        // Compact live summary. Expanded diagnostics are optional.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE6081521))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = if (analysisReady) {
                            "AI visual awareness is active. Inference time $inferenceMs milliseconds."
                        } else {
                            "AI visual awareness is initializing."
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (analysisReady) "LIVE AI AWARENESS" else "INITIALIZING AI",
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (analysisReady) "${inferenceMs} ms • On-device" else "Preparing...",
                    color = Muted,
                    fontSize = 10.sp
                )
            }

            Text(
                objectSummary.ifBlank {
                    if (analysisReady) {
                        "No supported objects detected in the current view."
                    } else {
                        "Waiting for the first detection results..."
                    }
                },
                modifier = Modifier.semantics {
                    contentDescription = "Detected objects. " + objectSummary.ifBlank {
                        if (analysisReady) {
                            "No supported objects detected in the current view."
                        } else {
                            "Waiting for the first detection results..."
                        }
                    }
                },
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = if (showDetails) 5 else 2
            )

                        // On-demand VLM scene description.
            if (sceneAnalyzing || sceneDescription.isNotBlank()) {
                Divider(color = Color.White.copy(alpha = 0.14f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = if (sceneAnalyzing) {
                                "Scene description is being generated."
                            } else {
                                "AI scene description. $sceneDescription"
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "AI SCENE DESCRIPTION",
                        color = Cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )


Text(
    text = if (sceneAnalyzing) {
        "Analyzing the scene. Please wait..."
    } else {
        sceneDescription
    },
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 220.dp)
        .verticalScroll(rememberScrollState()),
    color = Color.White,
    fontSize = 13.sp,
    lineHeight = 18.sp
)

                }
            }

            if (showDetails) {
                Divider(color = Color.White.copy(alpha = 0.14f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription =
                                "AI engine: YOLOv8n. Total detected objects: ${detections.size}."
                        },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AI engine: YOLOv8n", color = Muted, fontSize = 11.sp)
                    Text(
                        "Objects: ${detections.size}",
                        color = Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "Experimental visual awareness only. Do not rely on this feature for navigation, road crossing or collision avoidance.",
                    color = Warning,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        // Camera errors remain visible above the preview.
        cameraError?.takeIf { it.isNotBlank() }?.let { error ->
            Text(
                error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xEE401C24))
                    .padding(16.dp)
                    .semantics {
                        contentDescription = "Camera error. $error"
                    },
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun LiveBrand() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xD9091926))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Green)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "AI GUARDIAN  •  LIVE",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DetectionPill(count: Int, ready: Boolean) {
    Text(
        text = if (ready) "$count DETECTED" else "ANALYZING",
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xD9091926))
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .semantics {
                contentDescription = if (ready) {
                    "$count objects detected"
                } else {
                    "AI is analyzing the camera view"
                }
            },
        color = Cyan,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
}

private fun runBusImageTest(context: Context): String {
    val bitmap = context.assets.open("bus.jpg").use { stream ->
        BitmapFactory.decodeStream(stream)
            ?: error("Could not decode bus.jpg")
    }

    try {
        return YoloDetector(context).use { detector ->
            val found = detector.detect(bitmap)

            if (found.isEmpty()) {
                return@use "Inference completed. No objects detected above threshold."
            }

            val summary = found.groupBy { it.label }
                .map { (label, items) -> "$label: ${items.size}" }
                .joinToString("\n")

            val details = found.take(10).joinToString("\n") {
                "${it.label} — ${(it.confidence * 100).toInt()}%"
            }

            buildString {
                appendLine("YOLO detection successful")
                appendLine("Image: ${bitmap.width} × ${bitmap.height}")
                appendLine("Total detections: ${found.size}")
                appendLine()
                appendLine(summary)
                appendLine()
                append(details)
            }
        }
    } finally {
        bitmap.recycle()
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: LifecycleOwner,
    detections: List<Detection>,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDetections: (List<Detection>, Long, OutputTransform) -> Unit,
    onPreviewViewChanged: (PreviewView?) -> Unit,
    analysisEnabled: Boolean,
    onYoloAnalyzerReleased: () -> Unit
) {
    val context = LocalContext.current

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var sourceTransform by remember {
        mutableStateOf<OutputTransform?>(null)
    }

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize =  thirtyTwoPx
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
            style = Paint.Style.FILL
            setShadowLayer(5f, 1f, 1f, android.graphics.Color.BLACK)
        }
    }

    DisposableEffect(lifecycleOwner, previewView, analysisEnabled) {
        onPreviewViewChanged(previewView)

        val future = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)

        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        var analyzer: YoloImageAnalyzer? = null
        var disposed = false

        val rotationListener = View.OnLayoutChangeListener {
                _, _, _, _, _, _, _, _, _ ->
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            preview?.targetRotation = rotation
            analysis?.targetRotation = rotation
        }

        previewView.addOnLayoutChangeListener(rotationListener)

        future.addListener({
            if (disposed) return@addListener

            try {
                provider = future.get()

                val previewUseCase = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val rotation =
    previewView.display?.rotation ?: Surface.ROTATION_0

previewUseCase.targetRotation = rotation

preview = previewUseCase

provider?.unbindAll()

if (analysisEnabled) {
    val analysisUseCase = ImageAnalysis.Builder()
        .setBackpressureStrategy(
            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        )
        .setOutputImageFormat(
            ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
        )
        .build()

    analysisUseCase.targetRotation = rotation

    val imageAnalyzer = YoloImageAnalyzer(
        context = context,
        onDetections = { found, duration, transform ->
            mainExecutor.execute {
                if (!disposed) {
                    sourceTransform = transform
                    onDetections(found, duration, transform)
                }
            }
        },
        onError = { message ->
            mainExecutor.execute {
                if (!disposed) onError(message)
            }
        }
    )

    analysis = analysisUseCase
    analyzer = imageAnalyzer

    analysisUseCase.setAnalyzer(executor, imageAnalyzer)

    provider?.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        previewUseCase,
        analysisUseCase
    )
} else {
    sourceTransform = null

    provider?.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        previewUseCase
    )
}
            } catch (error: Exception) {
                if (!disposed) {
                    onError(
                        "Camera could not start: ${
                            error.localizedMessage ?: "Unknown camera error"
                        }"
                    )
                }
                analysis?.clearAnalyzer()
                analyzer?.close()
                executor.shutdown()
            }
        }, mainExecutor)

        onDispose {
    disposed = true
    onPreviewViewChanged(null)
    previewView.removeOnLayoutChangeListener(rotationListener)
    sourceTransform = null
    analysis?.clearAnalyzer()

    val active = listOfNotNull(preview, analysis)
    if (active.isNotEmpty()) {
        provider?.unbind(*active.toTypedArray())
    }

    analyzer?.close()
    executor.shutdown()

    if (analysisEnabled) {
        onYoloAnalyzerReleased()
    }
}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        val detectionBoxColor = Green

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            val source = sourceTransform ?: return@Canvas
            val target = previewView.outputTransform ?: return@Canvas

            val transform = try {
                CoordinateTransform(source, target)
            } catch (_: Exception) {
                return@Canvas
            }

                clipRect(0f, 0f, size.width, size.height) {
                    detections.forEach { detection ->
                        val rect = android.graphics.RectF(
                            detection.boundingBox
                        )

                        transform.mapRect(rect)


                        // Keep detection boxes away from the screen edges.
                        val edgePadding = 10f

                        val left = rect.left.coerceIn(
                            edgePadding,
                            (size.width - edgePadding).coerceAtLeast(edgePadding)
                        )
                        val top = rect.top.coerceIn(
                            edgePadding,
                            (size.height - edgePadding).coerceAtLeast(edgePadding)
                        )
                        val right = rect.right.coerceIn(
                            edgePadding,
                            (size.width - edgePadding).coerceAtLeast(edgePadding)
                        )
                        val bottom = rect.bottom.coerceIn(
                            edgePadding,
                            (size.height - edgePadding).coerceAtLeast(edgePadding)
                        )


                        if (right - left < 2f || bottom - top < 2f) {
                            return@forEach
                        }

                        drawRect(
                            color = detectionBoxColor,
                            topLeft = androidx.compose.ui.geometry.Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(
                                right - left,
                                bottom - top
                            ),
                            style = Stroke(width = 3f)
                        )

                        val label =
                            "${detection.label} ${(detection.confidence * 100).toInt()}%"

                        val textWidth = labelPaint.measureText(label)
                        val x = left.coerceIn(
                            10f,
                            (size.width - textWidth - 10f).coerceAtLeast(10f)
                        )

                        val baseline = if (top >= 40f) {
                            top - 8f
                        } else {
                            (bottom + 30f).coerceAtMost(size.height - 4f)
                        }

                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            x,
                            baseline,
                            labelPaint
                        )
                    }
                }
        }
    }
}

private const val thirtyTwoPx = 32f
