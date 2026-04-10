package com.pet.pet.service.chat

import android.content.Context
import android.content.Intent
import com.pet.algorithm.sentiment.TextSentimentAnalyzer
import com.pet.core.common.logger.PetLogger
import com.pet.core.data.chat.ChatMessage
import com.pet.core.data.chat.DeepSeekChatRepository
import com.pet.core.data.preferences.PetPreferences
import okhttp3.Call
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 聊天管理器（升级版）
 * - 维护对话历史（最近 40 条），持久化到本地 JSON 文件
 * - 支持自定义系统提示词（通过 PetPreferences 持久化）
 * - 调用 DeepSeek 流式 API
 * - 回复完成后通过 TextSentimentAnalyzer 分析情绪，联动宠物表情/动作
 */
class ChatManager(private val context: Context) {

    companion object {
        private const val TAG = "ChatManager"
        const val ACTION_CHAT_TOKEN   = "com.pet.chat.ACTION_TOKEN"
        const val ACTION_CHAT_DONE    = "com.pet.chat.ACTION_DONE"
        const val ACTION_CHAT_ERROR   = "com.pet.chat.ACTION_ERROR"
        const val ACTION_BUBBLE_TOKEN = "com.pet.chat.ACTION_BUBBLE_TOKEN"
        const val ACTION_BUBBLE_DONE  = "com.pet.chat.ACTION_BUBBLE_DONE"
        const val EXTRA_TOKEN         = "extra_token"
        const val EXTRA_FULL_REPLY    = "extra_full_reply"
        const val EXTRA_EMOTION_SCORE = "extra_emotion_score"
        const val EXTRA_ERROR_MSG     = "extra_error_msg"
        const val EXTRA_MSG_ID        = "extra_msg_id"

        private const val MAX_HISTORY   = 40
        private const val PREFS_API_KEY = "deepseek_api_key"
        private const val HISTORY_FILE  = "chat_history.json"
    }

    private val prefs = PetPreferences(context)
    private val sentimentAnalyzer = TextSentimentAnalyzer(context)
    private val history = mutableListOf<ChatMessage>()
    private var currentCall: Call? = null

    init { loadHistory() }

    // ── 历史记录持久化 ────────────────────────────────────────────────

    private fun historyFile(): File = File(context.filesDir, HISTORY_FILE)

