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

class PetFloatView(context: Context) : FrameLayout(context) {

    private var petPosition = PetPosition()
    private var petSize = DensityUtils.dp2px(context, 200f).toFloat()

    private val live2dView: Live2DPetView = Live2DPetView(context)

    private var interactionHandler: ((UserInteractionEvent) -> Unit)? = null
    private var positionSettledListener: ((x: Int, y: Int) -> Unit)? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var downTime = 0L

    // 双击检测
    private var lastClickTime = 0L
    private val doubleClickTimeout: Long = 350L

    private val clickSlop: Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong()

    init {
        val size = petSize.toInt()
        addView(live2dView, LayoutParams(size, size))
        live2dView.modelDisplaySizeDp = 100f
        live2dView.onCanvasSizeChanged = { _, _ -> }
        loadActiveModel()
    }

    private fun loadActiveModel() {
        try {
            val modelInfo = com.pet.core.data.model.ModelManager.findModel(
                context,
                com.pet.core.data.model.ModelManager.getActiveModelId(context)
            ) ?: return
            val source = if (modelInfo.isBuiltin)
                Live2DPetView.ModelSource.Asset(modelInfo.modelJsonPath)
            else
                Live2DPetView.ModelSource.External(java.io.File(modelInfo.modelJsonPath))
            live2dView.switchModel(source)
            // 水色小狗永久去水印（Param121 Add 30）
            if (modelInfo.id == "water_dog") {
                live2dView.postDelayed({ live2dView.setParamPersistent("Param121", 30f) }, 500)
            } else {
                live2dView.clearPersistentParams()
            }
        } catch (e: Exception) {
            PetLogger.e("PetFloatView", "loadActiveModel failed", e)
        }
    }

    fun setPetPosition(position: PetPosition) {
        this.petPosition = position
        updateLayout()
    }

    fun setPetSize(size: Float) {
        this.petSize = size
        updateLayout()
    }

    fun setInteractionHandler(handler: ((UserInteractionEvent) -> Unit)?) {
        this.interactionHandler = handler
    }

    fun setPositionSettledListener(listener: ((x: Int, y: Int) -> Unit)?) {
        positionSettledListener = listener
    }

    private fun updateLayout() {
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        lp.x = petPosition.x.toInt()
        lp.y = petPosition.y.toInt()
        lp.width = petSize.toInt()
        lp.height = petSize.toInt()
        try {
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                ?.updateViewLayout(this, lp)
        } catch (e: Exception) {
            PetLogger.e("PetFloatView", "Failed to update layout", e)
        }
        live2dView.layoutParams = LayoutParams(petSize.toInt(), petSize.toInt())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val lp = layoutParams as? WindowManager.LayoutParams
        val w = lp?.width?.takeIf { it > 0 } ?: petSize.toInt()
        val h = lp?.height?.takeIf { it > 0 } ?: petSize.toInt()
        setMeasuredDimension(w, h)
        measureChildren(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val lp = layoutParams as? WindowManager.LayoutParams
            ?: return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX; downRawY = event.rawY
                lastRawX = downRawX; lastRawY = downRawY
                downTime = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                lp.x += (event.rawX - lastRawX).toInt()
                lp.y += (event.rawY - lastRawY).toInt()
                try {
                    (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                        ?.updateViewLayout(this, lp)
                } catch (e: Exception) {
                    PetLogger.e("PetFloatView", "Failed to drag update layout", e)
                }
                lastRawX = event.rawX; lastRawY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val duration = System.currentTimeMillis() - downTime
                val distance = hypot(event.rawX - downRawX, event.rawY - downRawY)
                if (distance < clickSlop) {
                    if (duration >= longPressTimeout) handleLongPress(event, duration)
                    else handleClick(event, duration)
                    return true
                }
                positionSettledListener?.invoke(lp.x, lp.y)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        playClickAnimation()
        return true
    }

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
        val now = System.currentTimeMillis()
        if (now - lastClickTime < doubleClickTimeout) {
            // 双击
            lastClickTime = 0L
            playClickAnimation()
            dispatchInteraction(InteractionType.DOUBLE_CLICK, event, duration)
        } else {
            lastClickTime = now
            playClickAnimation()
            dispatchInteraction(InteractionType.CLICK, event, duration)
        }
    }

    private fun handleLongPress(event: MotionEvent, duration: Long) {
        dispatchInteraction(InteractionType.LONG_PRESS, event, duration)
    }

    private fun dispatchInteraction(type: InteractionType, event: MotionEvent, duration: Long) {
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

    fun switchModel(source: Live2DPetView.ModelSource) { live2dView.switchModel(source) }
    fun playExpression(fileName: String) { live2dView.playExpression(fileName) }
    fun playMotionFile(fileName: String) { live2dView.playMotionFile(fileName) }
}
