package com.pet.core.data.chat

/**
 * 聊天消息模型
 */
data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val isUser: Boolean,
    val isLoading: Boolean = false  // 流式输出时宠物消息的占位状态
)

