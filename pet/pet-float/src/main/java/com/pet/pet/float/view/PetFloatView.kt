package com.pet.pet.floating.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.WindowManager
import com.pet.core.common.logger.PetLogger
import com.pet.core.domain.model.PetPosition
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
    private var petSize = 100f // 默认大小（像素）

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

    // 弹跳偏移量
    private var bounceOffset = 0f

    // 弹跳动画
    private val bounceAnimator: ValueAnimator = ValueAnimator.ofFloat(0f, 16f).apply {
        duration = 800L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { animator ->
            bounceOffset = animator.animatedValue as Float
            invalidate()
        }
        start()
    }

    fun setPetPosition(position: PetPosition) {
        this.petPosition = position
        updateLayout()
    }

    fun setPetSize(size: Float) {
        this.petSize = size
        updateLayout()
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
