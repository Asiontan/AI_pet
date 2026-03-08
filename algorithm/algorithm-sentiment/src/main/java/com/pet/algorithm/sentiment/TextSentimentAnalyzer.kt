package com.pet.algorithm.sentiment

import android.content.Context
import com.pet.core.common.logger.PetLogger
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 文本情感分析器（轻量化实现）
 * 使用简单的关键词匹配和规则，避免依赖大型NLP模型
 * 实际项目中可以使用TensorFlow Lite加载MobileBERT模型
 */
class TextSentimentAnalyzer(private val context: Context) {
    
    // 积极情感关键词
    private val positiveWords = setOf(
        "开心", "高兴", "快乐", "喜欢", "爱", "好", "棒", "赞", "不错", "很好",
        "happy", "good", "great", "love", "like", "nice", "awesome", "excellent"
    )
    
    // 消极情感关键词
    private val negativeWords = setOf(
        "难过", "伤心", "生气", "讨厌", "烦", "累", "困", "不好", "糟糕", "差",
        "sad", "angry", "tired", "bad", "hate", "terrible", "awful", "sick"
    )
    
    /**
     * 分析文本情绪（返回积极度：0.0-1.0，1.0为最积极）
     */
    fun analyze(text: String): Float {
        if (text.isBlank()) return 0.5f // 中性
        
        val lowerText = text.lowercase()
        var positiveCount = 0
        var negativeCount = 0
        
        // 统计积极和消极关键词
        positiveWords.forEach { word ->
            if (lowerText.contains(word.lowercase())) {
                positiveCount++
            }
        }
        
        negativeWords.forEach { word ->
            if (lowerText.contains(word.lowercase())) {
                negativeCount++
            }
        }
        
        // 计算积极度
        val totalWords = positiveCount + negativeCount
        if (totalWords == 0) {
            return 0.5f // 中性
        }
        
        val sentiment = positiveCount.toFloat() / totalWords
        PetLogger.d("TextSentimentAnalyzer", "Text: $text, Sentiment: $sentiment")
        return sentiment
    }
    
    /**
     * 获取情绪标签
     */
    fun getEmotionLabel(text: String): EmotionLabel {
        val score = analyze(text)
        return when {
            score > 0.7f -> EmotionLabel.POSITIVE
            score < 0.3f -> EmotionLabel.NEGATIVE
            else -> EmotionLabel.NEUTRAL
        }
    }
}

enum class EmotionLabel {
    POSITIVE,  // 积极
    NEGATIVE,  // 消极
    NEUTRAL    // 中性
}

