package com.example.pet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pet.chat.ChatMiniService
import com.pet.pet.service.PetForegroundService

/**
 * 接收来自 PetForegroundService 的聊天相关广播：
 * - ACTION_CLOSE_MINI_AND_OPEN_CHAT：先关 Mini 条，再打开全屏聊天
 * - ACTION_CLOSE_ALL_CHAT_UI：服务停止时关闭所有聊天 UI
 */
class OpenChatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PetForegroundService.ACTION_CLOSE_MINI_AND_OPEN_CHAT -> {
                // 先关闭 Mini 悬浮条（若存在）
                if (ChatMiniService.isShowing) {
                    ChatMiniService.hide(context)
                }
                // 打开全屏聊天界面
                try {
                    context.startActivity(Intent().apply {
                        setClassName(context.packageName, "com.example.pet.chat.ChatDialogActivity")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    })
                } catch (e: Exception) {
                    android.util.Log.e("OpenChatReceiver", "Failed to open chat dialog", e)
                }
            }
            PetForegroundService.ACTION_SWIPE_DOWN -> {
                val dm = context.resources.displayMetrics
                if (PetAccessibilityService.isAvailable) {
                    PetAccessibilityService.swipeUp(dm.widthPixels, dm.heightPixels)
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "请先在「设置 → 无障碍」中开启 Pet Desktop 手势控制服务",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            PetForegroundService.ACTION_CLOSE_ALL_CHAT_UI -> {
                // 关闭 Mini 悬浮条
                if (ChatMiniService.isShowing) {
                    ChatMiniService.hide(context)
                }
                // 发送广播通知 ChatDialogActivity 关闭自身
                try {
                    context.sendBroadcast(Intent(ACTION_FINISH_CHAT_ACTIVITY).apply {
                        setPackage(context.packageName)
                    })
                } catch (_: Exception) {}
            }
        }
    }

    companion object {
        /** ChatDialogActivity 监听此广播后调用 finish() */
        const val ACTION_FINISH_CHAT_ACTIVITY = "com.example.pet.action.FINISH_CHAT_ACTIVITY"
    }
}
