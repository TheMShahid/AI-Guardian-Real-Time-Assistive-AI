package org.aiguardian.mobile

import android.util.Log
import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Central Text-to-Speech controller for AI Guardian.
 *
 * Responsibilities:
 * - Initialize and release the Android TTS engine.
 * - Configure the selected speech language and rate.
 * - Handle unsupported-language fallback.
 * - Speak announcements using queue or flush behavior.
 *
 * MainActivity should use this class instead of accessing TextToSpeech directly.
 */
internal class VoiceAssistant(
    context: Context,
    private val onReadyChanged: (Boolean) -> Unit
) {

    private val appContext = context.applicationContext

    // Ensures Compose state callbacks are delivered on the main thread.
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var engine: TextToSpeech? = null

    @Volatile
    private var closed = false

    init {
        val createdEngine = TextToSpeech(appContext) { status ->
            // Posting prevents the callback from reading engine before
            // the constructor's returned instance has been assigned.
            mainHandler.post {
                handleInitialization(status)
            }
        }

        engine = createdEngine
    }

    /**
     * Process initialization on the main thread.
     */
    private fun handleInitialization(status: Int) {
        if (closed) return

        val currentEngine = engine ?: return

        Log.i("GuardianVoice", "TTS initialization status=$status")
        if (status == TextToSpeech.SUCCESS) {
            currentEngine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )

            onReadyChanged(true)
        } else {
            onReadyChanged(false)
        }
    }

    /**
     * Configure speech rate and requested language.
     * English is used when the requested language is unavailable.
     */
    @Synchronized
    fun configure(
        speechLanguage: String,
        speechRate: Float
    ): String {
        if (closed) {
            return "Speech engine is unavailable."
        }

        val currentEngine = engine
            ?: return "Speech engine is not ready."

        currentEngine.setSpeechRate(speechRate)

        val selectedLocale = when (speechLanguage) {
            "Urdu (Pakistan)" -> Locale.forLanguageTag("ur-PK")
            "Pashto (Afghanistan)" -> Locale.forLanguageTag("ps-AF")
            else -> Locale.US
        }

        val result = currentEngine.setLanguage(selectedLocale)


        return when (result) {
            TextToSpeech.LANG_MISSING_DATA -> {
                currentEngine.setLanguage(Locale.US)
                "$speechLanguage voice data is missing. English fallback is active."
            }

            TextToSpeech.LANG_NOT_SUPPORTED -> {
                currentEngine.setLanguage(Locale.US)
                "$speechLanguage is not supported by the installed TTS engine. English fallback is active."
            }

            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                "$speechLanguage voice selected successfully."
            }

            else -> {
                currentEngine.setLanguage(Locale.US)
                "Could not configure $speechLanguage. English fallback is active."
            }
        }
    }

    /**
     * Speak a message.
     *
     * flushQueue = true:
     * Stops queued speech and speaks the new message immediately.
     *
     * flushQueue = false:
     * Adds the message after existing queued speech.
     */
    @Synchronized
    fun speak(
        message: String,
        flushQueue: Boolean = true,
        utteranceId: String? = null
    ) {
        if (closed || message.isBlank()) return

        val currentEngine = engine ?: return

        val queueMode = if (flushQueue) {
            TextToSpeech.QUEUE_FLUSH
        } else {
            TextToSpeech.QUEUE_ADD
        }

        val result = currentEngine.speak(
    message,
    queueMode,
    null,
    utteranceId
)

Log.i(
    "GuardianVoice",
    "TTS speak request: result=$result, textLength=${message.length}, utteranceId=$utteranceId"
)
    }

    /**
     * Stop active speech without destroying the engine.
     */
    @Synchronized
    fun stop() {
        if (!closed) {
            engine?.stop()
        }
    }

    /**
     * Release native TTS resources when the owner is disposed.
     */
    @Synchronized
    fun close() {
        if (closed) return

        closed = true

        val currentEngine = engine
        engine = null

        currentEngine?.stop()
        currentEngine?.shutdown()
    }
}
