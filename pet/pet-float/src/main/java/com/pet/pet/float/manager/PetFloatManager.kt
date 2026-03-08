package com.pet.pet.floating.manager

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import com.pet.core.common.logger.PetLogger
import com.pet.core.domain.model.PetPosition
import com.pet.pet.floating.view.PetFloatView

/**
 * 悬浮窗管理器
 */
class PetFloatManager(private val context: Context) {
    
    private var windowManager: WindowManager? = null
    private var floatView: PetFloatView? = null
    private var isShowing = false
    
    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
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
            floatView = PetFloatView(context)
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
     * 检查是否显示
     */
    fun isShowing(): Boolean = isShowing
}

