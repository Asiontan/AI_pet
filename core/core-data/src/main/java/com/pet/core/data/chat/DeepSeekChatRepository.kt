package com.pet.core.data.chat

import com.pet.core.common.logger.PetLogger
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * DeepSeek Chat API 封装
 * 使用 OpenAI 兼容接口，流式输出（SSE）
 */
class DeepSeekChatRepository(
    private val apiKey: String,
    private val baseUrl: String = "https://api.deepseek.com",
    private val model: String = "deepseek-chat",
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "DeepSeekChatRepository"
        const val DEFAULT_SYSTEM_PROMPT =
            "你是用户手机桌面上的可爱宠物，性格活泼温柔，喜欢用简短可爱的语气回复。" +
            "回复要简洁，不超过80个字。" +
            "偶尔用颜文字或 emoji 表达情绪，但不要过度。" +
            "记住你是一只桌宠，不要脱离这个人设。"
    }

    /**
     * 发送消息，通过回调逐字返回流式内容
     * @param history 历史消息列表（不含 system prompt）
     * @param userMessage 本次用户输入
     * @param onToken 每收到一个 token 回调一次（在 IO 线程）
     * @param onDone  完成回调，返回完整回复文本
     * @param onError 错误回调
     * @return Call 对象，可调用 cancel() 中断
     */
    fun sendMessage(
        history: List<ChatMessage>,
        userMessage: String,
        onToken: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (Exception) -> Unit
    ): Call {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            history.takeLast(20).forEach { msg ->
                put(JSONObject().apply {
                    put("role", if (msg.isUser) "user" else "assistant")
                    put("content", msg.content)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("stream", true)
            put("max_tokens", 200)
            put("temperature", 0.9)
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    PetLogger.e(TAG, "Request failed", e)
                    onError(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: "unknown error"
                    PetLogger.e(TAG, "HTTP ${response.code}: $errBody")
                    onError(IOException("HTTP ${response.code}: $errBody"))
                    return
                }

                val fullText = StringBuilder()
                try {
                    response.body?.source()?.let { source ->
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith("data: ")) {
                                val data = line.removePrefix("data: ").trim()
                                if (data == "[DONE]") break
                                try {
                                    val json = JSONObject(data)
                                    val delta = json
                                        .getJSONArray("choices")
                                        .getJSONObject(0)
                                        .getJSONObject("delta")
                                    val token = delta.optString("content", "")
                                    if (token.isNotEmpty()) {
                                        fullText.append(token)
                                        onToken(token)
                                    }
                                } catch (_: Exception) { /* 跳过非 JSON 行 */ }
                            }
                        }
                    }
                    onDone(fullText.toString())
                } catch (e: Exception) {
                    if (!call.isCanceled()) {
                        PetLogger.e(TAG, "Stream parse error", e)
                        onError(e)
                    }
                }
            }
        })
        return call
    }
}

