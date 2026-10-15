package com.sara.hermes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
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
import com.sara.hermes.audio.SpeechManager
import com.sara.hermes.data.ApiClient
import com.sara.hermes.data.AssistantState
import com.sara.hermes.data.ChatMessage
import com.sara.hermes.data.DeviceActionHandler
import com.sara.hermes.ui.HomeScreen
import com.sara.hermes.ui.SaraHermesTheme
import com.sara.hermes.ui.SettingsDialog
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

    private lateinit var speechManager: SpeechManager
    private lateinit var apiClient: ApiClient
    private lateinit var deviceActionHandler: DeviceActionHandler

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
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
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Siri...")
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

        // Load saved preferences if any
        val prefs = getSharedPreferences("sara_prefs", MODE_PRIVATE)
        backendUrl = prefs.getString("backend_url", "http://10.0.2.2:8000") ?: "http://10.0.2.2:8000"

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

        // Initial welcome message
        messages.add(
            ChatMessage(
                text = "Hello! I am Siri, your Hermes AI Assistant. Tap the microphone or type below to get started.",
                isUser = false
            )
        )

        setContent {
            SaraHermesTheme {
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
                        onSave = { newUrl ->
                            backendUrl = newUrl
                            prefs.edit().putString("backend_url", newUrl).apply()
                            Toast.makeText(this, "Backend URL updated", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { showSettingsDialog = false }
                    )
                }
            }
        }
    }

    private fun checkPermissionAndToggle() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                toggleListening()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
        val localAction = deviceActionHandler.tryExecute(input)
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

            speechManager.speak(
                text = localAction.speechResponse,
                onStart = {
                    assistantState = AssistantState.SPEAKING
                },
                onDone = {
                    assistantState = AssistantState.IDLE
                    currentStatus = "Ready"
                    triggerActionOnce()
                }
            )

            // Launch within 600ms so the user feels immediate responsiveness like Siri
            lifecycleScope.launch {
                delay(600)
                triggerActionOnce()
                assistantState = AssistantState.IDLE
                currentStatus = "Ready"
            }
            return
        }

        // 2. Cloud AI / Hermes Assistant query
        assistantState = AssistantState.THINKING
        currentStatus = "Connecting to Hermes..."

        lifecycleScope.launch {
            val result = apiClient.submitTask(input) { statusUpdate ->
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
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.destroy()
    }
}
