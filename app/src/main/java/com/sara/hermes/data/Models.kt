package com.sara.hermes.data

import java.util.UUID

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class TaskStatus(
    val taskId: String,
    val status: String,
    val summary: String = ""
)
