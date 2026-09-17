package com.matheo.flashcardcompanion.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class AiException(message: String, val status: Int? = null) : Exception(message)

data class ChatMessage(val role: String, val content: String)

/**
 * Direct client for Infercom's OpenAI-compatible endpoint.
 *
 * This is the app's only outbound call — the rest of the app is fully offline.
 * It replaces the Python backend's OpenAI SDK usage, including its retry
 * policy, which `HttpURLConnection` gives us none of for free.
 *
 * Deliberately sends only `model` and `messages`: the backend never set a
 * temperature, and the product's tone comes entirely from the prompts plus the
 * server default. Adding one here would be a silent behaviour change.
 */
class InfercomClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 600_000
        private const val MAX_RETRIES = 2
    }

    val hasKey: Boolean get() = apiKey.isNotBlank()

    suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        if (!hasKey) throw AiException("no-key")

        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach {
                    put(JSONObject().apply { put("role", it.role); put("content", it.content) })
                }
            })
        }.toString()

        var lastError: Exception? = null
        repeat(MAX_RETRIES + 1) { attempt ->
            if (attempt > 0) delay(500L * (1L shl (attempt - 1)))
            try {
                return@withContext parseChoice(post("/chat/completions", body))
            } catch (e: AiException) {
                // 4xx other than rate-limiting is a real answer: do not retry it.
                val s = e.status
                if (s != null && s < 500 && s != 408 && s != 409 && s != 429) throw e
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: AiException("unreachable")
    }

    /** Cheap reachability probe — a valid key and real internet. */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        if (!hasKey) return@withContext false
        runCatching {
            val conn = open("/models", "GET")
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = CONNECT_TIMEOUT_MS
            conn.connect()
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            ok
        }.getOrDefault(false)
    }

    private fun open(path: String, method: String): HttpURLConnection {
        val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        return conn
    }

    private fun post(path: String, body: String): String {
        val conn = open(path, "POST")
        conn.doOutput = true
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (status !in 200..299) throw AiException("HTTP $status: ${text.take(300)}", status)
            return text
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Only `choices[0].message.content` is read. A null content is treated as
     * empty rather than crashing — the backend would have thrown here.
     */
    private fun parseChoice(response: String): String {
        val choices = JSONObject(response).optJSONArray("choices")
            ?: throw AiException("malformed response")
        if (choices.length() == 0) throw AiException("empty response")
        val message = choices.getJSONObject(0).optJSONObject("message")
        return message?.optString("content").orEmpty()
    }
}
