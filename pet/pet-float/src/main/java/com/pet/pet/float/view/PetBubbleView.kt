package com.pet.pet.floating.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.pet.core.common.logger.PetLogger
import com.pet.pet.floating.R

/**
 * 宠物头顶气泡（增强版）
 * - 进场：从宠物中心向上弹出（OvershootInterpolator 弹簧效果）
 * - 流式逐字追加文字 + 打字机光标闪烁
 * - 退场：淡出+上移消失动画
 * - 显示完整回复后 AUTO_HIDE_MS 毫秒自动隐藏
 */
class PetBubbleView(private val context: Context) {

    companion object {
        private const val TAG = "PetBubbleView"
        private const val AUTO_HIDE_MS   = 7000L
        private const val ENTER_ANIM_MS  = 320L
        private const val EXIT_ANIM_MS   = 260L
        private const val CURSOR_BLINK_MS = 530L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var rootView: View? = null
    private var tvBubble: TextView? = null
    private var viewCursor: View? = null
    private var cursorAnimator: ValueAnimator? = null
    private val hideRunnable = Runnable { animateHide() }

    private var currentLp: WindowManager.LayoutParams? = null

    /**
     * 在宠物位置上方显示气泡，开始流式输出
     */
    fun show(petX: Int, petY: Int, petSize: Int = 400) {
        handler.removeCallbacks(hideRunnable)
        if (rootView != null) {
            tvBubble?.text = ""
            updatePosition(petX, petY, petSize)
            startCursorBlink()
            return
        }
        try {
            val themedContext = ContextThemeWrapper(
                context, android.R.style.Theme_DeviceDefault_Light
            )
            val view = LayoutInflater.from(themedContext)
                .inflate(R.layout.view_pet_bubble, null, false)
            tvBubble   = view.findViewById(R.id.tvBubble)
            viewCursor = view.findViewById(R.id.viewCursor)

            val lp = buildLayoutParams(petX, petY, petSize)
            currentLp = lp
            windowManager.addView(view, lp)
            rootView = view

            // 进场动画：从下往上弹出 + 淡入
            view.alpha = 0f
            view.translationY = 40f
            val fadeIn  = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
            val slideUp = ObjectAnimator.ofFloat(view, "translationY", 40f, 0f)
            slideUp.interpolator = OvershootInterpolator(1.4f)
            AnimatorSet().apply {
                playTogether(fadeIn, slideUp)
                duration = ENTER_ANIM_MS
                start()
            }

            startCursorBlink()
            PetLogger.d(TAG, "Bubble shown at ($petX, $petY) size=$petSize")
        } catch (e: Exception) {
            PetLogger.e(TAG, "Failed to show bubble", e)
        }
    }

    /** 追加流式 token（主线程安全） */
    fun appendToken(token: String) {
        handler.post { tvBubble?.append(token) }
    }

    /** 回复完成：停止光标闪烁，启动自动隐藏计时 */
    fun onReplyDone() {
        handler.post { stopCursorBlink() }
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, AUTO_HIDE_MS)
    }

    /** 立即隐藏气泡（带退场动画） */
    fun hide() {
        handler.removeCallbacks(hideRunnable)
        stopCursorBlink()
        animateHide()
    }

    fun isShowing() = rootView != null

    /** 宠物拖动时实时更新气泡位置 */
    fun updatePositionIfShowing(petX: Int, petY: Int, petSize: Int) {
        if (rootView == null) return
        updatePosition(petX, petY, petSize)
    }

    // ── 私有 ─────────────────────────────────────────────────────────

    private fun animateHide() {
        val view = rootView ?: return
        val fadeOut  = ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f)
        val slideUp  = ObjectAnimator.ofFloat(view, "translationY", 0f, -20f)
        AnimatorSet().apply {
            playTogether(fadeOut, slideUp)
            duration = EXIT_ANIM_MS
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    removeViewSafe(view)
                }
            })
            start()
        }
    }

    private fun removeViewSafe(view: View) {
        try { windowManager.removeView(view) } catch (_: Exception) {}
        if (rootView === view) {
            rootView   = null
            tvBubble   = null
            viewCursor = null
            currentLp  = null
        }
    }

    private fun startCursorBlink() {
        val cursor = viewCursor ?: return
        cursor.visibility = View.VISIBLE
        cursorAnimator?.cancel()
        cursorAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration   = CURSOR_BLINK_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { cursor.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopCursorBlink() {
        cursorAnimator?.cancel()
        cursorAnimator = null
        viewCursor?.visibility = View.GONE
    }

    private fun updatePosition(petX: Int, petY: Int, petSize: Int) {
        val view = rootView ?: return
        val lp   = view.layoutParams as? WindowManager.LayoutParams ?: return
        val newLp = buildLayoutParams(petX, petY, petSize)
        lp.x = newLp.x
        lp.y = newLp.y
        currentLp = lp
        try { windowManager.updateViewLayout(view, lp) } catch (_: Exception) {}
    }

    private fun buildLayoutParams(petX: Int, petY: Int, petSize: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 气泡居中对齐宠物，显示在宠物上方
            x = petX + petSize / 2 - 220   // 估算气泡宽约 440px，居中
            y = maxOf(8, petY - 150)         // 宠物上方 150px，距屏顶最少 8px
        }
    }
}
