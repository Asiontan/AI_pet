package com.pet.pet.behavior.statemachine

import com.pet.core.common.logger.PetLogger
import com.pet.core.domain.model.BehaviorState
import com.pet.core.domain.model.event.UserInteractionEvent
import com.pet.algorithm.rl.RLBehaviorManager

/**
 * 宠物行为状态机
 * 集成强化学习算法
 */
class PetBehaviorStateMachine(
    private val rlBehaviorManager: RLBehaviorManager
) {
    
    private var currentState = BehaviorState.IDLE
    
    /**
     * 处理用户交互
     */
    fun handleInteraction(interaction: UserInteractionEvent): BehaviorState {
        val newState = rlBehaviorManager.handleUserInteraction(interaction)
        if (newState != currentState) {
            PetLogger.d("PetBehaviorStateMachine", "State changed: $currentState -> $newState")
            currentState = newState
        }
        return currentState
    }
    
    /**
     * 处理用户反馈
     */
    fun handleFeedback(feedback: com.pet.core.domain.model.event.PetBehaviorFeedbackEvent) {
        rlBehaviorManager.handleUserFeedback(feedback)
    }
    
    /**
     * 定期更新
     */
    fun updatePeriodic(): BehaviorState {
        val newState = rlBehaviorManager.updatePeriodic()
        if (newState != currentState) {
            PetLogger.d("PetBehaviorStateMachine", "Periodic update: $currentState -> $newState")
            currentState = newState
        }
        return currentState
    }
    
    /**
     * 获取当前状态
     */
    fun getCurrentState(): BehaviorState = currentState
}

