package com.sara.hermes.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sara.hermes.MainActivity
import java.util.Locale

class HeySiriService : Service() {

    companion object {
        const val TAG = "HeySiriService"
        const val CHANNEL_ID = "siri_hotword_channel"
        const val NOTIFICATION_ID = 2026

        const val ACTION_START = "com.sara.hermes.action.START_HOTWORD"
        const val ACTION_STOP = "com.sara.hermes.action.STOP_HOTWORD"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, HeySiriService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HeySiriService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var shouldKeepListening = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListeningLoop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isRunning = false
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isRunning = true
        shouldKeepListening = true
        startListeningLoop()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Siri Background Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Listens in background for 'Hey Siri' wake-word"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_HEY_SIRI"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, HeySiriService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Siri Assistant Active")
            .setContentText("Listening for 'Hey Siri' in background...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", pendingStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startListeningLoop() {
        if (!shouldKeepListening) return
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                        Log.w(TAG, "Speech recognition not available for hotword")
                        return@post
                    }
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                    speechRecognizer?.setRecognitionListener(createListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ta-IN", "en-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                isListening = true
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting hotword listening: ${e.message}")
                restartListeningWithDelay(1000)
            }
        }
    }

    private fun restartListeningWithDelay(delayMs: Long) {
        if (!shouldKeepListening) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (shouldKeepListening) {
                try {
                    speechRecognizer?.cancel()
                } catch (_: Throwable) {}
                startListeningLoop()
            }
        }, delayMs)
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                // Automatically restart listening on error (timeout, no match, etc.)
                restartListeningWithDelay(400)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && checkHotwordMatches(matches)) {
                    // Hotword detected!
                    return
                }
                restartListeningWithDelay(250)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (partials != null && checkHotwordMatches(partials)) {
                    // Hotword detected early!
                    try {
                        speechRecognizer?.cancel()
                    } catch (_: Throwable) {}
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun checkHotwordMatches(phrases: List<String>): Boolean {
        for (phrase in phrases) {
            val lower = phrase.lowercase().trim()
            if (containsHotword(lower)) {
                Log.i(TAG, "Wake-word matched: $lower")
                handleHotwordDetected(lower)
                return true
            }
        }
        return false
    }

    private fun containsHotword(text: String): Boolean {
        val hotwords = listOf(
            "hey siri",
            "siri",
            "hello siri",
            "hi siri",
            "ok siri",
            "ஹே சிரி",
            "சிரி"
        )
        return hotwords.any { text.contains(it) }
    }

    private fun handleHotwordDetected(phrase: String) {
        vibrateBriefly()
        wakeScreen()

        // Extract command if user said "Hey Siri open YouTube" in one breath
        val cleanCommand = phrase
            .replace("hey siri", "")
            .replace("hello siri", "")
            .replace("hi siri", "")
            .replace("ok siri", "")
            .replace("siri", "")
            .replace("ஹே சிரி", "")
            .replace("சிரி", "")
            .trim()

        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_HEY_SIRI"
            putExtra("EXTRA_OVERLAY_MODE", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (cleanCommand.isNotBlank()) {
                putExtra("EXTRA_COMMAND", cleanCommand)
            }
        }

        try {
            startActivity(openIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MainActivity directly: ${e.message}")
        }

        // Pause service listening so it doesn't collide with MainActivity's microphone
        restartListeningWithDelay(5000)
    }

    private fun wakeScreen() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "sara:HeySiriWakeLock"
            )
            wakeLock?.acquire(3000)
        } catch (_: Throwable) {}
    }

    private fun vibrateBriefly() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(120)
            }
        } catch (_: Throwable) {}
    }

    private fun stopListeningLoop() {
        shouldKeepListening = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
        } catch (_: Throwable) {}
        speechRecognizer = null
        isListening = false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListeningLoop()
        isRunning = false
    }
}