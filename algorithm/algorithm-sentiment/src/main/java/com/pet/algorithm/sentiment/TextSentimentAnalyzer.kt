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
    
    private val positiveWords = setOf(
        "开心", "高兴", "快乐", "喜欢", "爱", "好", "棒", "赞", "不错", "很好",
        "幸福", "满足", "期待", "兴奋", "感动", "温暖", "舒服", "放松", "惊喜", "感谢",
        "谢谢", "哈哈", "嘻嘻", "太好了", "厉害", "优秀", "完美", "有趣", "好玩", "可爱",
        "喜悦", "美好", "精彩", "愉快", "欣慰", "自豪", "安心", "甜", "暖", "乐",
        "happy", "good", "great", "love", "like", "nice", "awesome", "excellent",
        "wonderful", "amazing", "fantastic", "beautiful", "excited", "grateful", "thankful",
        "joyful", "cheerful", "pleased", "delighted", "perfect", "brilliant", "cool", "fun"
    )

    private val negativeWords = setOf(
        "难过", "伤心", "生气", "讨厌", "烦", "累", "困", "不好", "糟糕", "差",
        "焦虑", "紧张", "郁闷", "无聊", "孤独", "寂寞", "失望", "沮丧", "痛苦", "崩溃",
        "害怕", "恐惧", "担心", "后悔", "委屈", "心烦", "压抑", "绝望", "无奈", "心累",
        "烦躁", "抱怨", "倒霉", "受伤", "想哭", "头疼", "难受", "丧", "emo", "破防",
        "sad", "angry", "tired", "bad", "hate", "terrible", "awful", "sick",
        "anxious", "depressed", "lonely", "frustrated", "disappointed", "worried", "upset",
        "stressed", "bored", "scared", "hopeless", "exhausted", "miserable", "painful", "annoyed"
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

