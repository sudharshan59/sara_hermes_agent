package com.sara.hermes.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient(private val getBaseUrl: () -> String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun JSONObject.getSafeString(key: String): String {
        if (isNull(key)) return ""
        val str = optString(key, "").trim()
        return if (str.equals("null", ignoreCase = true)) "" else str
    }

    suspend fun submitTask(
        text: String,
        onStatusUpdate: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl().trimEnd('/')
        try {
            onStatusUpdate("thinking")

            // 1. Try synchronous /api/v1/chat for fast direct answer
            try {
                val chatBody = JSONObject().apply {
                    put("message", text)
                    put("client_id", "com.sara.hermes")
                }
                val chatRequest = Request.Builder()
                    .url("$baseUrl/api/v1/chat")
                    .post(chatBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val chatResponse = client.newCall(chatRequest).execute()
                if (chatResponse.isSuccessful) {
                    val chatJson = JSONObject(chatResponse.body?.string() ?: "{}")
                    val reply = chatJson.getSafeString("response").ifBlank { chatJson.getSafeString("summary") }
                    if (reply.isNotBlank()) {
                        onStatusUpdate("completed")
                        return@withContext Result.success(reply)
                    }
                }
            } catch (_: Exception) {
                // Fallback to /api/v1/tasks
            }

            // 2. Fallback: Submit via /api/v1/tasks
            val jsonBody = JSONObject().apply {
                put("prompt", text)
                put("client_id", "com.sara.hermes")
            }

            val request = Request.Builder()
                .url("$baseUrl/api/v1/tasks")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            onStatusUpdate("queued")
            val response: Response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Server error: ${response.code}"))
            }

            val bodyStr = response.body?.string() ?: "{}"
            val json = JSONObject(bodyStr)
            val taskId = json.getSafeString("task_id")
            val immediateSummary = json.getSafeString("summary").ifBlank { json.getSafeString("output") }

            if (immediateSummary.isNotBlank()) {
                onStatusUpdate("completed")
                return@withContext Result.success(immediateSummary)
            }

            if (taskId.isNotBlank()) {
                // Poll /api/v1/tasks/{task_id} for the completed AI response
                var finalResult = ""
                for (i in 1..60) {
                    delay(500)
                    val pollRequest = Request.Builder()
                        .url("$baseUrl/api/v1/tasks/$taskId")
                        .get()
                        .build()
                    val pollResponse = client.newCall(pollRequest).execute()
                    if (pollResponse.isSuccessful) {
                        val statusJson = JSONObject(pollResponse.body?.string() ?: "{}")
                        val pollStatus = statusJson.optString("status", "")
                        val pollSummary = statusJson.getSafeString("summary")
                        onStatusUpdate(pollStatus)
                        if (pollStatus == "completed" && pollSummary.isNotBlank()) {
                            finalResult = pollSummary
                            break
                        } else if (pollStatus == "failed" || pollStatus == "cancelled") {
                            return@withContext Result.failure(Exception(statusJson.getSafeString("detail").ifBlank { "Task failed" }))
                        }
                    }
                }
                if (finalResult.isNotBlank()) {
                    Result.success(finalResult)
                } else {
                    Result.failure(Exception("Task execution timed out"))
                }
            } else {
                Result.failure(Exception("No task ID received from server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
