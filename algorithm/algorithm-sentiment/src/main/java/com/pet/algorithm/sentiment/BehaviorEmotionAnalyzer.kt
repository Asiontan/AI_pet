package com.pet.algorithm.sentiment

import android.app.usage.UsageStatsManager
import android.content.Context
import com.pet.core.common.logger.PetLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于用户行为的情绪推断
 * 通过分析用户的使用模式推断情绪状态
 */
class BehaviorEmotionAnalyzer(private val context: Context) {
    
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    
    /**
     * 分析用户行为特征，推断情绪
     * 返回情绪值：0-10（0=非常消极，10=非常积极）
     */
    suspend fun analyzeBehaviorEmotion(): Int = withContext(Dispatchers.IO) {
        try {
            // 特征1：解锁频率（频繁解锁可能表示烦躁）
            val unlockFrequency = getUnlockFrequency()
            
            // 特征2：应用使用时长（长时间使用可能表示专注或疲惫）
            val usageDuration = getRecentUsageDuration()
            
            // 特征3：应用类型（社交类应用使用多可能表示活跃）
            val socialAppUsage = getSocialAppUsage()
            
            // 特征4：屏幕亮度变化（快速变化可能表示烦躁）
            val brightnessVariation = getBrightnessVariation()
            
            // 综合评分（简化版规则）
            var emotionScore = 5 // 中性起点
            
            // 解锁频率影响
            when {
                unlockFrequency > 20 -> emotionScore -= 2  // 频繁解锁，可能烦躁
                unlockFrequency < 5 -> emotionScore += 1  // 较少解锁，可能平静
            }
            
            // 使用时长影响
            when {
                usageDuration > 120 -> emotionScore -= 1  // 长时间使用，可能疲惫
                usageDuration in 30..60 -> emotionScore += 1  // 适度使用，可能专注
            }
            
            // 社交应用使用影响
            if (socialAppUsage > 0.5f) {
                emotionScore += 1  // 社交活跃，可能积极
            }
            
            // 亮度变化影响
            if (brightnessVariation > 0.3f) {
                emotionScore -= 1  // 快速变化，可能烦躁
            }
            
            val finalScore = emotionScore.coerceIn(0, 10)
            PetLogger.d("BehaviorEmotionAnalyzer", "Emotion score: $finalScore")
            return@withContext finalScore
        } catch (e: Exception) {
            PetLogger.e("BehaviorEmotionAnalyzer", "Failed to analyze behavior emotion", e)
            return@withContext 5 // 默认中性
        }
    }
    
    private fun getUnlockFrequency(): Int {
        // 简化实现：返回模拟值
        // 实际应该通过UsageStatsManager获取
        return 10
    }
    
    private fun getRecentUsageDuration(): Long {
        // 返回最近1小时的使用时长（分钟）
        return 45
    }
    
    private fun getSocialAppUsage(): Float {
        // 返回社交类应用使用占比（0-1）
        return 0.3f
    }
    
    private fun getBrightnessVariation(): Float {
        // 返回屏幕亮度变化率（0-1）
        return 0.2f
    }
}

