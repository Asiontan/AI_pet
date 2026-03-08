package com.pet.algorithm.rl

import android.content.Context
import com.pet.core.common.logger.PetLogger
import com.pet.core.domain.model.BehaviorState
import com.pet.core.domain.model.event.PetBehaviorFeedbackEvent
import com.pet.core.domain.model.event.UserInteractionEvent
import com.pet.core.domain.model.event.UserResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 强化学习行为管理器
 * 整合RL算法与宠物行为系统
 */
class RLBehaviorManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val rlAgent = PetRLAgent(context)
    
    // 交互统计
    private var clickCount = 0
    private var lastInteractionTime = System.currentTimeMillis()
    private var interactionStartTime = 0L
    private var petEmotion = 5 // 初始情绪值（0-10）
    
    /**
     * 处理用户交互，返回推荐的行为
     */
    fun handleUserInteraction(interaction: UserInteractionEvent): BehaviorState {
        // 更新统计
        when (interaction.type) {
            com.pet.core.domain.model.event.InteractionType.CLICK,
            com.pet.core.domain.model.event.InteractionType.DOUBLE_CLICK -> {
                clickCount++
            }
            else -> {}
        }
        
        val currentTime = System.currentTimeMillis()
        val clickFrequency = if (currentTime - lastInteractionTime > 60000) {
            // 超过1分钟，重置计数
            clickCount = 1
            1f
        } else {
            clickCount.toFloat() / ((currentTime - lastInteractionTime) / 60000f + 0.1f)
        }
        
        val lastInterval = (currentTime - lastInteractionTime) / 60000 // 分钟
        lastInteractionTime = currentTime
        interactionStartTime = currentTime
        
        // 根据交互类型调整情绪
        when (interaction.type) {
            com.pet.core.domain.model.event.InteractionType.CLICK -> {
                petEmotion = (petEmotion + 1).coerceIn(0, 10)
            }
            com.pet.core.domain.model.event.InteractionType.DOUBLE_CLICK -> {
                petEmotion = (petEmotion + 2).coerceIn(0, 10)
            }
            com.pet.core.domain.model.event.InteractionType.LONG_PRESS -> {
                petEmotion = (petEmotion - 1).coerceIn(0, 10)
            }
            else -> {}
        }
        
        // 使用RL选择动作
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val action = rlAgent.chooseAction(
            clickFrequency = clickFrequency,
            lastInteractionInterval = lastInterval,
            currentHour = currentHour,
            petEmotion = petEmotion
        )
        
        PetLogger.d("RLBehaviorManager", "Interaction: ${interaction.type}, Action: $action, Emotion: $petEmotion")
        return action.toBehaviorState()
    }
    
    /**
     * 处理用户反馈，更新RL模型
     */
    fun handleUserFeedback(feedback: PetBehaviorFeedbackEvent) {
        scope.launch {
            val interactionDuration = System.currentTimeMillis() - interactionStartTime
            val reward = rlAgent.calculateReward(feedback.userResponse, interactionDuration)
            rlAgent.learn(reward)
            
            // 根据反馈调整情绪
            when (feedback.userResponse) {
                UserResponse.POSITIVE -> {
                    petEmotion = (petEmotion + 1).coerceIn(0, 10)
                }
                UserResponse.NEGATIVE -> {
                    petEmotion = (petEmotion - 2).coerceIn(0, 10)
                }
                UserResponse.NEUTRAL -> {}
            }
            
            PetLogger.d("RLBehaviorManager", "Feedback: ${feedback.userResponse}, Reward: $reward, Emotion: $petEmotion")
        }
    }
    
    /**
     * 定期更新（无交互时的行为选择）
     */
    fun updatePeriodic(): BehaviorState {
        val currentTime = System.currentTimeMillis()
        val lastInterval = (currentTime - lastInteractionTime) / 60000
        
        // 长时间无交互，情绪下降
        if (lastInterval > 30) {
            petEmotion = (petEmotion - 1).coerceIn(0, 10)
            lastInteractionTime = currentTime // 重置计时
        }
        
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val action = rlAgent.chooseAction(
            clickFrequency = 0f,
            lastInteractionInterval = lastInterval,
            currentHour = currentHour,
            petEmotion = petEmotion
        )
        
        return action.toBehaviorState()
    }
    
    /**
     * 获取当前情绪值
     */
    fun getCurrentEmotion(): Int = petEmotion
}

