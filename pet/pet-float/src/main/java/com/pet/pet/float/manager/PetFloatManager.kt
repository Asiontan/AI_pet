package com.pet.pet.floating.manager

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import com.pet.core.common.logger.PetLogger
import com.pet.core.domain.model.PetPosition
import com.pet.core.domain.model.event.UserInteractionEvent
import com.pet.pet.floating.view.PetBubbleView
import com.pet.pet.floating.view.PetFloatView

/**
 * 悬浮窗管理器
 */
class PetFloatManager(private val context: Context) {
    
    private var windowManager: WindowManager? = null
    private var floatView: PetFloatView? = null
    private var bubbleView: PetBubbleView? = null
    private var isShowing = false
    private var interactionHandler: ((UserInteractionEvent) -> Unit)? = null
    private var positionSettledListener: ((x: Int, y: Int) -> Unit)? = null
    
    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }
    
    /**
     * 设置交互事件回调，在显示悬浮窗前/后都可以调用
     */
    fun setInteractionHandler(handler: ((UserInteractionEvent) -> Unit)?) {
        interactionHandler = handler
        floatView?.setInteractionHandler(handler)
    }

    fun setPositionSettledListener(listener: ((x: Int, y: Int) -> Unit)?) {
        positionSettledListener = listener
        floatView?.setPositionSettledListener(listener)
    }

    /**
     * 显示悬浮窗
     */
    fun show() {
        if (isShowing) {
            PetLogger.w("PetFloatManager", "Float view already showing")
            return
        }
        
        try {
            floatView = PetFloatView(context).apply {
                setInteractionHandler(interactionHandler)
                setPositionSettledListener(positionSettledListener)
                // 拖动时实时同步气泡位置
                onPositionChangedListener = { x, y ->
                    val lp = this.layoutParams as? WindowManager.LayoutParams
                    val size = lp?.width?.takeIf { it > 0 } ?: 400
                    bubbleView?.updatePositionIfShowing(x, y, size)
                }
            }
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 800
                y = 1500
            }
            
            windowManager?.addView(floatView, layoutParams)
            isShowing = true
            PetLogger.d("PetFloatManager", "Float view shown")
        } catch (e: Exception) {
            PetLogger.e("PetFloatManager", "Failed to show float view", e)
        }
    }
    
    /**
     * 隐藏悬浮窗
     */
    fun hide() {
        if (!isShowing) {
            return
        }
        
        try {
            floatView?.let { view ->
                windowManager?.removeView(view)
            }
            floatView = null
            isShowing = false
            PetLogger.d("PetFloatManager", "Float view hidden")
        } catch (e: Exception) {
            PetLogger.e("PetFloatManager", "Failed to hide float view", e)
        }
    }
    
    /**
     * 更新宠物位置
     */
    fun updatePosition(position: PetPosition) {
        floatView?.setPetPosition(position)
    }
    
    /**
     * 更新宠物大小
     */
    fun updateSize(size: Float) {
        floatView?.setPetSize(size)
    }
    
    /**
     * 获取当前视图
     */
    fun getView(): PetFloatView? = floatView

    /**
     * 切换模型
     */
    fun switchModel(source: com.pet.pet.render.view.Live2DPetView.ModelSource) {
        floatView?.switchModel(source)
    }

    /**
     * 播放 exp3 表情文件
     */
    fun playExpression(fileName: String) {
        floatView?.playExpression(fileName)
    }

    /**
     * 播放 motion3 动作文件
     */
    fun playMotionFile(fileName: String) {
        floatView?.playMotionFile(fileName)
    }

    /**
     * 检查是否显示
     */
    fun isShowing(): Boolean = isShowing

    /**
     * 获取宠物当前位置 (x, y)，不可见时返回 null
     */
    fun getCurrentPosition(): Pair<Int, Int>? {
        if (!isShowing) return null
        val lp = floatView?.layoutParams as? WindowManager.LayoutParams ?: return null
        return Pair(lp.x, lp.y)
    }

    /**
     * 将宠物移动到指定坐标（用于路径规划自主移动）
     */
    fun movePetTo(x: Int, y: Int) {
        val view = floatView ?: return
        val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
        lp.x = x
        lp.y = y
        try {
            windowManager?.updateViewLayout(view, lp)
        } catch (e: Exception) {
            PetLogger.e("PetFloatManager", "Failed to move pet", e)
        }
    }

    // ---- 气泡相关 ----

    /**
     * 开始显示气泡（流式输出前调用）
     */
    fun showBubble() {
        val pos = getCurrentPosition() ?: return
        val lp = floatView?.layoutParams as? WindowManager.LayoutParams
        val size = lp?.width?.takeIf { it > 0 } ?: 400
        if (bubbleView == null) bubbleView = PetBubbleView(context)
        bubbleView?.show(pos.first, pos.second, size)
    }

    /**
     * 追加流式 token 到气泡
     */
    fun appendBubbleToken(token: String) {
        bubbleView?.appendToken(token)
    }

    /**
     * 回复完成，启动气泡自动隐藏计时
     */
    fun onBubbleReplyDone() {
        bubbleView?.onReplyDone()
    }

    /**
     * 立即隐藏气泡
     */
    fun hideBubble() {
        bubbleView?.hide()
    }

    /**
     * 气泡是否正在显示
     */
    fun isBubbleShowing(): Boolean = bubbleView?.isShowing() == true
}

