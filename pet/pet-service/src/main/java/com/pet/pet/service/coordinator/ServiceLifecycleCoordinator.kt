package com.pet.pet.service.coordinator

import android.content.Context
// import android.graphics.PointF
// import android.view.WindowManager
// import com.pet.algorithm.path.PathPlanner
import com.pet.algorithm.prediction.BehaviorPredictor
import com.pet.algorithm.rl.RLBehaviorManager
import com.pet.algorithm.sentiment.BehaviorEmotionAnalyzer
import com.pet.algorithm.sentiment.TextSentimentAnalyzer
import com.pet.core.common.logger.PetLogger
import com.pet.core.data.model.ModelManager
import com.pet.core.data.preferences.PetPreferences
// import com.pet.core.domain.model.BehaviorState  // A* 自主移动禁用后暂不使用
import com.pet.core.domain.model.event.UserInteractionEvent
import com.pet.pet.behavior.statemachine.PetBehaviorStateMachine
import com.pet.pet.floating.manager.PetFloatManager
import com.pet.pet.service.chat.ChatManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 服务生命周期协调器
 * 整合所有算法模块和功能模块
 * - 情绪分析结果反馈给 RLBehaviorManager 影响行为决策
 * - 根据行为状态驱动宠物自主移动（A*路径规划，暂时禁用）
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
    // private lateinit var pathPlanner: PathPlanner  // A* 路径规划（暂时禁用）
    private lateinit var preferences: PetPreferences

    // A* 路径规划移动状态（暂时禁用）
    // private var currentPath: List<PointF> = emptyList()
    // private var pathIndex: Int = 0
    // private var isMoving: Boolean = false

    // 悬浮窗管理器引用（由 PetForegroundService 注入）
    var floatManager: PetFloatManager? = null

    // 聊天管理器引用（由 PetForegroundService 注入）
    var chatManager: ChatManager? = null

    private var isRunning = false

    // 上一次情绪等级，用于检测变化（避免重复播放相同表情）
    private var lastEmotionLevel: EmotionLevel = EmotionLevel.NEUTRAL

    /** 情绪等级枚举，对应不同的表情关键词 */
    private enum class EmotionLevel { NEGATIVE, NEUTRAL, POSITIVE }

    /** 将情绪分（0-10）映射到等级 */
    private fun emotionToLevel(emotion: Int): EmotionLevel = when {
        emotion <= 3 -> EmotionLevel.NEGATIVE
        emotion >= 7 -> EmotionLevel.POSITIVE
        else          -> EmotionLevel.NEUTRAL
    }

    /**
     * 根据情绪等级，在当前模型的表情列表中查找匹配的表情文件名。
     * 匹配规则：表情文件名（不含扩展名）包含对应关键词（大小写不敏感）。
     * 若没有匹配项则返回 null，不作处理。
     */
    private fun findExpressionForLevel(level: EmotionLevel, expressions: List<String>): String? {
        val positiveKeywords = listOf("happy", "smile", "joy", "excited", "开心", "高兴", "快乐", "喜", "笑")
        val negativeKeywords = listOf("sad", "angry", "cry", "fear", "pain", "hurt", "难过", "伤心", "哭", "生气", "愤怒")
        val keywords = when (level) {
            EmotionLevel.POSITIVE -> positiveKeywords
            EmotionLevel.NEGATIVE -> negativeKeywords
            EmotionLevel.NEUTRAL  -> return null  // 中性不主动切换表情
        }
        val nameLower = expressions.map { it.removeSuffix(".exp3.json").lowercase() }
        for (keyword in keywords) {
            val idx = nameLower.indexOfFirst { it.contains(keyword) }
            if (idx >= 0) return expressions[idx]
        }
        return null
    }

    /**
     * 根据情绪等级，在当前模型的动作列表中查找匹配的动作文件名。
     */
    private fun findMotionForLevel(level: EmotionLevel, motions: List<String>): String? {
        val positiveKeywords = listOf("happy", "excited", "joy", "dance", "wave", "cheer", "开心", "高兴", "欢快", "跳舞")
        val negativeKeywords = listOf("sad", "cry", "angry", "depressed", "hurt", "难过", "伤心", "哭", "生气")
        val neutralKeywords  = listOf("idle", "normal", "relax", "calm", "breath", "待机", "放松", "呼吸")
        val keywords = when (level) {
            EmotionLevel.POSITIVE -> positiveKeywords
            EmotionLevel.NEGATIVE -> negativeKeywords
            EmotionLevel.NEUTRAL  -> neutralKeywords
        }
        val nameLower = motions.map { it.removeSuffix(".motion3.json").lowercase() }
        for (keyword in keywords) {
            val idx = nameLower.indexOfFirst { it.contains(keyword) }
            if (idx >= 0) return motions[idx]
        }
        // 中性时兜底取第一个动作（让宠物有基本反应）
        if (level == EmotionLevel.NEUTRAL && motions.isNotEmpty()) return motions[0]
        return null
    }

    /**
     * 当情绪等级发生变化时，尝试播放对应表情和动作。
     * - 表情（exp3）：精确匹配关键词
     * - 动作（motion3）：匹配关键词，中性时兜底播放第一个动作
     * 如果当前模型没有对应资源，则跳过该项。
     */
    private fun applyEmotionExpression(emotion: Int) {
        val newLevel = emotionToLevel(emotion)
        if (newLevel == lastEmotionLevel) return  // 等级未变化，不重复播放
        lastEmotionLevel = newLevel

        val fm = floatManager ?: return
        val activeModelId = ModelManager.getActiveModelId(context)
        val modelInfo = ModelManager.findModel(context, activeModelId) ?: return

        // 1. 播放表情（exp3）
        if (modelInfo.expressions.isNotEmpty()) {
            val expFileName = findExpressionForLevel(newLevel, modelInfo.expressions)
            if (expFileName != null) {
                fm.playExpression(expFileName)
                PetLogger.d("ServiceLifecycleCoordinator",
                    "情绪$emotion($newLevel) → 表情: $expFileName")
            } else {
                // 积极/消极无匹配表情时清除当前表情回默认
                if (newLevel != EmotionLevel.NEUTRAL) fm.playExpression("")
                PetLogger.d("ServiceLifecycleCoordinator",
                    "情绪$emotion($newLevel) → 无匹配表情，跳过")
            }
        }

        // 2. 播放动作（motion3）
        if (modelInfo.motions.isNotEmpty()) {
            val motFileName = findMotionForLevel(newLevel, modelInfo.motions)
            if (motFileName != null) {
                fm.playMotionFile(motFileName)
                PetLogger.d("ServiceLifecycleCoordinator",
                    "情绪$emotion($newLevel) → 动作: $motFileName")
            } else {
                PetLogger.d("ServiceLifecycleCoordinator",
                    "情绪$emotion($newLevel) → 无匹配动作，跳过")
            }
        }

        if (modelInfo.expressions.isEmpty() && modelInfo.motions.isEmpty()) {
            PetLogger.d("ServiceLifecycleCoordinator",
                "模型 ${modelInfo.name} 无表情/动作资源，跳过情绪映射")
        }
    }

    /**
     * 启动协调器
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        // 初始化所有模块
        preferences = PetPreferences(context)
        rlBehaviorManager = RLBehaviorManager(context, scope)
        behaviorStateMachine = PetBehaviorStateMachine(rlBehaviorManager)

        // 恢复上次持久化的情绪值
        scope.launch(Dispatchers.IO) {
            val savedEmotion = preferences.getPetEmotion()
            rlBehaviorManager.updateEmotion(savedEmotion)
            PetLogger.d("ServiceLifecycleCoordinator", "Restored emotion=$savedEmotion")
        }
        textSentimentAnalyzer = TextSentimentAnalyzer(context)
        behaviorEmotionAnalyzer = BehaviorEmotionAnalyzer(context)
        behaviorPredictor = BehaviorPredictor(context)

        // A* 路径规划初始化（暂时禁用）
        // val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // val dm = android.util.DisplayMetrics()
        // try {
        //     @Suppress("DEPRECATION")
        //     wm.defaultDisplay.getRealMetrics(dm)
        // } catch (_: Exception) {}
        // val screenW = if (dm.widthPixels > 0) dm.widthPixels else 1080
        // val screenH = if (dm.heightPixels > 0) dm.heightPixels else 1920
        // pathPlanner = PathPlanner(screenW, screenH, gridSize = 30)

        // 启动定期更新
        scope.launch { periodicUpdate() }
        // 启动前台应用检测（高频，5秒一次）
        scope.launch { foregroundAppLoop() }
        // 启动自主移动调度（暂时禁用）
        // scope.launch { autonomousMoveLoop() }

        PetLogger.d("ServiceLifecycleCoordinator", "Coordinator started")
    }

    /**
     * 停止协调器
     */
    fun stop() {
        isRunning = false
        // currentPath = emptyList()  // A* 暂时禁用
        // isMoving = false           // A* 暂时禁用
        // 持久化当前情绪值，下次启动时恢复
        if (::rlBehaviorManager.isInitialized && ::preferences.isInitialized) {
            preferences.savePetEmotion(rlBehaviorManager.getCurrentEmotion())
            PetLogger.d("ServiceLifecycleCoordinator", "Saved emotion=${rlBehaviorManager.getCurrentEmotion()}")
        }
        PetLogger.d("ServiceLifecycleCoordinator", "Coordinator stopped")
    }

    /**
     * 对话情绪联动：聊天回复完成后由 PetForegroundService 调用
     * 强制刷新情绪等级（忽略等级相同的防抖逻辑）
     */
    fun applyEmotionFromChat(emotionScore: Int) {
        lastEmotionLevel = EmotionLevel.NEUTRAL // 重置，确保一定触发
        applyEmotionExpression(emotionScore)
        if (::rlBehaviorManager.isInitialized) {
            rlBehaviorManager.updateEmotion(emotionScore)
        }
    }

    /**
     * 处理用户交互
     */
    fun handleUserInteraction(interaction: UserInteractionEvent) {
        if (!isRunning) return
        // 用户交互时中断自主移动（A* 暂时禁用）
        // isMoving = false
        // currentPath = emptyList()
        scope.launch {
            val newState = behaviorStateMachine.handleInteraction(interaction)
            PetLogger.d("ServiceLifecycleCoordinator", "Interaction: ${interaction.type}, State: $newState")
        }
    }

    /**
     * 定期更新：情绪分析 → 反馈给 RL → 更新行为状态
     */
    private suspend fun periodicUpdate() {
        while (isRunning) {
            try {
                // 1. 分析当前情绪（行为分析）
                val emotion = behaviorEmotionAnalyzer.analyzeBehaviorEmotion()
                PetLogger.d("ServiceLifecycleCoordinator", "Emotion: $emotion")

                // 2. 将情绪值同步到 RLBehaviorManager（影响下次行为决策）
                rlBehaviorManager.updateEmotion(emotion)

                // 3. 情绪映射到宠物表情（如果当前模型有对应表情）
                applyEmotionExpression(emotion)

                // 4. 驱动行为状态机定期更新
                val newState = behaviorStateMachine.updatePeriodic()
                PetLogger.d("ServiceLifecycleCoordinator", "Periodic state: $newState")

                // 4. A* 自主移动触发（暂时禁用）
                // if (newState == BehaviorState.WALK && !isMoving) {
                //     triggerAutonomousMove()
                // }

                // 5. 预测用户行为并打印详细日志
                val predictions = behaviorPredictor.predictNextHour()
                if (predictions.isNotEmpty()) {
                    predictions.forEachIndexed { index, p ->
                        val appName = behaviorEmotionAnalyzer.getAppName(p.packageName)
                        val minutesLater = (p.predictedTime - System.currentTimeMillis()) / 60_000L
                        val confidencePct = (p.confidence * 100).toInt()
                        PetLogger.d("ServiceLifecycleCoordinator",
                            "行为预测[${index + 1}] 约${minutesLater}分钟后使用「$appName」，置信度：${confidencePct}%")
                    }
                } else {
                    PetLogger.d("ServiceLifecycleCoordinator", "行为预测：暂无预测结果（历史数据不足）")
                }

                delay(30_000L) // 测试模式：30秒更新一次（正式发布改回 60_000L）
            } catch (e: Exception) {
                PetLogger.e("ServiceLifecycleCoordinator", "Error in periodicUpdate", e)
                delay(10_000L)
            }
        }
    }

    // A* 自主移动循环（暂时禁用）
    // private suspend fun autonomousMoveLoop() {
    //     while (isRunning) {
    //         try {
    //             if (isMoving && pathIndex < currentPath.size) {
    //                 val target = currentPath[pathIndex]
    //                 floatManager?.movePetTo(target.x.toInt(), target.y.toInt())
    //                 pathIndex++
    //                 if (pathIndex >= currentPath.size) {
    //                     isMoving = false
    //                     PetLogger.d("ServiceLifecycleCoordinator", "Autonomous move completed")
    //                 }
    //                 delay(60L)
    //             } else {
    //                 delay(200L)
    //             }
    //         } catch (e: Exception) {
    //             PetLogger.e("ServiceLifecycleCoordinator", "Error in autonomousMoveLoop", e)
    //             delay(500L)
    //         }
    //     }
    // }

    // A* 触发自主移动（暂时禁用）
    // fun triggerAutonomousMove() {
    //     val fm = floatManager ?: return
    //     val currentPos = fm.getCurrentPosition() ?: return
    //     val screenW = pathPlanner.screenWidth
    //     val screenH = pathPlanner.screenHeight
    //     val margin = 150
    //     val targetX = (margin + Math.random() * (screenW - margin * 2)).toFloat()
    //     val targetY = (margin + Math.random() * (screenH - margin * 2)).toFloat()
    //     val start = PointF(currentPos.first.toFloat(), currentPos.second.toFloat())
    //     val end = PointF(targetX, targetY)
    //     val rough = pathPlanner.findPath(start, end)
    //     val smooth = pathPlanner.smoothPath(rough)
    //     currentPath = smooth
    //     pathIndex = 0
    //     isMoving = smooth.size > 1
    //     PetLogger.d("ServiceLifecycleCoordinator", "Autonomous move: ${start} -> ${end}, path=${smooth.size} pts")
    // }

    /**
     * 前台应用检测循环（每5秒一次，仅打印日志）
     * 相比 periodicUpdate 更高频，用于近实时感知用户当前在用哪个应用
     */
    private suspend fun foregroundAppLoop() {
        while (isRunning) {
            try {
                val foregroundApp = behaviorEmotionAnalyzer.getCurrentForegroundApp()
                val appName = foregroundApp?.let { behaviorEmotionAnalyzer.getAppName(it) } ?: "null"
                PetLogger.d("ServiceLifecycleCoordinator", "Foreground app: $foregroundApp ($appName)")
            } catch (e: Exception) {
                PetLogger.e("ServiceLifecycleCoordinator", "foregroundAppLoop error", e)
            }
            delay(5_000L)
        }
    }
}
