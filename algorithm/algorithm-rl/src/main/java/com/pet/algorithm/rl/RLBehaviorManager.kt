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
import java.util.Calendar

/**
 * 强化学习行为管理器
 * 整合 Q-Learning 算法与宠物行为系统
 * - 情绪值由 ServiceLifecycleCoordinator 通过 updateEmotion() 外部注入
 * - 夜间时段（22:00-7:00）自动触发 SLEEP 状态
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

    // 情绪值（0-10），由外部 BehaviorEmotionAnalyzer 分析结果注入
    private var petEmotion = 5

    /**
     * 由 ServiceLifecycleCoordinator 定期调用，将情绪分析结果同步进来
     */
    fun updateEmotion(emotion: Int) {
        petEmotion = emotion.coerceIn(0, 10)
        PetLogger.d("RLBehaviorManager", "Emotion updated to $petEmotion")
    }

    /**
     * 处理用户交互，返回推荐行为状态
     */
    fun handleUserInteraction(interaction: UserInteractionEvent): BehaviorState {
        when (interaction.type) {
            com.pet.core.domain.model.event.InteractionType.CLICK ->
                clickCount++
            com.pet.core.domain.model.event.InteractionType.DOUBLE_CLICK -> {
                clickCount += 2
                petEmotion = (petEmotion + 2).coerceIn(0, 10)
            }
            com.pet.core.domain.model.event.InteractionType.LONG_PRESS ->
                petEmotion = (petEmotion - 1).coerceIn(0, 10)
            else -> {}
        }

        val currentTime = System.currentTimeMillis()
        val elapsedMs = currentTime - lastInteractionTime
        val clickFrequency = if (elapsedMs > 60_000L) {
            clickCount = 1; 1f
        } else {
            clickCount.toFloat() / ((elapsedMs / 60_000f).coerceAtLeast(0.1f))
        }
        val lastIntervalMin = elapsedMs / 60_000L
        lastInteractionTime = currentTime
        interactionStartTime = currentTime

        // 点击自增情绪
        if (interaction.type == com.pet.core.domain.model.event.InteractionType.CLICK) {
            petEmotion = (petEmotion + 1).coerceIn(0, 10)
        }

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        // 夜间强制睡眠
        if (isNightTime(currentHour)) return BehaviorState.SLEEP

        val action = rlAgent.chooseAction(
            clickFrequency = clickFrequency,
            lastInteractionInterval = lastIntervalMin,
            currentHour = currentHour,
            petEmotion = petEmotion
        )
        PetLogger.d("RLBehaviorManager", "Interaction: ${interaction.type}, Action: $action, Emotion: $petEmotion")
        return action.toBehaviorState()
    }

    /**
     * 处理用户反馈，更新 RL 模型
     */
    fun handleUserFeedback(feedback: PetBehaviorFeedbackEvent) {
        scope.launch {
            val interactionDuration = System.currentTimeMillis() - interactionStartTime
            val reward = rlAgent.calculateReward(feedback.userResponse, interactionDuration)
            rlAgent.learn(reward)

            when (feedback.userResponse) {
                UserResponse.POSITIVE -> petEmotion = (petEmotion + 1).coerceIn(0, 10)
                UserResponse.NEGATIVE -> petEmotion = (petEmotion - 2).coerceIn(0, 10)
                UserResponse.NEUTRAL  -> {}
            }
            PetLogger.d("RLBehaviorManager", "Feedback: ${feedback.userResponse}, Emotion: $petEmotion")
        }
    }

    /**
     * 定期更新（无交互时的自主行为决策）
     * 长时间不交互情绪自然下降；夜间进入睡眠
     */
    fun updatePeriodic(): BehaviorState {
        val currentTime = System.currentTimeMillis()
        val lastIntervalMin = (currentTime - lastInteractionTime) / 60_000L
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // 夜间自动睡眠
        if (isNightTime(currentHour)) {
            PetLogger.d("RLBehaviorManager", "Night time, sleeping")
            return BehaviorState.SLEEP
        }

        // 长时间无交互，情绪缓慢下降
        if (lastIntervalMin > 30) {
            petEmotion = (petEmotion - 1).coerceIn(0, 10)
            lastInteractionTime = currentTime
        }

        val action = rlAgent.chooseAction(
            clickFrequency = 0f,
            lastInteractionInterval = lastIntervalMin,
            currentHour = currentHour,
            petEmotion = petEmotion
        )
        PetLogger.d("RLBehaviorManager", "Periodic action: $action, Emotion: $petEmotion")
        return action.toBehaviorState()
    }

    /** 获取当前情绪值 */
    fun getCurrentEmotion(): Int = petEmotion

    /** 判断是否夜间（22:00 - 07:00） */
    private fun isNightTime(hour: Int): Boolean = hour >= 22 || hour < 7
}
