package com.sara.hermes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            toggleListening()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved preferences if any
        val prefs = getSharedPreferences("sara_prefs", MODE_PRIVATE)
        backendUrl = prefs.getString("backend_url", "http://10.0.2.2:8000") ?: "http://10.0.2.2:8000"

        apiClient = ApiClient { backendUrl }

        speechManager = SpeechManager(
            context = this,
            onTranscript = { text ->
                currentTranscript = text
                processUserInput(text)
            },
            onPartialTranscript = { partial ->
                currentTranscript = partial
            },
            onRmsChanged = { rms ->
                audioRms = rms
            },
            onStateChanged = { isListening ->
                if (isListening) {
                    assistantState = AssistantState.LISTENING
                } else if (assistantState == AssistantState.LISTENING) {
                    assistantState = AssistantState.THINKING
                }
            },
            onError = { error ->
                currentStatus = error
                assistantState = AssistantState.IDLE
                currentTranscript = ""
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            toggleListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun toggleListening() {
        if (assistantState == AssistantState.LISTENING) {
            speechManager.stopListening()
            assistantState = AssistantState.IDLE
            currentTranscript = ""
        } else {
            currentTranscript = "Listening..."
            speechManager.startListening()
        }
    }

    private fun processUserInput(input: String) {
        if (input.isBlank()) return

        messages.add(ChatMessage(text = input, isUser = true))
        currentTranscript = ""
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
