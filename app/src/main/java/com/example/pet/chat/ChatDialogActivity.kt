package com.example.pet.chat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pet.R
import com.pet.core.data.chat.ChatMessage
import com.pet.pet.service.PetForegroundService
import com.pet.pet.service.chat.ChatManager

/**
 * 完整聊天弹窗 Activity
 * - 长按宠物悬浮窗 / Mini 条点展开 触发
 * - 缩小按钮：启动 ChatMiniService（TYPE_APPLICATION_OVERLAY 悬浮条），Activity finish()
 * - Mini 条背后完全透明可交互，点展开重新启动本 Activity
 */
class ChatDialogActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnMinimize: ImageButton

    private val adapter = ChatAdapter()
    private var streamingMsgId: Long = -1L
    private var streamingContent = StringBuilder()
    private var isWaiting = false

    private val chatReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val msgId = intent.getLongExtra(ChatManager.EXTRA_MSG_ID, -1L)
            when (intent.action) {
                ChatManager.ACTION_CHAT_TOKEN -> {
                    val token = intent.getStringExtra(ChatManager.EXTRA_TOKEN) ?: return
                    if (msgId == streamingMsgId) {
                        streamingContent.append(token)
                        adapter.appendOrUpdateLast(
                            ChatMessage(id = streamingMsgId,
                                content = streamingContent.toString(),
                                isUser = false, isLoading = true)
                        )
                        rvMessages.scrollToPosition(adapter.itemCount - 1)
                    }
                }
                ChatManager.ACTION_CHAT_DONE -> {
                    val fullReply = intent.getStringExtra(ChatManager.EXTRA_FULL_REPLY) ?: ""
                    val emotionScore = intent.getIntExtra(ChatManager.EXTRA_EMOTION_SCORE, 5)
                    adapter.appendOrUpdateLast(
                        ChatMessage(id = streamingMsgId, content = fullReply,
                            isUser = false, isLoading = false)
                    )
                    rvMessages.scrollToPosition(adapter.itemCount - 1)
                    setWaiting(false)
                    applyEmotionToService(emotionScore)
                }
                ChatManager.ACTION_CHAT_ERROR -> {
                    val errMsg = intent.getStringExtra(ChatManager.EXTRA_ERROR_MSG) ?: "未知错误"
                    adapter.appendOrUpdateLast(
                        ChatMessage(id = streamingMsgId, content = "⚠️ $errMsg",
                            isUser = false, isLoading = false)
                    )
                    rvMessages.scrollToPosition(adapter.itemCount - 1)
                    setWaiting(false)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContentView(R.layout.activity_chat_dialog)

        rvMessages   = findViewById(R.id.rvMessages)
        etInput      = findViewById(R.id.etInput)
        btnSend      = findViewById(R.id.btnSend)
        btnClose     = findViewById(R.id.btnClose)
        btnSettings  = findViewById(R.id.btnSettings)
        btnMinimize  = findViewById(R.id.btnMinimize)

        rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.adapter = adapter

        etInput.setHintTextColor(0x88555577.toInt())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            etInput.textCursorDrawable = ColorDrawable(0xFFA78BFA.toInt())
        }

        // 点击半透明背景关闭
        findViewById<View>(R.id.rootLayout).setOnClickListener { finish() }
        findViewById<View>(R.id.cardChat).setOnClickListener { /* 阻止冒泡 */ }

        btnClose.setOnClickListener { finish() }
        btnSettings.setOnClickListener { showApiKeyDialog() }
        btnMinimize.setOnClickListener { minimizeToFloat() }
        btnSend.setOnClickListener { sendMessage() }

        etInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN)) {
                sendMessage(); true
            } else false
        }

        registerChatReceiver()

        // 加载本地历史记录
        val savedHistory = chatManager.getHistory()
        if (savedHistory.isNotEmpty()) {
            savedHistory.forEach { adapter.addMessage(it) }
            rvMessages.scrollToPosition(adapter.itemCount - 1)
        } else if (!chatManager.getApiKey().isBlank()) {
            adapter.addMessage(
                ChatMessage(content = "嗨~有什么想和我说的吗？(｡•ᴗ•｡)", isUser = false)
            )
        }

        if (chatManager.getApiKey().isBlank()) {
            showApiKeyDialog()
        }

        // 处理来自 Mini 悬浮条的待发消息
        handlePendingMessage(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePendingMessage(intent)
    }

    private fun handlePendingMessage(intent: Intent?) {
        val pending = intent?.getStringExtra(EXTRA_PENDING_MESSAGE) ?: return
        if (pending.isBlank()) return
        // 填入输入框并自动发送
        etInput.setText(pending)
        // 延迟一帧确保 UI 已就绪
        etInput.post { sendMessage() }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(chatReceiver) } catch (_: Exception) {}
    }

    /** 缩小：启动 Mini 悬浮条 Service（完全透明可穿透），Activity 退出 */
    private fun minimizeToFloat() {
        hideKeyboard()
        ChatMiniService.show(this)
        finish()
    }

    private fun sendMessage() {
        val text = etInput.text.toString().trim()
        if (text.isBlank() || isWaiting) return
        if (chatManager.getApiKey().isBlank()) { showApiKeyDialog(); return }

        adapter.addMessage(ChatMessage(content = text, isUser = true))
        rvMessages.scrollToPosition(adapter.itemCount - 1)
        etInput.setText("")
        hideKeyboard()

        streamingMsgId = System.currentTimeMillis()
        streamingContent.clear()
        adapter.addMessage(
            ChatMessage(id = streamingMsgId, content = "", isUser = false, isLoading = true)
        )
        rvMessages.scrollToPosition(adapter.itemCount - 1)

        setWaiting(true)
        chatManager.sendMessage(text, streamingMsgId)
    }

    private fun setWaiting(waiting: Boolean) {
        isWaiting = waiting
        btnSend.isEnabled = !waiting
        btnSend.alpha = if (waiting) 0.5f else 1f
    }

    private fun applyEmotionToService(emotionScore: Int) {
        try {
            startService(Intent(this, PetForegroundService::class.java).apply {
                action = PetForegroundService.ACTION_APPLY_EMOTION
                putExtra(PetForegroundService.EXTRA_EMOTION_SCORE, emotionScore)
            })
        } catch (_: Exception) {}
    }

    private fun registerChatReceiver() {
        val filter = IntentFilter().apply {
            addAction(ChatManager.ACTION_CHAT_TOKEN)
            addAction(ChatManager.ACTION_CHAT_DONE)
            addAction(ChatManager.ACTION_CHAT_ERROR)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(chatReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(chatReceiver, filter)
        }
    }

    private fun showApiKeyDialog() {
        val editText = EditText(this).apply {
            hint = "sk-xxxxxxxxxxxxxxxx"
            setText(chatManager.getApiKey())
            setTextColor(0xFFE8D5C4.toInt())
            setHintTextColor(0x88AAAAAA.toInt())
            setPadding(48, 32, 48, 32)
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("配置 DeepSeek API Key")
            .setMessage("请前往 platform.deepseek.com 获取 API Key")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val key = editText.text.toString().trim()
                if (key.isNotBlank()) {
                    chatManager.saveApiKey(key)
                    if (adapter.itemCount == 0) {
                        adapter.addMessage(ChatMessage(
                            content = "API Key 已保存！来和我聊天吧 (｡•ᴗ•｡)", isUser = false))
                    }
                } else {
                    Toast.makeText(this, "API Key 不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private val chatManager: ChatManager by lazy { ChatManager(applicationContext) }

    companion object {
        const val EXTRA_PENDING_MESSAGE = "extra_pending_message"
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }
}
