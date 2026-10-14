package com.sara.hermes.data

import kotlinx.coroutines.Dispatchers
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

    suspend fun submitTask(
        text: String,
        onStatusUpdate: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl().trimEnd('/')
        try {
            val jsonBody = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("type", "text")
                    put("text", text)
                })
                put("mode", "execute")
                put("response", JSONObject().apply {
                    put("text", true)
                    put("speech", true)
                })
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
            val taskId = json.optString("task_id", "")
            val output = json.optString("output", "")
            val status = json.optString("status", "succeeded")

            onStatusUpdate(status)
            if (output.isNotBlank()) {
                Result.success(output)
            } else if (taskId.isNotBlank()) {
                // Connect websocket or wait for result
                Result.success("Task $taskId submitted successfully")
            } else {
                Result.success("Completed task successfully")
            }
        } catch (e: Exception) {
            // Graceful fallback for offline demo / disconnected state
            Result.failure(e)
        }
    }
}
