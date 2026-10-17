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
        const val ACTION_PAUSE = "com.sara.hermes.action.PAUSE_HOTWORD"
        const val ACTION_RESUME = "com.sara.hermes.action.RESUME_HOTWORD"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var isPausedForActivity = false
            private set

        private var instance: HeySiriService? = null

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

        fun pause(context: Context? = null) {
            isPausedForActivity = true
            val activeInstance = instance
            if (activeInstance != null) {
                activeInstance.pauseListeningInternal()
            } else if (context != null) {
                try {
                    val intent = Intent(context, HeySiriService::class.java).apply {
                        action = ACTION_PAUSE
                    }
                    context.startService(intent)
                } catch (_: Throwable) {}
            }
        }

        fun resume(context: Context? = null, delayMs: Long = 300) {
            isPausedForActivity = false
            val activeInstance = instance
            if (activeInstance != null) {
                activeInstance.resumeListeningInternal(delayMs)
            } else if (context != null) {
                try {
                    val intent = Intent(context, HeySiriService::class.java).apply {
                        action = ACTION_RESUME
                    }
                    context.startService(intent)
                } catch (_: Throwable) {}
            }
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var shouldKeepListening = true
    private var isTriggeringHotword = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListeningLoop()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isRunning = false
                instance = null
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseListeningInternal()
                return START_STICKY
            }
            ACTION_RESUME -> {
                resumeListeningInternal(300)
                return START_STICKY
            }
        }

        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isRunning = true
        shouldKeepListening = true
        isPausedForActivity = false
        isTriggeringHotword = false
        acquireWakeLock()
        startListeningLoop()

        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sara:HeySiriServiceWakeLock")
                wakeLock?.setReferenceCounted(false)
                wakeLock?.acquire()
                Log.d(TAG, "Acquired partial CPU wake lock for continuous background listening")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire CPU wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Released CPU wake lock")
            }
        } catch (_: Throwable) {}
        wakeLock = null
    }

    fun pauseListeningInternal() {
        isPausedForActivity = true
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            destroyRecognizer()
        }
    }

    fun resumeListeningInternal(delayMs: Long = 300) {
        isPausedForActivity = false
        isTriggeringHotword = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (!isPausedForActivity && shouldKeepListening) {
                startListeningLoop()
            }
        }, delayMs)
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Throwable) {}
        speechRecognizer = null
        isListening = false
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
        if (!shouldKeepListening || isPausedForActivity || isTriggeringHotword) return

        mainHandler.post {
            if (!shouldKeepListening || isPausedForActivity || isTriggeringHotword) return@post

            try {
                if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                    Log.w(TAG, "Speech recognition not available for hotword")
                    restartListeningWithDelay(1500)
                    return@post
                }

                destroyRecognizer()

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(createListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ta-IN", "en-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 200L)
                }

                isListening = true
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting hotword listening: ${e.message}")
                destroyRecognizer()
                restartListeningWithDelay(800)
            }
        }
    }

    private fun restartListeningWithDelay(delayMs: Long) {
        if (!shouldKeepListening || isPausedForActivity) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (shouldKeepListening && !isPausedForActivity && !isTriggeringHotword) {
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
                if (isPausedForActivity || isTriggeringHotword) return

                val delay = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 150L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_AUDIO -> 800L
                    else -> 400L
                }

                destroyRecognizer()
                restartListeningWithDelay(delay)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                if (isPausedForActivity || isTriggeringHotword) return

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && checkHotwordMatches(matches)) {
                    return
                }
                destroyRecognizer()
                restartListeningWithDelay(150)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (isPausedForActivity || isTriggeringHotword) return

                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (partials != null && checkHotwordMatches(partials)) {
                    // Hotword detected early!
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun checkHotwordMatches(phrases: List<String>): Boolean {
        if (isTriggeringHotword || isPausedForActivity) return false

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
            "hey siri", "siri", "hello siri", "hi siri", "ok siri",
            "hey sara", "sara", "hello sara", "hi sara", "ok sara",
            "serious", "hey serious", "shiri", "serie", "seri",
            "cereal", "hey cereal",
            "ஹே சிரி", "சிரி", "ஹே சீரி", "சீரி", "ஹே சாரா", "சாரா"
        )
        if (hotwords.any { text.contains(it) }) return true

        val words = text.split(" ", "-", "_")
        return words.any { it in listOf("siri", "sara", "serious", "serie", "shiri", "சிரி", "சீரி", "சாரா") }
    }

    private fun handleHotwordDetected(phrase: String) {
        if (isTriggeringHotword) return
        isTriggeringHotword = true
        isPausedForActivity = true

        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()

        vibrateBriefly()
        wakeScreen()

        var cleanCommand = phrase
        val stripWords = listOf(
            "hey siri", "hello siri", "hi siri", "ok siri", "siri",
            "hey sara", "hello sara", "hi sara", "ok sara", "sara",
            "hey serious", "serious", "cereal",
            "ஹே சிரி", "சிரி", "ஹே சீரி", "சீரி", "ஹே சாரா", "சாரா"
        )
        for (w in stripWords) {
            cleanCommand = cleanCommand.replace(w, "")
        }
        cleanCommand = cleanCommand.trim()

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
            isTriggeringHotword = false
            isPausedForActivity = false
            restartListeningWithDelay(2000)
        }
    }

    private fun wakeScreen() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val screenWakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "sara:HeySiriScreenWakeLock"
            )
            screenWakeLock?.acquire(3000)
        } catch (_: Throwable) {}
    }

    private fun vibrateBriefly() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(100)
            }
        } catch (_: Throwable) {}
    }

    private fun stopListeningLoop() {
        shouldKeepListening = false
        isPausedForActivity = true
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListeningLoop()
        releaseWakeLock()
        isRunning = false
        instance = null
    }
}