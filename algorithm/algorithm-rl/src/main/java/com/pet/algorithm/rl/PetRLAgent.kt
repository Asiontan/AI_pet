package com.pet.algorithm.rl

import android.content.Context
import com.pet.core.common.logger.PetLogger
import com.pet.core.common.result.Result
import com.pet.core.domain.model.BehaviorState
import com.pet.core.domain.model.event.PetBehaviorFeedbackEvent
import com.pet.core.domain.model.event.UserInteractionEvent
import com.pet.core.domain.model.event.UserResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * 强化学习代理 - Q-Learning算法实现
 * 根据用户交互数据自主调整宠物行为策略
 */
class PetRLAgent(private val context: Context) {
    
    companion object {
        private const val STATE_COUNT = 6000 // 状态总数（离散化后）
        private const val ACTION_COUNT = 5   // 动作总数
        private const val ALPHA = 0.1f        // 学习率
        private const val GAMMA = 0.9f       // 折扣因子
        private const val EPSILON = 0.2f    // 探索率（20%随机探索）
        private const val Q_TABLE_FILE = "pet_q_table.dat"
    }

    // Q表：stateIndex -> actionIndex -> Q值
    private val qTable = Array(STATE_COUNT) { FloatArray(ACTION_COUNT) { 0f } }
    
    // 当前状态和动作（用于学习）
    private var currentState: RLState? = null
    private var currentAction: PetAction? = null
    
    init {
        loadQTable()
    }

    /**
     * 根据当前状态选择动作
     */
    fun chooseAction(
        clickFrequency: Float,      // 点击频率（次/分钟）
        lastInteractionInterval: Long, // 最近交互间隔（分钟）
        currentHour: Int,            // 当前时段（0-23）
        petEmotion: Int              // 宠物当前情绪（0-10）
    ): PetAction {
        val state = RLState(
            clickFrequency = clickFrequency,
            lastInteractionInterval = lastInteractionInterval,
            currentHour = currentHour,
            petEmotion = petEmotion
        )
        
        val stateIndex = state.toIndex()
        val action = if (Math.random() < EPSILON) {
            // 探索：随机选择动作
            PetAction.values().random()
        } else {
            // 利用：选择Q值最大的动作
            val maxQ = qTable[stateIndex].maxOrNull() ?: 0f
            val bestActionIndex = qTable[stateIndex].indexOfFirst { it == maxQ }
            PetAction.values()[bestActionIndex]
        }
        
        // 保存当前状态和动作，用于后续学习
        currentState = state
        currentAction = action
        
        PetLogger.d("PetRLAgent", "State: $stateIndex, Action: $action, Q-value: ${qTable[stateIndex][action.ordinal]}")
        return action
    }

    /**
     * 学习：根据奖励更新Q表
     */
    suspend fun learn(reward: Int) = withContext(Dispatchers.IO) {
        val state = currentState ?: return@withContext
        val action = currentAction ?: return@withContext
        
        val stateIndex = state.toIndex()
        val actionIndex = action.ordinal
        val nextStateIndex = stateIndex // 简化：假设下一状态相同
        
        // Q值更新公式：Q(s,a) = Q(s,a) + α[r + γ*maxQ(s',a') - Q(s,a)]
        val oldQ = qTable[stateIndex][actionIndex]
        val nextMaxQ = qTable[nextStateIndex].maxOrNull() ?: 0f
        val newQ = oldQ + ALPHA * (reward + GAMMA * nextMaxQ - oldQ)
        qTable[stateIndex][actionIndex] = newQ
        
        PetLogger.d("PetRLAgent", "Learning: state=$stateIndex, action=$actionIndex, reward=$reward, Q: $oldQ -> $newQ")
        
        // 保存Q表
        saveQTable()
    }

