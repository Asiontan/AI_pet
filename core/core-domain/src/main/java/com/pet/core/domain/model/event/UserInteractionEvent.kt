package com.pet.core.domain.model.event

import com.pet.core.domain.model.BehaviorState

/**
 * 用户交互事件
 */
data class UserInteractionEvent(
    val type: InteractionType,
    val timestamp: Long = System.currentTimeMillis(),
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val duration: Long = 0L // 交互持续时间（毫秒）
)

enum class InteractionType {
    CLICK,           // 点击
    LONG_PRESS,      // 长按
    DRAG,           // 拖拽
    SWIPE,          // 滑动
    DOUBLE_CLICK,   // 双击
    NONE            // 无交互
}

/**
 * 宠物行为反馈事件
 */
data class PetBehaviorFeedbackEvent(
    val behavior: BehaviorState,
    val userResponse: UserResponse,
    val timestamp: Long = System.currentTimeMillis()
)

enum class UserResponse {
    POSITIVE,  // 正面反馈（继续交互）
    NEGATIVE,  // 负面反馈（关闭宠物、点击打扰）
    NEUTRAL    // 中性反馈
}

