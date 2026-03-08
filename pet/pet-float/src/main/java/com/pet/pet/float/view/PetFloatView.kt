package com.pet.pet.floating.view

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.pet.core.common.logger.PetLogger
import com.pet.core.domain.model.PetPosition

/**
 * 宠物悬浮窗视图
 */
class PetFloatView(context: Context) : View(context) {
    
    private var petPosition = PetPosition()
    private var petSize = 100f // 默认大小（像素）
    
    fun setPetPosition(position: PetPosition) {
        this.petPosition = position
        updateLayout()
    }
    
    fun setPetSize(size: Float) {
        this.petSize = size
        updateLayout()
    }
    
    private fun updateLayout() {
        val layoutParams = layoutParams as? WindowManager.LayoutParams ?: return
        layoutParams.x = petPosition.x.toInt()
        layoutParams.y = petPosition.y.toInt()
        layoutParams.width = petSize.toInt()
        layoutParams.height = petSize.toInt()
        
        try {
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.updateViewLayout(this, layoutParams)
        } catch (e: Exception) {
            PetLogger.e("PetFloatView", "Failed to update layout", e)
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(petSize.toInt(), petSize.toInt())
    }
}