    /**
     * 根据用户反馈计算奖励
     */
    fun calculateReward(feedback: UserResponse, interactionDuration: Long): Int {
        return when (feedback) {
            UserResponse.POSITIVE -> {
                // 正面反馈：根据交互时长给予奖励
                when {
                    interactionDuration > 5000 -> 10  // 长时间交互
                    interactionDuration > 2000 -> 5   // 中等交互
                    else -> 3                        // 短时间交互
                }
            }
            UserResponse.NEGATIVE -> -20  // 负面反馈
            UserResponse.NEUTRAL -> 0     // 中性反馈
        }
    }

    /**
     * 状态量化为索引
     */
    private fun RLState.toIndex(): Int {
        // 离散化特征
        val clickFreqBin = when {
            clickFrequency < 1f -> 0
            clickFrequency < 3f -> 1
            clickFrequency < 5f -> 2
            clickFrequency < 10f -> 3
            else -> 4
        }
        
        val intervalBin = when {
            lastInteractionInterval < 5 -> 0      // 0-5分钟
            lastInteractionInterval < 15 -> 1     // 5-15分钟
            lastInteractionInterval < 30 -> 2     // 15-30分钟
            lastInteractionInterval < 60 -> 3     // 30-60分钟
            else -> 4                             // >60分钟
        }
        
        val hourBin = currentHour / 6  // 0-3（每6小时一个区间）
        val emotionBin = (petEmotion / 2).coerceIn(0, 4)  // 0-4（每2个情绪值一个区间）
        
        // 计算状态索引：5×5×4×5 = 500个状态（实际可以更细粒度）
        return clickFreqBin * 500 + intervalBin * 100 + hourBin * 25 + emotionBin * 5
    }

    /**
     * 保存Q表到本地
     */
    private fun saveQTable() {
        try {
            val file = File(context.filesDir, Q_TABLE_FILE)
            ObjectOutputStream(FileOutputStream(file)).use { oos ->
                oos.writeObject(qTable)
            }
            PetLogger.d("PetRLAgent", "Q-table saved")
        } catch (e: Exception) {
            PetLogger.e("PetRLAgent", "Failed to save Q-table", e)
        }
    }

    /**
     * 从本地加载Q表
     */
    private fun loadQTable() {
        try {
            val file = File(context.filesDir, Q_TABLE_FILE)
            if (file.exists()) {
                ObjectInputStream(FileInputStream(file)).use { ois ->
                    val loaded = ois.readObject() as Array<FloatArray>
                    for (i in loaded.indices) {
                        if (i < qTable.size) {
                            qTable[i] = loaded[i].copyOf()
                        }
                    }
                }
                PetLogger.d("PetRLAgent", "Q-table loaded")
            }
        } catch (e: Exception) {
            PetLogger.e("PetRLAgent", "Failed to load Q-table", e)
        }
    }

    /**
     * 重置Q表（用于测试）
     */
    fun resetQTable() {
        for (i in qTable.indices) {
            qTable[i].fill(0f)
        }
        saveQTable()
    }
}

/**
 * 强化学习状态
 */
data class RLState(
    val clickFrequency: Float,      // 点击频率（次/分钟）
    val lastInteractionInterval: Long, // 最近交互间隔（分钟）
    val currentHour: Int,            // 当前时段（0-23）
    val petEmotion: Int              // 宠物当前情绪（0-10）
)

/**
 * 宠物动作（对应BehaviorState）
 */
enum class PetAction {
    IDLE,        // 静止
    MOVE,        // 移动
    WAG_TAIL,    // 摇尾巴
    POPUP,       // 弹窗提醒
    PLAY_SOUND;   // 播放音效
    
    fun toBehaviorState(): BehaviorState {
        return when (this) {
            IDLE -> BehaviorState.IDLE
            MOVE -> BehaviorState.WALK
            WAG_TAIL -> BehaviorState.IDLE
            POPUP -> BehaviorState.IDLE
            PLAY_SOUND -> BehaviorState.IDLE
        }
    }
}

