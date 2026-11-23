package org.aiguardian.mobile

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import java.io.ByteArrayOutputStream
import java.io.File

class VlmSceneAnalyzer(context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "GuardianVLM"
        private const val MODEL_RELATIVE_PATH = "models/SmolVLM2-500M.litertlm"
        private const val MAX_IMAGE_DIMENSION = 768
        private const val JPEG_QUALITY = 85

        private fun scenePrompt(speechLanguage: String): String {
            val languageInstruction = when (speechLanguage) {
                "Urdu (Pakistan)" ->
                    "Speak in simple, natural everyday Urdu (Pakistan), using Urdu script."
                "Pashto (Afghanistan)" ->
                    "Speak in simple, natural Pashto, using Pashto script."
                else ->
                    "Speak in simple, natural English."
            }

            return """
You are Nazar, a calm and trusted sighted-style voice guide for a blind or low-vision person.
Describe only what is visible in the camera image, from the user's point of view.
Use 2 to 4 short spoken sentences. No lists, headings, markdown, emojis, or decorative wording.
First mention an obvious immediate hazard or obstacle and its direction.
Then describe whether the visible way ahead appears open, partly obstructed, or obstructed. Never call a route safe.
Describe important objects from nearest-looking to farther-looking, including what each object is and whether it is directly ahead, ahead-left, ahead-right, on the left, or on the right.
Use relative visual distance only when the image supports it. Do not invent steps or metres; a single image does not provide reliable physical distance.
Mention a visible open area only as an observation, not as a recommendation to walk there.
If the image is unclear, say that it is unclear and ask for a small camera adjustment.
Do not guess hidden objects, exact measurements, movement, or anything outside the frame.
$languageInstruction
""".trimIndent()
        }
    }

    private val modelFile = File(
        context.applicationContext.filesDir,
        MODEL_RELATIVE_PATH
    )

    private var engine: Engine? = null

    @Volatile
    private var closed = false

    @Synchronized
    fun describeScene(
        snapshot: Bitmap,
        speechLanguage: String = "English (US)"
    ): String {
        val totalStart = android.os.SystemClock.elapsedRealtime()

        Log.i(TAG, "STEP 1: describeScene started")
        Log.i(TAG, "Thread: ${Thread.currentThread().name}")

        check(!closed) {
            "Scene analyzer has already been closed"
        }

        require(!snapshot.isRecycled) {
            "Camera snapshot is no longer available"
        }

        Log.i(TAG, "Snapshot: ${snapshot.width}x${snapshot.height}")

        try {
            Log.i(TAG, "STEP 2: Initializing model engine")
            initializeEngine()
            Log.i(TAG, "STEP 2 SUCCESS: Engine initialized")

            Log.i(TAG, "STEP 3: Creating conversation")
            val conversation = requireNotNull(engine).createConversation(
                ConversationConfig(maxOutputToken = 80)
            )
            Log.i(TAG, "STEP 3 SUCCESS: Conversation created")

            try {
                Log.i(TAG, "STEP 4: Encoding camera image")
                val encodeStart = android.os.SystemClock.elapsedRealtime()
                val imageBytes = encodeSnapshot(snapshot)

                Log.i(
                    TAG,
                    "STEP 4 SUCCESS: Image encoded; bytes=${imageBytes.size}; " +
                        "timeMs=${android.os.SystemClock.elapsedRealtime() - encodeStart}"
                )

                val input = Contents.of(
                    Content.ImageBytes(imageBytes),
                    Content.Text(scenePrompt(speechLanguage))
                )

                Log.i(TAG, "STEP 5: sendMessage STARTED")
                val inferenceStart = android.os.SystemClock.elapsedRealtime()
                val response = conversation.sendMessage(input)

                Log.i(
                    TAG,
                    "STEP 5 SUCCESS: sendMessage completed; " +
                        "timeMs=${android.os.SystemClock.elapsedRealtime() - inferenceStart}"
                )

                val description = response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = " ") { it.text }
                    .trim()

                check(description.isNotBlank()) {
                    "The visual model returned an empty description"
                }

                Log.i(TAG, "STEP 6 SUCCESS: Description received")
                Log.i(TAG, "Description length: ${description.length}")
                Log.i(TAG, "RAW MODEL DESCRIPTION: $description")
                Log.i(
                    TAG,
                    "TOTAL SUCCESS: ${android.os.SystemClock.elapsedRealtime() - totalStart} ms"
                )

                return description
            } finally {
                Log.i(TAG, "Closing conversation")
                conversation.close()
                Log.i(TAG, "Conversation closed")
            }
        } catch (exception: Exception) {
            Log.e(TAG, "VLM INFERENCE FAILED", exception)
            Log.e(
                TAG,
                "Failure after ${android.os.SystemClock.elapsedRealtime() - totalStart} ms"
            )
            throw exception
        }
    }

    private fun initializeEngine() {
        if (engine != null) {
            Log.i(TAG, "Existing engine will be reused")
            return
        }

        Log.i(TAG, "Model path: ${modelFile.absolutePath}")
        Log.i(TAG, "Model exists: ${modelFile.isFile}")
        Log.i(TAG, "Model size: ${modelFile.length()} bytes")

        check(modelFile.isFile) {
            "SmolVLM2 model file not found: ${modelFile.absolutePath}"
        }

        fun initializeWithBackend(
            backendName: String,
            selectedBackend: Backend
        ): Engine {
            Log.i(TAG, "$backendName engine initialization STARTED")

            val candidate = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = selectedBackend,
                    visionBackend = selectedBackend
                )
            )

            val initStart = android.os.SystemClock.elapsedRealtime()
            candidate.initialize()

            Log.i(
                TAG,
                "$backendName engine initialization SUCCESS: " +
                    "${android.os.SystemClock.elapsedRealtime() - initStart} ms"
            )

            return candidate
        }

        try {
            Log.i(TAG, "Attempting GPU backend")
            engine = initializeWithBackend("GPU", Backend.GPU())
            Log.i(TAG, "ACTIVE BACKEND: GPU")
        } catch (gpuException: Exception) {
            Log.e(TAG, "GPU initialization failed; switching to CPU", gpuException)
            engine = initializeWithBackend("CPU", Backend.CPU())
            Log.i(TAG, "ACTIVE BACKEND: CPU (GPU fallback)")
        }
    }

    private fun encodeSnapshot(snapshot: Bitmap): ByteArray {
        val largestDimension = maxOf(snapshot.width, snapshot.height)
        val scale = minOf(
            1f,
            MAX_IMAGE_DIMENSION.toFloat() / largestDimension
        )

        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                snapshot,
                (snapshot.width * scale).toInt().coerceAtLeast(1),
                (snapshot.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            null
        }

        val bitmapToEncode = resized ?: snapshot

        return try {
            ByteArrayOutputStream().use { output ->
                check(
                    bitmapToEncode.compress(
                        Bitmap.CompressFormat.JPEG,
                        JPEG_QUALITY,
                        output
                    )
                ) {
                    "Could not encode camera snapshot"
                }
                output.toByteArray()
            }
        } finally {
            resized?.recycle()
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return

        Log.i(TAG, "Closing VLM engine")
        closed = true
        engine?.close()
        engine = null
        Log.i(TAG, "VLM engine closed")
    }
}
