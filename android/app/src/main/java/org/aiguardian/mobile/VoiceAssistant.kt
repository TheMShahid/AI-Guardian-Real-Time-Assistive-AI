package org.aiguardian.mobile

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Central Text-to-Speech controller for AI Guardian.
 * Tracks request acceptance and actual playback for reliable diagnostics.
 */
internal class VoiceAssistant(
    context: Context,
    private val onReadyChanged: (Boolean) -> Unit,
    private val onUtteranceEvent: (String, Boolean) -> Unit = { _, _ -> }
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val utteranceCounter = AtomicLong(0L)

    @Volatile private var engine: TextToSpeech? = null
    @Volatile private var closed = false
    @Volatile private var latestUtteranceId: String? = null

    init {
        val createdEngine = TextToSpeech(appContext) { status ->
            mainHandler.post { handleInitialization(status) }
        }
        engine = createdEngine
    }

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

            currentEngine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        Log.i("GuardianVoice", "TTS playback started: utteranceId=$utteranceId")
                    }

                    override fun onDone(utteranceId: String) {
                        Log.i("GuardianVoice", "TTS playback completed: utteranceId=$utteranceId")
                        mainHandler.post {
                            if (!closed && utteranceId == latestUtteranceId) {
                                latestUtteranceId = null
                                onUtteranceEvent(utteranceId, true)
                            }
                        }
                    }

                    override fun onError(utteranceId: String) {
                        Log.e("GuardianVoice", "TTS playback failed: utteranceId=$utteranceId")
                        mainHandler.post {
                            // Ignore delayed failures from utterances replaced by QUEUE_FLUSH.
                            if (!closed && utteranceId == latestUtteranceId) {
                                latestUtteranceId = null
                                onUtteranceEvent(utteranceId, false)
                            }
                        }
                    }

                    override fun onError(utteranceId: String, errorCode: Int) {
                        Log.e(
                            "GuardianVoice",
                            "TTS playback failed: utteranceId=$utteranceId, errorCode=$errorCode"
                        )
                        mainHandler.post {
                            // Ignore delayed failures from utterances replaced by QUEUE_FLUSH.
                            if (!closed && utteranceId == latestUtteranceId) {
                                latestUtteranceId = null
                                onUtteranceEvent(utteranceId, false)
                            }
                        }
                    }
                }
            )
            onReadyChanged(true)
        } else {
            Log.e("GuardianVoice", "TTS engine initialization failed: status=$status")
            onReadyChanged(false)
        }
    }

    @Synchronized
    fun configure(speechLanguage: String, speechRate: Float): String {
        if (closed) return "Speech engine is unavailable."
        val currentEngine = engine ?: return "Speech engine is not ready."

        val rateResult = currentEngine.setSpeechRate(speechRate)
        val selectedLocale = when (speechLanguage) {
            "Urdu (Pakistan)" -> Locale.forLanguageTag("ur-PK")
            "Pashto (Afghanistan)" -> Locale.forLanguageTag("ps-AF")
            else -> Locale.US
        }
        val result = currentEngine.setLanguage(selectedLocale)

        Log.i(
            "GuardianVoice",
            "TTS language configuration: locale=" +
                selectedLocale.toLanguageTag() +
                ", languageResult=" + result +
                ", speechRateResult=" + rateResult
        )

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
     * Returns true only when Android accepts the request.
     * Every utterance receives a unique ID for playback callbacks.
     */
    @Synchronized
    fun speak(
        message: String,
        flushQueue: Boolean = true,
        utteranceId: String? = null
    ): Boolean {
        if (closed || message.isBlank()) {
            Log.w(
                "GuardianVoice",
                "TTS request skipped: closed=$closed, blank=" + message.isBlank()
            )
            return false
        }

        val currentEngine = engine
        if (currentEngine == null) {
            Log.w("GuardianVoice", "TTS request skipped: engine is null")
            return false
        }

        val queueMode = if (flushQueue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val requestId = (utteranceId ?: "guardian-utterance") +
            "-" + utteranceCounter.incrementAndGet()
        val result = currentEngine.speak(message, queueMode, null, requestId)
        val accepted = result == TextToSpeech.SUCCESS

        Log.i(
            "GuardianVoice",
            "TTS speak request: accepted=$accepted, result=$result, textLength=" +
                message.length + ", utteranceId=" + requestId
        )
        if (accepted) {
            latestUtteranceId = requestId
        } else {
            Log.e("GuardianVoice", "TTS engine rejected speech request: result=$result")
        }
        return accepted
    }

    /**
     * Lets the live guidance pipeline avoid stacking stale announcements.
     */
    @Synchronized
    fun isSpeaking(): Boolean {
        if (closed) return false
        return engine?.isSpeaking == true
    }

    @Synchronized
    fun stop() {
        if (!closed) {
            latestUtteranceId = null
            engine?.stop()
            Log.i("GuardianVoice", "TTS stop requested")
        }
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        val currentEngine = engine
        latestUtteranceId = null
        engine = null
        currentEngine?.stop()
        currentEngine?.shutdown()
        Log.i("GuardianVoice", "TTS engine released")
    }
}
