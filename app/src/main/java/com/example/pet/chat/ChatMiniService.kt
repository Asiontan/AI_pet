package com.example.pet.chat

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * 管理聊天 Mini 悬浮条的 Service（运行在 app 模块，可直接引用 ChatMiniFloatView）
 * Actions:
 *   ACTION_SHOW  - 显示 Mini 悬浮条
 *   ACTION_HIDE  - 隐藏 Mini 悬浮条
 */
class ChatMiniService : Service() {

    private var miniFloatView: ChatMiniFloatView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        miniFloatView = ChatMiniFloatView(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> miniFloatView?.show()
            ACTION_HIDE -> {
                isShowing = false
                miniFloatView?.hide()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isShowing = false
        miniFloatView?.hide()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.example.pet.chat.SHOW_MINI"
        const val ACTION_HIDE = "com.example.pet.chat.HIDE_MINI"

        /** 全局标记：Mini 悬浮条是否正在显示，供其他组件查询 */
        @Volatile
        var isShowing: Boolean = false
            private set

        fun show(context: Context) {
            isShowing = true
            context.startService(
                Intent(context, ChatMiniService::class.java).apply { action = ACTION_SHOW }
            )
        }

        fun hide(context: Context) {
            isShowing = false
            context.startService(
                Intent(context, ChatMiniService::class.java).apply { action = ACTION_HIDE }
            )
        }
    }
}

