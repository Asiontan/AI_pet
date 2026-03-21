package com.pet.algorithm.prediction

import android.app.usage.UsageStatsManager
import android.content.Context
import com.pet.core.common.logger.PetLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.ln

/**
 * 用户行为预测器（升级版）
 * 基于以下多维度加权统计预测未来行为：
 * 1. 同时段历史频率（主权重）
 * 2. 使用时长权重（时长越长权重越高）
 * 3. 工作日/周末区分（同类型日期优先）
 * 4. 时间衰减（越近的历史数据权重越高）
 * 5. 过滤系统/桌面无意义进程
 */
class BehaviorPredictor(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    // 需要过滤的系统/无意义包名前缀
    private val systemPackagePrefixes = listOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.vivo.launcher",
        "com.samsung.android.app.launcher",
        "com.bbk.launcher",
        "android",
        "com.android.phone",
        "com.android.settings",
        context.packageName  // 排除自身
    )

    /**
     * 预测未来1小时内最可能使用的应用，返回 Top3
     */
    suspend fun predictNextHour(): List<PredictedBehavior> = withContext(Dispatchers.IO) {
        try {
            val records = loadBehaviorHistory()
            if (records.isEmpty()) {
                PetLogger.d("BehaviorPredictor", "No history data available")
                return@withContext emptyList()
            }

            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val isCurrentWeekend = isWeekend(now)
            val nowMs = System.currentTimeMillis()

            // 按包名聚合加权分数
            val scoreMap = mutableMapOf<String, Double>()

            records.forEach { record ->
                val cal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                val recHour = cal.get(Calendar.HOUR_OF_DAY)
                val recIsWeekend = isWeekend(cal)

                // 1. 时段相关性：同小时最高，相差1小时次之，超过2小时不计
                val hourDiff = minOf(
                    Math.abs(recHour - currentHour),
                    24 - Math.abs(recHour - currentHour)
                )
                val hourWeight = when (hourDiff) {
                    0    -> 1.0
                    1    -> 0.5
                    else -> 0.0
                }
                if (hourWeight == 0.0) return@forEach

                // 2. 工作日/周末匹配权重
                val dayTypeWeight = if (recIsWeekend == isCurrentWeekend) 1.2 else 0.8

                // 3. 时间衰减：使用对数衰减，7天内权重1.0，越久越低
                val daysAgo = (nowMs - record.timestamp) / (24 * 60 * 60 * 1000.0)
                val decayWeight = if (daysAgo <= 0) 1.0
                    else maxOf(0.1, 1.0 - ln(daysAgo + 1) / ln(15.0))

                // 4. 使用时长权重（分钟，上限60分钟）
                val durationMin = (record.duration / 60_000.0).coerceIn(0.0, 60.0)
                val durationWeight = 1.0 + durationMin / 60.0  // 1.0 ~ 2.0

                val totalWeight = hourWeight * dayTypeWeight * decayWeight * durationWeight
                scoreMap[record.packageName] =
                    (scoreMap[record.packageName] ?: 0.0) + totalWeight
            }

            if (scoreMap.isEmpty()) return@withContext emptyList()

            // 归一化为置信度（最高分 = 1.0）
            val maxScore = scoreMap.values.max()
            val predictions = scoreMap.entries
                .sortedByDescending { it.value }
                .take(3)
                .mapIndexed { rank, (pkg, score) ->
                    val confidence = (score / maxScore).toFloat().coerceIn(0f, 1f)
                    // 预测时间：置信度越高越快发生
                    // 第1名：2~8分钟后；第2名：5~15分钟后；第3名：10~25分钟后
                    val baseMinutes = when (rank) {
                        0 -> 2L + (6 * (1f - confidence)).toLong()   // 2~8分钟
                        1 -> 5L + (10 * (1f - confidence)).toLong()  // 5~15分钟
                        else -> 10L + (15 * (1f - confidence)).toLong() // 10~25分钟
                    }
                    PredictedBehavior(
                        packageName = pkg,
                        predictedTime = nowMs + baseMinutes * 60 * 1000L,
                        confidence = confidence
                    )
                }

            PetLogger.d("BehaviorPredictor",
                "Predicted ${predictions.size} behaviors (from ${records.size} records)")
            return@withContext predictions
        } catch (e: Exception) {
            PetLogger.e("BehaviorPredictor", "Failed to predict behavior", e)
            return@withContext emptyList()
        }
    }

    /**
     * 从 UsageStatsManager 加载过去14天的行为历史
     * 使用 INTERVAL_DAILY 获取每日聚合数据，再拆分为按天的记录
     */
    private suspend fun loadBehaviorHistory(): List<BehaviorRecord> = withContext(Dispatchers.IO) {
        try {
            val manager = usageStatsManager ?: return@withContext emptyList()
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 14 * 24 * 60 * 60 * 1000L  // 过去14天（扩大样本）

            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            ) ?: return@withContext emptyList()

            val result = stats
                .filter { stat ->
                    stat.totalTimeInForeground > 0 &&
                    stat.lastTimeUsed >= startTime &&
                    systemPackagePrefixes.none { stat.packageName.startsWith(it) }
                }
                .map { stat ->
                    BehaviorRecord(
                        packageName = stat.packageName,
                        timestamp = stat.lastTimeUsed,
                        duration = stat.totalTimeInForeground
                    )
                }

            PetLogger.d("BehaviorPredictor",
                "Loaded ${result.size} valid records (from ${stats.size} total)")
            return@withContext result
        } catch (e: Exception) {
            PetLogger.e("BehaviorPredictor", "Failed to load behavior history", e)
            return@withContext emptyList()
        }
    }

    /** 记录当前行为（外部调用接口） */
    fun recordBehavior(packageName: String, duration: Long) {
        PetLogger.d("BehaviorPredictor", "Recorded: $packageName, ${duration / 1000}s")
    }

    /** 判断给定 Calendar 是否为周末 */
    private fun isWeekend(cal: Calendar): Boolean {
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }
}

data class BehaviorRecord(
    val packageName: String,
    val timestamp: Long,
    val duration: Long
)

data class PredictedBehavior(
    val packageName: String,
    val predictedTime: Long,
    val confidence: Float  // 0.0-1.0，相对最高分归一化
)
