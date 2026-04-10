package com.example.pet

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.pet.core.common.logger.PetLogger

/**
 * 宠物无障碍服务
 * 用于注入触摸手势（上滑/下滑）到当前前台应用
 * 需要用户在「设置 → 无障碍 → 已安装的服务」中手动开启
 */
class PetAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PetAccessibilityService"

        /** 全局单例引用，供外部调用注入手势 */
        @Volatile
        var instance: PetAccessibilityService? = null
            private set

        /** 是否可用（服务已连接） */
        val isAvailable: Boolean get() = instance != null

        /**
         * 在当前屏幕执行下滑手势（模拟手指从屏幕中部向下滑动）
         * @param screenWidth  屏幕宽度（px）
         * @param screenHeight 屏幕高度（px）
         */
        fun swipeDown(screenWidth: Int, screenHeight: Int) {
            instance?.performSwipe(
                startX = screenWidth / 2f,
                startY = screenHeight * 0.35f,
                endX   = screenWidth / 2f,
                endY   = screenHeight * 0.70f,
                durationMs = 300
            )
        }

        /**
         * 在当前屏幕执行上滑手势
         */
        fun swipeUp(screenWidth: Int, screenHeight: Int) {
            instance?.performSwipe(
                startX = screenWidth / 2f,
                startY = screenHeight * 0.70f,
                endX   = screenWidth / 2f,
                endY   = screenHeight * 0.35f,
                durationMs = 300
            )
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        PetLogger.d(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理无障碍事件
    }

    override fun onInterrupt() {
        PetLogger.d(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        instance = null
        PetLogger.d(TAG, "AccessibilityService destroyed")
        super.onDestroy()
    }

    /**
     * 通过 GestureDescription 注入滑动触摸事件
     */
    private fun performSwipe(
        startX: Float, startY: Float,
        endX: Float,   endY: Float,
        durationMs: Long
    ) {
        try {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    PetLogger.d(TAG, "Swipe gesture completed")
                }
                override fun onCancelled(gestureDescription: GestureDescription) {
                    PetLogger.w(TAG, "Swipe gesture cancelled")
                }
            }, null)
            PetLogger.d(TAG, "performSwipe dispatched=$dispatched start=($startX,$startY) end=($endX,$endY)")
        } catch (e: Exception) {
            PetLogger.e(TAG, "performSwipe failed", e)
        }
    }
}

