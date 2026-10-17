package com.sara.hermes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.Toast
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sara.hermes.audio.HeySiriService
import com.sara.hermes.audio.SpeechManager
import com.sara.hermes.data.ApiClient
import com.sara.hermes.data.AssistantState
import com.sara.hermes.data.ChatMessage
import com.sara.hermes.data.DeviceActionHandler
import com.sara.hermes.ui.HomeScreen
import com.sara.hermes.ui.SaraHermesTheme
import com.sara.hermes.ui.SettingsDialog
import com.sara.hermes.ui.SiriOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var assistantState by mutableStateOf(AssistantState.IDLE)
    private var audioRms by mutableFloatStateOf(0f)
    private var currentTranscript by mutableStateOf("")
    private var currentStatus by mutableStateOf("Ready")
    private val messages = mutableStateListOf<ChatMessage>()
    private var showSettingsDialog by mutableStateOf(false)
    private var backendUrl by mutableStateOf("http://10.0.2.2:8000")
    private var isBackgroundListening by mutableStateOf(true)
    private var isOverlayMode by mutableStateOf(false)
    private var selectedLanguage by mutableStateOf("auto")

    private lateinit var speechManager: SpeechManager
    private lateinit var apiClient: ApiClient
    private lateinit var deviceActionHandler: DeviceActionHandler

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val audioGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            if (isBackgroundListening) {
                HeySiriService.start(this)
            }
            toggleListening()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    private val speechIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                currentTranscript = spokenText
                processUserInput(spokenText)
            }
        }
        if (assistantState == AssistantState.LISTENING) {
            assistantState = AssistantState.IDLE
        }
    }

    private fun launchSpeechIntent() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ta-IN", "en-IN", "en-US"))
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now / பேசுங்கள்...")
            }
            assistantState = AssistantState.LISTENING
            speechIntentLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice recognition unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            assistantState = AssistantState.IDLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen and show over lockscreen like Siri and Google Assistant
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Load saved preferences if any
        val prefs = getSharedPreferences("sara_prefs", MODE_PRIVATE)
        backendUrl = prefs.getString("backend_url", "http://10.0.2.2:8000") ?: "http://10.0.2.2:8000"
        isBackgroundListening = prefs.getBoolean("bg_listening", true)
        selectedLanguage = prefs.getString("voice_lang", "auto") ?: "auto"

        if (isBackgroundListening && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            HeySiriService.start(this)
        }

        apiClient = ApiClient { backendUrl }
        deviceActionHandler = DeviceActionHandler(this)

        speechManager = SpeechManager(
            context = this,
            onTranscriptResult = { text ->
                currentTranscript = text
                processUserInput(text)
            },
            onPartialTranscriptResult = { partial ->
                currentTranscript = partial
            },
            onAudioRmsChanged = { rms ->
                audioRms = rms
            },
            onListeningStateChanged = { isListening ->
                if (isListening) {
                    assistantState = AssistantState.LISTENING
                } else if (assistantState == AssistantState.LISTENING) {
                    assistantState = AssistantState.THINKING
                }
            },
            onSpeechError = { error ->
                currentStatus = error
                assistantState = AssistantState.IDLE
                currentTranscript = ""
                if (error.contains("not available", ignoreCase = true) || error.contains("busy", ignoreCase = true)) {
                    launchSpeechIntent()
                }
            }
        )
        speechManager.setLanguage(selectedLanguage)

        // Initial welcome message
        messages.add(
            ChatMessage(
                text = "Hello! I am Siri, your Hermes AI Assistant. Tap the microphone or type below to get started.",
                isUser = false
            )
        )

        setContent {
            SaraHermesTheme {
                if (isOverlayMode) {
                    SiriOverlay(
                        state = assistantState,
                        audioRms = audioRms,
                        currentTranscript = currentTranscript,
                        lastResponse = currentStatus,
                        onMicToggle = { checkPermissionAndToggle() },
                        onDismiss = {
                            isOverlayMode = false
                            finish()
                        }
                    )
                } else {
                    HomeScreen(
                        state = assistantState,
                        audioRms = audioRms,
                        currentTranscript = currentTranscript,
                        currentStatus = currentStatus,
                        messages = messages,
                        onMicToggle = { checkPermissionAndToggle() },
                        onSendMessage = { text -> processUserInput(text) },
                        onOpenSettings = { showSettingsDialog = true }
                    )

                    if (showSettingsDialog) {
                    SettingsDialog(
                        currentUrl = backendUrl,
                        isBackgroundListening = isBackgroundListening,
                        currentLanguage = selectedLanguage,
                        onLanguageChange = { lang ->
                            selectedLanguage = lang
                            speechManager.setLanguage(lang)
                            val prefs = getSharedPreferences("sara_prefs", MODE_PRIVATE)
                            prefs.edit().putString("voice_lang", lang).apply()
                        },
                        onToggleBackgroundListening = { enabled ->
                            isBackgroundListening = enabled
                            prefs.edit().putBoolean("bg_listening", enabled).apply()
                            if (enabled) {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    HeySiriService.start(this@MainActivity)
                                    Toast.makeText(this@MainActivity, "Background 'Hey Siri' active", Toast.LENGTH_SHORT).show()
                                } else {
                                    checkPermissionAndToggle()
                                }
                            } else {
                                HeySiriService.stop(this@MainActivity)
                                Toast.makeText(this@MainActivity, "Background listening stopped", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenAssistSettings = {
                            try {
                                val assistIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(assistIntent)
                            } catch (_: Exception) {
                                try {
                                    val manageIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(manageIntent)
                                } catch (_: Exception) {
                                    Toast.makeText(this@MainActivity, "Set Siri in Settings -> Apps -> Default apps -> Assist app", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onSave = { newUrl ->
                            backendUrl = newUrl
                            prefs.edit().putString("backend_url", newUrl).apply()
                            Toast.makeText(this@MainActivity, "Backend URL updated", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { showSettingsDialog = false }
                    )
                }
            }
        }
    }

    // Automatically handle wake-word intent if activity launched by HeySiriService or Assist
    handleIncomingIntent(intent)
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
}

private fun handleIncomingIntent(incomingIntent: Intent?) {
    if (incomingIntent == null) return
    val command = incomingIntent.getStringExtra("EXTRA_COMMAND")
    val action = incomingIntent.action
    val isOverlay = incomingIntent.getBooleanExtra("EXTRA_OVERLAY_MODE", false)

    if (isOverlay || action == "ACTION_HEY_SIRI" || action == Intent.ACTION_ASSIST || action == Intent.ACTION_VOICE_COMMAND) {
        isOverlayMode = true
    }

        if (!command.isNullOrBlank()) {
            lifecycleScope.launch {
                delay(50)
                processUserInput(command)
            }
        } else if (action == "ACTION_HEY_SIRI" || action == Intent.ACTION_ASSIST || action == Intent.ACTION_VOICE_COMMAND) {
            lifecycleScope.launch {
                delay(50)
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    toggleListening()
                } else {
                    checkPermissionAndToggle()
                }
            }
        }
    }

    private fun checkPermissionAndToggle() {
        try {
            val audioPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (audioPerm) {
                if (isBackgroundListening && !HeySiriService.isRunning) {
                    HeySiriService.start(this)
                }
                toggleListening()
            } else {
                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    arrayOf(Manifest.permission.RECORD_AUDIO)
                }
                permissionLauncher.launch(perms)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleListening() {
        try {
            if (assistantState == AssistantState.LISTENING) {
                speechManager.stopListening()
                assistantState = AssistantState.IDLE
                currentTranscript = ""
            } else {
                if (speechManager.isRecognitionSupported()) {
                    currentTranscript = "Listening..."
                    speechManager.startListening()
                } else {
                    launchSpeechIntent()
                }
            }
        } catch (e: Exception) {
            launchSpeechIntent()
        }
    }

    private fun processUserInput(input: String) {
        if (input.isBlank()) return

        messages.add(ChatMessage(text = input, isUser = true))
        currentTranscript = ""

        // 1. Siri-style local device actions (Open YouTube, WhatsApp, Camera, Settings, etc.)
        val localAction = deviceActionHandler.tryExecute(input, selectedLanguage)
        if (localAction is DeviceActionHandler.ActionResult.Handled) {
            messages.add(ChatMessage(text = localAction.speechResponse, isUser = false))
            currentStatus = localAction.speechResponse

            var actionTriggered = false
            fun triggerActionOnce() {
                if (!actionTriggered) {
                    actionTriggered = true
                    localAction.action()
                }
            }

            // Immediately execute device action (set alarm, pause music, torch, etc.)
            triggerActionOnce()

            // Speak out completion confirmation and wait until voice finishes before dismissing overlay
            speechManager.speak(
                text = localAction.speechResponse,
                onStart = {
                    assistantState = AssistantState.SPEAKING
                },
                onDone = {
                    assistantState = AssistantState.IDLE
                    currentStatus = "Ready"
                    if (isOverlayMode) {
                        lifecycleScope.launch {
                            delay(1200)
                            finish()
                        }
                    }
                }
            )
            return
        }

        // 2. Cloud AI / Hermes Assistant query
        assistantState = AssistantState.THINKING
        currentStatus = "Connecting to Hermes..."

        lifecycleScope.launch {
            val recentHistory = messages.dropLast(1).takeLast(5)
            val result = apiClient.submitTask(input, history = recentHistory) { statusUpdate ->
                currentStatus = statusUpdate.replaceFirstChar { it.uppercase() }
            }

            val replyText = result.getOrElse { err ->
                // If backend is offline / not running yet, provide clean response indicating state
                "I heard you say: '$input'. I'm ready to connect to your Hermes backend at $backendUrl."
            }

            currentStatus = "Responding..."
            messages.add(ChatMessage(text = replyText, isUser = false))

            // Speak out reply
            speechManager.speak(
                text = replyText,
                onStart = {
                    assistantState = AssistantState.SPEAKING
                },
                onDone = {
                    assistantState = AssistantState.IDLE
                    currentStatus = "Ready"
                    if (isOverlayMode) {
                        lifecycleScope.launch {
                            delay(2000)
                            finish()
                        }
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (isBackgroundListening) {
            HeySiriService.pause(this)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isBackgroundListening) {
            HeySiriService.resume(this, 300)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.destroy()
        if (isBackgroundListening) {
            HeySiriService.resume(this, 300)
        }
    }
}
