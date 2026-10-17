package com.sara.hermes.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class SpeechManager(
    private val context: Context,
    private val onTranscriptResult: (String) -> Unit,
    private val onPartialTranscriptResult: (String) -> Unit,
    private val onAudioRmsChanged: (Float) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onSpeechError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    var preferredLanguage: String = "auto" // "auto", "ta", "en"

    fun setLanguage(lang: String) {
        preferredLanguage = lang
        if (isTtsReady) {
            setupOptimalVoice(isTamil = lang == "ta")
        }
    }

    init {
        try {
            // Prefer Google Speech Services engine for studio-quality neural voices
            textToSpeech = TextToSpeech(context.applicationContext, this, "com.google.android.tts")
        } catch (_: Exception) {
            try {
                textToSpeech = TextToSpeech(context.applicationContext, this)
            } catch (_: Exception) {}
        }
    }

    fun isRecognitionSupported(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }
    }

    fun startListening() {
        stopSpeaking()
        mainHandler.post {
            try {
                if (!isRecognitionSupported()) {
                    onSpeechError("Speech recognition not available on this device")
                    return@post
                }

                speechRecognizer?.destroy()
                speechRecognizer = null

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            onListeningStateChanged(true)
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {
                            try {
                                onAudioRmsChanged(rmsdB)
                            } catch (_: Throwable) {}
                        }
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            onListeningStateChanged(false)
                        }
                        override fun onError(error: Int) {
                            onListeningStateChanged(false)
                            val message = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy"
                                else -> "Recognition error ($error)"
                            }
                            onSpeechError(message)
                        }
                        override fun onResults(results: Bundle?) {
                            try {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull() ?: ""
                                if (text.isNotBlank()) {
                                    onTranscriptResult(text)
                                }
                            } catch (e: Throwable) {
                                onSpeechError("Error: ${e.message}")
                            } finally {
                                onListeningStateChanged(false)
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            try {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull() ?: ""
                                if (text.isNotBlank()) {
                                    onPartialTranscriptResult(text)
                                }
                            } catch (_: Throwable) {}
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    when (preferredLanguage) {
                        "ta" -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ta-IN")
                        }
                        "en" -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
                        }
                        else -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ta-IN", "en-IN", "en-US"))
                        }
                    }
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 750L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 750L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Throwable) {
                onSpeechError("Microphone initialization failed: ${e.message}")
                onListeningStateChanged(false)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Throwable) {}
            onListeningStateChanged(false)
        }
    }

    fun speak(text: String, onStart: () -> Unit = {}, onDone: () -> Unit = {}) {
        if (!isTtsReady) {
            onDone()
            return
        }

        val isTamil = when (preferredLanguage) {
            "ta" -> true
            "en" -> false
            else -> text.any { it in '\u0B80'..'\u0BFF' }
        }
        setupOptimalVoice(isTamil)

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post { onStart() }
            }
            override fun onDone(utteranceId: String?) {
                mainHandler.post { onDone() }
            }
            override fun onError(utteranceId: String?) {
                mainHandler.post { onDone() }
            }
        })
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
    }

    private fun setupOptimalVoice(isTamil: Boolean) {
        val tts = textToSpeech ?: return
        val targetLocale = if (isTamil) Locale("ta", "IN") else Locale.US
        try {
            val res = tts.setLanguage(targetLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (isTamil) {
                    Log.w("SpeechManager", "Tamil TTS data missing, falling back to default locale")
                    tts.language = Locale.getDefault()
                }
            }
        } catch (_: Throwable) {
            tts.language = Locale.getDefault()
        }

        // Natural pitch and rate tuning for fluid speech flow
        tts.setPitch(1.05f)
        tts.setSpeechRate(0.96f)

        try {
            val voices = tts.voices
            if (!voices.isNullOrEmpty()) {
                val matchedVoice = if (isTamil) {
                    voices.firstOrNull { v ->
                        val n = v.name.lowercase()
                        (v.locale.language == "ta" || n.contains("ta-in") || n.contains("tamil")) &&
                        (n.contains("network") || n.contains("neural") || n.contains("taf") || v.quality >= Voice.QUALITY_HIGH)
                    } ?: voices.firstOrNull { v ->
                        v.locale.language == "ta" || v.name.lowercase().contains("ta-in")
                    }
                } else {
                    voices.firstOrNull { v ->
                        val n = v.name.lowercase()
                        (n.contains("en-us") || n.contains("en_us")) &&
                        (n.contains("neural") || n.contains("network") || n.contains("female") || n.contains("sfg"))
                    } ?: voices.firstOrNull { v ->
                        v.locale.language == "en"
                    }
                }

                if (matchedVoice != null) {
                    tts.voice = matchedVoice
                    Log.d("SpeechManager", "Selected Optimal Voice: ${matchedVoice.name}")
                }
            }
        } catch (e: Exception) {
            Log.w("SpeechManager", "Voice selection fallback: ${e.message}")
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            setupOptimalVoice(isTamil = false)
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
