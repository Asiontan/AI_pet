package com.pet.pet.service.coordinator

import android.content.Context
import com.pet.algorithm.prediction.BehaviorPredictor
import com.pet.algorithm.rl.RLBehaviorManager
import com.pet.algorithm.sentiment.BehaviorEmotionAnalyzer
import com.pet.algorithm.sentiment.TextSentimentAnalyzer
import com.pet.core.common.logger.PetLogger
import com.pet.core.domain.model.event.UserInteractionEvent
import com.pet.pet.behavior.statemachine.PetBehaviorStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 服务生命周期协调器
 * 整合所有算法模块和功能模块
 */
class ServiceLifecycleCoordinator(
    private val context: Context,
    private val scope: CoroutineScope
) {
    
    private lateinit var rlBehaviorManager: RLBehaviorManager
    private lateinit var behaviorStateMachine: PetBehaviorStateMachine
    private lateinit var textSentimentAnalyzer: TextSentimentAnalyzer
    private lateinit var behaviorEmotionAnalyzer: BehaviorEmotionAnalyzer
    private lateinit var behaviorPredictor: BehaviorPredictor
    
    private var isRunning = false
    
    /**
     * 启动协调器
     */
    fun start() {
        if (isRunning) {
            return
        }
        
        isRunning = true
        
        // 初始化所有模块
        rlBehaviorManager = RLBehaviorManager(context, scope)
        behaviorStateMachine = PetBehaviorStateMachine(rlBehaviorManager)
        textSentimentAnalyzer = TextSentimentAnalyzer(context)
        behaviorEmotionAnalyzer = BehaviorEmotionAnalyzer(context)
        behaviorPredictor = BehaviorPredictor(context)
        
        // 启动定期更新
        scope.launch {
            periodicUpdate()
        }
        
        PetLogger.d("ServiceLifecycleCoordinator", "Coordinator started")
    }
    
    /**
     * 停止协调器
     */
    fun stop() {
        isRunning = false
        PetLogger.d("ServiceLifecycleCoordinator", "Coordinator stopped")
    }
    
    /**
     * 处理用户交互
     */
    fun handleUserInteraction(interaction: UserInteractionEvent) {
        if (!isRunning) return
        
        scope.launch {
            val newState = behaviorStateMachine.handleInteraction(interaction)
            PetLogger.d("ServiceLifecycleCoordinator", "Interaction handled: ${interaction.type}, State: $newState")
        }
    }
    
    /**
     * 定期更新
     */
    private suspend fun periodicUpdate() {
        while (isRunning) {
            try {
                // 更新行为状态机
                behaviorStateMachine.updatePeriodic()
                
                // 分析用户情绪
                val emotion = behaviorEmotionAnalyzer.analyzeBehaviorEmotion()
                PetLogger.d("ServiceLifecycleCoordinator", "Current emotion: $emotion")
                
                // 预测用户行为
                val predictions = behaviorPredictor.predictNextHour()
                if (predictions.isNotEmpty()) {
                    PetLogger.d("ServiceLifecycleCoordinator", "Predicted ${predictions.size} behaviors")
                }
                
                delay(60000) // 每分钟更新一次
            } catch (e: Exception) {
                PetLogger.e("ServiceLifecycleCoordinator", "Error in periodic update", e)
                delay(10000) // 出错后等待10秒再试
            }
        }
    }
}