    private fun loadHistory() {
        try {
            val file = historyFile()
            if (!file.exists()) return
            val json = JSONArray(file.readText())
            history.clear()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                history.add(ChatMessage(
                    id      = obj.getLong("id"),
                    content = obj.getString("content"),
                    isUser  = obj.getBoolean("isUser")
                ))
            }
            PetLogger.d(TAG, "Loaded ${history.size} messages")
        } catch (e: Exception) {
            PetLogger.e(TAG, "Failed to load history", e)
        }
    }

    private fun saveHistory() {
        try {
            val json = JSONArray()
            history.takeLast(MAX_HISTORY).forEach { msg ->
                json.put(JSONObject().apply {
                    put("id",      msg.id)
                    put("content", msg.content)
                    put("isUser",  msg.isUser)
                })
            }
            historyFile().writeText(json.toString())
        } catch (e: Exception) {
            PetLogger.e(TAG, "Failed to save history", e)
        }
    }

    fun clearHistory() {
        history.clear()
        try { historyFile().delete() } catch (_: Exception) {}
    }

    // ── API Key ───────────────────────────────────────────────────────

    fun saveApiKey(key: String) {
        context.getSharedPreferences("pet_chat_prefs", Context.MODE_PRIVATE)
            .edit().putString(PREFS_API_KEY, key).apply()
    }

    fun getApiKey(): String {
        val userKey = context.getSharedPreferences("pet_chat_prefs", Context.MODE_PRIVATE)
            .getString(PREFS_API_KEY, "") ?: ""
        if (userKey.isNotBlank()) return userKey
        return try {
            val clazz = Class.forName("${context.packageName}.BuildConfig")
            clazz.getField("DEEPSEEK_API_KEY").get(null) as? String ?: ""
        } catch (_: Exception) { "" }
    }

    // ── 系统提示词 ────────────────────────────────────────────────────

    /**
     * 获取当前系统提示词：
     * - 用户已自定义：使用自定义内容
     * - 否则使用默认桌宠人设
     */
    fun getSystemPrompt(): String {
        val custom = prefs.getCustomSystemPrompt()
        return if (custom.isNotBlank()) custom else DeepSeekChatRepository.DEFAULT_SYSTEM_PROMPT
    }

    fun saveSystemPrompt(prompt: String) {
        prefs.saveCustomSystemPrompt(prompt.trim())
        PetLogger.d(TAG, "System prompt saved: ${prompt.take(40)}...")
    }

    fun resetSystemPrompt() {
        prefs.saveCustomSystemPrompt("")
        PetLogger.d(TAG, "System prompt reset to default")
    }

    fun isUsingCustomPrompt(): Boolean = prefs.getCustomSystemPrompt().isNotBlank()

    // ── 消息发送 ──────────────────────────────────────────────────────

    fun sendMessage(userText: String, msgId: Long) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            broadcastError(msgId, "请先在设置中填写 DeepSeek API Key")
            return
        }

        history.add(ChatMessage(content = userText, isUser = true))
        if (history.size > MAX_HISTORY) history.removeAt(0)

        val repo = DeepSeekChatRepository(
            apiKey       = apiKey,
            systemPrompt = getSystemPrompt()
        )
        val historySnapshot = history.dropLast(1)

        currentCall?.cancel()
        currentCall = repo.sendMessage(
            history     = historySnapshot,
            userMessage = userText,
            onToken     = { token ->
                broadcastToken(msgId, token)
                broadcastBubbleToken(token)
            },
            onDone      = { fullReply ->
                history.add(ChatMessage(content = fullReply, isUser = false))
                if (history.size > MAX_HISTORY) history.removeAt(0)
                saveHistory()

                val emotionScore = try {
                    (sentimentAnalyzer.analyze(fullReply) * 10).toInt().coerceIn(0, 10)
                } catch (e: Exception) {
                    PetLogger.e(TAG, "Sentiment analysis failed", e); 5
                }
                broadcastDone(msgId, fullReply, emotionScore)
                broadcastBubbleDone()
                PetLogger.d(TAG, "Reply done, emotion=$emotionScore")
            },
            onError     = { e ->
                PetLogger.e(TAG, "Chat error", e)
                if (history.lastOrNull()?.isUser == true) history.removeAt(history.size - 1)
                broadcastError(msgId, e.message ?: "网络错误")
            }
        )
    }

    fun cancelCurrent() { currentCall?.cancel(); currentCall = null }

    fun getHistory(): List<ChatMessage> = history.toList()

    // ── Broadcast helpers ─────────────────────────────────────────────

    private fun broadcastToken(msgId: Long, token: String) {
        context.sendBroadcast(Intent(ACTION_CHAT_TOKEN).apply {
            putExtra(EXTRA_MSG_ID, msgId); putExtra(EXTRA_TOKEN, token)
            setPackage(context.packageName)
        })
    }

    private fun broadcastDone(msgId: Long, fullReply: String, emotionScore: Int) {
        context.sendBroadcast(Intent(ACTION_CHAT_DONE).apply {
            putExtra(EXTRA_MSG_ID, msgId)
            putExtra(EXTRA_FULL_REPLY, fullReply)
            putExtra(EXTRA_EMOTION_SCORE, emotionScore)
            setPackage(context.packageName)
        })
    }

    private fun broadcastError(msgId: Long, errorMsg: String) {
        context.sendBroadcast(Intent(ACTION_CHAT_ERROR).apply {
            putExtra(EXTRA_MSG_ID, msgId); putExtra(EXTRA_ERROR_MSG, errorMsg)
            setPackage(context.packageName)
        })
    }

    private fun broadcastBubbleToken(token: String) {
        context.sendBroadcast(Intent(ACTION_BUBBLE_TOKEN).apply {
            putExtra(EXTRA_TOKEN, token); setPackage(context.packageName)
        })
    }

    private fun broadcastBubbleDone() {
        context.sendBroadcast(Intent(ACTION_BUBBLE_DONE).apply {
            setPackage(context.packageName)
        })
    }
}
