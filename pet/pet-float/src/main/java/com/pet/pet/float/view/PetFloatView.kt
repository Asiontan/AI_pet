package com.pet.pet.floating.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import com.pet.core.common.logger.PetLogger
import com.pet.core.common.util.DensityUtils
import com.pet.core.domain.model.PetPosition
import com.pet.core.domain.model.event.InteractionType
import com.pet.core.domain.model.event.UserInteractionEvent
import com.pet.pet.render.view.Live2DPetView
import kotlin.math.hypot

/**
 * 宠物悬浮窗视图
 *
 * 负责拖拽、点击等交互逻辑，本身作为一个容器承载 Live2D 视图。
 */
class PetFloatView(context: Context) : FrameLayout(context) {

    private var petPosition = PetPosition()
    // 默认大小：使用 dp 转 px，这里取 50dp（你之前的设置）
    private var petSize = DensityUtils.dp2px(context, 150f).toFloat()

    // 承载 Live2D 模型的 View（具体渲染逻辑在 Live2DPetView 中）
    private val live2dView: Live2DPetView = Live2DPetView(context)

    // 交互事件回调，由上层（Service）注入
    private var interactionHandler: ((UserInteractionEvent) -> Unit)? = null

    // 位置最终落点回调（拖拽结束后触发），用于持久化
    private var positionSettledListener: ((x: Int, y: Int) -> Unit)? = null

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

    init {
        val size = petSize.toInt()
        addView(
            live2dView,
            LayoutParams(size, size)
        )
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
     * 注入交互事件处理器，让上层可以接收点击 / 长按等事件
     */
    fun setInteractionHandler(handler: ((UserInteractionEvent) -> Unit)?) {
        this.interactionHandler = handler
    }

    /**
     * 监听悬浮窗最终位置（例如拖拽结束后）
     */
    fun setPositionSettledListener(listener: ((x: Int, y: Int) -> Unit)?) {
        positionSettledListener = listener
    }

    /**
     * 更新悬浮窗的布局参数（位置 & 大小），同时更新内部 Live2D 视图大小
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

        // 同步内部 Live2D 视图大小
        val size = petSize.toInt()
        live2dView.layoutParams = LayoutParams(size, size)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 固定为 petSize 的正方形区域
        setMeasuredDimension(petSize.toInt(), petSize.toInt())
        val childSize = petSize.toInt()
        measureChildren(
            MeasureSpec.makeMeasureSpec(childSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(childSize, MeasureSpec.EXACTLY)
        )
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
                    // 点击/长按不改变位置
                    return true
                }

                // 发生了拖拽：结束时直接记录当前位置
                positionSettledListener?.invoke(layoutParams.x, layoutParams.y)
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
        // 触发 Live2D 的点击动作
        live2dView.playTapMotion()
        dispatchInteraction(InteractionType.CLICK, event, duration)
    }

    private fun handleLongPress(event: MotionEvent, duration: Long) {
        // 长按可以作为“切换状态”，这里简单触发一个 Idle 动作
        live2dView.playIdleMotion()
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
}

