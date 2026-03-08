package com.pet.pet.floating.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.pet.core.common.logger.PetLogger
import com.pet.core.common.util.DensityUtils
import com.pet.core.domain.model.PetPosition
import com.pet.core.domain.model.event.InteractionType
import com.pet.core.domain.model.event.UserInteractionEvent
import kotlin.math.hypot
import kotlin.math.min

/**
 * 宠物悬浮窗视图
 *
 * 这里直接绘制一个简单的“弹跳小宠物”动画：
 * - 使用圆形作为宠物身体
 * - 两个小圆点作为眼睛
 * - 通过 ValueAnimator 实现上下弹跳效果
 */
class PetFloatView(context: Context) : View(context) {

    private var petPosition = PetPosition()
    // 默认大小：使用 dp 转 px，保证在不同分辨率下都相对合适，这里取 120dp
    private var petSize = DensityUtils.dp2px(context, 50f).toFloat()

    // 绘制宠物主体的画笔
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB74D") // 柔和一点的橙色
        style = Paint.Style.FILL
    }

    // 眼睛
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    // 弹跳偏移量（基础值），实际位移会乘以弹跳系数
    private var bounceOffset = 0f
    private var bounceAmplitudeFactor = 1f

    // 简单“睡眠”状态，用于长按切换：睡着时颜色和弹跳幅度不同
    private var isSleeping = false

    // 交互事件回调，由上层（Service）注入
    private var interactionHandler: ((UserInteractionEvent) -> Unit)? = null

    // 弹跳动画
    private val bounceAnimator: ValueAnimator = ValueAnimator.ofFloat(0f, 16f).apply {
        duration = 800L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { animator ->
            val base = animator.animatedValue as Float
            bounceOffset = base * bounceAmplitudeFactor
            invalidate()
        }
        start()
    }

    // 拖拽 & 点击检测
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var downTime = 0L

    private val clickSlop: Float =
        ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeout: Long =
        ViewConfiguration.getLongPressTimeout().toLong()

    fun setPetPosition(position: PetPosition) {
        this.petPosition = position
        updateLayout()
    }

    fun setPetSize(size: Float) {
        this.petSize = size
        updateLayout()
    }

    /**
     * 注入交互事件处理器，让上层可以接收点击 / 长按等事件
     */
    fun setInteractionHandler(handler: ((UserInteractionEvent) -> Unit)?) {
        this.interactionHandler = handler
    }

    /**
     * 更新悬浮窗的布局参数（位置 & 大小）
     */
    private fun updateLayout() {
        val layoutParams = layoutParams as? WindowManager.LayoutParams ?: return
        layoutParams.x = petPosition.x.toInt()
        layoutParams.y = petPosition.y.toInt()
        layoutParams.width = petSize.toInt()
        layoutParams.height = petSize.toInt()

        try {
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                ?.updateViewLayout(this, layoutParams)
        } catch (e: Exception) {
            PetLogger.e("PetFloatView", "Failed to update layout", e)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 固定为 petSize 的正方形区域
        setMeasuredDimension(petSize.toInt(), petSize.toInt())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val layoutParams = layoutParams as? WindowManager.LayoutParams
            ?: return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = downRawX
                lastRawY = downRawY
                downTime = System.currentTimeMillis()
                // 确保可以收到后续事件
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - lastRawX).toInt()
                val dy = (event.rawY - lastRawY).toInt()

                layoutParams.x += dx
                layoutParams.y += dy

                try {
                    (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                        ?.updateViewLayout(this, layoutParams)
                } catch (e: Exception) {
                    PetLogger.e("PetFloatView", "Failed to drag update layout", e)
                }

                lastRawX = event.rawX
                lastRawY = event.rawY
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val duration = System.currentTimeMillis() - downTime
                val distance = hypot(event.rawX - downRawX, event.rawY - downRawY)

                if (distance < clickSlop) {
                    if (duration >= longPressTimeout) {
                        // 长按
                        handleLongPress(event, duration)
                    } else {
                        // 短按点击
                        handleClick(event, duration)
                    }
                }
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        // 保留给辅助功能使用，真正的点击逻辑在 handleClick 中
        playClickAnimation()
        return true
    }

    /**
     * 点击时做一个“小跳跃 + 缩放”的反馈动画
     */
    private fun playClickAnimation() {
        try {
            val scaleUpX = ObjectAnimator.ofFloat(this, "scaleX", 1f, 1.15f)
            val scaleUpY = ObjectAnimator.ofFloat(this, "scaleY", 1f, 1.15f)
            val scaleDownX = ObjectAnimator.ofFloat(this, "scaleX", 1.15f, 1f)
            val scaleDownY = ObjectAnimator.ofFloat(this, "scaleY", 1.15f, 1f)

            AnimatorSet().apply {
                play(scaleUpX).with(scaleUpY)
                play(scaleDownX).with(scaleDownY).after(scaleUpX)
                duration = 160L
                start()
            }
        } catch (e: Exception) {
            PetLogger.e("PetFloatView", "Failed to play click animation", e)
        }
    }

    private fun handleClick(event: MotionEvent, duration: Long) {
        playClickAnimation()
        dispatchInteraction(InteractionType.CLICK, event, duration)
    }

    private fun handleLongPress(event: MotionEvent, duration: Long) {
        // 切换简单的“睡觉 / 清醒”状态：颜色变淡、弹跳幅度减小
        isSleeping = !isSleeping
        bounceAmplitudeFactor = if (isSleeping) 0.2f else 1f
        bodyPaint.color = if (isSleeping) {
            Color.parseColor("#90CAF9") // 偏冷色，表示睡觉
        } else {
            Color.parseColor("#FFB74D") // 原来的暖色
        }
        invalidate()

        dispatchInteraction(InteractionType.LONG_PRESS, event, duration)
    }

    private fun dispatchInteraction(
        type: InteractionType,
        event: MotionEvent,
        duration: Long
    ) {
        try {
            interactionHandler?.invoke(
                UserInteractionEvent(
                    type = type,
                    positionX = event.rawX,
                    positionY = event.rawY,
                    duration = duration
                )
            )
        } catch (e: Exception) {
            PetLogger.e("PetFloatView", "Failed to dispatch interaction: $type", e)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        val cx = width / 2f
        val cy = height / 2f + bounceOffset
        val radius = min(width, height) / 2f * 0.9f

        // 身体
        canvas.drawCircle(cx, cy, radius, bodyPaint)

        // 眼睛
        val eyeOffsetX = radius * 0.3f
        val eyeOffsetY = -radius * 0.2f
        val eyeRadius = radius * 0.1f
        canvas.drawCircle(cx - eyeOffsetX, cy + eyeOffsetY, eyeRadius, eyePaint)
        canvas.drawCircle(cx + eyeOffsetX, cy + eyeOffsetY, eyeRadius, eyePaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 避免内存泄漏
        bounceAnimator.cancel()
    }
}
