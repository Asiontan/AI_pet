package com.example.pet.chat

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import com.example.pet.R
import com.pet.pet.service.PetForegroundService
import com.pet.pet.service.chat.ChatManager

/**
 * 聊天缩小悬浮条
 * 通过 WindowManager 以 TYPE_APPLICATION_OVERLAY 显示，与宠物悬浮窗同层级。
 * 点击展开 → 发送 ACTION_OPEN_CHAT 重新启动 ChatDialogActivity
 * 点击发送 → 直接调用 ChatManager 发消息（回复在展开后显示）
 */
class ChatMiniFloatView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null
    private val chatManager = ChatManager(context)

    fun show() {
        if (rootView != null) return

        // Service 没有 Activity 主题，需用 ContextThemeWrapper 注入 Material 主题
        // 否则 ?attr/selectableItemBackgroundBorderless 无法解析导致 inflate 崩溃
        val themedContext = ContextThemeWrapper(context, R.style.Theme_Pet)
        val view = LayoutInflater.from(themedContext)
            .inflate(R.layout.view_chat_mini_float, null, false)

        val etInput  = view.findViewById<EditText>(R.id.etMiniInput)
        val btnSend  = view.findViewById<ImageButton>(R.id.btnMiniSend)
        val btnClose = view.findViewById<ImageButton>(R.id.btnMiniClose)
        val btnExpand = view.findViewById<ImageButton>(R.id.btnMiniExpand)

        etInput.setHintTextColor(0x88555577.toInt())

        // 展开 → 隐藏自己，打开完整聊天界面
        btnExpand.setOnClickListener {
            hide()
            openFullChat()
        }

        // 发送消息：先展开完整界面，再发送，确保回复可见
        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotBlank()) {
                hide()
                openFullChat(text)
            }
        }

        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                btnSend.performClick()
                true
            } else false
        }

        // 关闭
        btnClose.setOnClickListener { hide() }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 120  // 距底部 120px
        }

        // 点击输入框时让窗口可获焦（弹出键盘）
        etInput.setOnFocusChangeListener { _, hasFocus ->
            val flags = if (hasFocus) {
                lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            lp.flags = flags
            try { windowManager.updateViewLayout(view, lp) } catch (_: Exception) {}
        }

        try {
            windowManager.addView(view, lp)
            rootView = view
        } catch (e: Exception) {
            android.util.Log.e("ChatMiniFloatView", "Failed to show mini float", e)
        }
    }

    fun hide() {
        rootView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            rootView = null
        }
    }

    fun isShowing() = rootView != null

    private fun openFullChat(pendingMessage: String = "") {
        val intent = Intent().apply {
            setClassName(context.packageName, "com.example.pet.chat.ChatDialogActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (pendingMessage.isNotBlank()) {
                putExtra(ChatDialogActivity.EXTRA_PENDING_MESSAGE, pendingMessage)
            }
        }
        context.startActivity(intent)
    }

    private fun hideKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

