package com.pet.pet.render.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.AnimationDrawable
import android.view.View
import com.pet.core.common.logger.PetLogger
import com.pet.core.domain.model.AnimationState

/**
 * 宠物动画视图
 */
class PetAnimationView(context: Context) : View(context) {
    
    private var currentAnimation: AnimationDrawable? = null
    private var animationState = AnimationState.IDLE
    
    /**
     * 设置动画状态
     */
    fun setAnimationState(state: AnimationState) {
        if (animationState == state) {
            return
        }
        
        animationState = state
        loadAnimation(state)
        invalidate()
    }
    
    private fun loadAnimation(state: AnimationState) {
        // TODO: 加载对应的动画资源
        // val resId = when (state) {
        //     AnimationState.IDLE -> R.drawable.anim_pet_idle
        //     AnimationState.WALK -> R.drawable.anim_pet_walk
        //     ...
        // }
        // currentAnimation = resources.getDrawable(resId) as? AnimationDrawable
        // currentAnimation?.start()
        
        PetLogger.d("PetAnimationView", "Animation state changed: $state")
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // TODO: 绘制动画帧
        currentAnimation?.draw(canvas)
    }
}

