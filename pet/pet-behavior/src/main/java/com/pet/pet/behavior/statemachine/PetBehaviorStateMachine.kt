package com.pet.pet.behavior.statemachine

import com.pet.core.common.logger.PetLogger
import com.pet.core.domain.model.BehaviorState
import com.pet.core.domain.model.event.PetBehaviorFeedbackEvent
import com.pet.core.domain.model.event.UserInteractionEvent
import com.pet.algorithm.rl.RLBehaviorManager
import java.util.Calendar

/**
 * 宠物行为状态机
 * 集成强化学习算法，并叠加时间段规则（夜间睡眠 / 早晨唤醒）
 */
class PetBehaviorStateMachine(
    private val rlBehaviorManager: RLBehaviorManager
) {

    private var currentState = BehaviorState.IDLE

    /**
     * 处理用户交互，返回新行为状态
     */
    fun handleInteraction(interaction: UserInteractionEvent): BehaviorState {
        // 如处于睡眠状态，任意交互唤醒
        if (currentState == BehaviorState.SLEEP) {
            return transition(BehaviorState.WAKE_UP)
        }
        // 拖拽直接映射
        if (interaction.type == com.pet.core.domain.model.event.InteractionType.DRAG) {
            return transition(BehaviorState.DRAG)
        }
        val newState = rlBehaviorManager.handleUserInteraction(interaction)
        return transition(newState)
    }

    /**
     * 处理用户反馈，驱动 RL 学习
     */
    fun handleFeedback(feedback: PetBehaviorFeedbackEvent) {
        rlBehaviorManager.handleUserFeedback(feedback)
    }

    /**
     * 定期更新（无交互时调用）
     * 根据时间段叠加睡眠 / 唤醒逻辑
     */
    fun updatePeriodic(): BehaviorState {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        // 夜间 22:00-07:00 → SLEEP
        val shouldSleep = hour >= 22 || hour < 7
        // 早晨 07:00 唤醒一次
        val shouldWakeUp = hour == 7 && currentState == BehaviorState.SLEEP

        val newState = when {
            shouldWakeUp -> BehaviorState.WAKE_UP
            shouldSleep  -> BehaviorState.SLEEP
            else         -> rlBehaviorManager.updatePeriodic()
        }
        return transition(newState)
    }

    /** 获取当前状态 */
    fun getCurrentState(): BehaviorState = currentState

    // ── 私有 ─────────────────────────────────────────────────────────
    private fun transition(newState: BehaviorState): BehaviorState {
        if (newState != currentState) {
            PetLogger.d("PetBehaviorStateMachine", "$currentState -> $newState")
            currentState = newState
        }
        return currentState
    }
}
