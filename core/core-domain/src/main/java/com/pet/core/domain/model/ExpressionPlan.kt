package com.pet.core.domain.model

data class ExpressionPlan(
    val expressionName: String? = null,
    val motionName: String? = null,
    val bubbleText: String? = null,
    val triggerBubble: Boolean = false,
    val clearExpression: Boolean = false,
    val bubbleDelayMs: Long = 0L,
    val interventionLevel: Int = 0,
    val triggerNonverbalOnly: Boolean = false,
    val motionDelayMs: Long = 0L,
    val expressionFirst: Boolean = true,
    val sequenceGapMs: Long = 0L
)
